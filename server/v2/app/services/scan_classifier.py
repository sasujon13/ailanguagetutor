"""Document classification + recommended Clean / AI Clean mode and level."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from app.services.scan_analyzer import ScanAnalysisMetrics
from app.services.scan_standards import (
    Classify,
    Recommend,
    recommend_mode,
    route_for_class,
)


class DocumentClass(str, Enum):
    TEXT_HEAVY = "text-heavy"
    VISUAL_HEAVY = "visual-heavy"
    MIXED = "mixed-content"
    OFFICIAL_ID = "official-id"
    MACHINE_READABLE = "machine-readable"
    HANDWRITTEN = "handwritten"
    DAMAGED = "damaged-document"


@dataclass(frozen=True)
class ScanEnhanceRecommendation:
    document_class: DocumentClass
    recommended_mode: str  # "clean" | "ai_clean"
    recommended_level: int
    dewarp_cap: float  # max dewarp strength 0..1
    label: str


def classify(metrics: ScanAnalysisMetrics) -> DocumentClass:
    c = Classify
    if metrics.has_machine_readable:
        return DocumentClass.MACHINE_READABLE
    if (
        c.OFFICIAL_ID_ASPECT_MIN < metrics.aspect_ratio < c.OFFICIAL_ID_ASPECT_MAX
        and metrics.edge_density > c.OFFICIAL_ID_EDGE_MIN
        and metrics.color_richness < c.OFFICIAL_ID_COLOR_MAX
    ):
        return DocumentClass.OFFICIAL_ID
    if metrics.damage_score > c.DAMAGE_SCORE_MIN or metrics.wrinkle_score > c.WRINKLE_SCORE_MIN:
        return DocumentClass.DAMAGED
    if metrics.color_richness > c.VISUAL_COLOR_MIN and metrics.edge_density < c.VISUAL_EDGE_MAX:
        return DocumentClass.VISUAL_HEAVY
    if metrics.edge_density > c.TEXT_EDGE_MIN and metrics.color_richness < c.TEXT_COLOR_MAX:
        return DocumentClass.TEXT_HEAVY
    if metrics.edge_density < c.HANDWRITTEN_EDGE_MAX:
        return DocumentClass.HANDWRITTEN
    return DocumentClass.MIXED


def recommend(metrics: ScanAnalysisMetrics, premium: bool = True) -> ScanEnhanceRecommendation:
    doc_class = classify(metrics)
    route = route_for_class(doc_class.value, premium)
    level = route.level
    mode = recommend_mode(doc_class.value, premium, route.force_clean)

    if metrics.shadow_severity > Recommend.SHADOW_BOOST_THRESHOLD:
        level = min(7, level + 1)
    if metrics.blur_score < Recommend.BLUR_BOOST_THRESHOLD:
        level = min(7, level + 1)

    label = f"{'AI Clean' if mode == 'ai_clean' else 'Clean'} Level {level}"
    return ScanEnhanceRecommendation(
        document_class=doc_class,
        recommended_mode=mode,
        recommended_level=level,
        dewarp_cap=route.dewarp_cap,
        label=label,
    )

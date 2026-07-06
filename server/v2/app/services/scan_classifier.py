"""Document classification + recommended Clean / AI Clean mode and level."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from app.services.scan_analyzer import ScanAnalysisMetrics


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
    if metrics.has_machine_readable:
        return DocumentClass.MACHINE_READABLE
    if 0.55 < metrics.aspect_ratio < 1.8 and metrics.edge_density > 0.08 and metrics.color_richness < 0.25:
        return DocumentClass.OFFICIAL_ID
    if metrics.damage_score > 0.35 or metrics.wrinkle_score > 0.45:
        return DocumentClass.DAMAGED
    if metrics.color_richness > 0.35 and metrics.edge_density < 0.12:
        return DocumentClass.VISUAL_HEAVY
    if metrics.edge_density > 0.14 and metrics.color_richness < 0.2:
        return DocumentClass.TEXT_HEAVY
    if metrics.edge_density < 0.06:
        return DocumentClass.HANDWRITTEN
    return DocumentClass.MIXED


def recommend(metrics: ScanAnalysisMetrics, premium: bool = True) -> ScanEnhanceRecommendation:
    doc_class = classify(metrics)
    mode = "ai_clean" if premium else "clean"
    level = 3
    dewarp_cap = 0.7

    match doc_class:
        case DocumentClass.TEXT_HEAVY:
            level = 5 if premium else 5
            dewarp_cap = 0.85
        case DocumentClass.VISUAL_HEAVY:
            level = 4 if premium else 4
            dewarp_cap = 0.5
        case DocumentClass.MIXED:
            level = 5 if premium else 4
            dewarp_cap = 0.65
        case DocumentClass.OFFICIAL_ID:
            level = 2 if premium else 2
            dewarp_cap = 0.25
            mode = "clean"  # preserve geometry — offline safer
        case DocumentClass.MACHINE_READABLE:
            level = 3 if premium else 3
            dewarp_cap = 0.2
            mode = "clean"
        case DocumentClass.HANDWRITTEN:
            level = 3 if premium else 3
            dewarp_cap = 0.45
        case DocumentClass.DAMAGED:
            level = 7 if premium else 6
            dewarp_cap = 0.95

    if metrics.shadow_severity > 0.4:
        level = min(7, level + 1)
    if metrics.blur_score < 0.15:
        level = min(7, level + 1)

    label = f"{'AI Clean' if mode == 'ai_clean' else 'Clean'} Level {level}"
    return ScanEnhanceRecommendation(
        document_class=doc_class,
        recommended_mode=mode,
        recommended_level=level,
        dewarp_cap=dewarp_cap,
        label=label,
    )

"""Global scan-enhancement constants — language-agnostic, same for all users."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

MIN_LEVEL = 0
MAX_LEVEL = 7
COMPARE_PREVIEW_LOW = 1
COMPARE_PREVIEW_HIGH = 7
READABILITY_ROLLBACK_RATIO = 0.92
EXPORT_JPEG_QUALITY = 92


class Classify:
    OFFICIAL_ID_ASPECT_MIN = 0.55
    OFFICIAL_ID_ASPECT_MAX = 1.8
    OFFICIAL_ID_EDGE_MIN = 0.08
    OFFICIAL_ID_COLOR_MAX = 0.25
    DAMAGE_SCORE_MIN = 0.35
    WRINKLE_SCORE_MIN = 0.45
    VISUAL_COLOR_MIN = 0.35
    VISUAL_EDGE_MAX = 0.12
    TEXT_EDGE_MIN = 0.14
    TEXT_COLOR_MAX = 0.2
    HANDWRITTEN_EDGE_MAX = 0.06


class Recommend:
    DEFAULT_LEVEL = 3
    DEFAULT_DEWARP_CAP = 0.7
    SHADOW_BOOST_THRESHOLD = 0.4
    BLUR_BOOST_THRESHOLD = 0.15


@dataclass(frozen=True)
class Route:
    level: int
    dewarp_cap: float
    force_clean: bool = False


def route_for_class(doc_class: str, premium: bool) -> Route:
    """Mirror Android ScanEnhanceStandards.route()."""
    match doc_class:
        case "text-heavy":
            return Route(5, 0.85)
        case "visual-heavy":
            return Route(4, 0.5)
        case "mixed-content":
            return Route(5 if premium else 4, 0.65)
        case "official-id":
            return Route(2, 0.25, force_clean=True)
        case "machine-readable":
            return Route(3, 0.2, force_clean=True)
        case "handwritten":
            return Route(3, 0.45)
        case "damaged-document":
            return Route(7 if premium else 6, 0.95)
        case _:
            return Route(Recommend.DEFAULT_LEVEL, Recommend.DEFAULT_DEWARP_CAP)


def recommend_mode(doc_class: str, premium: bool, force_clean: bool) -> str:
    if force_clean or not premium:
        return "clean"
    if doc_class in ("official-id", "machine-readable"):
        return "clean"
    return "ai_clean"

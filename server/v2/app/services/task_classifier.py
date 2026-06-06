"""Classify requests into translation, answer/tutor, or OCR cleanup."""

from enum import Enum

from app.schemas import AiRequest, InputSource, ProcessingIntent


class TaskIntent(str, Enum):
    TRANSLATION = "translation"
    ANSWER = "answer_mode"
    OCR_CLEANUP = "ocr_cleanup"


_TRANSLATE_HINTS = (
    "translate",
    "translation",
    "in english",
    "in french",
    "convert to",
)

_ANSWER_HINTS = (
    "why",
    "how",
    "explain",
    "what does",
    "meaning of",
    "grammar",
    "help me understand",
)


def classify_intent(req: AiRequest, endpoint: str = "") -> TaskIntent:
    """Rule-based intent classifier (lightweight model optional later)."""
    if endpoint == "clean-ocr":
        return TaskIntent.OCR_CLEANUP

    if req.processing_intent == ProcessingIntent.TRANSLATION:
        return TaskIntent.TRANSLATION

    if req.ai_engine_mode == 2:
        return TaskIntent.TRANSLATION

    lower = req.text.lower().strip()
    if any(h in lower for h in _TRANSLATE_HINTS) and not any(h in lower for h in _ANSWER_HINTS):
        return TaskIntent.TRANSLATION

    if req.input_source == InputSource.OCR and len(lower) < 80 and "?" not in lower:
        if not any(h in lower for h in _ANSWER_HINTS):
            return TaskIntent.OCR_CLEANUP

    return TaskIntent.ANSWER

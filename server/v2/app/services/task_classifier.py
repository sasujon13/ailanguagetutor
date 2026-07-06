"""Classify requests into translation, answer/tutor, coding, or OCR cleanup."""

from enum import Enum
import re

from app.schemas import AiRequest, InputSource, ProcessingIntent


class TaskIntent(str, Enum):
    TRANSLATION = "translation"
    ANSWER = "answer_mode"
    CODING = "coding"
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

_CODING_KEYWORDS = (
    "python",
    "javascript",
    "typescript",
    "kotlin",
    "java",
    "c++",
    "c#",
    "algorithm",
    "debug",
    "compile",
    "syntax error",
    "stack trace",
    "regex",
    "sql",
    "html",
    "css",
    "react",
    "android studio",
    "api endpoint",
    "for loop",
    "while loop",
    "recursion",
    "binary search",
    "leetcode",
    "write code",
    "fix this code",
    "fix my code",
    "implement",
    "refactor",
    "programming",
    "source code",
)

_CODING_SNIPPETS = (
    "```",
    "def ",
    "class ",
    "function ",
    "import ",
    "public static",
    "console.log",
    "println",
    "=>",
    "#include",
    "<?php",
    "select ",
    "var ",
    "const ",
    "let ",
    "fun ",
    "void ",
    "int main",
    "fn ",
    "struct ",
    "interface ",
    "async ",
    "await ",
)


def is_coding_question(text: str) -> bool:
    """Heuristic: code blocks, syntax, or dev keywords (not plain grammar tutoring)."""
    if not text or not text.strip():
        return False
    lower = text.lower()
    if "```" in text:
        return True
    if any(snippet in text or snippet in lower for snippet in _CODING_SNIPPETS):
        return True
    keyword_hits = sum(1 for kw in _CODING_KEYWORDS if kw in lower)
    if keyword_hits >= 2:
        return True
    if keyword_hits >= 1 and re.search(r"\b(code|coding|program|script|bug)\b", lower):
        return True
    if re.search(r"\b(fix|write|implement|debug)\b.+\b(function|class|method|loop)\b", lower):
        return True
    return False


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

    if is_coding_question(req.text):
        return TaskIntent.CODING

    return TaskIntent.ANSWER

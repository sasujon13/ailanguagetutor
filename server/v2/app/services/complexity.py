"""Complexity scoring for Answer Mode — drives Qwen 7B vs 14B vs Mistral."""

from dataclasses import dataclass


@dataclass
class ComplexityResult:
    score: int
    bucket: str  # LOW | MEDIUM | HIGH
    question_type: str


def detect_question_type(text: str) -> str:
    lower = text.lower()
    for w in ("why", "how", "explain"):
        if w in lower:
            return w
    if "detail" in lower or "in depth" in lower or "thoroughly" in lower:
        return "detail"
    return ""


def complexity_score(
    text: str,
    ocr_noise: bool,
    lang_count: int = 1,
    wants_detail: bool = False,
) -> ComplexityResult:
    score = 0
    qtype = detect_question_type(text)

    if len(text) > 500:
        score += 2
    if qtype in ("why", "how", "explain"):
        score += 3
    if qtype == "detail" or wants_detail:
        score += 2
    if lang_count > 1:
        score += 2
    if ocr_noise:
        score += 2

    if score <= 3:
        bucket = "LOW"
    elif score <= 6:
        bucket = "MEDIUM"
    else:
        bucket = "HIGH"

    return ComplexityResult(score=score, bucket=bucket, question_type=qtype)


def complexity_bucket(text: str, ocr_noise: bool, lang_count: int = 1) -> str:
    return complexity_score(text, ocr_noise, lang_count).bucket

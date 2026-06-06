"""Batch UI string translation — Home AI NLLB/LLM, optional Google fallback."""

import json
import logging
import re

from app.schemas import TranslateStringsRequest, TranslateStringsResponse

logger = logging.getLogger(__name__)


def _cache_key(target: str) -> str:
    return f"translate_strings:v1:{target.lower()}"


def _extract_json(raw: str) -> dict:
    text = raw.strip()
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    if fence:
        text = fence.group(1).strip()
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        text = text[start : end + 1]
    return json.loads(text)


def build_batch_prompt(source: str, target: str, strings: dict[str, str]) -> str:
    payload = json.dumps(strings, ensure_ascii=False)
    return (
        f"Translate these mobile app UI strings from {source} to {target}.\n"
        f"Return ONLY a JSON object with the SAME keys and translated values.\n"
        f"Keep placeholders like %s unchanged.\n\n{payload}"
    )


def parse_batch_response(raw: str, fallback: dict[str, str]) -> dict[str, str]:
    try:
        data = _extract_json(raw)
        if isinstance(data, dict):
            return {k: str(data.get(k, v)) for k, v in fallback.items()}
    except (json.JSONDecodeError, TypeError, ValueError) as e:
        logger.warning("translate-strings JSON parse failed: %s", e)
    return fallback

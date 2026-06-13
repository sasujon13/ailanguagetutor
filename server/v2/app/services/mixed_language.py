"""Detect mixed-script input (e.g. Bengali + English) for smarter prompts."""

from __future__ import annotations

import re


def _script_counts(text: str) -> dict[str, int]:
    latin = bengali = arabic = devanagari = cjk = other = 0
    for ch in text:
        cp = ord(ch)
        if cp <= 0x024F and ch.isalpha():
            latin += 1
        elif 0x0980 <= cp <= 0x09FF:
            bengali += 1
        elif 0x0600 <= cp <= 0x06FF or 0x0750 <= cp <= 0x077F:
            arabic += 1
        elif 0x0900 <= cp <= 0x097F:
            devanagari += 1
        elif 0x4E00 <= cp <= 0x9FFF or 0x3040 <= cp <= 0x30FF:
            cjk += 1
        elif not ch.isspace():
            other += 1
    return {
        "latin": latin,
        "bn": bengali,
        "ar": arabic,
        "hi": devanagari,
        "cjk": cjk,
        "other": other,
    }


def _script_to_lang(script: str, fallback: str) -> str:
    mapping = {"bn": "bn", "ar": "ar", "hi": "hi", "latin": "en"}
    if script == "cjk":
        fb = fallback.lower()
        if fb.startswith("ja") or fb.startswith("ko"):
            return fb
        return "zh"
    return mapping.get(script, fallback.lower())


def analyze_mixed(text: str, configured_input: str, configured_output: str) -> dict:
    counts = _script_counts(text)
    ranked = sorted(
        ((k, v) for k, v in counts.items() if k != "other" and v > 0),
        key=lambda x: x[1],
        reverse=True,
    )
    top = ranked[0] if ranked else None
    second = ranked[1] if len(ranked) > 1 else None

    detected_primary = _script_to_lang(top[0], configured_input) if top else None
    detected_secondary = None
    if second:
        sec_lang = _script_to_lang(second[0], configured_input)
        if sec_lang != detected_primary:
            detected_secondary = sec_lang

    is_mixed = (
        top is not None
        and second is not None
        and top[1] >= 8
        and second[1] >= 8
        and top[0] != second[0]
    )

    has_math = bool(re.search(r"[=^+\-*/\\]|\\frac|\\tan|\\alpha|\$\$|∫|∑|√|≤|≥", text))
    has_code = (
        "```" in text
        or any(line.lstrip().startswith("//") for line in text.splitlines())
        or bool(re.search(
            r"(?m)^\s*(fun |def |class |import |#include|public |private |val |var |const )",
            text,
        ))
    )

    return {
        "configured_input": configured_input.lower(),
        "configured_output": configured_output.lower(),
        "detected_primary": detected_primary,
        "detected_secondary": detected_secondary if is_mixed else None,
        "is_mixed": is_mixed,
        "has_math": has_math,
        "has_code": has_code,
    }

"""Heuristics for NLLB-600M short-phrase and low-quality output detection."""

_SHORT_PHRASE_MAX_CHARS = 48
_SHORT_PHRASE_MAX_WORDS = 6

# NLLB-600M often emits this for unrelated English greetings.
_SUSPICIOUS_EN_FR_FRAGMENTS = ("je vous en prie",)


def is_short_phrase(text: str) -> bool:
    stripped = text.strip()
    if not stripped:
        return False
    words = stripped.split()
    return len(stripped) <= _SHORT_PHRASE_MAX_CHARS and len(words) <= _SHORT_PHRASE_MAX_WORDS


def nllb_result_suspicious(source: str, target: str, original: str, translated: str) -> bool:
    if not translated.strip():
        return True

    orig_norm = original.strip().lower()
    trans_norm = translated.strip().lower()
    if orig_norm == trans_norm and source.lower() != target.lower():
        return True

    if source.lower() == "en" and target.lower() == "fr":
        if any(fragment in trans_norm for fragment in _SUSPICIOUS_EN_FR_FRAGMENTS):
            if "please" not in orig_norm and "thank" not in orig_norm:
                return True

    return False

"""Tests for short-phrase and NLLB quality heuristics."""

from app.services.translation_quality import is_short_phrase, nllb_result_suspicious


def test_short_phrase_detects_greetings():
    assert is_short_phrase("Good morning.")
    assert is_short_phrase("Hello world.")
    assert not is_short_phrase("The weather is nice today and we should go outside.")


def test_nllb_suspicious_en_fr_greeting():
    assert nllb_result_suspicious("en", "fr", "Good morning.", "Je vous en prie.")
    assert nllb_result_suspicious("en", "fr", "Hello.", "Je vous en prie.")
    assert not nllb_result_suspicious("en", "fr", "Please help me.", "Je vous en prie.")


def test_nllb_suspicious_unchanged_text():
    assert nllb_result_suspicious("en", "fr", "Hello", "Hello")
    assert not nllb_result_suspicious("en", "en", "Hello", "Hello")


def test_nllb_ok_translation():
    assert not nllb_result_suspicious("en", "fr", "Hello world.", "Bonjour le monde.")

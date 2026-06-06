"""Tests for grammar prefetch planning."""

from app.services.grammar_prompt import build_grammar_prompt
from app.schemas import GrammarDepth


def test_build_grammar_prompt_word():
    prompt = build_grammar_prompt("Bonjour le monde.", "Bonjour", "fr", "en", GrammarDepth.WORD)
    assert "Bonjour" in prompt
    assert "grammar role" in prompt.lower() or "grammar" in prompt.lower()


def test_build_grammar_prompt_sentence():
    prompt = build_grammar_prompt("Hello world.", "Hello", "en", "fr", GrammarDepth.SENTENCE)
    assert "Hello world." in prompt


def test_build_grammar_prompt_paragraph():
    prompt = build_grammar_prompt("Line one.\n\nLine two.", None, "en", "fr", GrammarDepth.PARAGRAPH)
    assert "paragraph" in prompt.lower()

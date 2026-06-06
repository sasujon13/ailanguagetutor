"""Tests for grammar book parsing."""

from app.services.grammar_book import (
    build_grammar_book_prompt,
    build_section_enrich_prompt,
    fallback_grammar_book,
    grammar_book_enrich_cache_key,
    parse_grammar_book,
    parse_section_enrich,
)


SAMPLE_JSON = """
{
  "title": "Grammar Book — French",
  "language_code": "fr",
  "language_name": "French",
  "chapters": [
    {
      "number": 1,
      "title": "Sounds",
      "summary": "Pronunciation basics",
      "sections": [
        {
          "heading": "Alphabet",
          "body": "French uses the Latin alphabet.",
          "examples": ["bonjour — hello"]
        }
      ]
    }
  ]
}
"""


def test_parse_grammar_book_json():
    book = parse_grammar_book(SAMPLE_JSON, "fr", "French")
    assert book.language_code == "fr"
    assert len(book.chapters) == 1
    assert book.chapters[0].title == "Sounds"
    assert book.chapters[0].sections[0].examples[0] == "bonjour — hello"


def test_fallback_grammar_book():
    book = fallback_grammar_book("de", "German")
    assert book.language_code == "de"
    assert len(book.chapters) >= 6


def test_build_grammar_book_prompt():
    prompt = build_grammar_book_prompt("fr", "French")
    assert "French" in prompt
    assert "chapters" in prompt


def test_parse_section_enrich():
    raw = '{"expanded_body": "More detail here.", "extra_examples": ["je suis — I am"], "learner_tip": "Practice daily."}'
    result = parse_section_enrich(raw, "Original body.")
    assert "More detail" in result["expanded_body"]
    assert result["extra_examples"][0] == "je suis — I am"
    assert result["learner_tip"] == "Practice daily."


def test_grammar_book_enrich_cache_key():
    key = grammar_book_enrich_cache_key("fr", 2, "Present tense")
    assert key.startswith("grammar_book_enrich:")
    assert ":fr:2:" in key

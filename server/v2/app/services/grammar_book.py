"""Structured grammar learning book generation."""

import json
import logging
import re

from app.schemas import GrammarBookChapter, GrammarBookResponse, GrammarBookSection

logger = logging.getLogger(__name__)

BOOK_VERSION = "v1"


def grammar_book_cache_key(language_code: str) -> str:
    return f"grammar_book:{BOOK_VERSION}:{language_code.lower()}"


def grammar_book_enrich_cache_key(
    language_code: str,
    chapter_number: int,
    section_heading: str,
) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", section_heading.lower()).strip("_")[:40]
    return f"grammar_book_enrich:{BOOK_VERSION}:{language_code.lower()}:{chapter_number}:{slug}"


def build_section_enrich_prompt(
    language_code: str,
    language_name: str,
    chapter_number: int,
    chapter_title: str,
    section_heading: str,
    section_body: str,
    examples: list[str],
) -> str:
    display = language_name or language_code
    ex = "\n".join(f"- {e}" for e in examples[:4]) if examples else "(none yet)"
    return (
        f"You are expanding a grammar lesson for learners of {display} ({language_code}).\n"
        f"Chapter {chapter_number}: {chapter_title}\n"
        f"Section: {section_heading}\n"
        f"Current text:\n{section_body}\n"
        f"Existing examples:\n{ex}\n\n"
        "Return ONLY valid JSON (no markdown fences):\n"
        "{\n"
        '  "expanded_body": "2-4 sentences of deeper explanation building on the current text",\n'
        '  "extra_examples": ["new example in target language with gloss", "..."],\n'
        '  "learner_tip": "One practical tip for remembering this rule"\n'
        "}\n"
        "Use the target language in examples. Be clear and encouraging."
    )


def parse_section_enrich(raw: str, section_body: str) -> dict:
    try:
        data = _extract_json(raw)
        expanded = str(data.get("expanded_body") or "").strip()
        if not expanded:
            expanded = section_body
        return {
            "expanded_body": expanded,
            "extra_examples": [str(ex) for ex in (data.get("extra_examples") or [])][:4],
            "learner_tip": str(data.get("learner_tip") or "").strip(),
        }
    except (json.JSONDecodeError, TypeError, ValueError) as e:
        logger.warning("Section enrich JSON parse failed: %s", e)
        return {
            "expanded_body": section_body,
            "extra_examples": [],
            "learner_tip": "",
        }


def build_grammar_book_prompt(language_code: str, language_name: str) -> str:
    display = language_name or language_code
    return (
        f"You are writing a concise grammar learning book for learners of {display} ({language_code}).\n"
        "Return ONLY valid JSON (no markdown fences) with this exact shape:\n"
        "{\n"
        '  "title": "Grammar Book — {language}",\n'
        f'  "language_code": "{language_code}",\n'
        f'  "language_name": "{display}",\n'
        '  "chapters": [\n'
        "    {\n"
        '      "number": 1,\n'
        '      "title": "Chapter title",\n'
        '      "summary": "One sentence overview",\n'
        '      "sections": [\n'
        "        {\n"
        '          "heading": "Section heading",\n'
        '          "body": "Clear learner-friendly explanation (2-4 sentences)",\n'
        '          "examples": ["example in target language with brief gloss"]\n'
        "        }\n"
        "      ]\n"
        "    }\n"
        "  ]\n"
        "}\n"
        "Include exactly 8 chapters from sounds/alphabet through common verbs, "
        "sentence structure, questions, negation, and everyday patterns. "
        "Each chapter: 2-3 sections. Examples must use the target language."
    )


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


def parse_grammar_book(raw: str, language_code: str, language_name: str) -> GrammarBookResponse:
    try:
        data = _extract_json(raw)
        chapters = []
        for ch in data.get("chapters") or []:
            sections = [
                GrammarBookSection(
                    heading=str(sec.get("heading") or ""),
                    body=str(sec.get("body") or ""),
                    examples=[str(ex) for ex in (sec.get("examples") or [])][:4],
                )
                for sec in (ch.get("sections") or [])
                if sec.get("heading") or sec.get("body")
            ]
            if not sections:
                continue
            chapters.append(
                GrammarBookChapter(
                    number=int(ch.get("number") or len(chapters) + 1),
                    title=str(ch.get("title") or f"Chapter {len(chapters) + 1}"),
                    summary=str(ch.get("summary") or ""),
                    sections=sections,
                )
            )
        if chapters:
            return GrammarBookResponse(
                title=str(data.get("title") or f"Grammar Book — {language_name or language_code}"),
                language_code=str(data.get("language_code") or language_code),
                language_name=str(data.get("language_name") or language_name),
                chapters=chapters[:12],
            )
    except (json.JSONDecodeError, TypeError, ValueError) as e:
        logger.warning("Grammar book JSON parse failed: %s", e)
    return fallback_grammar_book(language_code, language_name)


def fallback_grammar_book(language_code: str, language_name: str) -> GrammarBookResponse:
    display = language_name or language_code
    outline = [
        ("Sounds & spelling", "Alphabet, pronunciation, and writing basics."),
        ("Nouns & articles", "Gender, number, and common noun patterns."),
        ("Verbs — present", "Regular and essential irregular verbs in the present tense."),
        ("Questions & negation", "Forming questions and saying no / not."),
        ("Adjectives & agreement", "Describing nouns and agreement rules."),
        ("Pronouns", "Subject, object, and possessive pronouns."),
        ("Past & future", "Talking about yesterday and tomorrow."),
        ("Everyday patterns", "Politeness, prepositions, and useful sentence templates."),
    ]
    chapters = []
    for i, (title, summary) in enumerate(outline, start=1):
        chapters.append(
            GrammarBookChapter(
                number=i,
                title=title,
                summary=summary,
                sections=[
                    GrammarBookSection(
                        heading="Overview",
                        body=f"This chapter covers {title.lower()} in {display}. "
                        f"Open a section while online for deeper AI explanations.",
                        examples=[f"[{display}] — example pending"],
                    ),
                ],
            )
        )
    return GrammarBookResponse(
        title=f"Grammar Book — {display}",
        language_code=language_code,
        language_name=display,
        chapters=chapters,
    )

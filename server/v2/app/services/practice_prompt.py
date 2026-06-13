"""Practice hub prompts — Answer (tutor) and Translation quality."""

_FORMAT_RULES = """
Formatting:
- Use clear paragraphs and short section headings (## …) when helpful.
- For math: one equation per line; Unicode symbols (α, β, ×, ÷, √) or plain notation — never broken LaTeX $$.
- For code: use fenced ``` blocks; keep syntax unchanged.
""".strip()


def _mixed_language_rules(source_lang: str, target: str, is_mixed: bool = False) -> str:
    if not is_mixed:
        return f"- Respond in {target} for all descriptive explanation."
    return (
        f"- Input may mix {source_lang} with English (technical terms).\n"
        f"- Translate descriptive / explanatory prose into {target}.\n"
        f"- When English appears alongside {source_lang}, prefer {source_lang} for explanations in {target}.\n"
        "- Keep equations, code, and formulas exactly as in the source.\n"
        "- Use Unicode math or plain notation — not raw LaTeX $$ delimiters."
    )


def build_answer_prompt(
    text: str,
    source_lang: str,
    target_langs: list[str],
    is_mixed: bool = False,
) -> str:
    target = (target_langs[0] if target_langs else source_lang).lower()
    src = source_lang.lower()
    trimmed = text.strip()
    lang_rules = _mixed_language_rules(source_lang, target, is_mixed)
    if target == src:
        return (
            f"You are an expert language tutor. The learner submitted text in {source_lang}.\n\n"
            f"Explain the following in clear, detailed {target}. Cover meaning, grammar, and key vocabulary.\n"
            f"Language & formatting rules:\n{lang_rules}\n{_FORMAT_RULES}\n"
            "Write in natural paragraphs like Gemini or ChatGPT tutoring.\n"
            "Reply with the explanation only.\n\n"
            f"Text:\n{trimmed}"
        )
    return (
        f"You are an expert language tutor.\n\n"
        f"Source text ({source_lang}):\n{trimmed}\n\n"
        f"Write a clear, detailed explanation entirely in {target} for a language learner.\n"
        f"Language & formatting rules:\n{lang_rules}\n{_FORMAT_RULES}\n"
        "Reply with the explanation only.\n"
    )


def build_translation_llm_prompt(
    text: str,
    source_lang: str,
    target_lang: str,
    is_mixed: bool = False,
) -> str:
    trimmed = text.strip()
    if "\n" in trimmed or len(trimmed) > 280:
        unit = "paragraph"
    elif " " in trimmed:
        unit = "sentence"
    else:
        unit = "word or short phrase"
    lang_rules = _mixed_language_rules(source_lang, target_lang, is_mixed)
    return (
        f"Translate from {source_lang} to {target_lang}.\n"
        f"Output ONLY the translation in {target_lang}. Preserve line breaks.\n"
        f"Natural fluency like Google Translate for a {unit}.\n"
        f"Keep names, numbers, math, code, and formulas unchanged.\n"
        f"Language rules:\n{lang_rules}\n\n"
        f"Source:\n{trimmed}"
    )

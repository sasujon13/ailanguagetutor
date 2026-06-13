"""Practice hub prompts — Answer (tutor) and Translation quality."""


def build_answer_prompt(text: str, source_lang: str, target_langs: list[str]) -> str:
    target = (target_langs[0] if target_langs else source_lang).lower()
    src = source_lang.lower()
    trimmed = text.strip()
    if target == src:
        return (
            f"You are an expert language tutor. The learner submitted text in {source_lang}.\n\n"
            f"Explain the following in clear, detailed {target}. Cover meaning, grammar, and key vocabulary.\n"
            "Write in natural paragraphs like Gemini or ChatGPT tutoring.\n"
            "Reply with the explanation only.\n\n"
            f"Text:\n{trimmed}"
        )
    return (
        f"You are an expert language tutor.\n\n"
        f"Source text ({source_lang}):\n{trimmed}\n\n"
        f"Write a clear, detailed explanation entirely in {target} for a language learner.\n"
        "Cover meaning, grammar patterns, and useful vocabulary.\n"
        "Reply with the explanation only.\n"
    )


def build_translation_llm_prompt(text: str, source_lang: str, target_lang: str) -> str:
    trimmed = text.strip()
    if "\n" in trimmed or len(trimmed) > 280:
        unit = "paragraph"
    elif " " in trimmed:
        unit = "sentence"
    else:
        unit = "word or short phrase"
    return (
        f"Translate from {source_lang} to {target_lang}.\n"
        f"Output ONLY the translation in {target_lang}. Preserve line breaks.\n"
        f"Natural fluency like Google Translate for a {unit}.\n\n"
        f"Source:\n{trimmed}"
    )

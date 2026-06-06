"""Grammar explanation prompts — keep in sync with Android AIManager.buildGrammarPrompt."""

from app.schemas import GrammarDepth


def build_grammar_prompt(
    context_text: str,
    focus_word: str | None,
    source_lang: str,
    target_lang: str,
    depth: GrammarDepth,
) -> str:
    if depth == GrammarDepth.WORD:
        return (
            f"You are a language tutor. Language: {source_lang} "
            f"(learner may read notes in {target_lang} if helpful).\n"
            f'Explain the grammar role of the word "{focus_word}" in this sentence only:\n'
            f'"{context_text}"\n'
            "Cover: part of speech, morphology/inflection if any, and why it appears in this form.\n"
            "Keep it concise (3–5 sentences). No dictionary definitions — grammar only."
        )
    if depth == GrammarDepth.SENTENCE:
        return (
            f"You are a language tutor. Language: {source_lang}.\n"
            f"Explain the grammar of this sentence (structure, tense/mood, clauses, agreement):\n"
            f'"{context_text}"\n'
            "Include brief notes on key words. Keep it clear for a learner (4–6 sentences)."
        )
    return (
        f"You are a language tutor. Language: {source_lang}.\n"
        "Explain the grammar patterns in this paragraph — sentence by sentence overview, then main rules:\n"
        f'"{context_text}"\n'
        "Be structured and learner-friendly. Max 8 short sentences."
    )

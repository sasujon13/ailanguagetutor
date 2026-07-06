"""IDE assistant prompts for Cheradip VS Code extension."""

from __future__ import annotations

from typing import Any


def _format_file_context(files: list[dict[str, Any]] | None) -> str:
    if not files:
        return ""
    parts: list[str] = []
    for f in files[:12]:
        path = f.get("path", "unknown")
        lang = f.get("language", "")
        content = (f.get("content") or "").strip()
        if not content:
            continue
        tag = lang or "text"
        parts.append(f"### File: {path}\n```{tag}\n{content}\n```")
    if not parts:
        return ""
    return "Workspace context:\n\n" + "\n\n".join(parts) + "\n\n"


def build_chat_prompt(
    messages: list[dict[str, str]],
    file_context: list[dict[str, Any]] | None = None,
) -> str:
    """Flatten chat history into a single prompt for Ollama generate."""
    system = (
        "You are Cheradip, an expert AI coding assistant inside VS Code.\n"
        "Help with code, debugging, refactoring, architecture, and explanations.\n"
        "Use fenced ``` blocks with correct language tags for code.\n"
        "Be concise but thorough. Match the user's project style when editing code."
    )
    lines = [f"System: {system}"]
    ctx = _format_file_context(file_context)
    if ctx:
        lines.append(ctx)
    for msg in messages[-24:]:
        role = msg.get("role", "user").capitalize()
        content = (msg.get("content") or "").strip()
        if content:
            lines.append(f"{role}: {content}")
    lines.append("Assistant:")
    return "\n\n".join(lines)


def build_complete_prompt(
    prefix: str,
    suffix: str,
    filepath: str | None = None,
    language: str | None = None,
) -> str:
    path = filepath or "unknown"
    lang = language or "text"
    return (
        "You are an inline code completion engine. "
        "Output ONLY the text that should be inserted at the cursor — no explanation, no markdown fences.\n"
        f"File: {path}\nLanguage: {lang}\n\n"
        f"PREFIX:\n{prefix}\n\nSUFFIX:\n{suffix}\n\n"
        "COMPLETION:"
    )


def build_edit_prompt(
    instruction: str,
    code: str,
    filepath: str | None = None,
    language: str | None = None,
) -> str:
    path = filepath or "unknown"
    lang = language or "text"
    return (
        "You are a precise code editor. Apply the user's instruction to the code.\n"
        "Output ONLY the full updated code — no explanation, no markdown fences.\n"
        f"File: {path}\nLanguage: {lang}\n\n"
        f"Instruction: {instruction.strip()}\n\n"
        f"Code:\n{code}\n\n"
        "Updated code:"
    )

"""Cloud LLM calls — Gemini, OpenAI, Groq. Falls back to template when no keys."""

from __future__ import annotations

import logging

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


def provider_has_key(provider_id: str) -> bool:
    if provider_id == "gemini":
        return bool(settings.gemini_api_key)
    if provider_id in ("openai", "openai_paid"):
        return bool(settings.openai_api_key)
    if provider_id == "groq":
        return bool(settings.groq_api_key)
    if provider_id in ("claude", "claude_paid"):
        return bool(settings.anthropic_api_key)
    if provider_id == "mistral":
        return bool(settings.mistral_api_key)
    if provider_id in ("openrouter", "openrouter_paid"):
        return bool(settings.openrouter_api_key)
    return False


async def generate_text(provider_id: str, prompt: str, max_tokens: int = 512) -> str | None:
    if provider_id == "gemini" and settings.gemini_api_key:
        return await _gemini(prompt, max_tokens)
    if provider_id in ("openai", "openai_paid") and settings.openai_api_key:
        return await _openai(prompt, max_tokens, model="gpt-4o-mini")
    if provider_id == "groq" and settings.groq_api_key:
        return await _groq(prompt, max_tokens)
    if provider_id in ("claude", "claude_paid") and settings.anthropic_api_key:
        return await _anthropic(prompt, max_tokens)
    if provider_id == "mistral" and settings.mistral_api_key:
        return await _mistral(prompt, max_tokens)
    if provider_id == "openrouter" and settings.openrouter_api_key:
        return await _openrouter(prompt, max_tokens, model=settings.openrouter_model)
    if provider_id == "openrouter_paid" and settings.openrouter_api_key:
        return await _openrouter(prompt, max_tokens, model=settings.openrouter_paid_model)
    return None


async def _gemini(prompt: str, max_tokens: int) -> str:
    model = settings.gemini_model
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"maxOutputTokens": max_tokens},
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(
            url,
            json=body,
            headers={
                "Content-Type": "application/json",
                "X-goog-api-key": settings.gemini_api_key,
            },
        )
        resp.raise_for_status()
        data = resp.json()
        parts = data["candidates"][0]["content"]["parts"]
        return parts[0].get("text", "").strip()


async def _openai(prompt: str, max_tokens: int, model: str = "gpt-4o-mini") -> str:
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(
            "https://api.openai.com/v1/chat/completions",
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": max_tokens,
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()


async def _groq(prompt: str, max_tokens: int) -> str:
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={"Authorization": f"Bearer {settings.groq_api_key}"},
            json={
                "model": "llama-3.1-8b-instant",
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": max_tokens,
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()


async def _anthropic(prompt: str, max_tokens: int) -> str:
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(
            "https://api.anthropic.com/v1/messages",
            headers={
                "x-api-key": settings.anthropic_api_key,
                "anthropic-version": "2023-06-01",
            },
            json={
                "model": "claude-3-5-haiku-20241022",
                "max_tokens": max_tokens,
                "messages": [{"role": "user", "content": prompt}],
            },
        )
        resp.raise_for_status()
        return resp.json()["content"][0]["text"].strip()


async def _mistral(prompt: str, max_tokens: int) -> str:
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(
            "https://api.mistral.ai/v1/chat/completions",
            headers={"Authorization": f"Bearer {settings.mistral_api_key}"},
            json={
                "model": "mistral-small-latest",
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": max_tokens,
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()


async def _openrouter(prompt: str, max_tokens: int, model: str) -> str:
    async with httpx.AsyncClient(timeout=60.0) as client:
        resp = await client.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {settings.openrouter_api_key}",
                "HTTP-Referer": settings.public_base_url,
                "X-Title": "AI Language Tutor",
            },
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": max_tokens,
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()

"""Ollama HTTP backend for MVP dev on Intel Arc."""

import json
from collections.abc import AsyncIterator

import httpx


class OllamaBackend:
    def __init__(self, base_url: str = "http://127.0.0.1:11434"):
        self.base_url = base_url.rstrip("/")

    async def generate(self, model: str, prompt: str, max_tokens: int = 512) -> str:
        async with httpx.AsyncClient(timeout=120.0) as client:
            resp = await client.post(
                f"{self.base_url}/api/generate",
                json={"model": model, "prompt": prompt, "stream": False, "options": {"num_predict": max_tokens}},
            )
            resp.raise_for_status()
            return resp.json().get("response", "")

    async def generate_stream(self, model: str, prompt: str, max_tokens: int = 512) -> AsyncIterator[str]:
        async with httpx.AsyncClient(timeout=120.0) as client:
            async with client.stream(
                "POST",
                f"{self.base_url}/api/generate",
                json={"model": model, "prompt": prompt, "stream": True, "options": {"num_predict": max_tokens}},
            ) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line:
                        continue
                    payload = json.loads(line)
                    chunk = payload.get("response", "")
                    if chunk:
                        yield chunk
                    if payload.get("done"):
                        break

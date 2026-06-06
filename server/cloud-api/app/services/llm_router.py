"""Pick providers from routing policy and retry across the pool on API errors."""

from __future__ import annotations

import logging
import random

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import AiProvider, AiRoutingPolicy
from app.security import ms_now
from app.services.llm_client import generate_text, provider_has_key

logger = logging.getLogger(__name__)


def routing_pool(db: Session) -> list[AiProvider]:
    routing = db.scalar(select(AiRoutingPolicy).limit(1))
    mode = routing.mode if routing else "random_free"
    providers = db.scalars(select(AiProvider).where(AiProvider.enabled.is_(True))).all()
    if mode == "paid_only":
        pool = [p for p in providers if p.tier == "paid"]
    elif mode == "random_all":
        pool = list(providers)
    else:
        pool = [p for p in providers if p.tier == "free" and p.health != "exhausted"]
    if not pool and routing and routing.prefer_paid_when_free_exhausted:
        pool = [p for p in providers if p.tier == "paid"]
    return pool


def touch_provider(db: Session, provider: AiProvider) -> str:
    provider.requests_today += 1
    provider.last_used_at_ms = ms_now()
    if provider.quota_daily_limit and provider.requests_today >= provider.quota_daily_limit:
        provider.health = "exhausted"
    db.commit()
    return provider.id


async def generate_with_fallback(
    db: Session,
    prompt: str,
    *,
    max_tokens: int = 512,
) -> tuple[str | None, str]:
    """Try each eligible provider in random order until one returns text."""
    candidates = [p for p in routing_pool(db) if provider_has_key(p.id)]
    random.shuffle(candidates)
    if not candidates:
        return None, "local-stub"

    last_id = "local-stub"
    for provider in candidates:
        last_id = provider.id
        touch_provider(db, provider)
        try:
            text = await generate_text(provider.id, prompt, max_tokens=max_tokens)
            if text and text.strip():
                provider.last_error = None
                db.commit()
                return text.strip(), provider.id
            provider.last_error = "empty response"
            db.commit()
            logger.warning("Provider %s returned empty text — trying next", provider.id)
        except Exception as exc:
            provider.last_error = str(exc)[:500]
            db.commit()
            logger.warning("Provider %s failed (%s) — trying next", provider.id, exc)
    return None, last_id

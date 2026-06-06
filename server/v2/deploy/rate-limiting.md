# Rate Limiting (v2 Home AI Server)

Protect the Intel Arc home server from abuse. Implement in `app/services/rate_limit.py`.

## Strategy

| Layer | Scope | Tool |
|-------|-------|------|
| Device | `X-Device-Id` header from Android | Token bucket in Redis or in-memory |
| Tier | `subscription_tier` from verified JWT or request | Pro vs Plus quotas |
| Global | All traffic | Max concurrent GPU jobs (queue) |

## Suggested quotas (adjust after V2-8 metrics)

| Tier | AI requests / hour / device | Burst |
|------|----------------------------|-------|
| **Pro** | 60 | 10/min |
| **Plus** | 120 | 20/min |
| **Free** | 0 (403 — offline only) | — |

Cache hits **do not** count toward quota (encourages repeat study, saves GPU).

## Response headers

```http
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1717700400
Retry-After: 30
```

HTTP **429** when exceeded; app shows friendly “AI busy, try again” message.

## Auth

- Home server validates short-lived token from `cheradip.com/api/ailt/auth/home-ai-token`
- Never expose Ollama/OpenVINO ports publicly without FastAPI + auth in front

## Admin overrides

- Whitelist device IDs for testing
- Temporary burst mode for demos (disables rate limit, enables cloud fallback)

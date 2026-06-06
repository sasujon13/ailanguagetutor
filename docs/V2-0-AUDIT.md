# V2-0 Audit — v1.0.0 → v2.0.0 upgrade gate

**Date:** June 2026 · **Spec revision:** 1.4.3

## v1 phases complete (ship-ready core)

| Phase | Status | Notes |
|-------|--------|-------|
| 0–11 | ✅ | Scaffold through auth |
| 12 | ✅ | Trial, paywall, promo/referral stubs |
| 13 | ✅ | AIManager batch-only, ai_cache |
| 14 | 🔄 | Unit tests partial |

## v1 gaps before v2 UX (non-blocking for server)

| Item | Priority | v2 phase |
|------|----------|----------|
| Paywall two promo fields (LAUNCH50 slot1 + manual slot2) | ✅ | V2-7 |
| `GET /promo/paywall-config` client | ✅ | V2-7 |
| Play Billing 9.x live flow | ✅ | `PlayBillingManager`, restore + verify |
| ModeSelectionScreen (Answer vs Translation + modes 1–5) | ✅ | V2-3 — `feature/practice/ModeSelectionScreen.kt` |
| HomeAiService + LOCAL_HOME routing | ✅ | V2-5 + V2-8 admin URL tab |
| Unified input Scan/Type/Speak | ✅ | V2-4 |

## v2.0 additions (this upgrade)

| Deliverable | Location |
|-------------|----------|
| `SubscriptionTier`, `AiEngineMode`, `AiBackend` | `core/model/AiV2Models.kt` |
| FastAPI home server scaffold | `server/v2/` |
| Intel Arc model guide + download scripts | `server/v2/deploy/`, `server/v2/scripts/` |
| Routing + cache spec | `server/v2/docs/ROUTING_AND_CACHE.md` |

## Architecture decision (unchanged)

- **Cloud:** `cheradip.com/api/ailt/` — auth, billing, promos, packs
- **Home PC:** `HOME_AI_BASE_URL` — inference only (OCR clean, translate, ask, TTS)
- **Offline-first:** packs, dictionary, translation cache always work without home AI

## versionName

**2.0.0** — released after V2-9 gate (June 2026).

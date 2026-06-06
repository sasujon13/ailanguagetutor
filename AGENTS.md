# AI Language Tutor — Agent Guide

## Start here

1. Read **`ailanguagetutor.md`** (Product Identity, ADRs, current build phase).
2. Check **`BUILD_STATUS.md`** for what is done vs next.
3. Implement **only the next phase step**; run phase acceptance when done.

## Spec highlights

| Item | Value |
|------|-------|
| App version | 1.0.0 |
| Languages | 243 (`catalog/world_languages.json`) |
| Active packs | Max 3 |
| Practice languages | 1–3 with flag markers |
| Monetization | 30-day trial → $1/mo or $10/yr · **no ads** |

## Cursor prompts

Full library: **`ailanguagetutor.md` → Cursor Prompt Library**

- **24-item Master coverage checklist** — nothing skipped
- **Mandatory after every phase** — 4 prompts (tests, constraints, acceptance, perf)
- **Referral (20%) + promo codes + admin console** — Phase 12 + dedicated prompts in spec
- **Phase sub-tasks** — one step per session for complex phases
- Update **`BUILD_STATUS.md`** after each phase

## Module map

- `:app` — entry, NavHost
- `:ui:*` — theme, components, navigation routes
- `:core:*` — domain, data, OCR, pack, auth, billing, …
- `:feature:*` — screens + ViewModels

## Phase 0 exit

All modules compile · app launches · Cheradip theme · bottom nav shell

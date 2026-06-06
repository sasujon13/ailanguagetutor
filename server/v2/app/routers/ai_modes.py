import json
from pathlib import Path

from fastapi import APIRouter, HTTPException, Query

from app.config import settings
from app.schemas import AiModeInfo, AiModesResponse, SubscriptionTier

router = APIRouter()


def _load_modes_config() -> dict:
    path = settings.ai_modes_config
    if not path.is_absolute():
        path = Path(__file__).resolve().parents[2] / path
    if not path.exists():
        path = Path(__file__).resolve().parents[3] / "ai-modes.example.json"
    with path.open(encoding="utf-8") as f:
        return json.load(f)


@router.get("/modes", response_model=AiModesResponse)
async def list_ai_modes(
    tier: SubscriptionTier = Query(default=SubscriptionTier.PRO),
):
    if tier == SubscriptionTier.FREE:
        return AiModesResponse(tier=tier, modes=[], note="Offline only after trial")

    cfg = _load_modes_config()
    picker: list[int] = cfg.get("tier_mode_picker", {}).get(tier.value, [])
    modes_out: list[AiModeInfo] = []

    for m in cfg.get("modes", []):
        mid = m["id"]
        if mid == 4:
            continue  # Mode 4 auto on OCR — not in picker
        if mid not in picker:
            continue
        modes_out.append(
            AiModeInfo(
                id=mid,
                key=m["key"],
                label=m["label"],
                emoji=m.get("emoji", ""),
                required_tier=m.get("required_tier", "pro"),
                selectable=True,
            )
        )

    if tier == SubscriptionTier.PRO and any(m.id == 5 for m in modes_out):
        raise HTTPException(500, detail="Config error: Mode 5 must not appear for Pro")

    return AiModesResponse(
        tier=tier,
        modes=modes_out,
        note="Mode 4 (Lightweight) applies automatically when OCR is active.",
    )

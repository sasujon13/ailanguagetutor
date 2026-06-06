from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import ReferralBalance, ReferralPolicy

router = APIRouter(prefix="/referral", tags=["referral"])


@router.get("/policy")
def referral_policy(db: Session = Depends(get_db)) -> dict:
    pol = db.scalar(select(ReferralPolicy).limit(1))
    if not pol:
        return {"commission_percent": 20, "notice_text": "Referral program"}
    return {
        "commission_percent": pol.commission_percent,
        "notice_text": pol.notice_text or "Refer friends and earn rewards.",
    }


@router.get("/balance")
def referral_balance(db: Session = Depends(get_db)) -> dict:
    # Authenticated balance would filter by user — return zero until session header wired
    row = db.scalar(select(ReferralBalance).limit(1))
    if not row:
        return {"balance_usd": 0.0, "lifetime_earned_usd": 0.0}
    return {"balance_usd": row.balance_usd, "lifetime_earned_usd": row.lifetime_earned_usd}

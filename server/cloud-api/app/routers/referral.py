from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user
from app.models import ReferralBalance, ReferralPolicy, ReferralWithdrawal, User
from app.schemas import ReferralWithdrawRequest

router = APIRouter(prefix="/referral", tags=["referral"])

MIN_WITHDRAWAL_USD = 100.0


@router.get("/policy")
def referral_policy(db: Session = Depends(get_db)) -> dict:
    pol = db.scalar(select(ReferralPolicy).limit(1))
    if not pol:
        return {
            "commission_percent": 20,
            "notice_text": "Referral program",
            "min_withdrawal_usd": MIN_WITHDRAWAL_USD,
        }
    return {
        "commission_percent": pol.commission_percent,
        "notice_text": pol.notice_text or "Refer friends and earn rewards.",
        "min_withdrawal_usd": MIN_WITHDRAWAL_USD,
    }


def _balance_row(db: Session, user_id: int) -> ReferralBalance:
    row = db.scalar(select(ReferralBalance).where(ReferralBalance.user_id == user_id))
    if row:
        return row
    row = ReferralBalance(user_id=user_id, balance_usd=0.0, lifetime_earned_usd=0.0)
    db.add(row)
    db.flush()
    return row


@router.get("/balance")
def referral_balance(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    row = _balance_row(db, user.id)
    db.commit()
    return {
        "balance_usd": row.balance_usd,
        "lifetime_earned_usd": row.lifetime_earned_usd,
        "min_withdrawal_usd": MIN_WITHDRAWAL_USD,
        "withdrawable": row.balance_usd >= MIN_WITHDRAWAL_USD,
    }


@router.post("/withdraw")
def referral_withdraw(
    body: ReferralWithdrawRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    method = body.method.strip().lower()
    payout_details = body.payoutDetails.strip()
    if method not in {"paypal", "bank_transfer", "mobile_wallet"}:
        raise HTTPException(400, "method must be paypal, bank_transfer, or mobile_wallet")
    if len(payout_details) < 3:
        raise HTTPException(400, "payoutDetails is required")

    row = _balance_row(db, user.id)
    amount = float(body.amountUsd if body.amountUsd is not None else row.balance_usd)
    if amount < MIN_WITHDRAWAL_USD:
        raise HTTPException(400, f"Minimum withdrawal is ${MIN_WITHDRAWAL_USD:.0f}")
    if amount > row.balance_usd:
        raise HTTPException(400, "Insufficient referral balance")

    row.balance_usd = round(row.balance_usd - amount, 2)
    withdrawal = ReferralWithdrawal(
        user_id=user.id,
        amount_usd=amount,
        method=method,
        payout_details=payout_details,
        status="pending",
    )
    db.add(withdrawal)
    db.commit()
    db.refresh(withdrawal)
    return {
        "ok": True,
        "message": "Withdrawal request submitted. Processing within 5–10 business days.",
        "balance_usd": row.balance_usd,
        "withdrawal_id": withdrawal.id,
    }

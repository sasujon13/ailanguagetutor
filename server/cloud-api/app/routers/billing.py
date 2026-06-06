from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import ReferralBalance, Subscription
from app.schemas import BillingVerifyRequest, BillingVerifyResponse
from app.security import ms_in_days
from app.services.promo_logic import (
    commission_for_purchase,
    compound_price,
    discount_for_code,
    price_for_product,
    slot1_active,
)

router = APIRouter(prefix="/billing", tags=["billing"])


def _tier_from_product(product_id: str) -> str:
    pid = product_id.lower()
    if "plus" in pid:
        return "plus"
    return "pro"


def _balance_row(db: Session, user_id: int) -> ReferralBalance:
    row = db.scalar(select(ReferralBalance).where(ReferralBalance.user_id == user_id))
    if row:
        return row
    row = ReferralBalance(user_id=user_id, balance_usd=0.0, lifetime_earned_usd=0.0)
    db.add(row)
    db.flush()
    return row


@router.post("/verify", response_model=BillingVerifyResponse)
def verify_purchase(body: BillingVerifyRequest, db: Session = Depends(get_db)) -> BillingVerifyResponse:
    # Play Billing verification stub — store entitlement locally until Play API wired
    tier = _tier_from_product(body.productId)
    expires = ms_in_days(30 if "month" in body.productId.lower() else 365)

    base = price_for_product(body.productId)
    discounts: list[int] = []
    referrer_user_id: int | None = None

    if body.slot1Code and slot1_active(db, body.slot1Code):
        try:
            d1, _, _ = discount_for_code(db, body.slot1Code)
            discounts.append(d1)
        except ValueError:
            pass

    if body.slot2Code:
        try:
            d2, _, referrer = discount_for_code(
                db,
                body.slot2Code,
                slot1_code=body.slot1Code,
            )
            discounts.append(d2)
            if referrer:
                referrer_user_id = referrer.id
        except ValueError as exc:
            raise HTTPException(400, str(exc)) from exc

    amount_paid = compound_price(base, discounts)

    if referrer_user_id:
        commission = commission_for_purchase(db, amount_paid)
        if commission > 0:
            bal = _balance_row(db, referrer_user_id)
            bal.balance_usd = round(bal.balance_usd + commission, 2)
            bal.lifetime_earned_usd = round(bal.lifetime_earned_usd + commission, 2)

    db.add(
        Subscription(
            product_id=body.productId,
            purchase_token=body.purchaseToken,
            tier=tier,
            active=True,
            expires_at_ms=expires,
        )
    )
    db.commit()
    return BillingVerifyResponse(active=True, expiresAt=expires, tier=tier)

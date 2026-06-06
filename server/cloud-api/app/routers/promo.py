from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import PromoCode, ReferralPolicy, User
from app.schemas import PromoValidateRequest

router = APIRouter(prefix="/promo", tags=["promo"])


def _promo_row(db: Session, code: str) -> PromoCode | None:
    return db.scalar(select(PromoCode).where(PromoCode.code == code.upper()))


def _referrer_discount(db: Session, code: str) -> int | None:
    pol = db.scalar(select(ReferralPolicy).limit(1))
    if not pol or not pol.active:
        return None
    user = db.scalar(select(User).where(User.username == code))
    if user:
        return pol.buyer_discount_percent
    return None


@router.get("/paywall-config")
def paywall_config(db: Session = Depends(get_db)) -> dict:
    promos = db.scalars(select(PromoCode).where(PromoCode.active.is_(True))).all()
    launch = next((p for p in promos if p.auto_apply and p.paywall_slot == 1), None)
    manual_active = any(p.paywall_slot == 2 and p.discount_percent > 0 for p in promos)
    pol = db.scalar(select(ReferralPolicy).limit(1))
    referral_active = bool(pol and pol.active)
    launch_active = bool(launch and launch.discount_percent > 0)
    show = launch_active or manual_active or referral_active
    return {
        "showPromoSection": show,
        "slot1": {
            "code": launch.code if launch else "LAUNCH50",
            "visible": launch_active,
            "discountPercent": launch.discount_percent if launch else 0,
            "readOnly": True,
        },
        "slot2": {"visible": show and (manual_active or referral_active), "manualEntry": True},
        "maxPromoCodesAtCheckout": 2,
    }


@router.post("/validate")
def validate_promo(body: PromoValidateRequest, db: Session = Depends(get_db)) -> dict:
    raw = body.code.strip()
    entry = _promo_row(db, raw.upper())
    discount = 0
    out_code = raw.upper()

    if entry and entry.active and entry.discount_percent > 0:
        discount = entry.discount_percent
    else:
        ref_pct = _referrer_discount(db, raw)
        if ref_pct:
            discount = ref_pct
            out_code = raw
        else:
            raise HTTPException(400, "Promo code is expired. No discount available.")

    price = body.base_price * (100 - discount) / 100
    return {
        "code": out_code,
        "discount_percent": discount,
        "discounted_price": f"{price:.2f}",
    }

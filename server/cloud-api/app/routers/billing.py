from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Subscription
from app.schemas import BillingVerifyRequest, BillingVerifyResponse
from app.security import ms_in_days

router = APIRouter(prefix="/billing", tags=["billing"])


def _tier_from_product(product_id: str) -> str:
    pid = product_id.lower()
    if "plus" in pid:
        return "plus"
    return "pro"


@router.post("/verify", response_model=BillingVerifyResponse)
def verify_purchase(body: BillingVerifyRequest, db: Session = Depends(get_db)) -> BillingVerifyResponse:
    # Play Billing verification stub — store entitlement locally until Play API wired
    tier = _tier_from_product(body.productId)
    expires = ms_in_days(30 if "month" in body.productId.lower() else 365)
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

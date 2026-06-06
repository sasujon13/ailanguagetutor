from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import DeviceTrial
from app.schemas import DeviceRegisterRequest, DeviceRegisterResponse
from app.security import ms_in_days, ms_now

router = APIRouter(prefix="/device", tags=["device"])


@router.post("/register", response_model=DeviceRegisterResponse)
def register_device(body: DeviceRegisterRequest, db: Session = Depends(get_db)) -> DeviceRegisterResponse:
    row = db.scalar(select(DeviceTrial).where(DeviceTrial.device_id == body.deviceId))
    if not row:
        ends = ms_in_days(settings.trial_days)
        row = DeviceTrial(
            device_id=body.deviceId,
            model=body.model,
            os_version=body.osVersion,
            trial_ends_at_ms=ends,
        )
        db.add(row)
        db.commit()
    remaining_ms = max(0, row.trial_ends_at_ms - ms_now())
    days = max(0, int(remaining_ms / 86400000) + (1 if remaining_ms % 86400000 else 0))
    if remaining_ms == 0:
        days = 0
    return DeviceRegisterResponse(trialEndsAt=row.trial_ends_at_ms, trialDaysRemaining=min(days, settings.trial_days))

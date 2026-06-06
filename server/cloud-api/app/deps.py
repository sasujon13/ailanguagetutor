from __future__ import annotations

from datetime import datetime

from fastapi import Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import SessionToken, User


def get_current_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
    db: Session = Depends(get_db),
) -> User:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Authentication required")
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(401, "Authentication required")
    row = db.scalar(select(SessionToken).where(SessionToken.token == token))
    if not row:
        raise HTTPException(401, "Invalid session")
    now = datetime.utcnow()
    if row.expires_at < now:
        raise HTTPException(401, "Session expired")
    user = db.get(User, row.user_id)
    if not user:
        raise HTTPException(401, "User not found")
    return user

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import OtpCode, SessionToken, User
from app.schemas import AuthLoginRequest, AuthLoginResponse, OtpRequest, OtpVerifyRequest
from app.security import (
    hash_password,
    new_otp_code,
    new_session_token,
    otp_expires_at,
    session_expires_at,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])


def _issue_session(db: Session, user: User, device_id: str | None) -> str:
    token = new_session_token()
    db.add(
        SessionToken(
            user_id=user.id,
            token=token,
            device_id=device_id,
            expires_at=session_expires_at(),
        )
    )
    return token


def _login_response(user: User, token: str) -> AuthLoginResponse:
    return AuthLoginResponse(
        email=user.email or user.username or "",
        role=user.role,
        whatsapp=user.whatsapp,
        sessionToken=token,
    )


@router.post("/login", response_model=AuthLoginResponse)
def login(body: AuthLoginRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    username = body.username.strip()
    user = db.scalar(
        select(User).where(
            (User.email == username) | (User.whatsapp == username) | (User.username == username)
        )
    )
    if not user or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "Invalid credentials")
    token = _issue_session(db, user, body.deviceId)
    db.commit()
    return _login_response(user, token)


@router.post("/register")
def register(body: OtpRequest, db: Session = Depends(get_db)) -> dict:
    target = body.target.strip()
    channel = "email" if "@" in target else "whatsapp"
    code = new_otp_code()
    db.add(OtpCode(target=target, channel=channel, code=code, expires_at=otp_expires_at()))
    db.commit()
    if settings.dev_log_otp:
        print(f"[OTP {channel}] {target} -> {code}")
    return {"ok": True, "message": "OTP sent"}


def _verify_otp(db: Session, body: OtpVerifyRequest, channel: str) -> User:
    row = db.scalar(
        select(OtpCode)
        .where(OtpCode.target == body.target.strip(), OtpCode.channel == channel, OtpCode.used.is_(False))
        .order_by(OtpCode.id.desc())
    )
    if not row or row.code != body.code.strip():
        raise HTTPException(400, "Invalid or expired OTP")
    now = __import__("datetime").datetime.now(__import__("datetime").timezone.utc).replace(tzinfo=None)
    if row.expires_at < now:
        raise HTTPException(400, "Invalid or expired OTP")
    row.used = True
    target = body.target.strip()
    if channel == "email":
        user = db.scalar(select(User).where(User.email == target))
        if not user:
            user = User(email=target, password_hash=hash_password(new_session_token()[:16]), role="user")
            db.add(user)
            db.flush()
    else:
        user = db.scalar(select(User).where(User.whatsapp == target))
        if not user:
            user = User(whatsapp=target, password_hash=hash_password(new_session_token()[:16]), role="user")
            db.add(user)
            db.flush()
    return user


@router.post("/verify-email", response_model=AuthLoginResponse)
def verify_email(body: OtpVerifyRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    user = _verify_otp(db, body, "email")
    token = _issue_session(db, user, None)
    db.commit()
    return _login_response(user, token)


@router.post("/verify-whatsapp", response_model=AuthLoginResponse)
def verify_whatsapp(body: OtpVerifyRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    user = _verify_otp(db, body, "whatsapp")
    token = _issue_session(db, user, None)
    db.commit()
    return _login_response(user, token)

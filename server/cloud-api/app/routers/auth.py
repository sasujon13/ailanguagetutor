from __future__ import annotations

import re

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models import OtpCode, SessionToken, User
from app.schemas import (
    AuthLoginRequest,
    AuthLoginResponse,
    OtpRequest,
    OtpVerifyRequest,
    SignupInitRequest,
    SignupInitResponse,
)
from app.security import (
    hash_password,
    new_otp_code,
    new_session_token,
    otp_expires_at,
    session_expires_at,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
WHATSAPP_RE = re.compile(r"^\+[1-9]\d{7,14}$")
PASSWORD_RE = re.compile(r"^(?=.*[A-Za-z])(?=.*\d).{8,}$")


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


def _send_otp(db: Session, target: str, channel: str) -> None:
    code = new_otp_code()
    db.add(OtpCode(target=target.strip(), channel=channel, code=code, expires_at=otp_expires_at()))
    if settings.dev_log_otp:
        print(f"[OTP {channel}] {target} -> {code}")


def _verify_otp_code(db: Session, target: str, channel: str, code: str) -> None:
    row = db.scalar(
        select(OtpCode)
        .where(OtpCode.target == target.strip(), OtpCode.channel == channel, OtpCode.used.is_(False))
        .order_by(OtpCode.id.desc())
    )
    if not row or row.code != code.strip():
        raise HTTPException(400, "Invalid or expired OTP")
    now = __import__("datetime").datetime.now(__import__("datetime").timezone.utc).replace(tzinfo=None)
    if row.expires_at < now:
        raise HTTPException(400, "Invalid or expired OTP")
    row.used = True


def _validate_signup(body: SignupInitRequest) -> None:
    if not EMAIL_RE.match(body.email.strip()):
        raise HTTPException(400, "Invalid email address")
    if not WHATSAPP_RE.match(body.whatsapp.strip()):
        raise HTTPException(400, "WhatsApp must be E.164 format, e.g. +8801XXXXXXXXX")
    if not PASSWORD_RE.match(body.password):
        raise HTTPException(400, "Password must be at least 8 characters with 1 letter and 1 number")
    if body.loginWith not in {"email", "whatsapp"}:
        raise HTTPException(400, "loginWith must be email or whatsapp")


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
    if user.role != "admin" and (not user.email_verified or not user.whatsapp_verified):
        raise HTTPException(403, "Verify email and WhatsApp before signing in")
    token = _issue_session(db, user, body.deviceId)
    db.commit()
    return _login_response(user, token)


@router.post("/signup/init", response_model=SignupInitResponse)
def signup_init(body: SignupInitRequest, db: Session = Depends(get_db)) -> SignupInitResponse:
    _validate_signup(body)
    email = body.email.strip().lower()
    whatsapp = body.whatsapp.strip()
    username = body.username.strip()

    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "Email already registered")
    if db.scalar(select(User).where(User.whatsapp == whatsapp)):
        raise HTTPException(409, "WhatsApp number already registered")
    if db.scalar(select(User).where(User.username == username)):
        raise HTTPException(409, "Username already taken")

    user = User(
        email=email,
        whatsapp=whatsapp,
        username=username,
        full_name=body.fullName.strip(),
        password_hash=hash_password(body.password),
        role="user",
        email_verified=False,
        whatsapp_verified=False,
        login_with=body.loginWith,
    )
    db.add(user)
    db.flush()
    _send_otp(db, email, "signup_email")
    db.commit()
    return SignupInitResponse(message="Verification code sent to your email")


@router.post("/signup/verify-email", response_model=AuthLoginResponse)
def signup_verify_email(body: OtpVerifyRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    email = body.target.strip().lower()
    user = db.scalar(select(User).where(User.email == email))
    if not user:
        raise HTTPException(404, "Signup not found for this email")
    _verify_otp_code(db, email, "signup_email", body.code)
    user.email_verified = True
    _send_otp(db, user.whatsapp or "", "signup_whatsapp")
    db.commit()
    return AuthLoginResponse(
        email=user.email or "",
        role=user.role,
        whatsapp=user.whatsapp,
        sessionToken=None,
    )


@router.post("/signup/verify-whatsapp", response_model=AuthLoginResponse)
def signup_verify_whatsapp(body: OtpVerifyRequest, db: Session = Depends(get_db)) -> AuthLoginResponse:
    whatsapp = body.target.strip()
    user = db.scalar(select(User).where(User.whatsapp == whatsapp))
    if not user:
        raise HTTPException(404, "Signup not found for this WhatsApp number")
    if not user.email_verified:
        raise HTTPException(400, "Verify email first")
    _verify_otp_code(db, whatsapp, "signup_whatsapp", body.code)
    user.whatsapp_verified = True
    token = _issue_session(db, user, None)
    db.commit()
    return _login_response(user, token)


@router.post("/register")
def register(body: OtpRequest, db: Session = Depends(get_db)) -> dict:
    target = body.target.strip()
    channel = "email" if "@" in target else "whatsapp"
    _send_otp(db, target, channel)
    db.commit()
    return {"ok": True, "message": "OTP sent"}


def _verify_otp(db: Session, body: OtpVerifyRequest, channel: str) -> User:
    _verify_otp_code(db, body.target.strip(), channel, body.code)
    target = body.target.strip()
    if channel == "email":
        user = db.scalar(select(User).where(User.email == target))
        if not user:
            user = User(
                email=target,
                password_hash=hash_password(new_session_token()[:16]),
                role="user",
                email_verified=True,
            )
            db.add(user)
            db.flush()
    else:
        user = db.scalar(select(User).where(User.whatsapp == target))
        if not user:
            user = User(
                whatsapp=target,
                password_hash=hash_password(new_session_token()[:16]),
                role="user",
                whatsapp_verified=True,
            )
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

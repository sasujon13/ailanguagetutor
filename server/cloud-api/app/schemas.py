from pydantic import BaseModel, Field


class AuthLoginRequest(BaseModel):
    username: str
    password: str
    deviceId: str | None = None


class AuthLoginResponse(BaseModel):
    email: str
    role: str = "user"
    whatsapp: str | None = None
    sessionToken: str | None = None


class OtpRequest(BaseModel):
    target: str


class OtpVerifyRequest(BaseModel):
    target: str
    code: str


class DeviceRegisterRequest(BaseModel):
    deviceId: str
    model: str
    osVersion: str


class DeviceRegisterResponse(BaseModel):
    trialEndsAt: int | None = None
    trialDaysRemaining: int = 7


class BillingVerifyRequest(BaseModel):
    purchaseToken: str
    productId: str


class BillingVerifyResponse(BaseModel):
    active: bool
    expiresAt: int | None = None
    tier: str | None = None


class PromoValidateRequest(BaseModel):
    code: str
    base_price: float = Field(2.0, alias="base_price")

    model_config = {"populate_by_name": True}


class AdminPromoCodeDto(BaseModel):
    code: str
    discount_percent: int = Field(alias="discount_percent")
    active: bool = True

    model_config = {"populate_by_name": True}


class AdminPromoPatchDto(BaseModel):
    discount_percent: int | None = Field(None, alias="discount_percent")
    active: bool | None = None

    model_config = {"populate_by_name": True}


class ReferralPolicyPatchDto(BaseModel):
    active: bool | None = None
    buyer_discount_percent: int | None = Field(None, alias="buyer_discount_percent")
    commission_percent: int | None = Field(None, alias="commission_percent")
    notice_text: str | None = Field(None, alias="notice_text")

    model_config = {"populate_by_name": True}


class AiActivityMetadataRequest(BaseModel):
    text: str
    language_code: str


class AiParagraphRequest(BaseModel):
    paragraph: str
    source_lang: str
    target_lang: str


class AiRoutingPolicyUpdateRequest(BaseModel):
    mode: str
    prefer_paid_when_free_exhausted: bool | None = None


class AiProviderToggleRequest(BaseModel):
    enabled: bool

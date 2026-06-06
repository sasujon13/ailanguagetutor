"""Request/response schemas."""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class SubscriptionTier(str, Enum):
    FREE = "free"
    PRO = "pro"
    PLUS = "plus"


class ProcessingIntent(str, Enum):
    ANSWER = "answer"
    TRANSLATION = "translation"


class InputSource(str, Enum):
    OCR = "ocr"
    TYPED = "typed"
    VOICE = "voice"


class AiRequest(BaseModel):
    processing_intent: ProcessingIntent = ProcessingIntent.ANSWER
    ai_engine_mode: int = Field(ge=1, le=5, default=1)
    input_source: InputSource = InputSource.TYPED
    subscription_tier: SubscriptionTier = SubscriptionTier.PRO
    text: str
    language_code: str = "en"
    target_languages: list[str] = Field(default_factory=list)


class AiModeInfo(BaseModel):
    id: int
    key: str
    label: str
    emoji: str
    required_tier: str
    selectable: bool = True


class AiModesResponse(BaseModel):
    tier: SubscriptionTier
    modes: list[AiModeInfo]
    note: str | None = None


class HealthResponse(BaseModel):
    status: str
    gpu_available: bool
    gpu_in_use: bool = False
    gpu_device: str | None = None
    gpu_library: str | None = None
    gpu_vram_total: str | None = None
    gpu_vram_available: str | None = None
    loaded_processors: list[str] = Field(default_factory=list)
    inference_backend: str
    model_loaded: str | None
    resident_models: list[str]
    queue_depth: int
    cache_hit_rate: float | None = None
    ollama_models: list[str] | None = None
    models_on_disk: list[str] | None = None


class CleanOcrResponse(BaseModel):
    cleaned_text: str
    mode: int = 4
    cached: bool = False


class TranslateResponse(BaseModel):
    translations: dict[str, str]
    mode: int
    cached: bool = False


class AskResponse(BaseModel):
    explanation: str
    simple: str | None = None
    translations: list[str] = Field(default_factory=list)
    vocabulary: list[dict[str, Any]] = Field(default_factory=list)
    mode: int
    cached: bool = False


class TtsResponse(BaseModel):
    audio_base64: str | None = None
    format: str = "wav"
    message: str | None = None

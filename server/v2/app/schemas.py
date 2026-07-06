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
    scan_models: list[str] | None = None


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


class SttRequest(BaseModel):
    audio_base64: str = ""
    language_code: str = "en"


class SttResponse(BaseModel):
    text: str | None = None
    language_code: str = "en"
    message: str | None = None


class GrammarDepth(int, Enum):
    WORD = 1
    SENTENCE = 2
    PARAGRAPH = 3


class GrammarPrefetchItem(BaseModel):
    context_text: str
    focus_word: str | None = None
    offset: int = 0


class GrammarPrefetchRequest(BaseModel):
    grammar_depth: GrammarDepth = GrammarDepth.WORD
    language_code: str = "en"
    target_languages: list[str] = Field(default_factory=list)
    ai_engine_mode: int = Field(ge=1, le=5, default=1)
    input_source: InputSource = InputSource.TYPED
    subscription_tier: SubscriptionTier = SubscriptionTier.PRO
    items: list[GrammarPrefetchItem] = Field(default_factory=list)


class GrammarPrefetchResponse(BaseModel):
    warmed: int = 0
    cached: int = 0
    total: int = 0
    language_code: str = "en"
    target_languages: list[str] = Field(default_factory=list)
    results: list["GrammarPrefetchResultItem"] = Field(default_factory=list)


class GrammarPrefetchResultItem(BaseModel):
    focus_word: str | None = None
    explanation: str = ""


class AiPrefetchRequest(BaseModel):
    """One background call: grammar warm + optional first-chunk explain or translate."""
    grammar_depth: GrammarDepth = GrammarDepth.WORD
    language_code: str = "en"
    target_languages: list[str] = Field(default_factory=list)
    ai_engine_mode: int = Field(ge=1, le=5, default=1)
    input_source: InputSource = InputSource.TYPED
    subscription_tier: SubscriptionTier = SubscriptionTier.PRO
    grammar_items: list[GrammarPrefetchItem] = Field(default_factory=list)
    explain_text: str | None = None
    translate_text: str | None = None


class AiPrefetchResponse(BaseModel):
    grammar: GrammarPrefetchResponse = Field(default_factory=GrammarPrefetchResponse)
    explain_warmed: bool = False
    explain_cached: bool = False
    translate_warmed: bool = False
    translate_cached: bool = False


class GrammarBookRequest(BaseModel):
    language_code: str = "en"
    language_name: str = ""
    ai_engine_mode: int = Field(ge=1, le=5, default=1)
    subscription_tier: SubscriptionTier = SubscriptionTier.PRO


class GrammarBookSection(BaseModel):
    heading: str = ""
    body: str = ""
    examples: list[str] = Field(default_factory=list)


class GrammarBookChapter(BaseModel):
    number: int = 1
    title: str = ""
    summary: str = ""
    sections: list[GrammarBookSection] = Field(default_factory=list)


class GrammarBookResponse(BaseModel):
    title: str = ""
    language_code: str = "en"
    language_name: str = ""
    chapters: list[GrammarBookChapter] = Field(default_factory=list)
    cached: bool = False


class GrammarBookEnrichRequest(BaseModel):
    language_code: str = "en"
    language_name: str = ""
    chapter_number: int = Field(ge=1, default=1)
    chapter_title: str = ""
    section_heading: str = ""
    section_body: str = ""
    examples: list[str] = Field(default_factory=list)
    ai_engine_mode: int = Field(ge=1, le=5, default=1)
    subscription_tier: SubscriptionTier = SubscriptionTier.PRO


class GrammarBookEnrichResponse(BaseModel):
    expanded_body: str = ""
    extra_examples: list[str] = Field(default_factory=list)
    learner_tip: str = ""
    cached: bool = False


class TranslateStringsRequest(BaseModel):
    target_language: str = "en"
    source_language: str = "en"
    strings: dict[str, str] = Field(default_factory=dict)


class TranslateStringsResponse(BaseModel):
    translations: dict[str, str] = Field(default_factory=dict)
    cached: bool = False
    backend: str | None = None

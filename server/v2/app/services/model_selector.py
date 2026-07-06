"""Select model slot from intent, complexity, user mode, and tier."""

from app.schemas import AiRequest, SubscriptionTier
from app.services.complexity import ComplexityResult
from app.services.model_loader import ModelSlot
from app.services.task_classifier import TaskIntent


def select_model(
    intent: TaskIntent,
    req: AiRequest,
    complexity: ComplexityResult | None = None,
) -> ModelSlot:
    """
    Router rules (cache checked before this runs):

    | Task              | Model        |
    | Translation       | NLLB         |
    | OCR cleanup       | Mistral 7B   |
    | Simple Q&A        | Mistral 7B   |
    | Medium reasoning  | Qwen 7B      |
    | Complex reasoning | Qwen 14B (Plus + mode 5 only) |
    | Coding (Plus)     | DeepSeek Coder V2; Qwen2.5-Coder 14B when complex / mode 5 |
    """
    if intent == TaskIntent.TRANSLATION:
        return ModelSlot.NLLB

    if intent == TaskIntent.OCR_CLEANUP:
        return ModelSlot.MISTRAL_7B

    if intent == TaskIntent.CODING:
        if req.subscription_tier != SubscriptionTier.PLUS:
            intent = TaskIntent.ANSWER
        else:
            mode = req.ai_engine_mode
            bucket = complexity.bucket if complexity else "MEDIUM"
            if mode == 5 or bucket == "HIGH":
                return ModelSlot.QWEN_CODER_14B
            return ModelSlot.DEEPSEEK_CODER

    # Answer / tutor — respect curated user mode first
    mode = req.ai_engine_mode
    bucket = complexity.bucket if complexity else "MEDIUM"

    if mode == 4:
        return ModelSlot.MISTRAL_7B
    if mode == 2:
        return ModelSlot.NLLB
    if mode in (1, 3, 5):
        if bucket == "LOW":
            return ModelSlot.MISTRAL_7B
        if bucket == "MEDIUM":
            return ModelSlot.QWEN_7B
        # HIGH complexity: Qwen 14B only for Plus tier + High Accuracy (mode 5)
        if mode == 5 and req.subscription_tier == SubscriptionTier.PLUS:
            return ModelSlot.QWEN_14B
        return ModelSlot.QWEN_7B

    # Default answer path by complexity only (no mode 5 / no 14B)
    if bucket == "LOW":
        return ModelSlot.MISTRAL_7B
    if bucket == "MEDIUM":
        return ModelSlot.QWEN_7B
    return ModelSlot.QWEN_7B


def fallback_chain(primary: ModelSlot) -> list[ModelSlot]:
    """Qwen 14B → Qwen 7B → Mistral 7B → Llama 8B; coder slots fall back to general LLMs."""
    if primary == ModelSlot.QWEN_CODER_14B:
        return [
            ModelSlot.QWEN_CODER_14B,
            ModelSlot.DEEPSEEK_CODER,
            ModelSlot.QWEN_14B,
            ModelSlot.QWEN_7B,
            ModelSlot.MISTRAL_7B,
        ]
    if primary == ModelSlot.DEEPSEEK_CODER:
        return [
            ModelSlot.DEEPSEEK_CODER,
            ModelSlot.QWEN_CODER_14B,
            ModelSlot.QWEN_7B,
            ModelSlot.MISTRAL_7B,
        ]
    if primary == ModelSlot.QWEN_14B:
        return [ModelSlot.QWEN_14B, ModelSlot.QWEN_7B, ModelSlot.MISTRAL_7B, ModelSlot.LLAMA_8B]
    if primary == ModelSlot.QWEN_7B:
        return [ModelSlot.QWEN_7B, ModelSlot.MISTRAL_7B, ModelSlot.LLAMA_8B]
    if primary == ModelSlot.MISTRAL_7B:
        return [ModelSlot.MISTRAL_7B, ModelSlot.QWEN_7B, ModelSlot.LLAMA_8B]
    if primary == ModelSlot.NLLB:
        return [ModelSlot.NLLB, ModelSlot.QWEN_7B]
    return [primary, ModelSlot.MISTRAL_7B]

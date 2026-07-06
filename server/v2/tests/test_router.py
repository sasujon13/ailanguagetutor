import pytest

from app.schemas import AiRequest, InputSource, ProcessingIntent, SubscriptionTier
from app.services.complexity import complexity_score
from app.services.model_loader import ModelSlot
from app.services.model_selector import fallback_chain, select_model
from app.services.task_classifier import TaskIntent, classify_intent, is_coding_question


def _req(**kwargs) -> AiRequest:
    defaults = {
        "text": "Why is this grammar wrong?",
        "processing_intent": ProcessingIntent.ANSWER,
        "ai_engine_mode": 1,
        "input_source": InputSource.TYPED,
        "subscription_tier": SubscriptionTier.PRO,
        "language_code": "en",
        "target_languages": [],
    }
    defaults.update(kwargs)
    return AiRequest(**defaults)


def test_classify_translation_intent():
    req = _req(
        text="Please translate this paragraph to French",
        processing_intent=ProcessingIntent.TRANSLATION,
    )
    assert classify_intent(req) == TaskIntent.TRANSLATION


def test_classify_answer_intent():
    req = _req(text="Why does the subjunctive appear here?")
    assert classify_intent(req) == TaskIntent.ANSWER


def test_classify_ocr_cleanup():
    req = _req(text="sh0rt ocr l1ne", input_source=InputSource.OCR)
    assert classify_intent(req, "clean-ocr") == TaskIntent.OCR_CLEANUP


def test_complexity_low_vs_high():
    low = complexity_score("Hi?", ocr_noise=False)
    high = complexity_score("Explain in detail why " + "word " * 200, ocr_noise=True, lang_count=2)
    assert low.bucket == "LOW"
    assert high.bucket == "HIGH"


def test_select_translation_always_nllb():
    req = _req(processing_intent=ProcessingIntent.TRANSLATION, ai_engine_mode=2)
    assert select_model(TaskIntent.TRANSLATION, req) == ModelSlot.NLLB


def test_select_ocr_mistral():
    assert select_model(TaskIntent.OCR_CLEANUP, _req()) == ModelSlot.MISTRAL_7B


def test_select_answer_complexity():
    req = _req(text="ok")
    low_cx = complexity_score("ok", False)
    assert select_model(TaskIntent.ANSWER, req, low_cx) == ModelSlot.MISTRAL_7B

    med_cx = complexity_score(
        "Explain how this works in the passage. " + "word " * 120,
        False,
    )
    assert med_cx.bucket == "MEDIUM"
    assert select_model(TaskIntent.ANSWER, req, med_cx) == ModelSlot.QWEN_7B


def test_select_qwen14_plus_mode5_high():
    req = _req(
        ai_engine_mode=5,
        subscription_tier=SubscriptionTier.PLUS,
        text="Explain in detail why " + "x " * 300,
    )
    cx = complexity_score(req.text, True, lang_count=2)
    assert cx.bucket == "HIGH"
    assert select_model(TaskIntent.ANSWER, req, cx) == ModelSlot.QWEN_14B


def test_plus_high_without_mode5_uses_7b():
    req = _req(
        ai_engine_mode=1,
        subscription_tier=SubscriptionTier.PLUS,
        text="Explain in detail why " + "x " * 300,
    )
    cx = complexity_score(req.text, True, lang_count=2)
    assert cx.bucket == "HIGH"
    assert select_model(TaskIntent.ANSWER, req, cx) == ModelSlot.QWEN_7B


def test_pro_mode5_high_uses_7b_without_plus():
    """Pro cannot select mode 5 at API tier gate; selector must not route 14B anyway."""
    req = _req(
        ai_engine_mode=5,
        subscription_tier=SubscriptionTier.PRO,
        text="Explain in detail why " + "x " * 300,
    )
    cx = complexity_score(req.text, True, lang_count=2)
    assert cx.bucket == "HIGH"
    assert select_model(TaskIntent.ANSWER, req, cx) == ModelSlot.QWEN_7B


def test_classify_coding_intent():
    req = _req(
        text="```python\ndef fib(n):\n    return n\n```\nWhy does this fail?",
        subscription_tier=SubscriptionTier.PLUS,
    )
    assert classify_intent(req) == TaskIntent.CODING


def test_classify_coding_keywords():
    req = _req(
        text="Debug this Kotlin function — the compile error says unresolved reference",
        subscription_tier=SubscriptionTier.PLUS,
    )
    assert classify_intent(req) == TaskIntent.CODING


def test_select_coding_plus_deepseek():
    req = _req(
        text="Fix this Python function: def add(a,b): return a+b",
        subscription_tier=SubscriptionTier.PLUS,
        ai_engine_mode=1,
    )
    cx = complexity_score(req.text, False)
    assert select_model(TaskIntent.CODING, req, cx) == ModelSlot.DEEPSEEK_CODER


def test_select_coding_plus_high_uses_qwen_coder():
    req = _req(
        text="Implement a binary search in Java with tests:\n" + "line\n" * 80,
        subscription_tier=SubscriptionTier.PLUS,
        ai_engine_mode=5,
    )
    cx = complexity_score(req.text, False)
    assert select_model(TaskIntent.CODING, req, cx) == ModelSlot.QWEN_CODER_14B


def test_select_coding_pro_uses_general_answer_models():
    req = _req(
        text="```js\nfunction foo() {}\n``` explain",
        subscription_tier=SubscriptionTier.PRO,
    )
    cx = complexity_score(req.text, False)
    assert select_model(TaskIntent.CODING, req, cx) == ModelSlot.MISTRAL_7B


def test_fallback_chain_coder():
    chain = fallback_chain(ModelSlot.DEEPSEEK_CODER)
    assert chain[0] == ModelSlot.DEEPSEEK_CODER
    assert ModelSlot.QWEN_CODER_14B in chain


def test_fallback_chain_14b():
    chain = fallback_chain(ModelSlot.QWEN_14B)
    assert chain[0] == ModelSlot.QWEN_14B
    assert ModelSlot.QWEN_7B in chain
    assert ModelSlot.MISTRAL_7B in chain

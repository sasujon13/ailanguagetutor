from fastapi import APIRouter

from app.schemas import TtsResponse

router = APIRouter()


@router.post("/tts", response_model=TtsResponse)
async def tts(text: str = "", voice: str = "en"):
    return TtsResponse(
        audio_base64=None,
        format="wav",
        message="Piper TTS stub — see docs/OPTIONAL_FEATURES.md and scripts/setup_models.ps1",
    )

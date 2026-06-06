from fastapi import APIRouter

from app.schemas import SttRequest, SttResponse

router = APIRouter()


@router.post("/stt", response_model=SttResponse)
async def stt(body: SttRequest) -> SttResponse:
    return SttResponse(
        text=None,
        language_code=body.language_code,
        message=(
            "Whisper STT — install weights via scripts/setup_models.ps1 "
            "(see docs/OPTIONAL_FEATURES.md)"
        ),
    )

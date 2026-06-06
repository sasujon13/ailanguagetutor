from fastapi import APIRouter, Request

from app.routers.deps import device_id_from_request
from app.schemas import TranslateStringsRequest, TranslateStringsResponse
from app.services.mode_router import ModeRouter

router = APIRouter()


@router.post("/translate-strings", response_model=TranslateStringsResponse)
async def translate_strings(body: TranslateStringsRequest, request: Request) -> TranslateStringsResponse:
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.translate_strings(body, device_id=device_id_from_request(request))

from fastapi import APIRouter, Request

from app.routers.deps import device_id_from_request
from app.schemas import AiRequest, CleanOcrResponse
from app.services.mode_router import ModeRouter

router = APIRouter()


@router.post("/clean-ocr", response_model=CleanOcrResponse)
async def clean_ocr(body: AiRequest, request: Request):
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.clean_ocr(body, device_id=device_id_from_request(request))

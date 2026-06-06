from fastapi import APIRouter, Request

from app.routers.deps import device_id_from_request
from app.schemas import AiPrefetchRequest, AiPrefetchResponse
from app.services.mode_router import ModeRouter

router = APIRouter()


@router.post("/prefetch-ai", response_model=AiPrefetchResponse)
async def prefetch_ai(body: AiPrefetchRequest, request: Request) -> AiPrefetchResponse:
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.prefetch_ai(body, device_id=device_id_from_request(request))

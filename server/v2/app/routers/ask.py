from fastapi import APIRouter, Request

from app.routers.deps import device_id_from_request
from app.schemas import AiRequest, AskResponse
from app.services.mode_router import ModeRouter

router = APIRouter()


@router.post("/ask", response_model=AskResponse)
async def ask(body: AiRequest, request: Request):
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.ask(body, device_id=device_id_from_request(request))

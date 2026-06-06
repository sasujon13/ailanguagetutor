from fastapi import APIRouter, Request

from app.routers.deps import device_id_from_request
from app.schemas import GrammarPrefetchRequest, GrammarPrefetchResponse
from app.services.mode_router import ModeRouter

router = APIRouter()


@router.post("/prefetch-grammar", response_model=GrammarPrefetchResponse)
async def prefetch_grammar(body: GrammarPrefetchRequest, request: Request) -> GrammarPrefetchResponse:
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.prefetch_grammar(body, device_id=device_id_from_request(request))

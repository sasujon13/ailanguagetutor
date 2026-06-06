from fastapi import APIRouter, Request

from app.routers.deps import device_id_from_request
from app.schemas import GrammarBookRequest, GrammarBookResponse, GrammarBookEnrichRequest, GrammarBookEnrichResponse
from app.services.mode_router import ModeRouter

router = APIRouter()


@router.post("/grammar-book", response_model=GrammarBookResponse)
async def grammar_book(body: GrammarBookRequest, request: Request) -> GrammarBookResponse:
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.grammar_book(body, device_id=device_id_from_request(request))


@router.post("/grammar-book/enrich-section", response_model=GrammarBookEnrichResponse)
async def grammar_book_enrich_section(
    body: GrammarBookEnrichRequest,
    request: Request,
) -> GrammarBookEnrichResponse:
    router_svc: ModeRouter = request.app.state.mode_router
    return await router_svc.grammar_book_enrich_section(body, device_id=device_id_from_request(request))

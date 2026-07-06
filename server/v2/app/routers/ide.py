"""IDE endpoints for Cheradip VS Code extension."""

import json

from fastapi import APIRouter, Request
from fastapi.responses import StreamingResponse

from app.schemas import (
    IdeChatRequest,
    IdeChatResponse,
    IdeCompleteRequest,
    IdeCompleteResponse,
    IdeEditRequest,
    IdeEditResponse,
    IdeModelsResponse,
)
from app.services.ide_service import IdeService
from app.services.mode_router import ModeRouter

router = APIRouter()


def _ide_service(request: Request) -> IdeService:
    router_svc: ModeRouter = request.app.state.mode_router
    return IdeService(request.app.state.model_loader, router_svc.inference)


@router.get("/ide/models", response_model=IdeModelsResponse)
async def ide_models(request: Request) -> IdeModelsResponse:
    return _ide_service(request).list_models()


@router.post("/ide/chat")
async def ide_chat(body: IdeChatRequest, request: Request):
    svc = _ide_service(request)

    async def event_stream():
        async for chunk in svc.chat_stream(body):
            yield f"data: {json.dumps({'content': chunk})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post("/ide/chat/sync", response_model=IdeChatResponse)
async def ide_chat_sync(body: IdeChatRequest, request: Request) -> IdeChatResponse:
    return await _ide_service(request).chat(body)


@router.post("/ide/complete", response_model=IdeCompleteResponse)
async def ide_complete(body: IdeCompleteRequest, request: Request) -> IdeCompleteResponse:
    return await _ide_service(request).complete(body)


@router.post("/ide/edit", response_model=IdeEditResponse)
async def ide_edit(body: IdeEditRequest, request: Request) -> IdeEditResponse:
    return await _ide_service(request).edit(body)

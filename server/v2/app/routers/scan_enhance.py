"""Scan analyze + enhance API."""

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, Response

from app.routers.deps import device_id_from_request
from app.services.scan_enhancer import get_scan_enhancer

router = APIRouter()
MAX_BYTES = 20 * 1024 * 1024


@router.post("/scan-analyze")
async def scan_analyze(
    request: Request,
    image: UploadFile = File(...),
    premium: bool = Form(True),
):
    device_id_from_request(request)
    raw = await image.read()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty image")
    if len(raw) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="Image too large (max 20MB)")
    try:
        enhancer = get_scan_enhancer()
        metrics, rec = enhancer.analyze(raw)
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    return JSONResponse(
        {
            "document_class": rec.document_class.value,
            "recommended_mode": rec.recommended_mode,
            "recommended_level": rec.recommended_level,
            "recommended_label": rec.label,
            "metrics": {
                "blur_score": round(metrics.blur_score, 3),
                "brightness": round(metrics.brightness, 3),
                "contrast": round(metrics.contrast, 3),
                "shadow_severity": round(metrics.shadow_severity, 3),
                "color_richness": round(metrics.color_richness, 3),
                "edge_density": round(metrics.edge_density, 3),
                "wrinkle_score": round(metrics.wrinkle_score, 3),
                "damage_score": round(metrics.damage_score, 3),
                "has_machine_readable": metrics.has_machine_readable,
            },
        }
    )


@router.post("/scan-enhance")
async def scan_enhance(
    request: Request,
    image: UploadFile = File(...),
    level: int = Form(0, ge=0, le=7),
    premium: bool = Form(True),
    document_class: str | None = Form(None),
):
    device_id_from_request(request)
    raw = await image.read()
    if not raw:
        raise HTTPException(status_code=400, detail="Empty image")
    if len(raw) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="Image too large (max 20MB)")

    try:
        enhancer = get_scan_enhancer()
        jpeg, models_used, rec = enhancer.enhance_jpeg(
            raw, level, premium=premium, doc_class_hint=document_class
        )
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e

    headers = {
        "X-Enhance-Level": str(level),
        "X-Models-Used": ",".join(models_used),
    }
    if rec is not None:
        headers["X-Document-Class"] = rec.document_class.value
        headers["X-Recommended-Mode"] = rec.recommended_mode
        headers["X-Recommended-Level"] = str(rec.recommended_level)
    return Response(content=jpeg, media_type="image/jpeg", headers=headers)

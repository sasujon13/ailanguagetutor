"""Shared helpers for inference routers."""

from fastapi import Request


def device_id_from_request(request: Request) -> str | None:
    return request.headers.get("X-Device-Id") or request.headers.get("x-device-id")

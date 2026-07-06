"""Synthetic challenging scan fixtures for level 4–7 validation."""

from __future__ import annotations

import cv2
import numpy as np


def _base_document(width: int = 640, height: int = 900) -> np.ndarray:
    page = np.full((height, width, 3), 245, dtype=np.uint8)
    for y in range(120, height - 80, 42):
        cv2.line(page, (70, y), (width - 70, y), (35, 35, 35), 2)
    cv2.putText(page, "Scan validation fixture", (80, 90), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (20, 20, 20), 2)
    cv2.rectangle(page, (40, 40), (width - 40, height - 40), (180, 180, 180), 2)
    return page


def make_shadowed_scan(width: int = 640, height: int = 900) -> np.ndarray:
    page = _base_document(width, height)
    gradient = np.linspace(1.0, 0.55, width, dtype=np.float32)
    shade = gradient[None, :, None]
    dark = (page.astype(np.float32) * shade).astype(np.uint8)
    vignette = np.linspace(1.0, 0.7, height, dtype=np.float32)[:, None, None]
    return np.clip(dark * vignette, 0, 255).astype(np.uint8)


def make_wrinkled_scan(width: int = 640, height: int = 900) -> np.ndarray:
    page = _base_document(width, height)
    map_x = np.zeros((height, width), dtype=np.float32)
    map_y = np.zeros((height, width), dtype=np.float32)
    for y in range(height):
        for x in range(width):
            nx = x / max(width - 1, 1)
            ny = y / max(height - 1, 1)
            bend_x = 14.0 * np.sin(ny * np.pi * 3.2) * (nx - 0.5)
            bend_y = 10.0 * np.sin(nx * np.pi * 2.5) * (ny - 0.5)
            map_x[y, x] = x + bend_x
            map_y[y, x] = y + bend_y
    warped = cv2.remap(page, map_x, map_y, cv2.INTER_LINEAR, borderValue=(230, 230, 230))
    noise = np.random.default_rng(42).integers(-6, 7, warped.shape, dtype=np.int16)
    return np.clip(warped.astype(np.int16) + noise, 0, 255).astype(np.uint8)


def make_folded_corner_scan(width: int = 640, height: int = 900) -> np.ndarray:
    page = make_shadowed_scan(width, height)
    pts_src = np.float32([[0, 0], [width, 0], [width, height], [0, height]])
    pts_dst = np.float32([[35, 25], [width - 10, 0], [width - 20, height - 15], [0, height]])
    matrix = cv2.getPerspectiveTransform(pts_src, pts_dst)
    return cv2.warpPerspective(page, matrix, (width, height), borderValue=(210, 210, 210))

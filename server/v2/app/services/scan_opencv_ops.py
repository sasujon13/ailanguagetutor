"""Shared OpenCV enhancement operators."""

from __future__ import annotations

try:
    import cv2
    import numpy as np

    _CV = True
except ImportError:
    _CV = False
    cv2 = None  # type: ignore
    np = None  # type: ignore


def order_points(pts: np.ndarray) -> np.ndarray:
    rect = np.zeros((4, 2), dtype="float32")
    s = pts.sum(axis=1)
    rect[0] = pts[np.argmin(s)]
    rect[2] = pts[np.argmax(s)]
    diff = np.diff(pts, axis=1)
    rect[1] = pts[np.argmin(diff)]
    rect[3] = pts[np.argmax(diff)]
    return rect


def perspective_correct(image: np.ndarray, strength: float) -> np.ndarray:
    if not _CV or strength <= 0.02:
        return image
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 50, 150)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return image
    contours = sorted(contours, key=cv2.contourArea, reverse=True)[:8]
    h, w = image.shape[:2]
    page = None
    for cnt in contours:
        peri = cv2.arcLength(cnt, True)
        approx = cv2.approxPolyDP(cnt, 0.02 * peri, True)
        if len(approx) == 4 and cv2.contourArea(approx) > (w * h) // 5:
            page = approx.reshape(4, 2).astype("float32")
            break
    if page is None:
        return image
    src = order_points(page)
    max_w = int(max(np.linalg.norm(src[0] - src[1]), np.linalg.norm(src[2] - src[3])))
    max_h = int(max(np.linalg.norm(src[0] - src[3]), np.linalg.norm(src[1] - src[2])))
    if max_w < 32 or max_h < 32:
        return image
    dst = np.array([[0, 0], [max_w - 1, 0], [max_w - 1, max_h - 1], [0, max_h - 1]], dtype="float32")
    matrix = cv2.getPerspectiveTransform(src, dst)
    warped = cv2.warpPerspective(image, matrix, (max_w, max_h))
    if strength >= 0.99:
        return warped
    resized = cv2.resize(warped, (w, h))
    return cv2.addWeighted(image, 1.0 - strength, resized, strength, 0)


def shadow_cleanup(image: np.ndarray, strength: float) -> np.ndarray:
    if not _CV or strength <= 0.02:
        return image
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    dilated = cv2.dilate(l, np.ones((7, 7), np.uint8))
    bg = cv2.medianBlur(dilated, 21)
    norm_l = cv2.divide(l, bg, scale=255)
    blend = cv2.addWeighted(l, 1.0 - strength, norm_l, strength, 0)
    merged = cv2.merge([blend, a, b])
    return cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)


def denoise(image: np.ndarray, strength: float) -> np.ndarray:
    if not _CV or strength < 0.15:
        return image
    h = int(3 + strength * 8)
    return cv2.fastNlMeansDenoisingColored(image, None, h, h, 7, 21)


def enhance_colors(image: np.ndarray, strength: float) -> np.ndarray:
    if not _CV or strength <= 0.02:
        return image
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clip = 1.5 + strength * 2.5
    clahe = cv2.createCLAHE(clipLimit=clip, tileGridSize=(8, 8))
    l2 = clahe.apply(l)
    l_blend = cv2.addWeighted(l, 1.0 - strength, l2, strength, 0)
    hsv = cv2.cvtColor(cv2.merge([l_blend, a, b]), cv2.COLOR_LAB2BGR)
    hsv = cv2.cvtColor(hsv, cv2.COLOR_BGR2HSV).astype("float32")
    hsv[:, :, 1] = np.clip(hsv[:, :, 1] * (1.0 + strength * 0.35), 0, 255)
    hsv[:, :, 2] = np.clip(hsv[:, :, 2] * (1.0 + strength * 0.12), 0, 255)
    return cv2.cvtColor(hsv.astype("uint8"), cv2.COLOR_HSV2BGR)


def sharpen(image: np.ndarray, cleanup: float, enhancement: float) -> np.ndarray:
    if not _CV:
        return image
    amount = max(cleanup, enhancement) * 0.6
    if amount < 0.08:
        return image
    blur = cv2.GaussianBlur(image, (0, 0), 1.2)
    return cv2.addWeighted(image, 1.0 + amount, blur, -amount, 0)

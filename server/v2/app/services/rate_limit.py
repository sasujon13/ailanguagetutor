"""Per-device token bucket rate limits (see deploy/rate-limiting.md)."""

import time
from dataclasses import dataclass, field

from fastapi import HTTPException

from app.schemas import SubscriptionTier


@dataclass
class _Bucket:
    tokens: float
    last_refill: float


@dataclass
class RateLimiter:
    pro_per_hour: int = 60
    plus_per_hour: int = 120
    pro_burst_per_min: int = 10
    plus_burst_per_min: int = 20
    _buckets: dict[str, _Bucket] = field(default_factory=dict)
    allowed: int = 0
    rejected: int = 0

    def _limits(self, tier: SubscriptionTier) -> tuple[float, float]:
        if tier == SubscriptionTier.PLUS:
            return float(self.plus_per_hour), float(self.plus_burst_per_min)
        return float(self.pro_per_hour), float(self.pro_burst_per_min)

    def check(self, device_id: str, tier: SubscriptionTier) -> None:
        if tier == SubscriptionTier.FREE:
            self.rejected += 1
            raise HTTPException(status_code=403, detail="FREE_TIER_OFFLINE_ONLY")

        hourly, burst = self._limits(tier)
        key = f"{device_id}:{tier.value}"
        now = time.monotonic()
        bucket = self._buckets.get(key)
        if bucket is None:
            bucket = _Bucket(tokens=burst, last_refill=now)
            self._buckets[key] = bucket

        elapsed_min = (now - bucket.last_refill) / 60.0
        if elapsed_min > 0:
            refill = elapsed_min * (burst / 1.0)
            bucket.tokens = min(burst, bucket.tokens + refill)
            bucket.last_refill = now

        if bucket.tokens < 1.0:
            self.rejected += 1
            raise HTTPException(
                status_code=429,
                detail="RATE_LIMIT_EXCEEDED",
                headers={"Retry-After": "30"},
            )
        bucket.tokens -= 1.0
        self.allowed += 1

    def stats(self) -> dict:
        return {
            "allowed": self.allowed,
            "rejected": self.rejected,
            "active_buckets": len(self._buckets),
            "pro_per_hour": self.pro_per_hour,
            "plus_per_hour": self.plus_per_hour,
        }

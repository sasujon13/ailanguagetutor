"""L2 shared cache — Redis or in-memory fallback (see docs/ROUTING_AND_CACHE.md)."""

import json
import time
from dataclasses import dataclass, field
from typing import Any


@dataclass
class L2Cache:
    """Process-wide dict with TTL; swap for Redis when REDIS_URL is set."""

    default_ttl_sec: float = 86400.0  # 24h
    max_entries: int = 5000
    _store: dict[str, tuple[str, float]] = field(default_factory=dict)
    hits: int = 0
    misses: int = 0

    def get(self, key: str) -> Any | None:
        entry = self._store.get(key)
        if entry is None:
            self.misses += 1
            return None
        raw, expires = entry
        if time.monotonic() > expires:
            del self._store[key]
            self.misses += 1
            return None
        self.hits += 1
        return json.loads(raw)

    def set(self, key: str, value: Any, ttl_sec: float | None = None) -> None:
        if len(self._store) >= self.max_entries:
            oldest = min(self._store, key=lambda k: self._store[k][1])
            del self._store[oldest]
        ttl = ttl_sec if ttl_sec is not None else self.default_ttl_sec
        self._store[key] = (json.dumps(value, default=str), time.monotonic() + ttl)

    def stats(self) -> dict:
        total = self.hits + self.misses
        rate = (self.hits / total * 100) if total else 0.0
        return {
            "l2_hits": self.hits,
            "l2_misses": self.misses,
            "l2_entries": len(self._store),
            "hit_rate_pct": round(rate, 1),
        }

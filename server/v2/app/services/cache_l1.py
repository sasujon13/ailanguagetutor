"""Multi-tier cache: L1 RAM → L2 shared → L3 SQLite (see docs/ROUTING_AND_CACHE.md)."""

import hashlib
import time
from dataclasses import dataclass, field
from typing import Any

from app.services.cache_l2 import L2Cache
from app.services.cache_l3 import L3Cache


def cache_key(text: str, intent: str, mode: int, src: str, targets: list[str]) -> str:
    normalized = " ".join(text.lower().split())
    payload = f"{normalized}|{intent}|{mode}|{src}|{','.join(sorted(targets))}"
    return hashlib.sha256(payload.encode()).hexdigest()


@dataclass
class CacheManager:
    l1_ttl_sec: float = 5.0
    l1_max: int = 500
    l2: L2Cache = field(default_factory=L2Cache)
    l3: L3Cache = field(default_factory=L3Cache)
    _l1: dict[str, tuple[Any, float]] = field(default_factory=dict)
    l1_hits: int = 0
    l2_hits: int = 0
    l3_hits: int = 0
    misses: int = 0

    def get(self, key: str) -> Any | None:
        entry = self._l1.get(key)
        if entry is not None:
            value, expires = entry
            if time.monotonic() <= expires:
                self.l1_hits += 1
                return value
            del self._l1[key]

        if (value := self.l2.get(key)) is not None:
            self.l2_hits += 1
            self._set_l1(key, value)
            return value

        if (value := self.l3.get(key)) is not None:
            self.l3_hits += 1
            self.l2.set(key, value)
            self._set_l1(key, value)
            return value

        self.misses += 1
        return None

    def set(self, key: str, value: Any) -> None:
        self._set_l1(key, value)
        self.l2.set(key, value)
        self.l3.set(key, value)

    def _set_l1(self, key: str, value: Any) -> None:
        if len(self._l1) >= self.l1_max:
            oldest = min(self._l1, key=lambda k: self._l1[k][1])
            del self._l1[oldest]
        self._l1[key] = (value, time.monotonic() + self.l1_ttl_sec)

    def stats(self) -> dict:
        total_hits = self.l1_hits + self.l2_hits + self.l3_hits
        total = total_hits + self.misses
        overall = (total_hits / total * 100) if total else 0.0
        return {
            "l1_hits": self.l1_hits,
            "l2_hits": self.l2_hits,
            "l3_hits": self.l3_hits,
            "misses": self.misses,
            "hit_rate_pct": round(overall, 1),
            "cache_hit_rate_l1": round((self.l1_hits / total * 100) if total else 0.0, 1),
            "cache_hit_rate_l2": round((self.l2_hits / total * 100) if total else 0.0, 1),
            "cache_hit_rate_l3": round((self.l3_hits / total * 100) if total else 0.0, 1),
            "l2": self.l2.stats(),
            "l3": self.l3.stats(),
        }

    def shutdown(self) -> None:
        self.l3.close()

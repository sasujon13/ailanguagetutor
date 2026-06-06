"""L3 SQLite persistent cache — warm restarts + admin stats."""

import json
import sqlite3
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class L3Cache:
    db_path: Path = field(default_factory=lambda: Path("./data/ai_cache.db"))
    hits: int = 0
    misses: int = 0
    _conn: sqlite3.Connection | None = field(default=None, repr=False)

    def _connection(self) -> sqlite3.Connection:
        if self._conn is None:
            self.db_path.parent.mkdir(parents=True, exist_ok=True)
            self._conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS ai_cache (
                    key TEXT PRIMARY KEY,
                    response_json TEXT NOT NULL,
                    created_at REAL NOT NULL,
                    hit_count INTEGER NOT NULL DEFAULT 0
                )
                """
            )
            self._conn.commit()
        return self._conn

    def get(self, key: str) -> Any | None:
        row = self._connection().execute(
            "SELECT response_json FROM ai_cache WHERE key = ?",
            (key,),
        ).fetchone()
        if row is None:
            self.misses += 1
            return None
        self._connection().execute(
            "UPDATE ai_cache SET hit_count = hit_count + 1 WHERE key = ?",
            (key,),
        )
        self._connection().commit()
        self.hits += 1
        return json.loads(row[0])

    def set(self, key: str, value: Any) -> None:
        now = time.time()
        self._connection().execute(
            """
            INSERT INTO ai_cache (key, response_json, created_at, hit_count)
            VALUES (?, ?, ?, 0)
            ON CONFLICT(key) DO UPDATE SET
                response_json = excluded.response_json,
                created_at = excluded.created_at
            """,
            (key, json.dumps(value, default=str), now),
        )
        self._connection().commit()

    def stats(self) -> dict:
        total = self.hits + self.misses
        rate = (self.hits / total * 100) if total else 0.0
        count = self._connection().execute("SELECT COUNT(*) FROM ai_cache").fetchone()[0]
        return {
            "l3_hits": self.hits,
            "l3_misses": self.misses,
            "l3_entries": count,
            "hit_rate_pct": round(rate, 1),
        }

    def close(self) -> None:
        if self._conn is not None:
            self._conn.close()
            self._conn = None

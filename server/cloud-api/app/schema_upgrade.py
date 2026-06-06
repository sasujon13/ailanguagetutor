"""Add missing columns on existing MySQL tables (create_all does not alter tables)."""

from __future__ import annotations

import logging

from sqlalchemy import inspect, text
from sqlalchemy.engine import Engine

logger = logging.getLogger(__name__)

# (table, column, ALTER TABLE ... ADD COLUMN fragment)
_COLUMN_PATCHES: list[tuple[str, str, str]] = [
    ("users", "full_name", "full_name VARCHAR(80) NULL"),
    ("users", "email_verified", "email_verified TINYINT(1) NOT NULL DEFAULT 0"),
    ("users", "whatsapp_verified", "whatsapp_verified TINYINT(1) NOT NULL DEFAULT 0"),
    ("users", "login_with", "login_with VARCHAR(16) NULL"),
    ("promo_codes", "auto_apply", "auto_apply TINYINT(1) NOT NULL DEFAULT 0"),
    ("promo_codes", "paywall_slot", "paywall_slot INT NOT NULL DEFAULT 2"),
]


def _table_exists(engine: Engine, table: str) -> bool:
    return inspect(engine).has_table(table)


def _column_exists(engine: Engine, table: str, column: str) -> bool:
    with engine.connect() as conn:
        row = conn.execute(
            text(
                """
                SELECT COUNT(*) AS n
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = :table_name
                  AND COLUMN_NAME = :column_name
                """
            ),
            {"table_name": table, "column_name": column},
        ).one()
        return int(row.n) > 0


def upgrade_schema(engine: Engine) -> None:
    """Apply idempotent ALTER TABLE patches for columns added after first deploy."""
    with engine.begin() as conn:
        for table, column, ddl in _COLUMN_PATCHES:
            if not _table_exists(engine, table):
                continue
            if _column_exists(engine, table, column):
                continue
            conn.execute(text(f"ALTER TABLE `{table}` ADD COLUMN {ddl}"))
            logger.info("Added column %s.%s", table, column)

        # Existing admins created before signup columns — treat as verified.
        if _table_exists(engine, "users") and _column_exists(engine, "users", "email_verified"):
            conn.execute(
                text(
                    """
                    UPDATE users
                    SET email_verified = 1, whatsapp_verified = 1
                    WHERE role = 'admin'
                      AND (email_verified = 0 OR whatsapp_verified = 0)
                    """
                )
            )

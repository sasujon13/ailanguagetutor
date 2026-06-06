"""Environment — XAMPP MySQL defaults."""

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    host: str = "0.0.0.0"
    port: int = 8790
    database_url: str = "mysql+pymysql://root:@127.0.0.1:3306/ailanguagetutor?charset=utf8mb4"
    admin_seed_email: str = "sashafik.me@gmail.com"
    admin_seed_whatsapp: str = "+8801722710298"
    admin_seed_password: str = ""
    trial_days: int = 7
    otp_ttl_minutes: int = 15
    session_ttl_days: int = 30
    dev_log_otp: bool = True
    gemini_api_key: str = ""
    gemini_model: str = "gemini-flash-latest"
    openai_api_key: str = ""
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-haiku-4-5-20251001"
    anthropic_paid_model: str = "claude-sonnet-4-5-20250929"
    groq_api_key: str = ""
    mistral_api_key: str = ""
    openrouter_api_key: str = ""
    openrouter_model: str = "openrouter/free"
    openrouter_paid_model: str = "anthropic/claude-3.5-sonnet"
    packs_dir: Path = Path("./packs")
    public_base_url: str = "http://localhost:8790/api/ailt"
    translate_api_responses: bool = True
    translate_api_timeout_seconds: float = 4.0
    home_ai_translate_url: str = "http://127.0.0.1:8787/translate-strings"


settings = Settings()

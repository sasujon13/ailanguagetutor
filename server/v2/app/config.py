"""Environment configuration."""

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    host: str = "0.0.0.0"
    port: int = 8787
    models_dir: Path = Path("./models")
    ai_modes_config: Path = Path("../ai-modes.example.json")
    max_concurrent: int = 4
    max_context_answer: int = 2048
    max_context_translate: int = 1024
    inference_backend: str = "stub"  # stub | ollama | openvino
    ollama_base_url: str = "http://127.0.0.1:11434"
    openvino_device: str = "GPU"
    redis_url: str | None = None
    cache_db_path: Path = Path("./data/ai_cache.db")
    cors_origins: list[str] = ["*"]
    resident_models: list[str] = ["nllb-600m", "mistral-7b-int4"]


settings = Settings()

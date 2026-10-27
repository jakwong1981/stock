"""Settings loaded from environment variables (decrypted at process start by dotenvx)."""

from functools import lru_cache

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    # --- LLM: DeepSeek is the preferred provider for every analysis task ---
    deepseek_api_key: SecretStr = SecretStr("")
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model_fast: str = "deepseek-chat"  # sentiment + summaries
    deepseek_model_chat: str = "deepseek-chat"  # research assistant (tool calling)
    llm_timeout_seconds: float = 30.0
    llm_max_retries: int = 2
    # Optional OpenAI-compatible fallback, used only when DeepSeek fails
    llm_fallback_base_url: str = ""
    llm_fallback_api_key: SecretStr = SecretStr("")
    llm_fallback_model: str = ""

    # --- Auth: same HS256 secret as the backend, used to verify user tokens ---
    jwt_secret: SecretStr = SecretStr("")
    jwt_issuer: str = "cyberstock-pulse"

    # --- Integration ---
    backend_url: str = "http://localhost:8080"
    database_url: str = ""  # postgresql://user:pass@host:5432/db ; empty → in-memory store
    redis_url: str = ""  # redis://host:6379/0 ; empty → in-memory cache

    # --- News ---
    news_provider: str = "mock"  # yahoo | mock | finnhub (Docker default: yahoo)
    finnhub_api_key: SecretStr = SecretStr("")
    news_poll_seconds: int = Field(default=60, ge=5)
    news_ingest_enabled: bool = True

    # --- Chat limits ---
    chat_max_messages: int = 20
    chat_max_chars: int = 4000
    chat_max_tool_rounds: int = 4

    cors_allowed_origins: str = "http://localhost:5173"

    @property
    def cors_origins(self) -> list[str]:
        return [o.strip() for o in self.cors_allowed_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()

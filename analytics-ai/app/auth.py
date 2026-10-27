"""Verifies backend-issued HS256 JWTs so only signed-in users can use paid LLM features."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

import jwt
from fastapi import Header, HTTPException, status

from app.config import Settings


@dataclass(frozen=True)
class CurrentUser:
    user_id: str
    token: str


def make_require_user(settings: Settings) -> Callable[..., CurrentUser]:
    """Builds a FastAPI dependency bound to the given settings."""

    def require_user(authorization: str | None = Header(default=None)) -> CurrentUser:
        secret = settings.jwt_secret.get_secret_value()
        if not secret:
            # Without the shared secret no token can be verified: fail closed.
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail={"code": "AUTH_NOT_CONFIGURED"})
        if not authorization or not authorization.lower().startswith("bearer "):
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail={"code": "UNAUTHORIZED"})
        token = authorization.split(" ", 1)[1].strip()
        try:
            claims = jwt.decode(token, secret, algorithms=["HS256"], issuer=settings.jwt_issuer,
                                options={"require": ["exp", "sub", "iss"]})
        except jwt.PyJWTError as e:
            raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail={"code": "UNAUTHORIZED"}) from e
        return CurrentUser(user_id=str(claims["sub"]), token=token)

    return require_user

import hmac

from fastapi import Header, HTTPException, status

from app.config import get_settings


def require_token(authorization: str | None = Header(default=None)) -> None:
    expected = get_settings().device_token
    scheme, _, token = (authorization or "").partition(" ")
    if scheme != "Bearer" or not hmac.compare_digest(token.encode(), expected.encode()):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token không hợp lệ",
            headers={"WWW-Authenticate": "Bearer"},
        )

"""PII minimisation before sending text to an external LLM, and secret masking for logs."""

import logging
import re

_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
# Phone numbers: 8+ digits possibly separated by spaces/dashes, optional +country code
_PHONE = re.compile(r"(?<!\d)(?:\+\d{1,3}[\s-]?)?(?:\d[\s-]?){7,13}\d(?!\d)")
_SECRET = re.compile(r"(sk-[A-Za-z0-9]{8,}|Bearer\s+[A-Za-z0-9._-]{16,})")


def redact_pii(text: str) -> str:
    text = _EMAIL.sub("[email]", text)
    return _PHONE.sub("[phone]", text)


def mask_secrets(text: str) -> str:
    return _SECRET.sub("[redacted]", text)


class SecretMaskingFilter(logging.Filter):
    """Masks API keys and bearer tokens in every log record."""

    def filter(self, record: logging.LogRecord) -> bool:
        if isinstance(record.msg, str):
            record.msg = mask_secrets(record.msg)
        if isinstance(record.args, tuple):
            record.args = tuple(mask_secrets(a) if isinstance(a, str) else a for a in record.args)
        return True

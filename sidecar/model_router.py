from __future__ import annotations

import logging
import os

import anthropic
import httpx

from action_parser import parse_and_validate
from models import GoalDecision, GoalType, WorldState
from prompt_builder import build_prompt

logger = logging.getLogger(__name__)

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5-coder:7b")
OLLAMA_TIMEOUT = float(os.getenv("OLLAMA_TIMEOUT", "4.0"))

ANTHROPIC_MODEL = os.getenv("ANTHROPIC_MODEL", "claude-haiku-4-5-20251001")

# Lazy singletons
_anthropic_client: anthropic.AsyncAnthropic | None = None


def _get_anthropic_client() -> anthropic.AsyncAnthropic:
    global _anthropic_client
    if _anthropic_client is None:
        _anthropic_client = anthropic.AsyncAnthropic()
    return _anthropic_client


async def _ollama_complete(system: str, user: str) -> str:
    """Call Ollama's /api/chat endpoint. Raises on timeout or connection error."""
    payload = {
        "model": OLLAMA_MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "stream": False,
    }
    async with httpx.AsyncClient(base_url=OLLAMA_BASE_URL, timeout=OLLAMA_TIMEOUT) as client:
        resp = await client.post("/api/chat", json=payload)
        resp.raise_for_status()
        data = resp.json()
        return data["message"]["content"]


async def _anthropic_complete(system: str, user: str) -> str:
    """Fallback: call Anthropic API (claude-haiku for speed/cost)."""
    client = _get_anthropic_client()
    message = await client.messages.create(
        model=ANTHROPIC_MODEL,
        max_tokens=256,
        system=system,
        messages=[{"role": "user", "content": user}],
    )
    return message.content[0].text  # type: ignore[union-attr]


async def decide(world_state: WorldState, memory_context: str) -> GoalDecision:
    """Route the decision request: try Ollama first, fall back to Anthropic."""
    system, user = build_prompt(world_state, memory_context)

    try:
        logger.debug("Trying Ollama (%s)…", OLLAMA_MODEL)
        raw = await _ollama_complete(system, user)
        logger.info("Ollama responded")
    except Exception as ollama_exc:
        logger.warning("Ollama unavailable (%s) — falling back to Anthropic", ollama_exc)
        try:
            raw = await _anthropic_complete(system, user)
            logger.info("Anthropic fallback responded")
        except Exception as api_exc:
            logger.error("Anthropic fallback also failed: %s", api_exc)
            return GoalDecision(
                goal=GoalType.IDLE,
                reason=f"All AI backends unavailable: {api_exc}",
            )

    return parse_and_validate(raw)

from __future__ import annotations

import logging

import anthropic
import httpx

from action_parser import _FALLBACK_PLAN, parse_plan
from config import CONFIG
from models import PlanResponse, WorldState
from prompt_builder import build_plan_prompt

logger = logging.getLogger(__name__)

_anthropic_client: anthropic.AsyncAnthropic | None = None


def _get_anthropic_client() -> anthropic.AsyncAnthropic:
    global _anthropic_client
    if _anthropic_client is None:
        _anthropic_client = anthropic.AsyncAnthropic()
    return _anthropic_client


async def _ollama_complete(system: str, user: str) -> str:
    payload = {
        "model":    CONFIG["ollama_model"],
        "messages": [
            {"role": "system", "content": system},
            {"role": "user",   "content": user},
        ],
        "stream": False,
    }
    async with httpx.AsyncClient(
        base_url=CONFIG["ollama_base_url"],
        timeout=CONFIG["ollama_timeout"],
    ) as client:
        resp = await client.post("/api/chat", json=payload)
        resp.raise_for_status()
        return resp.json()["message"]["content"]


async def _anthropic_complete(system: str, user: str) -> str:
    client  = _get_anthropic_client()
    message = await client.messages.create(
        model     = CONFIG["anthropic_model"],
        max_tokens= 1024,
        system    = system,
        messages  = [{"role": "user", "content": user}],
    )
    return message.content[0].text  # type: ignore[union-attr]


async def plan(world_state: WorldState, memory_context: str) -> PlanResponse:
    """Route a plan request: Ollama first, fall back to Anthropic."""
    system, user = build_plan_prompt(world_state, memory_context)
    available    = world_state.world.available_goals or world_state.available_goals

    try:
        logger.debug("Trying Ollama (%s)…", CONFIG["ollama_model"])
        raw = await _ollama_complete(system, user)
        logger.info("Ollama responded")
    except Exception as ollama_exc:
        logger.warning("Ollama unavailable (%s) — falling back to Anthropic", ollama_exc)
        try:
            raw = await _anthropic_complete(system, user)
            logger.info("Anthropic fallback responded")
        except Exception as api_exc:
            logger.error("Anthropic fallback also failed: %s", api_exc)
            return _FALLBACK_PLAN

    return parse_plan(raw, available_goals=available)

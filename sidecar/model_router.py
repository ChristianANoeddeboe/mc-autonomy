from __future__ import annotations

import logging
import os

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


async def _openrouter_complete(system: str, user: str) -> str:
    """OpenAI-compatible chat completion against OpenRouter."""
    api_key = os.getenv("OPENROUTER_API_KEY")
    if not api_key:
        raise RuntimeError("OPENROUTER_API_KEY is not set")

    payload = {
        "model":    CONFIG["openrouter_model"],
        "messages": [
            {"role": "system", "content": system},
            {"role": "user",   "content": user},
        ],
        "stream": False,
    }
    async with httpx.AsyncClient(
        base_url=CONFIG["openrouter_base_url"],
        timeout=CONFIG["openrouter_timeout"],
        headers={"Authorization": f"Bearer {api_key}"},
    ) as client:
        resp = await client.post("/chat/completions", json=payload)
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]


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
    """Route a plan request: primary provider first, fall back to Anthropic."""
    system, user = build_plan_prompt(world_state, memory_context)
    available    = world_state.world.available_goals or world_state.available_goals

    if CONFIG["primary_provider"] == "openrouter":
        primary_name  = f"OpenRouter ({CONFIG['openrouter_model']})"
        primary_call  = _openrouter_complete
    else:
        primary_name  = f"Ollama ({CONFIG['ollama_model']})"
        primary_call  = _ollama_complete

    try:
        logger.debug("Trying %s…", primary_name)
        raw = await primary_call(system, user)
        logger.info("%s responded", primary_name)
    except Exception as ollama_exc:
        logger.warning("%s unavailable (%s) — falling back to Anthropic", primary_name, ollama_exc)
        try:
            raw = await _anthropic_complete(system, user)
            logger.info("Anthropic fallback responded")
        except Exception as api_exc:
            logger.error("Anthropic fallback also failed: %s", api_exc)
            return _FALLBACK_PLAN

    return parse_plan(raw, available_goals=available)

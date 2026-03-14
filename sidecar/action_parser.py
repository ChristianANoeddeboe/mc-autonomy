from __future__ import annotations

import json
import logging
import re

from models import GoalDecision, GoalType

logger = logging.getLogger(__name__)

# Fallback returned when parsing fails
_FALLBACK = GoalDecision(goal=GoalType.IDLE, reason="Failed to parse LLM response — defaulting to IDLE")


def parse_and_validate(raw: str) -> GoalDecision:
    """Extract and validate a GoalDecision from raw LLM text.

    The LLM should return pure JSON, but may wrap it in markdown code fences.
    Falls back to IDLE on any parse/validation error.
    """
    text = raw.strip()

    # Strip ```json ... ``` or ``` ... ``` fences if present
    fence_match = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    if fence_match:
        text = fence_match.group(1).strip()

    # If there's surrounding prose, extract the first {...} block
    brace_match = re.search(r"\{[\s\S]*\}", text)
    if brace_match:
        text = brace_match.group(0)

    try:
        data = json.loads(text)
        decision = GoalDecision.model_validate(data)
        logger.debug("Parsed GoalDecision: %s", decision)
        return decision
    except (json.JSONDecodeError, ValueError) as exc:
        logger.warning("GoalDecision parse failed (%s) — raw: %.200s", exc, raw)
        return _FALLBACK

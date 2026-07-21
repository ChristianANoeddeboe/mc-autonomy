from __future__ import annotations

import json
import logging
import re
import uuid

from models import GoalType, PlanResponse, PlanStep

logger = logging.getLogger(__name__)

_FALLBACK_PLAN = PlanResponse(
    plan_id="fallback",
    name="idle",
    reason="Failed to parse LLM response — defaulting to idle",
    steps=[PlanStep(goal=GoalType.IDLE, note="Waiting for valid plan")],
)


def _extract_json(raw: str) -> str:
    """Strip markdown fences and extract the first JSON object."""
    text = raw.strip()
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    if fence:
        text = fence.group(1).strip()
    brace = re.search(r"\{[\s\S]*\}", text)
    if brace:
        text = brace.group(0)
    return text


def parse_plan(raw: str, available_goals: list[str] | None = None) -> PlanResponse:
    """Parse a PlanResponse from raw LLM output. Falls back to idle on any error."""
    text = _extract_json(raw)
    try:
        data = json.loads(text)

        if "steps" not in data:
            raise ValueError("Missing 'steps' key")

        plan_id = data.get("plan_id") or f"plan_{uuid.uuid4().hex[:8]}"
        name    = data.get("name", "unnamed")
        reason  = data.get("reason", "")

        allowed = set(available_goals) if available_goals else {g.value for g in GoalType}
        steps: list[PlanStep] = []

        for raw_step in data["steps"]:
            goal_str = str(raw_step.get("goal", "IDLE")).upper()
            if goal_str not in allowed:
                logger.warning("Step goal %s not in available goals — skipping", goal_str)
                continue
            steps.append(PlanStep(
                goal   = goal_str,
                params = raw_step.get("params", {}),
                note   = raw_step.get("note", ""),
            ))

        if not steps:
            logger.warning("Plan had no valid steps after filtering")
            return _FALLBACK_PLAN

        plan = PlanResponse(plan_id=plan_id, name=name, reason=reason, steps=steps)
        logger.debug("Parsed plan '%s' with %d steps", name, len(steps))
        return plan

    except (json.JSONDecodeError, ValueError, KeyError) as exc:
        logger.warning("Plan parse failed (%s) — raw: %.300s", exc, raw)
        return _FALLBACK_PLAN

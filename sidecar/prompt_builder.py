from __future__ import annotations

import json
import logging
from pathlib import Path

from config import CONFIG
from models import WorldState

logger = logging.getLogger(__name__)

_PERSONALITY_FILE = Path(__file__).parent / CONFIG.get("personality_file", "personality.json")

_GOAL_LIST = (
    "GATHER_WOOD, FIND_FOOD, BUILD_SHELTER, MINE_RESOURCES, "
    "EXPLORE, FOLLOW_PLAYER, CRAFT_ITEM, SLEEP, IDLE"
)

_GOAL_DECISION_SCHEMA = """\
{
  "goal": "<one of the goals listed above>",
  "params": {},
  "reason": "<one sentence explaining why>",
  "memory_note": "<optional string to remember long-term, or null>"
}"""


def _load_personality() -> dict:
    if _PERSONALITY_FILE.exists():
        try:
            return json.loads(_PERSONALITY_FILE.read_text())
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("Could not load personality file: %s", exc)
    return {
        "name": "Companion",
        "description": "A Minecraft companion entity.",
        "style": "practical",
        "priorities": [
            "Survival first — health and hunger before anything else",
            "Follow player instructions when recently given",
        ],
    }


def _build_system_prompt(personality: dict) -> str:
    name        = personality.get("name", "Companion")
    description = personality.get("description", "")
    style       = personality.get("style", "practical")
    priorities  = "\n".join(f"- {p}" for p in personality.get("priorities", []))

    return f"""\
You are {name}, a Minecraft companion entity. {description}
Your communication style is {style}.

Core priorities:
{priorities}

Your job is to choose the single best high-level goal to pursue next, given the
current world state and your memory context.

Available goals: {_GOAL_LIST}

Rules:
- Return ONLY valid JSON matching the GoalDecision schema — no extra text or prose
- Choose exactly one goal from the list above
- If the player recently gave an instruction, respect it unless survival demands otherwise
- Prefer goals that address the most urgent need (low health > low hunger > night > resources)

GoalDecision schema:
{_GOAL_DECISION_SCHEMA}
"""


_USER_TEMPLATE = """\
World state:
{world_state_json}

Memory context:
{memory_context}

Decide the next goal.
"""


def build_prompt(world_state: WorldState, memory_context: str) -> tuple[str, str]:
    """Return (system_prompt, user_message) ready to send to an LLM."""
    personality = _load_personality()
    system = _build_system_prompt(personality)
    user = _USER_TEMPLATE.format(
        world_state_json=json.dumps(world_state.model_dump(), indent=2),
        memory_context=memory_context,
    )
    return system, user

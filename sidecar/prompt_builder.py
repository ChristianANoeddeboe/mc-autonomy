from __future__ import annotations

import json

from models import WorldState

SYSTEM_PROMPT = """\
You are a Minecraft companion entity. Your job is to decide what high-level
goal to pursue next based on the current world state.

Rules:
- Prioritize survival (health, hunger, shelter at night)
- Respect the player's last instruction if recent
- Return ONLY valid JSON matching the GoalDecision schema — no extra text
- Choose one goal from: GATHER_WOOD, FIND_FOOD, BUILD_SHELTER,
  MINE_RESOURCES, EXPLORE, FOLLOW_PLAYER, CRAFT_ITEM, SLEEP, IDLE

GoalDecision schema:
{
  "goal": "<GOAL_TYPE>",
  "params": {},
  "reason": "<one sentence explaining why>",
  "memory_note": "<optional note to remember, or null>"
}
"""

USER_TEMPLATE = """\
World state:
{world_state_json}

Memory context:
{memory_context}

Decide the next goal.
"""


def build_prompt(world_state: WorldState, memory_context: str) -> tuple[str, str]:
    """Return (system_prompt, user_message) ready to send to an LLM."""
    world_json = json.dumps(world_state.model_dump(), indent=2)
    user_msg = USER_TEMPLATE.format(
        world_state_json=world_json,
        memory_context=memory_context,
    )
    return SYSTEM_PROMPT, user_msg

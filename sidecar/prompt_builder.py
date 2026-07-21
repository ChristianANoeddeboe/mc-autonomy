from __future__ import annotations

import json
import logging
from pathlib import Path

from config import CONFIG
from models import GoalType, WorldState

logger = logging.getLogger(__name__)

_PERSONALITY_FILE = Path(__file__).parent / CONFIG.get("personality_file", "personality.json")

_PLAN_SCHEMA = """\
{
  "plan_id": "<short unique id, e.g. gather_wood_001>",
  "name":    "<snake_case label, e.g. establish_camp>",
  "reason":  "<one sentence explaining why this plan now>",
  "steps": [
    {
      "goal":   "<one of the available goals>",
      "params": { "<key>": <value> },
      "note":   "<one sentence — what this step achieves>"
    }
  ],
  "knowledge_updates": {
    "<key>": "<value to remember — locations, flags, notes>"
  }
}"""

_GOAL_DESCRIPTIONS = {
    "GATHER_ITEM":    'Collect surface blocks. params: {"type": "<block>", "count": N}. '
                      'type: "log", "sand", "gravel", "cobblestone", "dirt", "oak_log", etc. Default count 8.',
    "BUILD_SHELTER":  "Build an enclosed shelter from inventory blocks (needs 26+).",
    "MINE_RESOURCES": 'Mine underground ore. params: {"target": "<ore>", "count": N}. '
                      'ore: "iron", "coal", "gold", "diamond", "copper". '
                      'REQUIRES a pickaxe — add CRAFT_ITEM(wooden_pickaxe) earlier in the plan if none in inventory.',
    "EXPLORE":        'Wander to discover new terrain. params: {"distance": N}.',
    "FOLLOW_PLAYER":  "Follow the nearest player.",
    "CRAFT_ITEM":     'Craft an item. params: {"item": "<name>"}. '
                      'e.g. "wooden_pickaxe", "stone_pickaxe", "crafting_table", "torch", "chest".',
    "SLEEP":          "Sleep through the night in a bed.",
    "NAVIGATE_TO":    'Walk to coordinates. params: {"x": N, "z": N} (y optional).',
    "FIND_FOOD":      "Hunt or forage for food.",
    "IDLE":           "Stand still.",
}


def _load_personality() -> dict:
    if _PERSONALITY_FILE.exists():
        try:
            return json.loads(_PERSONALITY_FILE.read_text())
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("Could not load personality file: %s", exc)
    return {
        "name":        "Companion",
        "description": "A Minecraft companion entity.",
        "style":       "practical",
        "priorities": [
            "Survival first — health and shelter before anything else",
            "Follow player instructions when recently given",
            "Progress toward the long-term objective when survival is secured",
        ],
    }


def _build_system_prompt(personality: dict, available_goals: list[str]) -> str:
    name        = personality.get("name", "Companion")
    description = personality.get("description", "")
    style       = personality.get("style", "practical")
    priorities  = "\n".join(f"- {p}" for p in personality.get("priorities", []))
    goal_lines  = "\n".join(
        f"  {g}: {_GOAL_DESCRIPTIONS.get(g, '')}"
        for g in available_goals
    )

    return f"""\
You are {name}, a Minecraft companion entity. {description}
Style: {style}.

Priorities:
{priorities}

Your task: produce a short, ordered PLAN — a sequence of goals that makes \
meaningful progress toward the current objective.

Rules:
- Return ONLY valid JSON matching the Plan schema — no extra text
- Only use goals from the available list
- Keep plans SHORT (2-8 steps) and focused on ONE clear deliverable
- Do NOT include steps for things already done (check inventory + memory first)
- Use NAVIGATE_TO to travel to a known location (coords from memory)
- Use knowledge_updates to record anything worth remembering: locations \
built or found, important flags, observations like "dungeon at [x,z] — revisit later"
- If the player recently sent a message, make that the plan's focus

Crafting prerequisites — ALWAYS check inventory before adding craft/mine steps:
- Wooden planks : 1 log → 4 planks  (no table needed)
- Sticks        : 2 planks → 4 sticks  (no table needed)
- Crafting table: 4 planks  (no table needed)
- Wooden pickaxe: 3 planks + 2 sticks  (needs crafting table)
- Stone pickaxe : 3 cobblestone + 2 sticks  (needs crafting table + pickaxe to get cobblestone)
- Furnace       : 8 cobblestone  (needs crafting table + pickaxe)
- To mine stone or ore you MUST have a pickaxe — if none in inventory, prepend:
  GATHER_ITEM(log,8) → CRAFT_ITEM(crafting_table) → CRAFT_ITEM(wooden_pickaxe) → MINE_RESOURCES
- Never plan MINE_RESOURCES as the first step unless a pickaxe is already in inventory

Available goals:
{goal_lines}

Plan schema (return exactly this structure):
{_PLAN_SCHEMA}
"""


_USER_TEMPLATE = """\
{objective_block}\
Current world state:
{world_state_json}

Memory context:
{memory_context}

{inventory_analysis}\
Produce the next plan.
"""

_OBJECTIVE_BLOCK = """\
═══════════════════════════════════════════
LONG-TERM OBJECTIVE: {type}
{display_name}

Guidance: {guidance}
Current milestone: {milestone}
Progress:
{progress_list}
{completion_note}═══════════════════════════════════════════

Your plan MUST advance the above objective once survival needs are met.

"""


def _build_objective_block(world_state: WorldState) -> str:
    obj = world_state.world.objective
    if obj.type == "NONE":
        return ""
    progress_list   = "\n".join(f"  {p}" for p in obj.progress) if obj.progress else "  (no data yet)"
    completion_note = "OBJECTIVE COMPLETE!\n" if obj.complete else ""
    return _OBJECTIVE_BLOCK.format(
        type=obj.type, display_name=obj.display_name, guidance=obj.guidance,
        milestone=obj.milestone, progress_list=progress_list, completion_note=completion_note,
    )


def _parse_inventory(inventory: list[str]) -> dict[str, int]:
    """Parse inventory strings like ['oak_log', 'stick x4'] into {name: count}."""
    items: dict[str, int] = {}
    for entry in inventory:
        if " x" in entry:
            name, _, n = entry.rpartition(" x")
            items[name] = items.get(name, 0) + int(n)
        else:
            items[entry] = items.get(entry, 0) + 1
    return items


def _build_inventory_analysis(world_state: WorldState) -> str:
    """
    Deterministically analyse the inventory and emit concrete, actionable gaps.

    The output is injected into the user message so the AI doesn't have to
    infer prerequisites from abstract rules — it sees exactly what is missing
    and what the shortest path to fix it is.
    """
    inv = _parse_inventory(world_state.world.companion.inventory)

    log_count    = sum(v for k, v in inv.items() if "log" in k)
    plank_count  = sum(v for k, v in inv.items() if "plank" in k)
    stick_count  = inv.get("stick", 0)
    has_table    = any("crafting_table" in k for k in inv)
    has_pickaxe  = any("pickaxe" in k for k in inv)
    has_axe      = any("axe" in k and "pickaxe" not in k for k in inv)
    has_shovel   = any("shovel" in k for k in inv)
    has_sword    = any("sword" in k for k in inv)
    cobble_count = sum(v for k, v in inv.items() if "cobblestone" in k)

    # planks equivalent available (logs convert 1→4)
    planks_available = plank_count + log_count * 4

    lines: list[str] = []

    # ── Pickaxe chain ─────────────────────────────────────────────────────
    if not has_pickaxe:
        if planks_available >= 11 and has_table:  # 4 planks table + 3 planks + 2 sticks(=4planks) for pick
            lines.append(
                "⚠ NO PICKAXE — but you have enough wood + a crafting table.\n"
                "  Required steps before any mining: CRAFT_ITEM(wooden_pickaxe)"
            )
        elif planks_available >= 11:
            lines.append(
                "⚠ NO PICKAXE — you have enough wood but no crafting table.\n"
                "  Required steps before any mining: CRAFT_ITEM(crafting_table) → CRAFT_ITEM(wooden_pickaxe)"
            )
        elif log_count >= 2:
            lines.append(
                "⚠ NO PICKAXE — gather more wood first.\n"
                "  Required steps before any mining: GATHER_ITEM(log,8) → CRAFT_ITEM(crafting_table) → CRAFT_ITEM(wooden_pickaxe)"
            )
        else:
            lines.append(
                "⚠ NO PICKAXE and very little wood in inventory.\n"
                "  Required steps before any mining: GATHER_ITEM(log,8) → CRAFT_ITEM(crafting_table) → CRAFT_ITEM(wooden_pickaxe)"
            )
    elif has_pickaxe and cobble_count >= 11 and has_table:
        # Already has a pickaxe but could upgrade
        stone_pick = any("stone_pickaxe" in k for k in inv)
        iron_pick  = any("iron_pickaxe" in k for k in inv)
        if not stone_pick and not iron_pick:
            lines.append(
                "ℹ Has wooden pickaxe + enough cobblestone — could upgrade: CRAFT_ITEM(stone_pickaxe)"
            )

    # ── Crafting table ────────────────────────────────────────────────────
    if not has_table:
        if planks_available >= 4:
            lines.append("ℹ No crafting table in inventory — can craft one now: CRAFT_ITEM(crafting_table)")
        else:
            lines.append(
                "ℹ No crafting table and not enough planks — "
                "need logs first: GATHER_ITEM(log,4) → CRAFT_ITEM(crafting_table)"
            )

    # ── Wood supply ───────────────────────────────────────────────────────
    if log_count == 0 and plank_count < 4:
        lines.append("ℹ No wood at all — GATHER_ITEM(log,8) is a good first step.")

    # ── Recent goal failure ───────────────────────────────────────────────
    failed_reason = world_state.world.goal_failed_reason
    if failed_reason:
        goal = world_state.world.current_goal
        if "found" in failed_reason.lower() or "no " in failed_reason.lower():
            lines.append(
                f"⚠ LAST STEP FAILED ({goal}): {failed_reason}\n"
                "  Do NOT plan the same goal as the first step.\n"
                "  Try EXPLORE first to find new terrain, then retry."
            )
        else:
            lines.append(f"⚠ LAST STEP FAILED ({goal}): {failed_reason}")

    if not lines:
        return ""

    return "Inventory analysis (act on these gaps when relevant to your plan):\n" \
           + "\n".join(lines) + "\n\n"


def build_plan_prompt(world_state: WorldState, memory_context: str) -> tuple[str, str]:
    """Return (system_prompt, user_message) for a /plan request."""
    personality = _load_personality()
    available   = (
        world_state.world.available_goals
        or world_state.available_goals
        or [g.value for g in GoalType]
    )

    system = _build_system_prompt(personality, available)

    state_dict = world_state.model_dump()
    companion  = state_dict.get("world", {}).get("companion", {})
    if isinstance(companion, dict) and companion.get("hunger") is None:
        companion.pop("hunger", None)

    user = _USER_TEMPLATE.format(
        objective_block    = _build_objective_block(world_state),
        world_state_json   = json.dumps(state_dict, indent=2),
        memory_context     = memory_context,
        inventory_analysis = _build_inventory_analysis(world_state),
    )
    return system, user

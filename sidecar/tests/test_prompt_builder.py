"""Tests for prompt_builder — no LLM calls, purely unit tests of prompt construction."""

from __future__ import annotations

import json

from models import CompanionState, ObjectiveInfo, PlanInfo, WorldInfo, WorldState
from prompt_builder import build_plan_prompt, _GOAL_DESCRIPTIONS


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_world_state(
    hunger: int | None = 15,
    health: float = 20.0,
    objective_type: str = "NONE",
    last_player_message: str | None = None,
    available_goals: list[str] | None = None,
) -> WorldState:
    companion = CompanionState(
        health=health,
        hunger=hunger,
        position=[0.0, 64.0, 0.0],
        inventory=[],
    )
    world = WorldInfo(
        time_of_day="day",
        weather="clear",
        biome="plains",
        light_level=15,
        companion=companion,
        last_player_message=last_player_message,
        objective=ObjectiveInfo(type=objective_type),
        available_goals=available_goals,
    )
    return WorldState(entity_id="test", tick=0, world=world)


def _user_state_dict(user_msg: str) -> dict:
    """Parse the world-state JSON block out of the user message."""
    start = user_msg.index("Current world state:\n") + len("Current world state:\n")
    end   = user_msg.index("\n\nMemory context:")
    return json.loads(user_msg[start:end])


# ---------------------------------------------------------------------------
# Hunger stripping
# ---------------------------------------------------------------------------

class TestHungerDisabled:
    def test_hunger_none_stripped(self):
        # hunger=None means "not tracked" — prompt_builder removes it to reduce noise
        ws = _make_world_state(hunger=None)
        _, user = build_plan_prompt(ws, "")
        state = _user_state_dict(user)
        assert "hunger" not in state["world"]["companion"]

    def test_normal_hunger_retained(self):
        ws = _make_world_state(hunger=10)
        _, user = build_plan_prompt(ws, "")
        state = _user_state_dict(user)
        assert state["world"]["companion"]["hunger"] == 10

    def test_full_hunger_retained(self):
        ws = _make_world_state(hunger=20)
        _, user = build_plan_prompt(ws, "")
        state = _user_state_dict(user)
        assert state["world"]["companion"]["hunger"] == 20


# ---------------------------------------------------------------------------
# Prompt structure
# ---------------------------------------------------------------------------

class TestPromptStructure:
    def test_returns_two_strings(self):
        ws = _make_world_state()
        result = build_plan_prompt(ws, "some memory")
        assert isinstance(result, tuple) and len(result) == 2
        system, user = result
        assert isinstance(system, str) and isinstance(user, str)

    def test_system_contains_goal_list(self):
        ws = _make_world_state()
        system, _ = build_plan_prompt(ws, "")
        assert "GATHER_WOOD" in system
        assert "FIND_FOOD" in system
        assert "IDLE" in system

    def test_system_contains_plan_schema(self):
        ws = _make_world_state()
        system, _ = build_plan_prompt(ws, "")
        assert "plan_id" in system
        assert "steps" in system
        assert "knowledge_updates" in system

    def test_user_contains_memory_context(self):
        ws = _make_world_state()
        _, user = build_plan_prompt(ws, "remember the cave")
        assert "remember the cave" in user

    def test_user_contains_world_state_json(self):
        ws = _make_world_state(health=13.5)
        _, user = build_plan_prompt(ws, "")
        state = _user_state_dict(user)
        assert state["world"]["companion"]["health"] == 13.5

    def test_user_entity_id_present(self):
        ws = _make_world_state()
        _, user = build_plan_prompt(ws, "")
        state = _user_state_dict(user)
        assert state["entity_id"] == "test"

    def test_system_mentions_navigate_to(self):
        ws = _make_world_state()
        system, _ = build_plan_prompt(ws, "")
        assert "NAVIGATE_TO" in system


# ---------------------------------------------------------------------------
# Objective block
# ---------------------------------------------------------------------------

class TestObjectiveBlock:
    def test_no_objective_omits_block(self):
        ws = _make_world_state(objective_type="NONE")
        _, user = build_plan_prompt(ws, "")
        assert "LONG-TERM OBJECTIVE" not in user

    def test_objective_present_includes_block(self):
        ws = _make_world_state()
        ws.world.objective = ObjectiveInfo(
            type="EXPLORE",
            display_name="Explore the world",
            guidance="Go far",
            milestone="Reach a new biome",
            progress=["Visited plains"],
        )
        _, user = build_plan_prompt(ws, "")
        assert "LONG-TERM OBJECTIVE" in user
        assert "Explore the world" in user
        assert "Visited plains" in user

    def test_completed_objective_shows_completion_note(self):
        ws = _make_world_state()
        ws.world.objective = ObjectiveInfo(
            type="BUILD",
            display_name="Build a house",
            guidance="Build",
            milestone="Done",
            complete=True,
        )
        _, user = build_plan_prompt(ws, "")
        assert "OBJECTIVE COMPLETE" in user


# ---------------------------------------------------------------------------
# available_goals constraint
# ---------------------------------------------------------------------------

class TestAvailableGoals:
    def test_none_uses_all_goals(self):
        ws = _make_world_state(available_goals=None)
        system, _ = build_plan_prompt(ws, "")
        for goal in ["GATHER_WOOD", "FIND_FOOD", "BUILD_SHELTER", "IDLE"]:
            assert goal in system

    def test_restricted_list_shown_in_system(self):
        ws = _make_world_state(available_goals=["GATHER_WOOD", "BUILD_SHELTER", "EXPLORE", "IDLE"])
        system, _ = build_plan_prompt(ws, "")
        assert "GATHER_WOOD" in system
        assert "BUILD_SHELTER" in system
        assert "FIND_FOOD" not in system

    def test_single_goal_list(self):
        ws = _make_world_state(available_goals=["IDLE"])
        system, _ = build_plan_prompt(ws, "")
        assert "IDLE" in system

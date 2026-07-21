from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel


class GoalType(str, Enum):
    GATHER_ITEM    = "GATHER_ITEM"
    GATHER_WOOD    = "GATHER_WOOD"    # deprecated alias — use GATHER_ITEM with type=log
    FIND_FOOD      = "FIND_FOOD"
    BUILD_SHELTER  = "BUILD_SHELTER"
    MINE_RESOURCES = "MINE_RESOURCES"
    EXPLORE        = "EXPLORE"
    FOLLOW_PLAYER  = "FOLLOW_PLAYER"
    CRAFT_ITEM     = "CRAFT_ITEM"
    SLEEP          = "SLEEP"
    NAVIGATE_TO    = "NAVIGATE_TO"
    IDLE           = "IDLE"


# ---------------------------------------------------------------------------
# Plan schema (returned by /plan)
# ---------------------------------------------------------------------------

class PlanStep(BaseModel):
    goal:   str
    params: dict[str, Any] = {}
    note:   str = ""


class PlanResponse(BaseModel):
    plan_id:           str
    name:              str
    reason:            str
    steps:             list[PlanStep]
    knowledge_updates: dict[str, Any] = {}


# ---------------------------------------------------------------------------
# Step-complete notification (received at /step_complete)
# ---------------------------------------------------------------------------

class StepCompleteRequest(BaseModel):
    plan_id:     str
    step_index:  int
    goal:        str
    note:        str = ""
    world_state: WorldState


# ---------------------------------------------------------------------------
# World-state schema (sent by the Fabric mod)
# ---------------------------------------------------------------------------

class BlockInfo(BaseModel):
    type:     str
    count:    int = 1
    relative: list[int]


class EntityInfo(BaseModel):
    type:     str
    distance: float
    hostile:  bool


class CompanionState(BaseModel):
    health:    float
    hunger:    int | None = None
    position:  list[float]
    inventory: list[str]


class ObjectiveInfo(BaseModel):
    type:         str       = "NONE"
    display_name: str       = "No objective"
    guidance:     str       = ""
    milestone:    str       = ""
    progress:     list[str] = []
    complete:     bool      = False


class PlanInfo(BaseModel):
    active:            bool = False
    plan_id:           str  = ""
    name:              str  = ""
    step:              int  = 0
    total_steps:       int  = 0
    current_step_goal: str  = ""
    current_step_note: str  = ""


class WorldInfo(BaseModel):
    time_of_day:         str
    weather:             str
    biome:               str
    light_level:         int
    nearby_blocks:       list[BlockInfo]  = []
    nearby_entities:     list[EntityInfo] = []
    companion:           CompanionState
    last_player_message: str | None       = None
    available_goals:     list[str] | None = None
    current_goal:        str              = "IDLE"
    current_plan:        PlanInfo         = PlanInfo()
    goal_failed_reason:  str | None       = None
    objective:           ObjectiveInfo    = ObjectiveInfo()


class WorldState(BaseModel):
    entity_id:       str
    tick:            int
    world:           WorldInfo
    available_goals: list[str] | None = None


# Resolve forward reference in StepCompleteRequest
StepCompleteRequest.model_rebuild()

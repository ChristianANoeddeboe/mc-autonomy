from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel


class GoalType(str, Enum):
    GATHER_WOOD = "GATHER_WOOD"
    FIND_FOOD = "FIND_FOOD"
    BUILD_SHELTER = "BUILD_SHELTER"
    MINE_RESOURCES = "MINE_RESOURCES"
    EXPLORE = "EXPLORE"
    FOLLOW_PLAYER = "FOLLOW_PLAYER"
    CRAFT_ITEM = "CRAFT_ITEM"
    SLEEP = "SLEEP"
    IDLE = "IDLE"


class GoalDecision(BaseModel):
    goal: GoalType
    params: dict[str, Any] = {}
    reason: str
    memory_note: str | None = None


# ---------------------------------------------------------------------------
# World-state schema (sent by the Fabric mod)
# ---------------------------------------------------------------------------

class BlockInfo(BaseModel):
    type: str
    relative: list[int]  # [x, y, z] relative to companion


class EntityInfo(BaseModel):
    type: str
    distance: float
    hostile: bool


class CompanionState(BaseModel):
    health: float
    hunger: int
    position: list[float]  # [x, y, z]
    inventory: list[str]


class ObjectiveInfo(BaseModel):
    type: str = "NONE"
    display_name: str = "No objective"
    guidance: str = ""
    milestone: str = ""
    progress: list[str] = []
    complete: bool = False


class WorldInfo(BaseModel):
    time_of_day: str          # dawn | day | dusk | night
    weather: str              # clear | rain | thunder
    biome: str
    light_level: int
    nearby_blocks: list[BlockInfo] = []
    nearby_entities: list[EntityInfo] = []
    companion: CompanionState
    last_player_message: str | None = None
    current_goal: str = "IDLE"
    goal_failed_reason: str | None = None
    objective: ObjectiveInfo = ObjectiveInfo()


class WorldState(BaseModel):
    entity_id: str
    tick: int
    world: WorldInfo

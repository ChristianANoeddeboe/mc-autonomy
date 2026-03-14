# Minecraft AI Companion Mod — Design Document

## Project Summary

A Fabric (Java Edition) mod that adds an autonomous companion entity to Minecraft.
The companion uses a **hierarchical AI architecture**: a Python sidecar powered by a local
Ollama model (with Anthropic API fallback) decides high-level goals, while hardcoded
reactive behaviors handle moment-to-moment survival (combat, fleeing, eating).

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Minecraft (JVM)                       │
│                                                          │
│   Goal Planner (slow loop, fires on events/completion)   │
│   └── HTTP POST world state → Python sidecar → GOAL     │
│                                                          │
│   Behavior Layer (runs every tick)                       │
│   ├── Reactivebehaviors (always-on, overrides goal)      │
│   │   ├── AttackIfHostileNearby                          │
│   │   ├── FleeIfHealthLow                               │
│   │   ├── EatIfHungry                                   │
│   │   └── DodgeCreeper                                  │
│   └── GoalExecutor (pursues current goal via state FSM)  │
└─────────────────────────────────────────────────────────┘
          │ HTTP (localhost JSON)  ▲
          ▼                       │
┌─────────────────────────────────────────────────────────┐
│                 Python AI Sidecar                        │
│                                                          │
│   FastAPI /decide endpoint                               │
│   ├── PromptBuilder   — world state → LLM prompt        │
│   ├── ModelRouter     — Ollama → Anthropic fallback      │
│   ├── MemoryStore     — rolling history + notes         │
│   └── ActionParser    — enforces structured JSON output  │
└─────────────────────────────────────────────────────────┘
```

---

## Repository Structure

```
minecraft-ai-companion/
├── mod/                        # Fabric mod (Java)
│   ├── build.gradle
│   ├── src/main/java/com/example/companion/
│   │   ├── CompanionMod.java
│   │   ├── entity/
│   │   │   ├── CompanionEntity.java
│   │   │   ├── WorldPerception.java
│   │   │   └── ActionExecutor.java
│   │   ├── goal/
│   │   │   ├── AIGoalPlanner.java       # Slow loop, calls sidecar
│   │   │   ├── GoalExecutor.java        # Drives current goal FSM
│   │   │   ├── goals/
│   │   │   │   ├── GatherWoodGoal.java
│   │   │   │   ├── FindFoodGoal.java
│   │   │   │   ├── BuildShelterGoal.java
│   │   │   │   ├── MineResourcesGoal.java
│   │   │   │   ├── ExploreGoal.java
│   │   │   │   ├── FollowPlayerGoal.java
│   │   │   │   ├── CraftItemGoal.java
│   │   │   │   └── SleepGoal.java
│   │   ├── behavior/
│   │   │   ├── ReactiveBehaviorController.java
│   │   │   ├── AttackBehavior.java
│   │   │   ├── FleeBehavior.java
│   │   │   ├── EatBehavior.java
│   │   │   └── DodgeBehavior.java
│   │   └── net/
│   │       └── SidecarClient.java       # Async HTTP client
│   └── src/main/resources/
│       ├── fabric.mod.json
│       └── companion.mixins.json
│
└── sidecar/                    # Python AI server
    ├── main.py                 # FastAPI app
    ├── model_router.py         # Ollama → Anthropic fallback
    ├── prompt_builder.py       # World state → prompt
    ├── memory.py               # Rolling context + persistent notes
    ├── action_parser.py        # Validate + parse LLM JSON output
    ├── models.py               # Pydantic schemas
    └── requirements.txt
```

---

## Mod Side (Java / Fabric)

### CompanionEntity

- Extends `PathAwareEntity`
- Registers `ReactiveBehaviorController` and `GoalExecutor` as its AI
- Holds current `GoalType` and goal state
- Exposes methods: `setGoal(GoalType, Map<String,Object> params)`, `reportGoalComplete()`, `reportGoalFailed(String reason)`

### WorldPerception

Scans a configurable radius (default 16 blocks) and serializes to JSON on demand.

```java
// Output structure:
{
  "entity_id": "uuid-string",
  "tick": 84200,
  "world": {
    "time_of_day": "dusk",       // dawn | day | dusk | night
    "weather": "clear",           // clear | rain | thunder
    "biome": "forest",
    "light_level": 8,
    "nearby_blocks": [
      { "type": "oak_log", "relative": [3, 0, 1] }
      // ... top N relevant blocks, grouped by type
    ],
    "nearby_entities": [
      { "type": "zombie", "distance": 8.2, "hostile": true }
    ],
    "companion": {
      "health": 14.0,
      "hunger": 18,
      "position": [120, 64, -30],
      "inventory": ["wooden_pickaxe", "dirt x12", "oak_log x5"]
    },
    "last_player_message": "get us some food",
    "current_goal": "GATHER_WOOD",
    "goal_failed_reason": null    // populated on failure
  }
}
```

### GoalExecutor

Each goal is a state machine implementing a common interface:

```java
interface CompanionGoal {
    void start(Map<String, Object> params);
    void tick();          // called every game tick
    boolean isComplete();
    boolean isFailed();
    String failureReason();
    void reset();
}
```

Goal tick should use vanilla `entity.getNavigation().startMovingTo(...)` for movement.
Mine/place/interact actions use server-side block manipulation APIs.

### AIGoalPlanner

Fires a sidecar request when:
1. Current goal reports **complete**
2. Current goal reports **failed**
3. Player sends a chat message to the companion (name-tagged)
4. Significant event: health drops >6 hearts in short time, night begins
5. Periodic fallback timer: every **60 seconds** if none of the above triggered

Requests are **non-blocking** — result is queued and applied on the next safe tick.

### ReactiveBehaviorController

Runs every tick, **before** the GoalExecutor. Can pause/resume the goal:

| Behavior | Trigger condition | Action |
|---|---|---|
| `AttackBehavior` | Hostile mob within 4 blocks | Swing sword, target nearest threat |
| `FleeBehavior` | Health ≤ 4 hearts | Sprint away, pause current goal |
| `EatBehavior` | Hunger ≤ 8, food in inventory | Consume food item |
| `DodgeBehavior` | Creeper within 6 blocks, lit fuse | Sprint perpendicular |

### SidecarClient

```java
// Non-blocking POST to http://localhost:8765/decide
// On response: deserialize JSON → call entity.setGoal(...)
// On timeout (>5s): log warning, keep current goal running
// On connection refused: log error, entity enters IDLE fallback
```

---

## Python Sidecar

### Stack
- **FastAPI** — HTTP server
- **httpx** — async HTTP client (for Ollama + Anthropic)
- **ollama** (Python client) — local model interface
- **anthropic** — fallback API client
- **pydantic** — schema validation

### `/decide` Endpoint

```python
POST /decide
Content-Type: application/json

# Input: WorldState (see above JSON schema)
# Output: GoalDecision
{
  "goal": "FIND_FOOD",
  "params": {},
  "reason": "Hunger is low and no food in inventory. Will seek animals or crops.",
  "memory_note": "Player prefers not to fight — fish or farm food when possible"
}
```

### ModelRouter

```python
async def decide(world_state: WorldState) -> GoalDecision:
    prompt = build_prompt(world_state, memory.get_context())
    try:
        raw = await ollama_complete(prompt, timeout=4.0)
    except (TimeoutError, OllamaUnavailable):
        raw = await anthropic_complete(prompt)   # fallback
    return parse_and_validate(raw)
```

**Local model:** Qwen2.5-Coder 72B (Q6_K) or similar via Ollama
**Fallback model:** `claude-haiku-4-5` (fast, cheap, reliable)

### PromptBuilder

System prompt defines the companion's persona and constraints:

```
You are a Minecraft companion entity. Your job is to decide what high-level
goal to pursue next based on the current world state.

Rules:
- Prioritize survival (health, hunger, shelter at night)
- Respect the player's last instruction if recent
- Return ONLY valid JSON matching the GoalDecision schema
- Choose one goal from: GATHER_WOOD, FIND_FOOD, BUILD_SHELTER,
  MINE_RESOURCES, EXPLORE, FOLLOW_PLAYER, CRAFT_ITEM, SLEEP, IDLE

World state: {world_state_json}
Memory context: {memory_context}
```

### MemoryStore

```python
class MemoryStore:
    recent_decisions: deque[DecisionRecord]  # last 20
    persistent_notes: list[str]              # AI-written notes, saved to disk
    player_preferences: dict                 # inferred from interactions
```

Persistent notes are saved to `memory.json` alongside the sidecar.
The AI can write a `memory_note` in its response to update this store.

### Models (Pydantic)

```python
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
    params: dict = {}
    reason: str
    memory_note: str | None = None
```

---

## Goal Definitions

| Goal | Complete when | Fails when |
|---|---|---|
| `GATHER_WOOD` | N logs in inventory | No trees in range after search |
| `FIND_FOOD` | Food item obtained OR hunger restored | No food source found after 60s |
| `BUILD_SHELTER` | Enclosed space with door constructed | Insufficient materials + night arrived |
| `MINE_RESOURCES` | Target resource obtained | Stuck, lost, or no ore found |
| `EXPLORE` | Moved X blocks from start, mapped area | Hazard encountered |
| `FOLLOW_PLAYER` | Player within 3 blocks (continuous) | Player too far, unreachable |
| `CRAFT_ITEM` | Item appears in inventory | Missing required materials |
| `SLEEP` | Morning arrives | Bed missing, unsafe |
| `IDLE` | Next planner tick | Never |

---

## Development Phases

### Phase 1 — Skeleton
- [ ] Scaffold Fabric mod from example template
- [ ] Scaffold Python sidecar with FastAPI stub returning hardcoded `IDLE` goal
- [ ] Spawn companion entity in-game (no real AI)
- [ ] Verify HTTP round-trip: mod POSTs state, sidecar responds, entity logs received goal

### Phase 2 — Perception & Actions
- [ ] Implement `WorldPerception` JSON serialization
- [ ] Implement `FOLLOW_PLAYER` and `EXPLORE` goals (movement only)
- [ ] Implement `ReactiveBehaviorController` with `AttackBehavior` and `FleeBehavior`
- [ ] Verify goal complete/fail callbacks trigger new planner request

### Phase 3 — AI Integration
- [ ] Implement `PromptBuilder` with system prompt
- [ ] Implement `ModelRouter` with Ollama local + Anthropic fallback
- [ ] Implement `MemoryStore` with rolling context and disk persistence
- [ ] Wire full loop: world state → AI goal → executor → completion → repeat

### Phase 4 — Full Goal Suite
- [ ] Implement all remaining goals: `GATHER_WOOD`, `FIND_FOOD`, `BUILD_SHELTER`, `CRAFT_ITEM`, `SLEEP`, `MINE_RESOURCES`
- [ ] Player chat integration (name-tag addressing triggers immediate replan)
- [ ] Companion narrates current goal/thought in chat (optional, toggleable)

### Phase 5 — Polish
- [ ] Goal failure recovery (retry with different strategy)
- [ ] Configurable personality via sidecar system prompt
- [ ] Config file: sidecar port, model name, decision interval, scan radius
- [ ] Persistent memory survives server restarts

---

## Key Decisions

| Decision | Choice | Reason |
|---|---|---|
| AI call frequency | Event-driven + 60s fallback | Avoids latency bottleneck for fast actions |
| Combat / eating | Hardcoded reactive behaviors | Real-time, no AI latency needed |
| AI output | Structured JSON (GoalDecision) | Reliable parsing, no hallucinated actions |
| Transport | HTTP localhost | Simple JVM↔Python bridge, easy to debug |
| Pathfinding | Vanilla navigator | No external dependency; sufficient for goal-level nav |
| Memory | In-memory + JSON file | Simple start; upgrade to SQLite if needed |
| Fallback behavior | IDLE + log warning | Safe default if sidecar unreachable |

---

## Running Locally

```bash
# Start the sidecar
cd sidecar
pip install -r requirements.txt
uvicorn main:app --port 8765

# Build and run the mod
cd mod
./gradlew runClient
```

Ensure Ollama is running locally with the chosen model pulled:
```bash
ollama pull qwen2.5-coder:72b
ollama serve
```

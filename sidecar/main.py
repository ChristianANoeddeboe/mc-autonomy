"""Python AI sidecar — FastAPI server for the Minecraft companion mod.

Endpoints
---------
POST /plan          — receive WorldState, return PlanResponse (ordered goal steps)
POST /step_complete — notify sidecar that a plan step finished; update knowledge
GET  /health        — liveness check; reports stub_mode status
GET  /memory        — inspect persistent memory state
DELETE /memory      — clear all persistent memory

Environment variables
---------------------
STUB_MODE=true        Skip the LLM and always return a single EXPLORE step (default: true).
                      Set to "false" for real AI decisions.
OLLAMA_BASE_URL       Ollama server URL (default: http://localhost:11434)
OLLAMA_MODEL          Model name (default: qwen2.5-coder:7b)
OLLAMA_TIMEOUT        Seconds before Ollama times out and Anthropic fallback fires (default: 4)
ANTHROPIC_MODEL       Fallback model (default: claude-haiku-4-5-20251001)
ANTHROPIC_API_KEY     Required when STUB_MODE=false and Ollama is unavailable.

Run with:
    uvicorn main:app --port 8765 --reload
    STUB_MODE=false uvicorn main:app --port 8765 --reload   # real AI
"""

from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from config import CONFIG
from memory import MemoryStore
from model_router import plan as ai_plan
from models import GoalType, PlanResponse, PlanStep, StepCompleteRequest, WorldState

logging.basicConfig(
    level=getattr(logging, CONFIG.get("log_level", "INFO").upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

STUB_MODE: bool = CONFIG["stub_mode"]

app = FastAPI(title="MC Companion Sidecar", version="0.2.0")
memory = MemoryStore()


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "stub_mode": STUB_MODE}


@app.get("/memory")
async def get_memory() -> dict:
    """Inspect the companion's current memory state."""
    return {
        "knowledge": memory.knowledge,
        "persistent_notes": memory.persistent_notes,
        "player_preferences": memory.player_preferences,
        "recent_plans": [
            {
                "tick": r.tick,
                "entity_id": r.entity_id,
                "plan_id": r.plan_id,
                "name": r.name,
                "reason": r.reason,
            }
            for r in memory.recent_plans
        ],
    }


@app.delete("/memory")
async def clear_memory() -> dict:
    """Clear all persistent memory."""
    memory.knowledge.clear()
    memory.persistent_notes.clear()
    memory.player_preferences.clear()
    memory._save()
    logger.info("Memory cleared via API")
    return {"cleared": True}


@app.post("/plan", response_model=PlanResponse)
async def plan(world_state: WorldState) -> PlanResponse:
    """Receive world state from the Fabric mod and return an ordered PlanResponse.

    In stub mode (STUB_MODE=true) returns a single EXPLORE step so the
    HTTP round-trip can be tested without a running LLM.
    """
    logger.info(
        "Received /plan for entity=%s tick=%d current_goal=%s",
        world_state.entity_id,
        world_state.tick,
        world_state.world.current_goal,
    )

    if STUB_MODE:
        stub = PlanResponse(
            plan_id="stub_001",
            name="stub_explore",
            reason="[STUB] STUB_MODE=true — set STUB_MODE=false to enable real AI decisions.",
            steps=[PlanStep(goal=GoalType.EXPLORE, params={"distance": 32}, note="Explore nearby terrain")],
        )
        logger.info("STUB response: %s", stub.name)
        return stub

    response = await ai_plan(world_state, memory.get_context())

    # Apply any knowledge updates the AI provided
    if response.knowledge_updates:
        for key, value in response.knowledge_updates.items():
            memory.set_knowledge(key, value)
        logger.info("Knowledge updated from plan: %s", response.knowledge_updates)

    memory.record_plan(
        tick=world_state.tick,
        entity_id=world_state.entity_id,
        plan_id=response.plan_id,
        name=response.name,
        reason=response.reason,
    )

    logger.info(
        "AI plan: id=%s name=%s steps=%d reason=%s",
        response.plan_id, response.name, len(response.steps), response.reason,
    )
    return response


@app.post("/step_complete")
async def step_complete(req: StepCompleteRequest) -> dict:
    """Notify sidecar that a plan step completed; update knowledge and observations."""
    logger.info(
        "Step complete: plan=%s step=%d goal=%s",
        req.plan_id, req.step_index, req.goal,
    )

    memory.record_step_complete(
        plan_id=req.plan_id,
        step_index=req.step_index,
        goal=req.goal,
        note=req.note,
        knowledge_updates=None,  # knowledge updates come from the plan response, not step complete
    )

    return {"recorded": True}


@app.exception_handler(Exception)
async def _generic_error(request, exc: Exception) -> JSONResponse:
    logger.error("Unhandled error on %s: %s", request.url, exc, exc_info=True)
    fallback = PlanResponse(
        plan_id="error_fallback",
        name="idle",
        reason=f"Sidecar error: {exc}",
        steps=[PlanStep(goal=GoalType.IDLE, note="Waiting for sidecar recovery")],
    )
    return JSONResponse(status_code=500, content=fallback.model_dump())

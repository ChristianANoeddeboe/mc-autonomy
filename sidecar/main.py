"""Python AI sidecar — FastAPI server for the Minecraft companion mod.

Endpoints
---------
POST /decide      — receive WorldState, return GoalDecision (AI or stub)
GET  /health      — liveness check; reports stub_mode status
GET  /memory      — inspect persistent notes and recent decisions
DELETE /memory    — clear all persistent notes

Environment variables
---------------------
STUB_MODE=true        Skip the LLM and always return IDLE (default: true).
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
from model_router import decide as ai_decide
from models import GoalDecision, GoalType, WorldState

logging.basicConfig(
    level=getattr(logging, CONFIG.get("log_level", "INFO").upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

STUB_MODE: bool = CONFIG["stub_mode"]

app = FastAPI(title="MC Companion Sidecar", version="0.1.0")
memory = MemoryStore()


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "stub_mode": STUB_MODE}


@app.get("/memory")
async def get_memory() -> dict:
    """Inspect the companion's current memory state."""
    return {
        "persistent_notes": memory.persistent_notes,
        "player_preferences": memory.player_preferences,
        "recent_decisions": [
            {
                "tick": r.tick,
                "entity_id": r.entity_id,
                "goal": r.goal,
                "reason": r.reason,
            }
            for r in memory.recent_decisions
        ],
    }


@app.delete("/memory")
async def clear_memory() -> dict:
    """Clear all persistent notes (recent decisions remain in RAM)."""
    memory.persistent_notes.clear()
    memory.player_preferences.clear()
    memory._save()
    logger.info("Memory cleared via API")
    return {"cleared": True}


@app.post("/decide", response_model=GoalDecision)
async def decide(world_state: WorldState) -> GoalDecision:
    """Receive world state from the Fabric mod and return a GoalDecision.

    In stub mode (STUB_MODE=true) this immediately returns IDLE so the
    HTTP round-trip can be tested without a running LLM.
    """
    logger.info(
        "Received /decide for entity=%s tick=%d current_goal=%s",
        world_state.entity_id,
        world_state.tick,
        world_state.world.current_goal,
    )

    if STUB_MODE:
        decision = GoalDecision(
            goal=GoalType.IDLE,
            reason="[STUB] STUB_MODE=true — set STUB_MODE=false to enable real AI decisions.",
        )
        logger.info("STUB response: %s", decision)
        return decision

    # --- Real AI path (Phase 3+) ---
    decision = await ai_decide(world_state, memory.get_context())

    # Persist any memory note the AI wrote
    if decision.memory_note:
        memory.add_note(decision.memory_note)

    memory.record_decision(
        tick=world_state.tick,
        entity_id=world_state.entity_id,
        goal=decision.goal,
        reason=decision.reason,
    )

    logger.info("AI decision: goal=%s reason=%s", decision.goal, decision.reason)
    return decision


@app.exception_handler(Exception)
async def _generic_error(request, exc: Exception) -> JSONResponse:
    logger.error("Unhandled error on %s: %s", request.url, exc, exc_info=True)
    fallback = GoalDecision(goal=GoalType.IDLE, reason=f"Sidecar error: {exc}")
    return JSONResponse(status_code=500, content=fallback.model_dump())

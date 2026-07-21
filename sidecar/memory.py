from __future__ import annotations

import json
import logging
from collections import deque
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

MEMORY_FILE = Path(__file__).parent / "memory.json"
MAX_RECENT  = 20


@dataclass
class PlanRecord:
    tick:      int
    entity_id: str
    plan_id:   str
    name:      str
    reason:    str


class MemoryStore:
    """Persistent memory for the companion AI.

    Layout (memory.json):
    {
      "knowledge":          { ... },   <- AI-owned key/value store
      "persistent_notes":   [...],     <- free-text observations
      "player_preferences": {...},
      "recent_plans":       [...]      <- last N plan decisions
    }

    The AI can freely read/write `knowledge` to remember locations, flags,
    and anything else a real player would note down — e.g.
      "base_location": [-5, 68, -80]
      "has_shelter":   true
      "dungeon_at":    [120, 40, -300]  (come back when better equipped)
    """

    def __init__(self) -> None:
        self.knowledge:          dict[str, Any]    = {}
        self.persistent_notes:   list[str]         = []
        self.player_preferences: dict[str, Any]    = {}
        self.recent_plans:       deque[PlanRecord] = deque(maxlen=MAX_RECENT)
        self._load()

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def record_plan(self, tick: int, entity_id: str, plan_id: str,
                    name: str, reason: str) -> None:
        self.recent_plans.append(PlanRecord(tick, entity_id, plan_id, name, reason))
        self._save()

    def record_step_complete(self, plan_id: str, step_index: int,
                              goal: str, note: str,
                              knowledge_updates: dict[str, Any] | None = None) -> None:
        """Called when a step completes; applies optional AI knowledge updates."""
        if knowledge_updates:
            self.knowledge.update(knowledge_updates)
            logger.info("Knowledge updated from step complete: %s", knowledge_updates)
        if note:
            entry = f"[{plan_id} step {step_index + 1}] Completed {goal}: {note}"
            if entry not in self.persistent_notes:
                self.persistent_notes.append(entry)
        self._save()

    def add_note(self, note: str) -> None:
        if note and note not in self.persistent_notes:
            self.persistent_notes.append(note)
            self._save()
            logger.info("Memory note added: %s", note)

    def set_knowledge(self, key: str, value: Any) -> None:
        self.knowledge[key] = value
        self._save()

    def get_context(self) -> str:
        """Return a text summary for LLM prompt injection."""
        parts: list[str] = []

        if self.knowledge:
            lines = "\n".join(f"  {k}: {v}" for k, v in self.knowledge.items())
            parts.append(f"Knowledge (what I know about the world):\n{lines}")

        if self.persistent_notes:
            notes = "\n".join(f"- {n}" for n in self.persistent_notes[-10:])
            parts.append(f"Recent observations:\n{notes}")

        if self.recent_plans:
            recent  = list(self.recent_plans)[-5:]
            summary = ", ".join(f"{r.name}(t={r.tick})" for r in recent)
            parts.append(f"Recent plans executed: {summary}")

        if self.player_preferences:
            prefs = "; ".join(f"{k}={v}" for k, v in self.player_preferences.items())
            parts.append(f"Player preferences: {prefs}")

        return "\n\n".join(parts) if parts else "(no memory yet)"

    # ------------------------------------------------------------------
    # Persistence
    # ------------------------------------------------------------------

    def _save(self) -> None:
        data = {
            "knowledge":          self.knowledge,
            "persistent_notes":   self.persistent_notes,
            "player_preferences": self.player_preferences,
            "recent_plans":       [asdict(r) for r in self.recent_plans],
        }
        try:
            MEMORY_FILE.write_text(json.dumps(data, indent=2))
        except OSError as exc:
            logger.warning("Could not save memory: %s", exc)

    def _load(self) -> None:
        if not MEMORY_FILE.exists():
            return
        try:
            data = json.loads(MEMORY_FILE.read_text())
            self.knowledge          = data.get("knowledge", {})
            self.persistent_notes   = data.get("persistent_notes", [])
            self.player_preferences = data.get("player_preferences", {})
            for rd in data.get("recent_plans", [])[-MAX_RECENT:]:
                self.recent_plans.append(PlanRecord(**rd))
            logger.info(
                "Loaded memory: %d knowledge entries, %d notes, %d recent plans",
                len(self.knowledge), len(self.persistent_notes), len(self.recent_plans),
            )
        except (OSError, json.JSONDecodeError, TypeError) as exc:
            logger.warning("Could not load memory: %s", exc)

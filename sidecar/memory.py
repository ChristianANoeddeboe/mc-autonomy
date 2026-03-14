from __future__ import annotations

import json
import logging
from collections import deque
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

MEMORY_FILE = Path(__file__).parent / "memory.json"
MAX_RECENT = 20


@dataclass
class DecisionRecord:
    tick: int
    entity_id: str
    goal: str
    reason: str


class MemoryStore:
    """Rolling decision history + persistent notes written by the AI.

    Persistence layout (memory.json):
    {
      "persistent_notes": [...],
      "player_preferences": {...},
      "recent_decisions": [{"tick":..., "entity_id":..., "goal":..., "reason":...}, ...]
    }

    All three sections survive server restarts.
    """

    def __init__(self) -> None:
        self.recent_decisions: deque[DecisionRecord] = deque(maxlen=MAX_RECENT)
        self.persistent_notes: list[str] = []
        self.player_preferences: dict[str, Any] = {}
        self._load()

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def record_decision(self, tick: int, entity_id: str, goal: str, reason: str) -> None:
        self.recent_decisions.append(
            DecisionRecord(tick=tick, entity_id=entity_id, goal=goal, reason=reason)
        )
        self._save()  # persist after every decision

    def add_note(self, note: str) -> None:
        if note and note not in self.persistent_notes:
            self.persistent_notes.append(note)
            self._save()
            logger.info("Memory note added: %s", note)

    def get_context(self) -> str:
        """Return a concise text summary for injection into the LLM prompt."""
        parts: list[str] = []

        if self.persistent_notes:
            parts.append("Persistent notes:\n" + "\n".join(f"- {n}" for n in self.persistent_notes))

        if self.recent_decisions:
            recent = list(self.recent_decisions)[-5:]
            summary = ", ".join(f"{r.goal}(t={r.tick})" for r in recent)
            parts.append(f"Recent goals: {summary}")

        if self.player_preferences:
            prefs = "; ".join(f"{k}={v}" for k, v in self.player_preferences.items())
            parts.append(f"Player preferences: {prefs}")

        return "\n".join(parts) if parts else "(no memory yet)"

    # ------------------------------------------------------------------
    # Persistence
    # ------------------------------------------------------------------

    def _save(self) -> None:
        data = {
            "persistent_notes": self.persistent_notes,
            "player_preferences": self.player_preferences,
            "recent_decisions": [asdict(r) for r in self.recent_decisions],
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
            self.persistent_notes    = data.get("persistent_notes", [])
            self.player_preferences  = data.get("player_preferences", {})
            raw_decisions            = data.get("recent_decisions", [])
            for rd in raw_decisions[-MAX_RECENT:]:
                self.recent_decisions.append(DecisionRecord(**rd))
            logger.info(
                "Loaded memory: %d notes, %d recent decisions",
                len(self.persistent_notes),
                len(self.recent_decisions),
            )
        except (OSError, json.JSONDecodeError, TypeError) as exc:
            logger.warning("Could not load memory: %s", exc)

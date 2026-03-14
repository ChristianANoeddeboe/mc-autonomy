"""Sidecar configuration — loaded from sidecar_config.json, overridden by env vars.

Priority (highest first):
  1. Environment variables
  2. sidecar_config.json values
  3. Built-in defaults

Copy sidecar_config.json.example to sidecar_config.json and edit to customise.
"""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path

logger = logging.getLogger(__name__)

_CONFIG_FILE = Path(__file__).parent / "sidecar_config.json"

_DEFAULTS: dict = {
    "stub_mode": True,
    "ollama_base_url": "http://localhost:11434",
    "ollama_model": "qwen2.5-coder:7b",
    "ollama_timeout": 4.0,
    "anthropic_model": "claude-haiku-4-5-20251001",
    "personality_file": "personality.json",
    "port": 8765,
    "log_level": "INFO",
}

# Mapping from config key → env var name
_ENV_MAP = {
    "stub_mode":       "STUB_MODE",
    "ollama_base_url": "OLLAMA_BASE_URL",
    "ollama_model":    "OLLAMA_MODEL",
    "ollama_timeout":  "OLLAMA_TIMEOUT",
    "anthropic_model": "ANTHROPIC_MODEL",
    "port":            "SIDECAR_PORT",
    "log_level":       "LOG_LEVEL",
}


def _load() -> dict:
    cfg = dict(_DEFAULTS)

    # Layer 1 — JSON file
    if _CONFIG_FILE.exists():
        try:
            cfg.update(json.loads(_CONFIG_FILE.read_text()))
            logger.debug("Loaded sidecar config from %s", _CONFIG_FILE)
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("Could not read %s: %s — using defaults", _CONFIG_FILE, exc)

    # Layer 2 — environment overrides
    for key, env in _ENV_MAP.items():
        val = os.getenv(env)
        if val is not None:
            cfg[key] = val

    # Type coercion
    if isinstance(cfg["stub_mode"], str):
        cfg["stub_mode"] = cfg["stub_mode"].lower() == "true"
    cfg["ollama_timeout"] = float(cfg["ollama_timeout"])
    cfg["port"] = int(cfg["port"])

    return cfg


CONFIG = _load()

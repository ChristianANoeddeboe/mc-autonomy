"""Sidecar configuration — loaded from sidecar_config.json, overridden by env vars.

Priority (highest first):
  1. Real environment variables
  2. sidecar/.env
  3. sidecar_config.json values
  4. Built-in defaults

Copy sidecar_config.json.example to sidecar_config.json and edit to customise.
Secrets (OPENROUTER_API_KEY, ANTHROPIC_API_KEY) belong in sidecar/.env — copy
.env.example to .env and fill it in. Both are gitignored.
"""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path

from dotenv import load_dotenv

logger = logging.getLogger(__name__)

_SIDECAR_DIR = Path(__file__).parent
_CONFIG_FILE = _SIDECAR_DIR / "sidecar_config.json"

# Load sidecar/.env into the environment. override=False keeps a real exported
# variable ahead of the file, so `OPENROUTER_API_KEY=… python main.py` still wins.
load_dotenv(_SIDECAR_DIR / ".env", override=False)

_DEFAULTS: dict = {
    "stub_mode": True,
    # Which provider serves the first planning attempt: "ollama" or "openrouter".
    # Anthropic is the fallback for both.
    "primary_provider": "ollama",
    "ollama_base_url": "http://localhost:11434",
    "ollama_model": "qwen3.5:latest",
    "ollama_timeout": 120.0,
    "openrouter_base_url": "https://openrouter.ai/api/v1",
    "openrouter_model": "nvidia/nemotron-3-ultra-550b-a55b:free",
    "openrouter_timeout": 120.0,
    "anthropic_model": "claude-haiku-4-5-20251001",
    "personality_file": "personality.json",
    "port": 8765,
    "log_level": "INFO",
}

# Mapping from config key → env var name.
# API keys are deliberately absent: OPENROUTER_API_KEY and ANTHROPIC_API_KEY are read
# straight from the environment at call time so they never land in sidecar_config.json.
_ENV_MAP = {
    "stub_mode":           "STUB_MODE",
    "primary_provider":    "PRIMARY_PROVIDER",
    "ollama_base_url":     "OLLAMA_BASE_URL",
    "ollama_model":        "OLLAMA_MODEL",
    "ollama_timeout":      "OLLAMA_TIMEOUT",
    "openrouter_base_url": "OPENROUTER_BASE_URL",
    "openrouter_model":    "OPENROUTER_MODEL",
    "openrouter_timeout":  "OPENROUTER_TIMEOUT",
    "anthropic_model":     "ANTHROPIC_MODEL",
    "port":                "SIDECAR_PORT",
    "log_level":           "LOG_LEVEL",
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
    cfg["openrouter_timeout"] = float(cfg["openrouter_timeout"])
    cfg["port"] = int(cfg["port"])
    cfg["primary_provider"] = str(cfg["primary_provider"]).lower()

    return cfg


CONFIG = _load()

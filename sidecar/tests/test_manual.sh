#!/usr/bin/env bash
# Manual integration test script for the MC Companion sidecar.
# Run the sidecar first:  uv run uvicorn main:app --port 8765
# Then run this script:   bash tests/test_manual.sh [stub|ai]

set -euo pipefail

BASE="http://localhost:8765"
MODE="${1:-stub}"  # "stub" or "ai"
PASS=0; FAIL=0

# ── colour helpers ────────────────────────────────────────────────────────────
green() { printf '\033[32m✓ %s\033[0m\n' "$*"; }
red()   { printf '\033[31m✗ %s\033[0m\n' "$*"; }
blue()  { printf '\033[34m» %s\033[0m\n' "$*"; }

check() {
    local label="$1" body="$2" jq_expr="$3" expected="$4"
    actual=$(echo "$body" | jq -r "$jq_expr" 2>/dev/null || echo "PARSE_ERROR")
    if [ "$actual" = "$expected" ]; then
        green "$label"
        ((PASS++)) || true
    else
        red "$label  (got: $actual, want: $expected)"
        ((FAIL++)) || true
    fi
}

contains() {
    local label="$1" body="$2" jq_expr="$3" substr="$4"
    actual=$(echo "$body" | jq -r "$jq_expr" 2>/dev/null || echo "PARSE_ERROR")
    if echo "$actual" | grep -q "$substr"; then
        green "$label"
        ((PASS++)) || true
    else
        red "$label  (got: $actual, want substring: $substr)"
        ((FAIL++)) || true
    fi
}

not_empty() {
    local label="$1" body="$2" jq_expr="$3"
    actual=$(echo "$body" | jq -r "$jq_expr" 2>/dev/null || echo "")
    if [ -n "$actual" ] && [ "$actual" != "null" ] && [ "$actual" != "[]" ]; then
        green "$label"
        ((PASS++)) || true
    else
        red "$label  (got empty/null: $actual)"
        ((FAIL++)) || true
    fi
}

# ── Minimal world-state payload ───────────────────────────────────────────────
WORLD_STATE=$(cat <<'JSON'
{
  "entity_id": "test-companion-001",
  "tick": 1000,
  "world": {
    "time_of_day": "day",
    "weather": "clear",
    "biome": "plains",
    "light_level": 15,
    "companion": {
      "health": 20.0,
      "position": [0.0, 64.0, 0.0],
      "inventory": ["oak_log", "oak_log", "stick"]
    },
    "nearby_blocks": [
      {"type": "oak_log", "count": 3, "relative": [2, 0, 1]}
    ],
    "nearby_entities": [],
    "current_goal": "IDLE",
    "current_plan": {
      "active": false, "plan_id": "", "name": "", "step": 0,
      "total_steps": 0, "current_step_goal": "", "current_step_note": ""
    },
    "objective": {
      "type": "NONE", "display_name": "No objective",
      "guidance": "", "milestone": "", "progress": [], "complete": false
    }
  }
}
JSON
)

# Variant with an active objective
WORLD_STATE_OBJECTIVE=$(echo "$WORLD_STATE" | jq '
  .world.objective = {
    "type": "SURVIVE",
    "display_name": "Survive the first night",
    "guidance": "Gather wood and build a shelter before nightfall",
    "milestone": "Have a shelter",
    "progress": [],
    "complete": false
  }
')

# Variant simulating a player message
WORLD_STATE_MSG=$(echo "$WORLD_STATE" | jq '
  .world.last_player_message = "gather some wood please"
')

# Variant with low health
WORLD_STATE_HURT=$(echo "$WORLD_STATE" | jq '
  .world.companion.health = 5.0 |
  .world.nearby_entities = [{"type":"zombie","distance":4.0,"hostile":true}]
')

# ── /health ───────────────────────────────────────────────────────────────────
blue "=== /health ==="
R=$(curl -sf "$BASE/health" || echo '{"error":"connection refused"}')
check "status is ok"         "$R" '.status'    "ok"
check "stub_mode matches"    "$R" '.stub_mode' "$([ "$MODE" = stub ] && echo true || echo false)"

# ── /plan — stub mode basics ──────────────────────────────────────────────────
blue "=== /plan (basic world state) ==="
R=$(curl -sf -X POST "$BASE/plan" \
    -H 'Content-Type: application/json' \
    -d "$WORLD_STATE" || echo '{}')
not_empty  "plan_id returned"         "$R" '.plan_id'
not_empty  "name returned"            "$R" '.name'
not_empty  "reason returned"          "$R" '.reason'
not_empty  "steps array non-empty"    "$R" '.steps'
not_empty  "first step has goal"      "$R" '.steps[0].goal'

if [ "$MODE" = stub ]; then
    check "stub plan_id"    "$R" '.plan_id'        "stub_001"
    check "stub goal"       "$R" '.steps[0].goal'  "EXPLORE"
fi

# ── /plan — with objective ────────────────────────────────────────────────────
blue "=== /plan (active objective: survive first night) ==="
R=$(curl -sf -X POST "$BASE/plan" \
    -H 'Content-Type: application/json' \
    -d "$WORLD_STATE_OBJECTIVE" || echo '{}')
not_empty "plan returned with objective"  "$R" '.plan_id'
not_empty "steps non-empty"              "$R" '.steps'

if [ "$MODE" = ai ]; then
    # AI should start with wood gathering or shelter since objective is survival
    FIRST_GOAL=$(echo "$R" | jq -r '.steps[0].goal')
    echo "   AI chose first step: $FIRST_GOAL  (reason: $(echo "$R" | jq -r '.reason'))"
fi

# ── /plan — player message ────────────────────────────────────────────────────
blue "=== /plan (player message: 'gather some wood please') ==="
R=$(curl -sf -X POST "$BASE/plan" \
    -H 'Content-Type: application/json' \
    -d "$WORLD_STATE_MSG" || echo '{}')
not_empty "plan returned with player msg"  "$R" '.plan_id'

if [ "$MODE" = ai ]; then
    FIRST_GOAL=$(echo "$R" | jq -r '.steps[0].goal')
    echo "   AI chose first step: $FIRST_GOAL  (reason: $(echo "$R" | jq -r '.reason'))"
    # Should include GATHER_WOOD
    if echo "$R" | jq -r '.steps[].goal' | grep -q "GATHER_WOOD"; then
        green "AI plan includes GATHER_WOOD in response to player message"
        ((PASS++)) || true
    else
        red "AI plan missing GATHER_WOOD despite player asking for wood"
        ((FAIL++)) || true
    fi
fi

# ── /plan — low health ────────────────────────────────────────────────────────
blue "=== /plan (health=5, zombie nearby) ==="
R=$(curl -sf -X POST "$BASE/plan" \
    -H 'Content-Type: application/json' \
    -d "$WORLD_STATE_HURT" || echo '{}')
not_empty "plan returned when hurt"  "$R" '.plan_id'

if [ "$MODE" = ai ]; then
    FIRST_GOAL=$(echo "$R" | jq -r '.steps[0].goal')
    echo "   AI chose first step: $FIRST_GOAL  (reason: $(echo "$R" | jq -r '.reason'))"
fi

# ── /step_complete ────────────────────────────────────────────────────────────
blue "=== /step_complete ==="
STEP_BODY=$(cat <<'JSON'
{
  "plan_id": "test_plan_001",
  "step_index": 0,
  "goal": "GATHER_WOOD",
  "note": "Chopped 8 oak logs",
  "world_state": {
    "entity_id": "test-companion-001",
    "tick": 1200,
    "world": {
      "time_of_day": "day",
      "weather": "clear",
      "biome": "plains",
      "light_level": 15,
      "companion": {
        "health": 20.0,
        "position": [5.0, 64.0, 3.0],
        "inventory": ["oak_log","oak_log","oak_log","oak_log","oak_log","oak_log","oak_log","oak_log"]
      },
      "nearby_blocks": [],
      "nearby_entities": [],
      "current_goal": "GATHER_WOOD",
      "current_plan": {
        "active": true, "plan_id": "test_plan_001", "name": "gather_wood",
        "step": 1, "total_steps": 3, "current_step_goal": "GATHER_WOOD", "current_step_note": ""
      },
      "objective": {
        "type": "NONE", "display_name": "No objective",
        "guidance": "", "milestone": "", "progress": [], "complete": false
      }
    }
  }
}
JSON
)
R=$(curl -sf -X POST "$BASE/step_complete" \
    -H 'Content-Type: application/json' \
    -d "$STEP_BODY" || echo '{}')
check "step_complete returns recorded=true"  "$R" '.recorded' "true"

# ── /memory ───────────────────────────────────────────────────────────────────
blue "=== /memory ==="
R=$(curl -sf "$BASE/memory" || echo '{}')
check "memory has knowledge key"           "$R" '.knowledge | type'           "object"
check "memory has persistent_notes key"   "$R" '.persistent_notes | type'    "array"
check "memory has recent_plans key"       "$R" '.recent_plans | type'        "array"
not_empty "recent_plans has entries (after /plan calls above)" "$R" '.recent_plans'

# ── /memory DELETE ────────────────────────────────────────────────────────────
blue "=== DELETE /memory ==="
R=$(curl -sf -X DELETE "$BASE/memory" || echo '{}')
check "clear returns cleared=true"  "$R" '.cleared' "true"
R=$(curl -sf "$BASE/memory" || echo '{}')
check "notes empty after clear"   "$R" '.persistent_notes | length' "0"
check "knowledge empty after clear" "$R" '.knowledge | length' "0"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────────"
printf "Results: \033[32m%d passed\033[0m, \033[31m%d failed\033[0m\n" "$PASS" "$FAIL"
echo "────────────────────────────────────────"
[ "$FAIL" -eq 0 ]

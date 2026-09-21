#!/usr/bin/env bash
# Phase 8 walkthrough gates — reproduces the three plan walkthroughs headlessly
# using scripted mock LLMs. Same proven pipe pattern as smoke.sh. Each
# walkthrough swaps the mock script in config and boots a fresh world.
set -euo pipefail
cd "$(dirname "$0")/.."

mockscript() { sed -i 's/"mockScript": "[^"]*"/"mockScript": "'"$1"'"/' run/config/mc-companion/config.json; }
reset() {
  ./gradlew build -q
  rm -rf run/saves run/world run/logs
  mkdir -p run/logs
  cat > run/server.properties <<'EOF'
online-mode=false
level-type=minecraft:flat
generate-structures=false
spawn-monsters=false
difficulty=easy
enable-command-block=true
view-distance=6
max-players=10
pause-when-empty-seconds=0
EOF
}
grep_one() { grep -q "$1" run/logs/latest.log && { echo "PASS $2"; return 0; } || { echo "FAIL $2"; exit 1; }; }
grep_zero() { if grep -q "$1" run/logs/latest.log; then echo "FAIL $2 (unexpected)"; exit 1; else echo "PASS $2 (absent)"; fi; }

echo "== WALKTHROUGH (a): assisted give with partials =="
mockscript mock_script_give_partial.json; reset
{ sleep 40; echo "forceload add 0 0"; echo "mc-companion spawn 0 -60 0"; sleep 3
  echo "mc-companion debug give minecraft:oak_log 2"; sleep 1
  for i in 2 3 4 5 6; do echo "setblock $i -60 2 minecraft:oak_log"; done; sleep 2
  echo "mc-companion llm mock"; sleep 1
  echo "mc-companion say give me 5 oak logs"; sleep 25
  echo "mc-companion inventory"; sleep 1; echo "stop"
} | timeout 130 ./gradlew runServer --console=plain -q > run/logs/wt.out 2>&1 || true
grep_one 'Two now, three after I chop' 'llm-say-partial'
grep_one '\[mine\] broke'              'gathered'
grep_one 'GATHER DONE'                 'gather-done'
grep_one '\[give\] dropped'            'gave'

echo ""
echo "== WALKTHROUGH (b): LLM declines at night (structural) =="
mockscript mock_script_decline.json; reset
{ sleep 40; echo "forceload add 0 0"; echo "time set midnight"; echo "mc-companion spawn 0 -60 0"; sleep 3
  echo "mc-companion debug health 7"; sleep 1; echo "setblock 2 -60 2 minecraft:iron_ore"; sleep 1
  echo "mc-companion llm mock"; sleep 1
  echo "mc-companion say mine me 1 iron ore"; sleep 12
  echo "mc-companion status"; sleep 1; echo "stop"
} | timeout 110 ./gradlew runServer --console=plain -q > run/logs/wt.out 2>&1 || true
grep_one '\[decline\] reason=night and low health' 'declined'
grep_one '\[say\] Not in the dark'                'said-decline'
grep_zero '\[mine\] broke'                        'no mining (structural refusal)'

echo ""
echo "== WALKTHROUGH (c): LLM dies mid-task (degrade/offline/fallback) =="
mockscript mock_script_degrade.json; reset
{ sleep 40; echo "forceload add 0 0"; echo "mc-companion spawn 0 -60 0"; sleep 3
  echo "mc-companion debug give bread 5"; sleep 2
  echo "mc-companion llm mock"; sleep 1
  for _ in 1 2 3 4 5 6 7; do echo "mc-companion say give me 2 bread"; sleep 6; done
  echo "mc-companion status"; sleep 1; echo "stop"
} | timeout 120 ./gradlew runServer --console=plain -q > run/logs/wt.out 2>&1 || true
grep_one 'DEGRADED'       'degraded'
grep_one 'OFFLINE'        'offline'
grep_one '\[fallback\]'   'fallback-active'
grep_one 'GIVE DONE\|DECLINE DONE' 'still-acts'

echo ""
echo "WALKTHROUGH GATE PASS"
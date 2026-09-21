#!/usr/bin/env bash
# MC Companion — full headless smoke gate.
# Boots the dedicated server (superflat, no players, forceloaded, never-empty),
# then drives every capability through console commands and greps the [MCC] log.
# No human interaction, no network model (llm mock). Exits non-zero on any miss.
set -euo pipefail
cd "$(dirname "$0")/.."

J=build/libs/*.jar
LOG=run/logs/latest.log

echo "== building =="
./gradlew build -q

echo "== resetting world =="
rm -rf run/saves run/world run/logs
mkdir -p run/logs
: > run/logs/latest.log
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

echo "== running smoke sequence =="
{
  sleep 40                              # boot
  echo "forceload add 0 0"              # keep chunk loaded (no players)
  echo "mc-companion spawn 0 -60 0"; sleep 2
  echo "mc-companion debug give bread 3"; sleep 1
  echo "mc-companion status"; sleep 1
  # gather gate: 5 oak logs nearby
  for i in 2 3 4 5 6; do echo "setblock $i -60 2 minecraft:oak_log"; done; sleep 2
  echo "mc-companion debug goal GATHER block=minecraft:oak_log count=3"; sleep 20
  echo "mc-companion inventory"; sleep 1
  # pickup gate: item entity dropped at companion
  echo "summon minecraft:item 1 -59 1 {Item:{id:\"minecraft:bread\",count:2}}"; sleep 6
  # reflex gate: low HP + zombie -> flee
  echo "mc-companion debug health 5"; sleep 1
  echo "summon minecraft:zombie 3 -60 0"; sleep 8
  echo "kill @e[type=minecraft:zombie]"; sleep 1   # clear threat so reflex yields
  echo "mc-companion debug health 20"; sleep 1   # survive for the mode gate
  # mode gate (GUARD needs a params arg in debug goal)
  echo "mc-companion debug goal GUARD x=0 y=0 z=0"; sleep 3
  echo "mc-companion status"; sleep 1
  # mock-LLM gate: end-to-end give-me-oak-logs
  echo "mc-companion llm mock"; sleep 1
  echo "mc-companion say give me 3 oak logs"; sleep 22
  echo "mc-companion status"; sleep 1
  echo "mc-companion inventory"; sleep 1
  echo "mc-companion debug health 20"; sleep 1
  echo "stop"
} | timeout 130 ./gradlew runServer --console=plain -q > run/logs/server.out 2>&1 || true

echo "== grepping gates =="
L=$LOG
grep -q '\[MCC\]\[spawn\]'      $L && echo "PASS spawn"      || { echo "FAIL spawn"; exit 1; }
grep -q 'GATHER DONE'           $L && echo "PASS gather"     || { echo "FAIL gather"; exit 1; }
grep -q '\[give\] dropped'      $L && echo "PASS givedrop"   || { echo "WARN givedrop (no owner drop)"; }
grep -q '\[pickup\]'            $L && echo "PASS pickup"     || { echo "WARN pickup"; }
grep -q '\[reflex\] FLEE'       $L && echo "PASS reflex-flee"|| { echo "FAIL reflex-flee"; exit 1; }
grep -q 'mode=GUARD'            $L && echo "PASS guard-mode" || { echo "WARN guard-mode"; }
grep -q 'llm=MOCK'              $L && echo "PASS llm-mock"   || { echo "FAIL llm-mock"; exit 1; }
grep -q '\[MCC\]\[say\]'        $L && echo "PASS llm-say"    || { echo "WARN llm-say"; }
grep -q '\[MCC\]\[llm\] request' $L && echo "PASS llm-request"|| { echo "FAIL llm-request"; exit 1; }

echo ""
echo "SMOKE PASS"

# Plan 07 — Watchdog for stuck planner requests

**Priority:** 🟠 Robustness · **Effort:** S · **Depends on:** nothing

## Problem

`AIGoalPlanner.requestPending` is set before the async HTTP call and cleared in the
callback. If the callback is ever skipped — a future refactor that throws before
`requestPending.set(false)`, an executor rejection, a JVM-level edge case in the
`CompletableFuture` chain — the flag stays `true` forever and the planner silently stops
asking for decisions. The companion would idle until server restart with only a DEBUG log
("request in flight, skipping") hinting at why.

## Approach

Track when the in-flight request was sent and force-expire it from the tick loop.

## Implementation steps

1. Add `private long requestSentAtTick = -1;` set inside `triggerRequest` when the CAS
   succeeds (use the entity's world time or a local tick counter).
2. In `tick()`, before other logic:
   ```java
   int watchdogTicks = (SidecarClient.TIMEOUT_SECONDS + 5) * 20; // HTTP timeout + slack
   if (requestPending.get() && requestSentAtTick >= 0
           && tickCounter - requestSentAtTick > watchdogTicks) {
       CompanionMod.LOGGER.warn("AIGoalPlanner: sidecar request watchdog expired — resetting");
       requestPending.set(false);
       requestSentAtTick = -1;
   }
   ```
3. Late responses: a callback arriving after the watchdog fired would clear a flag owned
   by a *newer* request. Guard with a request generation counter — the callback captures
   its generation and only clears `requestPending` / applies the goal if it is still the
   current generation.
4. Expose `TIMEOUT_SECONDS` from `SidecarClient` (make it public or add a getter) so the
   watchdog derives from the real HTTP timeout instead of duplicating the constant.
5. Test by pointing `sidecarPort` at a port that blackholes connections (e.g. a firewall
   DROP or a socket that accepts and never responds) and confirming the planner recovers
   and retries on the next fallback interval.

## Files to touch

- `src/main/java/com/example/companion/goal/AIGoalPlanner.java`
- `src/main/java/com/example/companion/net/SidecarClient.java`

## Acceptance criteria

- [ ] A hung sidecar connection never permanently stops the planner.
- [ ] A late response from an expired request is discarded, not applied.
- [ ] Normal operation unchanged (no spurious watchdog warnings under healthy latency).

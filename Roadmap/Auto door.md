# Auto door

Category: Dungeons
Status: Design complete; blocked on the reusable navigation system

## Purpose

Auto door performs the movement and door-opening portion of a dungeon Blood rush. It follows the currently known room-and-door route from the Entrance to the Blood room, reaches each required door as quickly as practical using the shared navigation system, waits in a usable interaction position when necessary, clicks as soon as a teammate obtains a usable key, continues through the opened doorway, and stops at the Blood door.

This module does not clear rooms, kill key mobs, pick up keys, choose a teammate, enter Blood, or implement pathfinding itself.

## Core behavior

1. Activate only during the non-boss portion of a started dungeon run.
2. Read the continuously updated Blood route from `DungeonRoomScanner`.
3. Select the first unopened route door ahead of the player.
4. Calculate a safe staging goal on the player's side of that door.
5. Submit that coordinate to the reusable navigator immediately.
6. Continue accepting scanner revisions while moving. Replace the pending door/goal if the known Blood route changes.
7. Once the door is reachable, face its real block and wait without repeatedly clicking.
8. When a teammate's key pickup is observed, attempt a normal vanilla right-click as soon as the door is in legal interaction range and line of sight.
9. Confirm that the door opened. Do not infer success merely because a click packet was sent.
10. Submit a goal safely inside the next room so navigation crosses the doorway.
11. Repeat for every required door on the route.
12. At the Blood door, open it when permitted and stop. Do not navigate into Blood.

## Scope

### In scope

- Normal player movement through the shared navigator.
- Etherwarp actions selected and executed by the shared navigator.
- Continuous response to newly loaded dungeon rooms and doors.
- Door-side selection and exact interaction positioning.
- Team-visible key pickup detection.
- Wither and Blood door interaction.
- Opening confirmation, retries, replanning, and safe cancellation.
- Optional diagnostics for route and state-machine development.

### Out of scope

- The navigation/pathfinding algorithm and movement executor.
- AOTE or other teleport abilities unless the future navigator independently supports them and Auto door is explicitly configured to allow them.
- Combat, room clearing, key collection, secret routing, or puzzle solving.
- Predicting rooms or doors that the vanilla client has not received.
- Entering or automating the Blood room.
- Boss-room behavior.
- Any movement or interaction impossible for a normal vanilla client.

## Dependencies

### Dungeon layout scanner

Auto door consumes these existing scanner results:

- `layout()` for an immutable, revisioned snapshot.
- `roomAt(x, z)` for the player's logical room.
- `bloodRoute()` for the known Entrance-to-Blood room sequence.
- `bloodRouteDoors()` for the exact physical doors between those rooms.
- Door type and world position.
- Door endpoints for determining the approach side and next room.

The scanner runs continuously because the initial render distance may contain only the first one or two rooms. A route that is incomplete is normal, not an error.

### Reusable navigator

The navigator is a separate general-purpose subsystem. Auto door must depend on an interface rather than concrete pathfinding classes. The minimum proposed contract is:

```kotlin
interface NavigatorService {
	fun navigate(request: NavigationRequest): NavigationHandle
	fun replaceGoal(handle: NavigationHandle, request: NavigationRequest): Boolean
	fun cancel(handle: NavigationHandle, reason: String)
	fun status(handle: NavigationHandle): NavigationStatus
}

data class NavigationRequest(
	val goal: BlockPos,
	val arrivalRadius: Double,
	val allowNormalMovement: Boolean,
	val allowEtherwarp: Boolean,
	val priority: NavigationPriority,
	val owner: String
)
```

Required status information:

- Planning, moving, arrived, blocked, failed, or cancelled.
- Whether a partial route is already executable.
- Current goal and request identity.
- A failure reason suitable for diagnostics.

Required behavior:

- Begin moving from a usable partial plan without waiting for a complete long-distance solution.
- Plan away from the client/render thread.
- Replan as chunks load or movement becomes invalid.
- Allow an urgent goal replacement without briefly continuing toward a stale door.
- Own and release all movement keys cleanly.
- Respect real movement, collision, rotation, item switching, Etherwarp range, line of sight, mana, and cooldown constraints.
- Never report arrival based solely on the planned endpoint; verify the live player position.

Auto door supplies goals and high-level intent. It must not inspect, rewrite, or execute individual path segments.

## Data model

```kotlin
data class AutoDoorTarget(
	val door: ScannedDungeonDoor,
	val approachRoom: ScannedDungeonRoom,
	val destinationRoom: ScannedDungeonRoom,
	val stagingGoal: BlockPos,
	val crossingGoal: BlockPos,
	val layoutRevision: Long
)

data class DoorPermission(
	val observedAtTick: Long,
	val remainingDoorUses: Int,
	val source: PermissionSource
)

enum class AutoDoorState {
	IDLE,
	WAITING_FOR_LAYOUT,
	NAVIGATING_TO_DOOR,
	STAGING_AT_DOOR,
	WAITING_FOR_KEY,
	AIMING_AT_DOOR,
	OPENING_DOOR,
	CONFIRMING_OPEN,
	CROSSING_DOOR,
	WAITING_FOR_ROUTE_EXTENSION,
	COMPLETE,
	FAILED
}
```

The active target must refer to room signatures and door coordinates from one scanner revision. It is revalidated before every navigation replacement and interaction.

## Route selection

### Starting point

- Prefer the player's current room if it lies on the known Blood route.
- If the player is inside a multi-tile room, treat all tiles with the same room signature as the same node.
- If the player is between rooms, retain the last confirmed route room briefly and resolve the side using player position relative to the target door.
- If the player is not on the known Blood route, request the scanner's connected path to the nearest reachable Blood-route room once that API exists. Until then, stop with a clear diagnostic rather than selecting a door geometrically.

### Next door

- Walk the route forward from the current route index.
- Ignore internal separators within a multi-tile room.
- Skip a door only when opening is positively confirmed or the player has positively entered its destination room.
- Do not skip a door merely because its marker block is absent from an unloaded chunk.
- When the route currently ends before Blood, navigate through the known prefix and enter `WAITING_FOR_ROUTE_EXTENSION` at its frontier.
- Recompute selection whenever the scanner revision changes, but replace the active target only when the new target is demonstrably more current or the old target is invalid.

### Blood-specific stopping rule

The Blood door is the terminal target. After its opening is confirmed:

- Cancel navigation.
- Release movement and interaction ownership.
- Enter `COMPLETE`.
- Do not issue the crossing goal.
- Do not restart during the same dungeon run unless the module is manually reset.

## Door geometry

The scanner supplies the actual doorway center. Auto door derives two sides from the two linked room centers.

For each door:

- `approachDirection` points from the approach room toward the door.
- `stagingGoal` is a standable block on the approach side and within normal interaction reach of a visible door block.
- `crossingGoal` is a standable block several blocks beyond the doorway inside the destination room.
- Candidate staging positions are searched around the approach axis rather than hard-coded to one Y level.
- The chosen position must provide player clearance, a collision-valid route, and a raycast hit on the actual door block.
- Prefer the closest navigator-reachable candidate with an unobstructed interaction ray.
- Re-evaluate staging geometry when door blocks or nearby chunks update.

The route door's `(x, 69, z)` marker is a topology anchor, not automatically the precise block face to click. Interaction targeting must inspect the live blocks in the doorway column and select a raycast-valid block face.

## Key and door signals

### Key permission

Primary signal: the formatted dungeon message matching the vanilla-visible key pickup prompt:

```text
RIGHT CLICK on ... to open it. This key can only be used to open <n> door(s)!
```

The parser records the advertised number of door uses. This message is treated as team permission to attempt the next required locked door.

Important rules:

- A pickup signal may arrive before Auto door reaches the staging point. Retain it for the current dungeon run.
- Multiple pickup messages increment the available-use count.
- Consume a permission only after an opening is confirmed, not when clicking begins.
- A teammate may open a door first. Opening confirmation advances the state without requiring our own click.
- Message wording and channel must be verified with a real-run capture before implementation is considered complete.

### Opening confirmation

Use the earliest positive signal among:

- The server message that a named player opened a Wither door.
- `The BLOOD DOOR has been opened!` for Blood.
- A loaded doorway's marker/barrier blocks changing to an open/passable state.
- The player entering the expected destination room.

Room entry is confirmation for progression, but it must not consume a locally stored key permission unless an opening signal establishes that a locked door was opened.

## Interaction behavior

- Use the normal client game-mode interaction path and main-hand swing.
- Aim through smooth vanilla mouse-equivalent rotation owned by the module/navigation coordination layer.
- Require normal client reach and a live block raycast.
- Never send a fabricated out-of-range interaction.
- Do not click through walls or unloaded blocks.
- Pause navigation input while performing the final aim/click unless the navigator explicitly supports a safe interaction hold.
- Preserve and restore the selected hotbar slot only if Auto door changes it. Normally, opening a teammate-key door should not require selecting the key locally.
- Rate-limit retries. A reasonable starting point is one attempt, wait for acknowledgement, then retry after a configurable 4–8 ticks if the door remains confirmed closed and permission still exists.
- Stop retrying immediately on an opening signal, route change, loss of reach, GUI opening, death, or module disable.

## State machine

### `IDLE`

No inputs are owned. Wait for enablement and valid dungeon conditions.

### `WAITING_FOR_LAYOUT`

Wait for a route door that is connected to the player's known position. Continue scanning; do not wander toward a guessed coordinate.

### `NAVIGATING_TO_DOOR`

Submit or maintain the staging goal. Accept partial navigation immediately. Revalidate against layout revisions without resetting an equivalent goal every tick.

### `STAGING_AT_DOOR`

Confirm position, reach, line of sight, and target identity. If the door is already open, proceed directly to crossing or completion.

### `WAITING_FOR_KEY`

Hold a safe position without spamming movement or interaction. Scanner and server-event processing remain active. A valid permission transitions immediately to aiming.

### `AIMING_AT_DOOR`

Acquire exclusive rotation/interaction ownership and aim at a currently valid door block. Return to staging if the target cannot be raycast.

### `OPENING_DOOR`

Perform one normal right-click and start the acknowledgement timer.

### `CONFIRMING_OPEN`

Watch messages, block changes, and room transitions. Retry only under the interaction rules above.

### `CROSSING_DOOR`

Submit the destination-room crossing goal. Once the player is positively in that room, select the next route door.

### `WAITING_FOR_ROUTE_EXTENSION`

The known prefix was exhausted but Blood has not been reached. Hold at a safe frontier position and resume immediately when a scanner revision adds the next connected door.

### `COMPLETE`

Blood door opening was confirmed. Remain inert for the rest of the run.

### `FAILED`

Release all owned inputs. Provide a rate-limited reason. Manual disable/enable or a new dungeon run resets the state.

## Timing requirements

Some runs provide only a few seconds between key pickup and the desired door click. The implementation must therefore:

- Navigate to and stage at the next locked door before the key is picked up whenever topology permits.
- Parse key/opening signals synchronously on the client event path and enqueue state changes for the next safe client tick.
- Avoid starting a new expensive path search at key-pickup time if the staging route already exists.
- Cache validated staging and crossing candidates per door and invalidate them only on relevant world/layout changes.
- Keep scanner work bounded and incremental.
- Never perform full path planning on the render or packet thread.
- Target reaction on the first eligible client tick after the permission signal.
- Record timestamps for pickup observed, staged, click sent, and open confirmed in debug mode.

No fixed latency promise is possible because packet arrival, client tick timing, server acknowledgement, line of sight, and player position are external conditions.

## Ownership and coordination

Auto door needs explicit ownership of:

- One navigator request.
- Movement input while its navigator request is active.
- Rotation during final interaction.
- The interact action during a click attempt.

It must yield or stop when:

- The user manually overrides movement, according to the shared input-owner policy.
- A GUI or chat screen opens.
- The player dies, becomes a ghost, changes world, enters a boss area, or leaves the dungeon.
- Another higher-priority module owns movement or rotation.
- The module is disabled.

Auto door must never call `InputScheduler.clear()` globally. It should release only its own owner/session through the future shared ownership API.

## Failure and recovery

| Condition | Response |
|---|---|
| Layout not yet connected to Blood | Follow the confirmed prefix, then wait for extension |
| Active route changes | Revalidate and urgently replace the goal if needed |
| Staging block becomes invalid | Search a new candidate and replan |
| Navigator reports blocked | Retry/replan with bounded backoff; fail after a configurable threshold |
| Navigator returns only a partial route | Execute it and request the continuation |
| Door already opened by teammate | Cancel the click and cross/complete |
| Key signal arrives while travelling | Store permission and click on first eligible tick at the door |
| Click receives no acknowledgement | Re-raycast and retry at the configured interval |
| Permission count is zero | Wait at the staging point |
| Player is pushed off position | Return to navigation with the same validated target |
| Door/chunk unloads | Stop interaction, retain logical target, and wait/replan |
| Player is no longer on the route | Stop rather than guess; optionally recover when a graph path is available |
| Blood door opens | Stop permanently for the current run |

## Settings

Initial user-facing settings:

- `Enabled`
- `Use Etherwarp` — passed to the navigator; default on.
- `Show Route` — render known Blood-route doors and active navigation target.
- `Show Status` — compact state/failure display.
- `Interaction Retry Delay` — bounded safe range, default selected after live testing.
- `Manual Input Cancels` — default on.
- `Debug Logging` — default off.

Do not expose pathfinder tuning values in Auto door. Generic navigation settings belong to the navigator module/service.

## Diagnostics

Debug output should include:

- Dungeon run sequence and layout revision.
- Current room and known Blood-route room signatures.
- Active door coordinate/type and both endpoint rooms.
- Staging/crossing goals and candidate rejection reasons.
- Navigation request ID and status.
- Permission count and last key signal.
- Last click target, attempt count, and acknowledgement timer.
- Every state transition with tick and monotonic timestamp.
- Terminal stop/failure reason.

Logging must be rate-limited and must not print every tick.

## Implementation phases

### Phase 1 — prerequisite navigator

- Design the reusable navigation API and ownership model.
- Implement coordinate goals, partial/rolling planning, normal movement, and Etherwarp.
- Prove cancellation, urgent goal replacement, arrival verification, and recovery independently through a movement-test module.

### Phase 2 — Auto door observation layer

- Add key pickup and door-opening event parsing.
- Add live door-block inspection and open/closed confirmation.
- Add staging/crossing geometry calculation.
- Add debug rendering without automated movement or clicking.

### Phase 3 — route controller

- Implement target selection and the state machine.
- Navigate to staging goals and cross already-open doors.
- Handle partial routes and scanner revisions.
- Stop at the Blood door without interacting.

### Phase 4 — interaction

- Enable permission-gated door clicks.
- Add acknowledgement, retry, teammate-open, and Blood completion behavior.
- Verify inventory slot preservation and module ownership cleanup.

### Phase 5 — hardening

- Exercise different dungeon floors, route lengths, Fairy-room paths, multi-tile rooms, low render distance, deaths, lag, knockback, missing Etherwarp, and mid-run enable/disable.
- Tune staging geometry and retry delays from captured real-run evidence.
- Keep safe fallbacks when the navigator or scanner cannot prove a route.

## Test plan

### Unit tests

- Route index selection from every room in a sample Blood path.
- Equivalent scanner revisions do not restart navigation.
- A changed next door replaces the active goal.
- Staging/crossing side calculation for north, south, east, and west doors.
- Multi-tile room separators are never selected as doors.
- Key permission is retained before arrival and consumed only on confirmed opening.
- Teammate opening advances without a local click.
- Retry timing never exceeds the configured rate.
- Blood opening transitions to `COMPLETE` without a crossing request.
- Reset and disable release owned resources.

### Integration tests

- Mock scanner revisions reveal a Blood path one room at a time.
- Mock navigator returns partial routes, failures, arrival, and urgent replacements.
- Door block changes confirm opening without chat.
- Chat confirms opening while the door chunk is unloaded.
- Player displacement forces restaging.
- Death/world change cancels all activity.

### In-game validation

- Capture the exact formatted key pickup and door-opening messages on supported floors.
- Confirm the scanner's physical door coordinate and clickable block column for every door orientation/type.
- Validate low-render-distance progressive routing.
- Confirm the player stages before key pickup and reacts on the first eligible tick.
- Confirm normal interaction reach and raycast behavior.
- Confirm Etherwarp casts and all movement remain humanly possible on a vanilla client.
- Confirm Auto door never enters Blood.

## Acceptance criteria

Auto door is ready when all of the following are true:

- It can consume a progressively revealed Blood route without guessing unloaded topology.
- It reaches and waits at each next required door using the shared navigator.
- It responds to a team key pickup on the first eligible client tick.
- It uses only normal in-range, raycast-valid interactions.
- It confirms openings before consuming permission or advancing.
- It handles a teammate opening the door first.
- It crosses ordinary route doors and selects the next one.
- It opens the Blood door and stops without entering Blood.
- Disable, death, world change, boss entry, and manual cancellation release all owned input.
- Unit, integration, and representative in-game tests pass.

## Decisions still requiring live evidence

These are intentionally not guessed:

1. The exact formatted key-pickup message(s) and whether every teammate receives them consistently on every supported floor.
2. The best safe interaction retry delay under normal and high latency.
3. The doorway blocks/faces that provide the most reliable vanilla raycast for each door orientation.
4. Whether enabling Auto door mid-run should immediately use already-observed permission or require a new key signal. The safer default is to require a signal observed during the active module session unless the current door is already visibly open.

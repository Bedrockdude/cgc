# Etherwarp-First Pathfinder Plan

> Direction change: this section supersedes the earlier hybrid walking/AOTV design below. The older material remains temporarily as implementation history and must not be treated as active requirements.

## Active design

The navigator is now an Etherwarp-first system. It should cross the route using targeted Etherwarp whenever a safe visible landing exists. Normal 12-block AOTV teleportation is removed completely.

Ordinary movement is not a general traversal system. It may only perform short setup corrections, normally one to three blocks, when needed to:

- peek around a nearby obstruction;
- move out from underneath an overhang;
- obtain line of sight to a useful Etherwarp target;
- gain safe body clearance before crouching;
- center the player on a stable launch position;
- finish within a few blocks of the requested coordinate.

Setup movement may use forward movement, strafing, or forward-strafing. It must not form long walking routes. After at most three setup blocks, the planner must find an Etherwarp continuation, arrive at the final goal, or report that the currently loaded geometry is insufficient.

The active search state is conceptually:

```text
position
facing
consecutive setup-movement blocks
previous Etherwarp target
```

Primary edges are safe visible Etherwarps. Setup-movement edges reset after a successful Etherwarp and are capped at three consecutive blocks.

The cost model should strongly prefer:

```text
Etherwarp → Etherwarp → Etherwarp
```

It may choose:

```text
Etherwarp → strafe 1-3 blocks → Etherwarp
```

It must reject routes resembling:

```text
walk through the room → occasional Etherwarp
```

All prior requirements concerning full-player-box collision, landing clearance, progressive world loading, SS-style camera rotation, deliberate hotbar swaps, crouch charging, execution verification, failed-edge blacklisting, debug rendering, and rapid stuck detection remain active.

Acceptance criteria for the new direction:

- AOTV is never selected or used.
- No route contains more than three consecutive setup-movement blocks.
- Setup movement has a concrete purpose: reaching the goal or enabling a subsequently validated Etherwarp.
- Unsafe, confined, or obstructed Etherwarp landings are rejected.
- The executor verifies each actual Etherwarp landing before continuing.
- When no safe Etherwarp continuation is visible after bounded setup exploration, the navigator waits or fails instead of beginning a long walk.
- Confirmed raycast surfaces are prioritized ahead of speculative landing samples so candidate limits cannot discard every usable Etherwarp.
- `/cgc ng status` exposes route state and candidate diagnostics rather than allowing planning failure to appear as a no-op.
- Etherwarp scoring rewards long forward progress and alignment with the destination, while penalizing lateral deviation, backward movement, short warps, and unnecessary yaw/pitch rotation. Sideways targets remain available when direct geometry is blocked, but should not tie with a clean straight-line warp.
- Etherwarp execution uses the same `AutoCLookController.startEtherwarp` profile and visible block-face aim selection as AutoC Etherwarp nodes, and verifies the live crosshair ray before clicking.
- The first planning expansion uses the player's real eye position. This is required after landing near a block edge, where substituting the center of the feet block can incorrectly place the virtual viewpoint behind geometry and prevent generation of the next Etherwarp.
- Tight tunnels distinguish physical validity from landing quality. A landing remains legal when the complete player box fits with the teleport margin, even if walls or a ceiling make its clearance score poor. Confinement is a cost used to prefer open space, not an absolute rejection rule.
- A safe short forward Etherwarp through confined geometry must beat returning an empty partial route. This permits repeated short tunnel warps while still preferring longer, clearer targets whenever they exist.

### RSA-derived Etherwarp graph discovery

The sparse fixed-angle candidate sampler has been replaced with the useful parts of RSA's `EtherwarpPathfinder` design:

- Every launch position receives an adaptive spherical ray scan rather than only a handful of goal-relative rays.
- A fine one-degree cone around the goal direction supplements the wider four-degree sphere. This preserves the precision needed to hit floor blocks down long narrow tunnels without paying the full cost of a two-degree sphere everywhere.
- Yaw spacing expands near vertical pitch angles using the cosine of pitch, avoiding redundant rays around the poles.
- Ray hits are deduplicated by resulting landing position and cached for the duration of the search.
- Up to 64 physically valid landing nodes are retained per launch position, ordered by route quality.
- The exact successful collision point, yaw, and pitch are stored on the edge. The planner never substitutes the center of the target block and then rejects a surface that was only visible at its edge.
- The executor first tries the preserved hit point and then falls back to AutoC's collision-shape aim-point search if player position or loaded geometry has changed.

The RSA implementation is not copied wholesale. CGC retains its player-box landing validation, actual-eye initial scan, AutoC look controller, bounded setup movement, failed-edge invalidation, execution verification, weighted route quality, and finite planning budget. RSA's unrestricted node expansion, fixed per-node cost, and incomplete empty-open-set termination are not adopted.

### Etherwarp activation contract

Every planned ray and every execution aim uses the crouched eye height, including hypothetical future landing nodes. Activation follows this order:

```text
find Etherwarp item
→ switch hotbar slot and settle
→ rotate with AutoC's Etherwarp look controller
→ conservatively confirm the target from the predicted crouched eye
→ begin a 15-tick sneak window
→ after seven pre-click sneak ticks, confirm actual CROUCHING pose and live target hit
→ right-click once at the middle of the window
→ retain sneak for seven post-click ticks
→ verify meaningful displacement and the expected landing
```

If pose or targeting is wrong at the middle of the window, the click is not sent. The navigator remains crouched, reacquires a collision-shape aim point from the real crouched eye, and starts a fresh centered window.

Etherwarp visibility is intentionally more conservative than a normal vanilla collider ray. Block cells containing collision geometry are treated as occupied for obstruction purposes, preventing routes through nominally empty portions of stairs and similar partial blocks that Hypixel Etherwarp may reject. The intended target still uses its real collision shape for selection of a hittable face point.

A click is not considered successful merely because the player was already close to the expected landing. Success requires both meaningful physical displacement and arrival near the planned endpoint. Failed Etherwarp transitions are added to the failed-edge set before replanning.

Search caching is position-based rather than treating every arrival yaw and pitch as a separate graph node. Heading remains available for rotation cost, but it does not multiply equivalent landing states. Together with the fine forward scan and a larger planning budget, this allows several future Etherwarp nodes to be buffered and executed continuously without recalculating at every landing.

### Rolling route calculation

Navigation does not wait for a complete route before beginning execution. Initial planning has a short 400 ms budget and returns the best usable prefix. As soon as that prefix starts executing, a separate rolling calculation begins from its hypothetical endpoint with a deeper 900 ms budget.

Only one rolling extension is computed at a time. When ready, it is held as the next route segment. At the end of the active segment it is appended without changing the action index. If the final active action and first appended action are both Etherwarps, the executor keeps the Etherwarp item and sneak state and immediately begins the next AutoC turn. A further rolling calculation then begins from the new buffered endpoint.

If lookahead is not ready when the active prefix ends, execution waits for that in-flight calculation instead of starting a duplicate full replan. Cancellation, manual input, world changes, or a replacement navigation request cancel both initial and rolling calculations.

### Compound-block aim selection

Aim fallback must never use only the center or union bounding box of a compound collision shape. Stairs, walls, fences, slabs, and similar blocks can have empty regions inside their overall bounds. The navigator enumerates every collision AABB in the target voxel shape independently, samples inset face/center points on each real component, checks conservative obstruction, and chooses the visible point requiring the smallest live rotation. Points in empty stair corners are never generated as aim candidates.

### Authoritative Etherwarp edge prediction

A waypoint is not valid merely because a generic Minecraft ray reaches a block and an ordinary player AABB fits above it. Candidate generation follows the RSA/RSM Etherwarp model:

- Traverse voxel cells from a crouched eye using Etherwarp's one-axis-at-a-time DDA ordering.
- Treat the same explicit block families as ray-transparent rather than relying on vanilla collision shapes.
- Stop on the first non-transparent target cell.
- Require both the feet and head cells above the target to be Etherwarp-valid space.
- Reject invalid landing-space types such as ladders, flower pots, and skulls even when they are ray-transparent.
- Reject fence, wall, and fence-gate targets until fractional-height landing states are represented accurately.
- Use the selected item's real range: 57 blocks plus transmission tuners, capped at 61.

The target block, resulting feet block, and exact DDA entry point are stored separately on the edge. Immediately before right-click, the same predictor runs again from the real crouched eye and live yaw/pitch. The click is sent only when both the predicted target and predicted landing exactly match the planned edge; otherwise that edge is blacklisted and replanned.

---

# Superseded Hybrid Design (historical reference)

## Objective

Create a fast hybrid pathfinder that can navigate from the player's current position to a specified destination while intelligently combining:

- Normal Minecraft movement
- The existing 12-block directional teleport
- The existing targeted teleport with a maximum range of 61 blocks

The primary objective is **minimum real traversal time**, not minimum distance or minimum number of actions.

The resulting movement should also look reasonably human. It should avoid erratic rotations, pointless action switching, strange detours, teleport spam in tight spaces, and obviously inefficient movement.

Path generation needs to be extremely fast. A useful route should normally be available in well under a few seconds.

---

# 1. Do Not Build Three Separate Pathfinders

Do not create:

- a walking pathfinder
- a 12-block teleport pathfinder
- a targeted teleport pathfinder

and then attempt to choose between their results afterward.

Instead, implement a single **hybrid action planner**.

Every state in the search should be able to produce different types of outgoing actions.

Conceptually:

```text
State
 ├── Walk somewhere
 ├── 12-block teleport
 └── Targeted teleport
```

All three actions compete using the same cost system.

The planner should therefore naturally be able to discover routes such as:

```text
Walk
→ 12-block teleport
→ 12-block teleport
→ walk around corner
→ targeted teleport to upper platform
→ walk to destination
```

This is the core architectural requirement.

---

# 2. Optimize for Execution Time

The planner should primarily estimate how many milliseconds a route will take to actually execute.

Do not use block distance as the main cost.

Each action should have an estimated time cost.

For example:

```text
walkCost =
    estimatedMovementTime

normalTeleportCost =
    rotationTime
    + itemUseTime
    + teleportDelay

targetTeleportCost =
    rotationTime
    + targetingTime
    + itemUseTime
    + teleportDelay
```

Exact numbers should be configurable and eventually calibrated from real execution.

This immediately solves a large amount of the "when should I teleport?" problem.

If walking 4 blocks takes less time than turning around and teleporting, walking wins.

If the player is travelling 50 blocks down a clear corridor, chaining 12-block teleports wins.

If a targeted teleport can skip an entire staircase or obstacle, it wins.

The planner should make these decisions from estimated traversal time rather than hard-coded rules whenever possible.

---

# 3. Add Secondary Penalties

Raw execution time should be the dominant cost, but several smaller penalties should influence route quality.

Recommended model:

```text
totalCost =
    executionTime
    + riskPenalty
    + rotationPenalty
    + actionSwitchPenalty
    + awkwardMovementPenalty
    + clearancePenalty
```

These penalties should be relatively small compared with actual traversal time.

Their purpose is to choose the better route when two routes have similar speeds.

## Rotation Penalty

Prefer routes requiring fewer or smaller camera rotations.

For example:

```text
0° → 20° turn = almost free
90° turn = noticeable penalty
180° turn = larger penalty
```

This helps paths appear intentional rather than constantly snapping between unrelated directions.

---

## Action Switch Penalty

Add a small penalty when repeatedly alternating movement types.

Bad:

```text
walk 2 blocks
teleport
walk 1 block
teleport
walk 2 blocks
teleport
```

Better:

```text
walk around obstacle
teleport
teleport
teleport
```

Do not make this penalty large enough to reject genuinely faster routes.

---

## Risk Penalty

Penalize actions where:

- landing space is tight
- the player lands close to a wall
- the landing location has limited headroom
- the landing is close to an edge
- small position inaccuracies could cause failure
- teleport validation is uncertain

Safe, open landing positions should be preferred.

---

## Awkward Movement Penalty

Discourage things such as:

- tiny walking segments between teleports
- unnecessary zig-zagging
- repeatedly changing direction
- walking backwards unless useful
- walking into extremely tight geometry when another route exists

---

# 4. Use a Sparse Search Space

Do not run the expensive hybrid planner over every possible Minecraft block.

That will become far too expensive, especially with 61-block teleport possibilities.

Instead, generate useful navigation states dynamically.

Important candidate states include:

- destination
- current player position
- corners
- corridor entrances/exits
- obstacle boundaries
- ledges
- platform edges
- elevation changes
- safe teleport landing positions
- important walking transition points

The exact implementation can depend on the navigation/world-scanning systems already present in the project.

The important requirement is:

> Search meaningful movement opportunities rather than every block in the world.

---

# 5. Hierarchical Planning

Use two levels of planning.

## Level 1 — Macro Planner

The macro planner decides approximately how to traverse the environment and which movement abilities are useful.

Example:

```text
Current room
→ corridor
→ teleport through long corridor
→ reach staircase area
→ targeted teleport to upper floor
→ destination room
```

This should operate on a relatively sparse graph and should be extremely fast.

---

## Level 2 — Local Planner

The local planner handles exact movement between nearby macro states.

It is responsible for:

- walking around walls
- moving through doors
- lining up teleport positions
- reaching exact teleport launch points
- walking the final few blocks
- correcting minor position errors

Do not expect the global search to determine every keyboard input.

---

# 6. Normal Movement Candidate Generation

Walking should remain the fallback action and should be heavily preferred for short or awkward distances.

The walking planner should be able to determine:

- whether two nearby points can reasonably be connected
- approximate walking distance
- estimated execution time
- required elevation changes
- whether jumping is needed
- obstacle complexity

For the hybrid search, walking edges do not always need their exact final movement sequence immediately.

The search can initially use an estimated walking cost.

Only fully calculate the local walking path when:

- the edge becomes part of a promising route
- the path needs validation
- the route is selected for execution

This prevents wasting CPU calculating detailed walking paths that will never be used.

---

# 7. 12-Block Teleport Candidate Generation

The directional teleport should be treated as a very important high-speed movement primitive.

Do not only consider using it when the destination is approximately 12 blocks away.

It should be possible to chain it repeatedly.

For example, a long open route should naturally become:

```text
Teleport
Teleport
Teleport
Teleport
Walk
```

rather than:

```text
Walk most of the distance
Teleport once
```

## Candidate Directions

Do not test hundreds of arbitrary yaw/pitch combinations.

Generate a small number of useful directions based on:

- direction toward destination
- direction toward the next macro waypoint
- direction along the current corridor
- directions toward visible open areas
- directions around nearby obstacles
- continuation of the previous movement direction

This greatly reduces branching.

---

## Chaining Bonus

The cost system should naturally favour consecutive directional teleports when they continue along roughly the same heading.

A small continuation bonus can also be used.

Example:

```text
Teleport east
→ teleport east
→ teleport east
```

should generally be preferable to:

```text
Teleport east
→ rotate 80°
→ walk
→ rotate again
→ teleport
```

assuming both routes are similarly safe.

---

# 8. Targeted 61-Block Teleport Candidate Generation

The targeted teleport must be handled very differently from ordinary movement.

Never generate every block within 61 blocks as a possible destination.

That branching factor would be enormous.

Instead, search for a relatively small collection of useful visible surfaces.

Prioritize candidates that:

- make major progress toward the destination
- skip obstacles
- skip elevation changes
- reach another floor
- reach the end of a corridor
- reach large open platforms
- connect otherwise distant navigation regions
- create a useful position for another teleport
- are close to the destination

Candidates should be ranked before being added to the search.

Keep only the strongest candidates.

For example, from hundreds of visible blocks, perhaps only the best 5-20 should become actual graph edges.

Exact limits should be configurable.

---

# 9. Teleports Can Be Setup Actions

A teleport should not be judged only by how much closer it gets to the final destination.

Some teleports are valuable because they create a better position for the next action.

Example:

```text
Targeted teleport sideways onto platform
→ directional teleport
→ directional teleport
→ destination
```

The first teleport may barely reduce straight-line distance to the destination, but it opens a much faster route.

Therefore, never use rules such as:

```text
Reject teleport if distanceToGoal does not decrease enough
```

Distance-to-goal should only be one heuristic component.

---

# 10. Search Algorithm

Use a fast heuristic search such as **Weighted A\*** for the macro planner.

Standard A* may spend too much time proving that a path is mathematically optimal.

Perfect optimality is not required.

A very good path found in 100 ms is considerably more useful than a theoretically optimal path found in 5 seconds.

Conceptually:

```text
f(n) = g(n) + weight * h(n)
```

Where:

```text
g(n)
```

is estimated execution time already spent.

```text
h(n)
```

is estimated minimum remaining traversal time.

Use a weight above `1.0` to make the search more aggressive.

Make this configurable.

---

# 11. Time-Bounded Search

The planner should be capable of returning the best route it currently knows instead of searching indefinitely.

Suggested behaviour:

### Stage 1

Find any good valid route as quickly as possible.

### Stage 2

If additional planning time remains, continue searching for improvements.

### Stage 3

Return the best route found when the planning budget expires.

This makes planning time predictable.

Possible budgets could eventually be something like:

```text
Fast mode:       ~50-150 ms
Normal mode:     ~200-500 ms
Deep mode:       ~1000-2000 ms
```

These are not mandatory values. Benchmark the actual implementation.

The important part is that planning should be explicitly time bounded.

---

# 12. Heuristic

The heuristic needs to understand that teleportation exists.

A normal Euclidean or Manhattan walking-distance heuristic will badly misjudge long routes.

Estimate something closer to:

```text
remainingTime =
    theoreticalFastTravelTime(distance)
    + approximateVerticalPenalty
```

For example, assume that sufficiently open distance could potentially be crossed using repeated teleports.

The heuristic does not need to know exactly how the player will travel.

It only needs to provide a fast optimistic estimate.

Avoid expensive raycasts inside the heuristic itself.

---

# 13. Search State

A search node should contain more than position.

Recommended information:

```text
position
approximate facing direction
previous action type
possibly current navigation region
```

Facing direction is useful because a route requiring a 180° turn should have a slightly different cost from one continuing straight ahead.

Do not store exact mouse rotation unless necessary.

Quantized heading buckets are probably enough for planning.

For example:

```text
8 or 16 horizontal heading buckets
```

Vertical facing can be represented separately if required.

---

# 14. Aggressive Pruning

Hybrid movement will create a large number of possible edges, so aggressively remove bad candidates.

Examples:

Reject or heavily deprioritize:

- unsafe landings
- nearly identical destination states
- teleport destinations dominated by another destination
- candidates going substantially backwards without a clear benefit
- redundant teleport targets on the same flat surface
- extremely small directional improvements
- states already reached considerably faster
- repeated oscillation between two regions

Use spatial bucketing.

If several search states end up extremely close together with similar heading and one has a substantially lower cost, discard the others.

---

# 15. Lazy Validation

Expensive checks should occur as late as possible.

Candidate generation can initially use cheap tests.

Then perform expensive geometry/raycast/path validation only when the candidate looks promising.

Example:

```text
Generate candidate
↓
Cheap bounds test
↓
Cheap safety test
↓
Cost/ranking check
↓
Only then perform expensive validation
```

This is especially important for targeted teleport candidates.

---

# 16. Route Smoothing

After finding a route, run a fast optimization pass before execution.

Attempt transformations such as:

```text
walk → walk
```

into one larger walk segment.

```text
walk → teleport → walk
```

into:

```text
teleport → walk
```

if the initial walk was unnecessary.

Try replacing multiple intermediate nodes with a direct teleport.

Try replacing a complicated walking section with a targeted teleport if one exists.

Try replacing:

```text
walk → directional teleport
```

with an earlier directional teleport.

The search does not need to create a perfectly clean route if a cheap post-processing step can improve it.

---

# 17. Human-Like Movement

"Human-like" should mean deliberate and plausible, not artificially slow.

The pathfinder should still move as quickly as possible.

Prefer:

- long continuous movement
- purposeful camera rotations
- consecutive teleports
- cutting corners where safe
- walking naturally through tight spaces
- teleporting across large open distances
- targeted teleports for meaningful skips

Avoid:

- camera flicking between unrelated targets
- needless teleport attempts
- 1-block walking segments between every action
- repeatedly stopping
- constant replanning while nothing has changed
- standing extremely close to walls before teleporting
- unnatural zig-zagging

---

# 18. Prefer Walking in Tight Spaces

Teleporting should become less attractive in cramped geometry.

This should mostly emerge through:

- risk penalties
- landing clearance
- setup time
- rotation cost

But an additional small tight-space penalty for teleport actions is acceptable.

Examples where walking will often be better:

- narrow doorway
- small room
- around a nearby corner
- destination only several blocks away
- complicated obstacle where lining up a teleport wastes time

---

# 19. Prefer Directional Teleporting in Long Open Routes

The directional teleport should dominate when travelling through clear open areas.

The planner should recognize situations similar to:

```text
============================== destination
player →
```

and quickly produce repeated teleports.

This should be one of the primary benchmark scenarios.

A long straight path must not become an unnecessarily complicated series of intermediate waypoints.

---

# 20. Prefer Targeted Teleporting for Major Skips

The targeted teleport should be particularly useful for:

- moving onto higher platforms
- moving down safely
- bypassing stairs
- bypassing walls where a valid target is visible
- crossing large open rooms
- skipping winding walking paths
- reaching distant navigation regions

Do not overuse it for tiny improvements where walking or directional teleporting is faster.

---

# 21. Execution and Replanning

Do not assume the calculated route will execute perfectly.

After important actions, compare the actual player position with the expected position.

Especially after:

- directional teleport
- targeted teleport
- jumps
- complicated walking sections

If the difference is within a small tolerance, continue the existing route.

If it is significant, replan from the new actual position.

Do not throw away the entire plan because of tiny positional differences.

---

# 22. Partial Replanning

Where practical, preserve valid future portions of the current route.

For example:

```text
Expected teleport landing:
(100, 70, 100)

Actual:
(100.4, 70, 100.3)
```

The existing route probably remains valid.

Do not perform an expensive full replan.

If the player lands several blocks away or in another navigation region, then perform a new plan.

---

# 23. Detect Execution Failure

Each action should know whether it succeeded.

Possible action states:

```text
PENDING
EXECUTING
SUCCESS
FAILED
```

Failures should include useful reasons where possible.

Examples:

```text
LANDING_BLOCKED
TARGET_NOT_VISIBLE
TELEPORT_DID_NOT_TRIGGER
MOVEMENT_BLOCKED
POSITION_MISMATCH
TIMEOUT
```

The planner should be able to blacklist or temporarily penalize failed edges before replanning so it does not immediately attempt the exact same failed action repeatedly.

---

# 24. Cache Useful Information

Cache expensive world information where safe.

Potential caches:

- standable positions
- collision checks
- teleport-safe locations
- line-of-sight results
- navigation regions
- walking connectivity
- previously generated targeted teleport candidates

Invalidate cache entries when relevant chunks or blocks change.

Do not repeatedly perform identical collision/raycast calculations during one search.

---

# 25. Separate Planning From Execution

Keep these systems separate.

Suggested architecture:

```text
HybridPathfinder
    Finds routes.

WorldNavigation
    Answers geometry/navigation questions.

MovementCostModel
    Estimates action execution time and penalties.

WalkActionGenerator
    Creates useful walking transitions.

DirectionalTeleportGenerator
    Creates 12-block teleport transitions.

TargetTeleportGenerator
    Creates targeted teleport transitions.

RouteOptimizer
    Cleans and simplifies completed routes.

PathExecutor
    Executes the route.

PathMonitor
    Detects deviations and failures.
```

Exact class names are not important.

The separation is.

---

# 26. Action Interface

All route actions should expose a common interface conceptually similar to:

```text
PathAction {
    start
    end

    estimatedTime
    estimatedRisk

    validate()
    execute()
    hasCompleted()
    hasFailed()
}
```

Possible implementations:

```text
WalkAction
DirectionalTeleportAction
TargetTeleportAction
```

This makes hybrid route execution significantly easier to reason about.

---

# 27. Debug Visualization

Add a debug renderer early in development.

Display:

- searched states
- selected path
- walking segments
- directional teleport segments
- targeted teleport segments
- rejected teleport targets if useful
- expected landing positions
- current action
- replanning events

Use different visual styles for each action type.

The exact colours do not matter.

Debug visualization is important because otherwise poor path decisions will be difficult to diagnose.

---

# 28. Planner Diagnostics

Add optional logging for why actions were chosen.

For each final route action, make it possible to inspect information such as:

```text
Type: DirectionalTeleport
Estimated time: 180 ms
Rotation: 7°
Risk penalty: 10 ms
Final cost: 190 ms
```

For targeted teleport:

```text
TargetedTeleport
Distance skipped: 43 blocks
Estimated time: 260 ms
Alternative walking estimate: 4100 ms
```

This will make balancing the cost model much easier.

---

# 29. Benchmark Scenarios

Create repeatable tests for the following situations.

## Test A — Long Straight Corridor

Expected:

```text
Directional teleport
Directional teleport
Directional teleport
...
Walk remainder
```

The planner should generate this almost immediately.

---

## Test B — Destination 5 Blocks Away

Expected:

```text
Walk
```

No unnecessary teleport.

---

## Test C — Tight Room and Doorway

Expected:

Walk naturally through the doorway before considering teleporting.

---

## Test D — Large Open Room

The pathfinder should compare directional teleport chains against a targeted teleport and select whichever has the lower execution time.

---

## Test E — Upper Platform

Destination is significantly above the player but a valid block is visible.

Expected:

Targeted teleport should often win over taking stairs or manually climbing.

---

## Test F — Obstacle Around Corner

The direct destination is blocked.

Expected:

Walk around enough of the obstruction to establish a useful route, then resume teleporting.

---

## Test G — Teleport Setup Route

The fastest route initially moves partly sideways or away from the destination in order to reach a location from which a large teleport becomes possible.

The planner must be capable of finding this.

---

## Test H — Failed Teleport

Artificially make a planned teleport fail.

Expected:

- detect failure
- avoid retry loop
- replan rapidly
- continue toward destination

---

## Test I — Mixed Route

Construct an environment where the clearly fastest route requires:

```text
walk
→ directional teleport
→ targeted teleport
→ walk
→ directional teleport
```

The planner should discover the mixed route without special-case scripting.

---

# 30. Performance Instrumentation

Measure at minimum:

```text
totalPlanningTime
statesExpanded
walkingCandidatesGenerated
directionalTeleportCandidatesGenerated
targetTeleportCandidatesGenerated
raycastsPerformed
collisionChecksPerformed
routesFound
routeEstimatedExecutionTime
replanCount
```

Planning performance must be treated as a first-class requirement.

Do not optimize blindly.

---

# 31. Recommended Development Order

Implement this incrementally.

## Phase 1 — Common Action System

Create the shared action representation and cost model.

Make walking and both teleport types representable as route actions.

---

## Phase 2 — Hybrid Search

Implement the Weighted A* search capable of mixing action types.

Initially use simple candidate generation.

Focus on proving that routes can contain arbitrary combinations of all three movement systems.

---

## Phase 3 — Directional Teleport Quality

Make long-distance directional teleport chaining work extremely well.

This is likely the easiest large performance gain.

Ensure straight/open travel does not become overcomplicated.

---

## Phase 4 — Targeted Teleport Candidate Generation

Build intelligent candidate discovery and aggressive ranking/pruning.

Avoid searching every possible target block.

---

## Phase 5 — Cost Calibration

Measure actual:

- walking speed
- rotation time
- teleport activation time
- teleport delay
- targeting overhead

Update the cost model using real measurements.

---

## Phase 6 — Route Optimization

Add the post-search simplification pass.

Remove unnecessary intermediate actions.

---

## Phase 7 — Execution Monitoring

Add:

- success detection
- failure detection
- positional tolerance
- replanning
- failed-edge penalties

---

## Phase 8 — Human-Like Improvements

Tune:

- rotation penalties
- action switching
- tight-space teleport penalties
- path smoothing
- continuation preferences

Do this after the pathfinder is already fast and functional.

Do not sacrifice major traversal speed merely to make movement look more human.

---

# 32. Important Rules

Do not solve movement selection with a giant collection of rules such as:

```text
if distance > 15 use teleport
if distance < 15 walk
if target is above player use targeted teleport
```

These will fail in complicated environments.

Use the shared cost/search system for the main decision-making.

Rules should primarily exist for:

- impossible actions
- safety validation
- candidate pruning
- obvious optimizations

The cost model should decide which valid action is best.

---

# 33. Main Success Criterion

The finished planner should behave as though it understands the purpose of each movement tool.

It should naturally learn through the cost model that:

```text
Walking
= precise, flexible and useful in constrained spaces

12-block directional teleport
= extremely effective for repeatedly covering clear distance

Targeted teleport
= powerful for large skips, elevation changes and bypassing geometry
```

The most important result is not simply finding a valid path.

It is consistently producing a route close to the fastest route a skilled human player would choose, while calculating it quickly enough that planning delay does not negate the speed gained from the route itself.

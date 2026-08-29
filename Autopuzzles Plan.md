# Auto Puzzles — Feature Plan

## Document control

- Feature name: `Auto Puzzles`
- Target project directory: `cgc-1.21.10`
- Verified configured Minecraft target: `26.1.2` (`minecraft_version` in the current `gradle.properties`; the directory name is historical)
- Verified configured build toolchain: Java/JVM `25`, Kotlin `2.4.0`
- Target category/tab: `Dungeons`
- Plan file: `Autopuzzles Plan.md`
- Plan revision: `0.21 — confirmed five-degree Terminator fan integrated; calibration UI removed`
- Last updated: `2026-08-27`
- Current phase: Phases 2–4 source implementation and the automated portions of Phases 5–6 are complete. The user visually confirmed a deterministic `5°` yaw offset on each side of the normal Terminator arrow; that value is hardcoded into Blaze/Creeper planning and final pre-shot validation. Terminator calibration tooling has been removed. Live Terminator puzzle shots and the remaining agreed Minecraft scenario matrices are the release gates
- Next implementation action: install the revision 0.21 JAR and exercise Terminator in the Blaze center-hit, side-hit, nearby-unintended-Blaze, and obstructed cases, then repeat target/other-endpoint safety checks in Creeper Beams. Report any chat stop reason or incorrect target update; then calibrate Ice Fill and run the remaining per-puzzle matrices

This document is the single source of truth for planning Auto Puzzles. Requirements, decisions, assumptions, questions, implementation steps, validation work, and later plan revisions belong here.

## Planning rules

1. Do not implement Auto Puzzles until all four in-scope puzzle specifications are captured, cross-puzzle decisions are resolved, and the user explicitly approves execution.
2. Work through the puzzles one at a time. After each discussion, update this document before moving to the next puzzle.
3. Do not infer unspecified puzzle behavior from another mod or from the puzzle's usual Minecraft mechanics. Local example mods may be inspected as implementation references only after the desired behavior is defined.
4. Mark unresolved behavior as `TBD`; do not silently turn it into an implementation decision.
5. Any automatic movement, aiming, inventory action, attack, use, or interaction must be technically possible for a human using an unmodified vanilla Minecraft client. In particular, the implementation must not depend on impossible reach, interaction through occluding blocks, teleport-like movement, impossible simultaneous inputs, or action rates the vanilla client cannot produce.
6. Re-check this plan immediately before implementation. If the requested behavior and the plan differ, update the plan and obtain confirmation before coding the changed scope.
7. Verify work in proportion to risk. Pure solving logic and state transitions should receive automated tests; world recognition and input behavior also require in-client validation.

## Status legend

- `CONFIRMED`: explicitly requested by the user or verified in the current codebase.
- `PROPOSED`: a planned design that must survive the completed requirements review.
- `TBD`: information is still required; implementation must not guess.
- `BLOCKED`: cannot safely proceed without a named decision, artifact, or environment condition.
- `DONE`: implemented and verified against the stated acceptance criteria.

## Confirmed product scope

- `CONFIRMED` Add one feature/module named `Auto Puzzles`.
- `CONFIRMED` Place it in the existing `Dungeons` category.
- `CONFIRMED` Present the following puzzle options as tabs across the top of the module, following TriggerBot's `Lever` and `Align` tab pattern and order:
  1. `Blaze`
  2. `Creeper Beams`
  3. `Ice Fill`
  4. `Waterboard`
- `CONFIRMED` Each puzzle will be specified separately and may have its own settings and runtime behavior.
- `CONFIRMED` The `Auto Puzzles` parent and all four puzzle tabs are disabled by default; every layer is explicitly opt-in and persists independently.
- `CONFIRMED` Do not add a `General` tab. The parent has no user-facing settings.
- `CONFIRMED` Implement and validate in this order: Blaze, Creeper Beams, Waterboard, Ice Fill.
- `CONFIRMED` Module description: `Automatically solves selected Dungeon puzzles with human-like movement, aiming, interactions, and configurable guidance.`
- `DONE` Planning-only restriction lifted by the user's explicit implementation approval on `2026-08-26`.

## Implementation and verification status

- `DONE` Implemented the `Auto Puzzles` parent, its four connected opt-in tabs, all confirmed settings, room dispatch, run-scoped lifecycle, and aggressive cleanup.
- `DONE` Implemented shared physical/generated input separation, one-controller ownership, sensitivity-correct aim adaptation, vanilla interaction/Etherwarp guards, room transforms, loaded-state checks, projectile first-hit checks, puzzle-state tracking, configurable line widths, and Waterboard billboard text.
- `DONE` Implemented Blaze, Creeper Beams, Waterboard, and Ice Fill solvers/controllers in the confirmed order. Ice Fill's deterministic path-to-action compilation is kept inside `IceFillController.kt` rather than a separate `IceFillPlanCompiler.kt`; the responsibility and emitted-path invariant are unchanged.
- `DONE` Bundled and schema/hash-tested the Creeper Beams and Waterboard data, the reused RSZ position snapshot, and the complete required RSZ BSD notice.
- `DONE` Hardcoded the user-confirmed deterministic Terminator model: one ordinary center direction plus left/right directions at exactly `-5°` and `+5°` yaw with the center pitch retained. There is no setting, persisted calibration artifact, randomized spread, or client-version rejection gate.
- `DONE` Integrated the model into `ProjectileAimPlanner`. It tests direct center candidates first, then camera directions compensated so either side arrow can reach the sampled target point. A candidate is safe only when at least one fan ray hits the intended entity/block first and no fan ray hits a supplied unintended Blaze/beam endpoint first; block occlusion is clipped independently for every ray.
- `DONE` Blaze now permits a hotbar Terminator and applies the three-ray model during initial planning, moving-target replanning, first-shot readiness, second-shot readiness, and retry planning. Creeper Beams applies it to every endpoint plan and final readiness check while treating every other selected endpoint as unintended.
- `DONE` Removed `/cgc termcal` from both command paths and help, deleted the preview/recorder and all global tick/world/render/packet hooks, and retained only the internal fan geometry used by real automation. Historical revisions 0.18–0.20 preserve the discarded calibration approaches for auditability.
- `DONE` Final automated result for revision 0.21 on `2026-08-27`: `71` tests, `0` failures, `0` errors, `0` skipped; focused hardcoded-angle, lane-compensation, fan-safety, and hidden-command tests plus a fresh `gradlew clean build` succeeded for the configured Minecraft `26.1.2` target.
- `DONE` Production artifact: `cgc-1.21.10/build/libs/cgc-1.0.0.jar`, SHA-256 `522A41017EA2ADB02A8A2215CC85B24CCDFBB98DFE2F43C819E34C18C44B17E9`. Clean JAR inspection confirmed `TerminatorFanGeometry`, lane/direction models, and the three-ray `ProjectileAimPlanner`; `TerminatorCalibrationRecorder` and every obsolete analyzer/report class are absent.
- `PENDING LIVE TERMINATOR VALIDATION` The angle itself is user-confirmed and automation eligibility is enabled, but no Blaze or Creeper Beams automation shot with the integrated three-ray planner has yet been observed. Center/side/obstruction/unintended-target behavior and all other named in-client matrices remain honestly unverified; automated compilation and fixtures do not close those gates.
- `DEFERRED AS APPROVED` Blaze reward-chest Etherwarp collection remains outside this first implementation; no controller automates a reward chest.

## Original discovery scope — now resolved below

The following were deliberately left open at revision 0.1 and are retained as planning history. Every implementation-affecting item is resolved by the confirmed puzzle specifications and cross-puzzle decisions below:

- Whether each puzzle fully completes itself, assists the player, or supports multiple modes.
- Whether activation is automatic on room entry, keybind-driven, hold-to-run, toggle-driven, or another explicit trigger.
- Whether a puzzle controls camera rotation, movement keys, attacks, block use, hotbar selection, or only renders guidance.
- Timing, delays, randomisation, retry policy, and response to lag.
- Default enabled state for the parent module and each puzzle tab.
- User-interruption and control-release policy.
- Visual overlays, sounds, chat messages, status HUD, or debug tools.
- Exact settings and their names, ranges, defaults, visibility rules, and config migration requirements.
- Puzzle completion/failure detection.
- Supported room variants, floors, modifiers, party conditions, and already-partially-solved states.
- Whether any local example implementation or data asset should be reused, adapted, replaced, or ignored.
- The order in which the four puzzles will be implemented.

## Verified codebase integration points

These findings describe the current project and should be revalidated if the surrounding code changes before implementation.

### Module and UI framework

- `CONFIRMED` Dungeon features derive from `CgcModule` and use `ModuleCategory.DUNGEONS`.
- `CONFIRMED` `TriggerBot.kt` creates one `SubModule` per top tab and registers each through `GroupSetting`. This is the requested UI pattern.
- `CONFIRMED` `CgcConfigScreen` renders a module's visible `GroupSetting` objects as the horizontal tabs at the top. Non-General tabs can be independently enabled/disabled through the configured toggle mouse button.
- `CONFIRMED` A normal click only selects a group when its submodule has at least one visible setting. Each Auto Puzzles tab therefore needs at least one meaningful visible setting, or the GUI behavior must deliberately be adjusted; a fake placeholder setting must not be added without approval.
- `CONFIRMED` Parent modules are registered manually in `CgcModules.bootstrap()`.
- `CONFIRMED` runtime hooks are dispatched to the enabled parent `CgcModule`, not automatically to its submodules. Auto Puzzles must explicitly route relevant events to enabled puzzle controllers and reset inactive controllers.
- `CONFIRMED` config persistence already stores the parent enabled state, each group/submodule enabled state, and each group's settings under the module ID. Stable group and setting names matter once released.

### Dungeon and room framework

- `CONFIRMED` `DungeonRoomScanner.currentRoom()` exposes a recognized room, its main world coordinates, its rotation, and local/world coordinate transforms.
- `CONFIRMED` the bundled room database already identifies these relevant room names:
  - Blaze: `Higher Blaze` and `Lower Blaze`
  - Creeper Beams: `Creeper Beams`
  - Ice Fill: `Ice Fill`
  - Waterboard UI option: room data currently calls the puzzle `Water Board`
- `CONFIRMED` every listed room is currently classified as `PUZZLE` and `1x1` in `assets/cgc/rooms.json`.
- `CONFIRMED` use recognized room identity plus the existing room transform instead of fixed global coordinates. Every controller must remain inactive/cancel safely when the room is unknown, incompletely scanned, unloaded, or has `UNKNOWN` rotation.

### Available runtime inputs

The parent module can use existing hooks for client tick start/end, world load, chat, action bar, block changes, world/HUD rendering, and packet receive/send/post-receive. Only the hooks justified by the completed puzzle specifications should be implemented.

### Local reference material

- The workspace contains example implementations and/or assets related to Blaze, Beams, Ice Fill, and Water Board.
- These are evidence sources, not requirements. Before adapting anything, check licensing/provenance as applicable, compatibility with the project's configured Minecraft `26.1.2` APIs/mappings, coordinate conventions, stale mechanics, and compliance with the vanilla-client-possibility constraint.
- Do not copy compiled classes or generated build outputs. Work only from appropriate source/resources and the user's confirmed behavior.

## Reference-source authority and completed audit

### Authority order

If two sources disagree, use this order until the user explicitly decides otherwise:

1. The user's confirmed Auto Puzzles requirements and later corrections.
2. The attached current Kotlin solver behavior and the exact solver-data snapshot audited below. The user reports these solvers are current and reliable.
3. Verified behavior and APIs in the target `cgc-1.21.10` project directory at its configured Minecraft `26.1.2` build target.
4. The older Java automation examples and their local resources. Blaze/Beams remain reference-only; Waterboard's proven control sequence and positions are an approved automation baseline, but its solution selection cannot override the current Kotlin solver/data.
5. New design proposals, which remain proposals until confirmed and tested.

The older Java controllers must never override a working solver result merely because they already contain automatic input code. Waterboard's explicit exception applies only to its confirmed positioning/action workflow, not to stale solution data or unsafe interaction techniques.

### Attachment inventory

All seven in-scope files under `Example puzzles Solvers` were read completely on `2026-08-25`:

| File | Role | Lines | SHA-256 at audit time |
|---|---|---:|---|
| `BlazeSolver.kt` | Current trusted solver | 102 | `E2A39AC443475BAD4A8954085D73E85E62B35490258970DF85A0CF96B1955EF0` |
| `CreeperBeamSolver.kt` | Current trusted solver | 98 | `F1873512D64CA23FB59825264670013E532C6AC7CBA4BD1F116BD7C73980E55F` |
| `IceFillSolver.kt` | Current trusted solver | 162 | `258A0B225A0579414C3D8A391D530EE897FE85F295D838BB04DF41421D2A7A6E` |
| `WaterBoardSolver.kt` | Current trusted solver | 208 | `094A867AC0845F442BB1A78AC11213995A6F45F545061489BAD84C1FBB15A2B2` |
| `Blaze.java` | Older semi-working automation | 1,003 | `BB88D7D769ADF75E91930487EA7E22AFD23A53ACD33DDCFA641F8485DF25B78B` |
| `Beams.java` | Older semi-working automation | 499 | `CBE7005CAF332E680EB0B817441A5328F816D725E51EDDD27C7FA4AE30635B61` |
| `WaterBoard.java` | Mechanically reliable automation reference whose camera/input presentation needs humanization | 1,349 | `98A362BA460C1EF8583B0AF8C8983385F1A169364BC5FA0F6B7275947A37274B` |

The four in-scope attached Kotlin files are byte-for-byte identical to their copies in the workspace's `NoammAddons-26.1.2` source tree. They rely on NoammAddons infrastructure and cannot simply be dropped into CGC; port the observations/algorithms and integrate them with CGC's lifecycle, room transforms, settings, rendering, and tests.

### External solver data

The attachment does not contain two JSON files loaded by the current solvers. Their authoritative source was traced through `DataDownloader.kt` to NoammAddons' `data` branch and inspected on `2026-08-25`:

- `creeperBeamSolutions.json`: 14 coordinate-pair entries.
- `waterSolutions.json`: four board variants; each contains all ten combinations of three closed gates (`012`, `013`, `014`, `023`, `024`, `034`, `123`, `124`, `134`, `234`); each solution maps lever identifiers to one or more click times in seconds.
- Data branch HEAD at audit time: `004c8a0022995da6e17a0a5da7e6452e576df7df`.
- Commit-pinned primary sources:
  - `https://raw.githubusercontent.com/Noamm9/NoammAddons/004c8a0022995da6e17a0a5da7e6452e576df7df/creeperBeamSolutions.json`
  - `https://raw.githubusercontent.com/Noamm9/NoammAddons/004c8a0022995da6e17a0a5da7e6452e576df7df/waterSolutions.json`

`CONFIRMED`: vendor reviewed snapshots into CGC resources rather than creating a runtime download. This gives offline/reproducible behavior and permits tests to pin the exact data. Both Creeper Beams and Waterboard use bundled data updated only through a later mod/resource release.

The older Water Board example also references local `WaterBoardSolutions.json` and `Waterboardpositions.json` resources elsewhere in the workspace. The older solutions have the same four-by-ten variant/subvariant shape; the positions file supplies room-local Etherwarp points for six material levers plus the water lever. The user confirms that every old Waterboard position still works and must be reused. Port them into CGC's transform convention and verify all four rotations without exposing calibration buttons.

License/provenance audit:

- The target CGC project and the audited NoammAddons source/data are distributed under `CC0-1.0`; the pinned Creeper/Water solution data can be vendored consistently with the target license.
- The local RSZ reference is `BSD-3-Clause`, copyright `2026 rice.who`. Because Auto Puzzles reuses its exact Waterboard positions and may adapt its proven mechanical workflow, retain the full RSZ BSD license text in the shipped Auto Puzzles resource directory and identify the derived position asset in the resource manifest. Do not strip the required source/binary notice.

### Audit conclusions by puzzle

#### Blaze sources

Current solver behavior to preserve unless the user requests a change:

- Activates in any room whose name contains `Blaze`.
- Treats `Lower Blaze` as reversed ordering; therefore Higher Blaze is ascending maximum health and Lower Blaze is descending maximum health.
- Finds named Blaze armor stands, parses the maximum-health value after `/`, associates each label with the Blaze entity beneath it, and re-sorts every tick.
- Highlights a configurable number of ordered Blaze entities and draws lines between ordered targets.

Mechanically trusted automation behavior from the older controller:

- Room-local stand points: Higher/low-to-high uses `(-6.5, 97, 0.5)`; Lower/high-to-low uses `(-6.5, 47, 1.5)` in that example's coordinate convention.
- Ten room-local physical aim slots are supplied for each vertical variant.
- It waits for the player to be almost exactly at the stand point; it does not navigate there despite the `Auto Position` setting name.
- It then performs a timed forward+sneak edge adjustment, selects/configures a bow slot, maps health-sorted labels back to physical height slots, eases the camera to fixed aim points, and sends two use-item actions per target.
- It requires all ten labels before shooting, so it cannot safely resume a partially completed puzzle.
- It does not verify an individual Blaze died before advancing, does not recover a missed/wrong shot, does not restore the previous hotbar slot, and does not define a robust manual-interruption policy.
- Fixed aim points, fixed edge-walk duration, direct rotation assignment, and two shots spaced only `80–100 ms` are hypotheses to replace or explicitly validate, not defaults.

#### Creeper Beams sources

Current solver behavior to preserve unless the user requests a change:

- Transforms the 14 external room-relative endpoint pairs into world positions for the recognized room rotation.
- Keeps candidate pairs whose two endpoints are either prismarine or sea lantern and renders a candidate while at least one endpoint is still a sea lantern.
- Assigns repeated display colors and optionally renders connecting lines.
- Resets when the room becomes complete/green.

Useful facts from the older controller, not approved behavior:

- Embeds a coordinate list corresponding to the same type of endpoint-pair data under a different local-coordinate convention.
- Uses room-local `(0.5, 75.0, 0.5)` as its automatic-start platform and otherwise does not navigate the player.
- The attached `Example puzzles Solvers/Beams.java` contains no Etherwarp, teleport, or post-start positioning sequence, and the duplicate RSZ copy is the same. The user confirmed that the earlier Etherwarp statement was mistaken: room-local `(0.5, 75.0, 0.5)` is the manually reached middle shooting block, so no route is missing and no Etherwarp behavior belongs in Creeper Beams.
- Selects up to four non-overlapping pairs whose endpoints are both sea lanterns, creates a deduplicated queue of endpoints, rotates to each block center, and uses a detected/configured bow.
- It fires once at every queued endpoint and resets immediately after the queue; it does not wait for projectile results or verify each beam transition before advancing.
- Its block-change completion bookkeeping excludes the current pair and can confuse overlap/completion. Do not port this logic as-is.
- The current external data contains a duplicate coordinate pair, so the automation layer must deliberately define deduplication and pairing identity while preserving correct solver output.

#### Ice Fill sources

Current solver behavior to preserve unless the user requests a change:

- Scans loaded ice/packed-ice floor cells with air above across the puzzle's vertical range.
- Separates the cells into horizontally connected clusters, orders them relative to four room-relative checkpoints, and solves the first three floors.
- For each floor, finds a Hamiltonian path that visits every cell exactly once from the checkpoint-nearest entrance to the checkpoint-nearest exit, using a low-onward-degree DFS heuristic.
- Renders the solved paths as world-space lines.

There is no attached older Ice Fill automation. Movement control, centering, turns, floor transitions, partial-state recovery, and action verification therefore require a fresh design on top of the trusted path.

#### Waterboard sources

Current solver behavior to preserve unless the user requests a change:

- Detects one of four board patterns from room-relative marker blocks.
- Detects exactly three of five closed colored gates and encodes their ordinal combination.
- Loads the corresponding timed lever sequence from current data.
- Uses the first water-lever interaction as time zero, tracks click counts for every lever, renders the next and following targets, and shows absolute/countdown click timing.
- Resets after the reward chest is interacted with only when no gate remains closed.

Useful facts from the older controller, not approved behavior:

- The user reports that this automation works correctly and that its deficiency is an inhuman-looking presentation. Preserve its proven sequencing and positions while replacing direct/unnatural camera behavior with sensitivity-correct mouse motion and tightening input safety.
- Supplies stable pattern detection, all seven room-local lever positions, approach points, candidate Etherwarp landing points, and a fall-rescue point; these positions are confirmed for reuse.
- Flattens the timed solution into ordered steps and derives the expected powered state after each toggle, allowing it to skip already-satisfied steps and wait for block-state confirmation.
- Treats all zero-time non-water toggles as preparation and, through enum ordering, opens water after them; this avoids pretending simultaneous clicks are possible.
- Can pre-aim, select a click slot, Etherwarp between lever areas, retry an unconfirmed toggle, and attempt an Etherwarp rescue after falling.
- It can fabricate a `BlockHitResult` when the crosshair did not actually hit the lever. That fallback must not be ported because distance alone does not establish vanilla-valid line of sight/targeting.
- It does not consistently restore the previous hotbar slot or preserve manual sneak state through every exit path, and its broad fallback room scan/grace interval can retain stale state too long.

### Existing CGC primitives relevant to later implementation

- `DungeonRoomScanner` already provides recognized room identity, rotation, signature, and local/world transforms.
- `VanillaMouseMotion` already converts desired rotations to sensitivity-correct whole raw-mouse counts with a bounded angular speed; this is a stronger human-feasibility foundation than the older controllers' direct rotation assignment.
- `AutoCInputController` already owns/releases movement keys and can detect new physical movement input relative to an activation baseline.
- `ItemInteractionUtils` and existing dungeon modules demonstrate hotbar selection and ordinary client use/interact paths, but each use must still be checked for correct reach and raycast behavior.
- Reuse or extract these only after the puzzle contracts establish the exact ownership and cancellation semantics.

## High-level architecture for final review

The completed puzzle requirements support this modular architecture: four independent solvers/controllers with different recognition and control needs coordinated by one parent. Product defaults and user-facing copy are frozen below.

### Parent module

Create a single `AutoPuzzles` parent module with this planned contract:

- ID: `AutoPuzzles`
- Display name: `Auto Puzzles`
- Category: `ModuleCategory.DUNGEONS`
- Default parent enabled state: `false` (`CONFIRMED`)
- Description: `Automatically solves selected Dungeon puzzles with human-like movement, aiming, interactions, and configurable guidance.` (`CONFIRMED`)
- Registered groups, in fixed display order: Blaze, Creeper Beams, Ice Fill, Waterboard
- Default state for every registered puzzle submodule: `false` (`CONFIRMED`)
- Parent-level settings/`General` group: none (`CONFIRMED`)

Parent responsibilities:

- Own and register the four puzzle submodules.
- Receive only the runtime interfaces needed by confirmed designs.
- Obtain shared client/dungeon/room context once per relevant event where practical.
- Dispatch events only to the enabled puzzle whose activation conditions are satisfied.
- Prevent more than one puzzle controller from owning player input at once.
- Release all owned input and clear transient state on disable, submodule disable, room exit/change, world load/disconnect, player death or missing player/level, puzzle completion/failure, GUI/container interruption where relevant, and unexpected exceptions.
- Avoid embedding puzzle-specific algorithms in the coordinator.

### Puzzle controllers

Each top-level tab should be backed by an independently resettable `SubModule<AutoPuzzles>` and a dedicated controller/solver rather than four large nested implementations in the parent file.

Frozen package boundary (the concrete file manifest appears in the execution-ready design below):

```text
cgc/cgc/module/impl/dungeon/AutoPuzzles.kt
cgc/cgc/module/impl/dungeon/autopuzzles/
  AutoPuzzleContext.kt                 # only if shared context is justified
  AutoPuzzleController.kt              # only if a common lifecycle helps all solvers
  blaze/...
  creeperbeams/...
  icefill/...
  waterboard/...
```

Do not create abstractions merely for symmetry. Extract a shared interface or utility only when at least two confirmed puzzle designs need the same invariant and the abstraction makes cleanup/testing safer.

### Candidate controller lifecycle

Each puzzle may use a subset of this state model; exact states and transitions must be tailored after its specification:

```text
INACTIVE
  -> RECOGNIZING
  -> READY
  -> ACTING
  -> VERIFYING
  -> COMPLETE

Any active state
  -> RECOVERING (if a bounded retry is valid)
  -> ABORTED (release control and require a safe reactivation condition)
  -> INACTIVE (room/module/world reset)
```

Required properties of any final state machine:

- One explicit owner for every simulated key/button/rotation action.
- Idempotent cleanup; calling reset multiple times must be harmless.
- No stale plan or target may survive a room signature/rotation change.
- Observed server/world state, not elapsed time alone, should confirm actions whenever possible.
- A bounded timeout/retry count for any wait that could otherwise hang forever.
- No action until required chunks/entities/blocks are loaded and the target is valid.
- A player's manual input must be handled according to an explicitly agreed policy.

### Solver/controller separation

Where a puzzle has nontrivial search or path logic, separate:

1. `Observation`: immutable representation derived from loaded client-visible state.
2. `Solver`: pure/deterministic logic that turns an observation into a solution or reports why none is valid.
3. `Plan`: ordered, inspectable actions in room-relative terms.
4. `Controller`: validates the next action against current world state and performs vanilla-possible input.
5. `Verifier`: confirms progress/completion and detects divergence before advancing.

This separation is optional for genuinely simple puzzles but strongly preferred for algorithms that can be unit-tested without Minecraft running.

## Execution-ready technical design

This section records the approved technical design and its implemented responsibilities. The user supplied explicit execution approval on `2026-08-26`; revision 0.18 reconciles the design with the resulting source layout, the new calibration recorder, and remaining live gates.

### Frozen integration surface

- Add `AutoPuzzles()` to `CgcModules.bootstrap()` with parent ID `AutoPuzzles`, display name `Auto Puzzles`, `ModuleCategory.DUNGEONS`, the confirmed description, and `defaultEnabled = false`.
- Register exactly four `GroupSetting` objects in display order `Blaze`, `Creeper Beams`, `Ice Fill`, `Waterboard`. Each wraps a `SubModule<AutoPuzzles>` with `defaultEnabled = false`.
- Register no parent-level setting. `CgcModule` will consequently omit its automatic `General` group.
- Treat the module ID, group names, and every setting name already frozen in the four settings tables as persistence keys. This is a new module, so no legacy config migration is required; tests must nevertheless pin these names and all first-launch defaults.
- Store ordinary module/group/settings state in CGC's existing `config/cgc/modules/AutoPuzzles.json` path. Store Ice Fill's captured coordinates separately as described below because `ButtonSetting` intentionally does not serialize a value.

### Parent runtime hooks

`AutoPuzzles` will implement only these existing hooks:

| Hook | Purpose |
|---|---|
| `ClientTickStartModule` | Consume new physical-input events before this feature emits another action; cancel/release an active controller promptly; service any one-tick owned-key releases. |
| `ClientTickModule` | After `DungeonState.tick()` and `DungeonRoomScanner.tick()`, build one room/run context, detect room-signature/run transitions, and tick only the enabled controller matching the recognized current room. |
| `BlockChangeModule` | Feed immutable old/new block observations to the eligible controller for Beams endpoint results, Ice Fill interference/reset observations, and Waterboard lever/gate acknowledgement. |
| `WorldRenderExtractModule` | Read immutable render snapshots and enqueue only the enabled in-room visuals through `CgcRenderer3D`. The attached `EnderPearlTrajectory.kt` confirms this is the project's normal line-extraction pattern. |
| `WorldLoadModule` | Release generated input and invalidate targets, timeouts, routes, observations, and render snapshots even when the parent is disabled. Run-scoped lock markers are not interpreted as a new run merely because of a reconnect/world-load event. |

No `ChatMessageModule`, action-bar, HUD, packet-send/receive, packet-post-receive, or `WorldRenderStartModule` hook is required on the `AutoPuzzles` parent itself. `DungeonState` already receives dungeon-start chat and player-list data globally; abnormal output can use `ChatUtils`, and Blaze removal is observed from the live entity set. The retired Terminator calibration experiment leaves no global tick, world, render, or packet hook behind.

### Concrete source-file manifest

Existing files to change:

| File | Planned change |
|---|---|
| `cgc/cgc/client/CgcCommandRegistry.kt` | Keep the retired `/cgc termcal` branch absent from both the Fabric client-command tree and CGC's prefixed local dispatcher. |
| `cgc/cgc/module/CgcModules.kt` | Register `AutoPuzzles()` in the Dungeons module sequence and route its required lifecycle hooks; no Terminator calibration hook remains. |
| `cgc/cgc/dungeon/DungeonState.kt` | Add a monotonic dungeon-run sequence and tick/reset the minimal puzzle-state tracker. A qualifying Mort start message advances the sequence once per real start; a world load clears transient state but does not masquerade as a confirmed new run. |
| `cgc/cgc/module/impl/dungeon/SimonSaysAimController.kt` | Add reusable timing-profile inputs needed by Auto Puzzles while preserving AutoSS's current default math and tests byte-for-behavior. Ice Fill may use `baseline duration / Turn Speed multiplier` without the current Simon-specific `0.35x` floor or `260 ms` cap; the vanilla velocity/acceleration ceilings remain enforced. |
| `cgc/cgc/runtime/CgcRenderer3D.kt` | Add a finite positive per-`lineList` width with the existing `3.0f` default for old callers, and add one queued billboard world-text primitive for Waterboard. Preserve queue clearing and depth-separated batching. |
| `cgc.mixins.json` | Register the two narrowly scoped physical-input observation mixins below. |

New shared files:

| File | Responsibility |
|---|---|
| `cgc/cgc/module/impl/dungeon/AutoPuzzles.kt` | Parent module, four groups, hook routing, exception containment, room/run transition dispatch, and final cleanup authority. It contains no puzzle solver algorithm. |
| `cgc/cgc/module/impl/dungeon/autopuzzles/AutoPuzzleController.kt` | Small lifecycle/event contract used by all four controllers: tick, block observation, render snapshot, transient stop, run change, and world reset. Default no-op methods are allowed only for hooks a controller does not use. |
| `.../autopuzzles/AutoPuzzleContext.kt` | Immutable tick context containing monotonic time, current run sequence/start state, current `ScannedDungeonRoom`/signature, and the required client/player/level references. Context construction rejects unknown room rotation. |
| `.../autopuzzles/AutoPuzzleRoomCoordinates.kt` | Canonical fixed-origin coordinate conversions around `ScannedDungeonRoom.toWorldBlock`/`toRelativeBlock`; converts legacy Waterboard `0..30` X/Z data by subtracting `15` exactly once. Rotation round trips receive fixtures for all four rotations. |
| `.../autopuzzles/AutoPuzzleInputOwner.kt` | One exclusive lease for generated camera, movement, sneak, use, and hotbar work. A second controller cannot act while a lease exists. Cleanup is idempotent and restores every owned key mapping to its current physical state rather than suppressing a newly pressed user key. |
| `.../autopuzzles/AutoPuzzleInputSession.kt` | Per-attempt snapshot/policy over raw physical input: keyboard presses/repeats, mouse buttons, and scroll cancel immediately; physical mouse movement cancels after exactly `1000 ms`; generated input never increments physical sequences. Waterboard's entry state additionally waits until tracked held keys/buttons are released. |
| `.../autopuzzles/AutoPuzzleAimController.kt` | Adapter over the generalized `SimonSaysAimController` plus `VanillaMouseMotion`; exposes normal target, chained retarget, pre-aim, Etherwarp, and slow Ice Fill turn profiles without direct yaw/pitch assignment. |
| `.../autopuzzles/AutoPuzzleItems.kt` | Left-to-right hotbar lookup/selection. A shortbow must be a vanilla bow whose lore contains formatting-insensitive `Shortbow: Instantly shoots!`; Terminator is additionally identified by SkyBlock ID `TERMINATOR`; Etherwarp uses `ItemUtils.isEtherwarp`; Dungeon Breaker prefers verified SkyBlock ID `DUNGEONBREAKER` and retains the confirmed formatting-insensitive exact display-name `Dungeon Breaker` fallback. |
| `.../autopuzzles/AutoPuzzleInteraction.kt` | Ordinary use-item/use-on-block paths plus live reach/LOS/crosshair checks. Controller states serialize calls; this helper never fabricates a hit result, teleports the player directly, mutates position, or sends an impossible packet-only action. |
| `.../autopuzzles/ProjectileAimPlanner.kt` | Shared visible-point search and first-collision ray evaluation for Blaze/Creeper. Ordinary shortbows use one ray; Terminator evaluates the hardcoded center/left/right fan and rejects any candidate whose first relevant collision includes an unintended target. |
| `.../autopuzzles/TerminatorFanGeometry.kt` | Internal deterministic fan model: center follows ordinary bow aim and the two side directions use equal `5°` yaw offsets. Also supplies camera compensation for deliberate side-arrow hits. |
| `cgc/cgc/dungeon/DungeonPuzzleStateTracker.kt` | Minimal English Hypixel tab-list parser for `Higher Or Lower`, `Creeper Beams`, `Ice Fill`, and `Water Board`, with `UNKNOWN`, `DISCOVERED`, `GREEN`, and `FAILED` states keyed to the current run. It reads the complete `ClientPacketListener.getListedOnlinePlayers()` snapshot rather than only entries from one partial update packet. This is not a dungeon-map implementation. |
| `cgc/cgc/runtime/PhysicalInputTracker.kt` | Thread-safe event sequences and held-key/button sets only; contains no Auto Puzzles policy. It records actual input callbacks, so `KeyMapping.isDown` changes and `LocalPlayer.turn` calls generated by modules cannot self-cancel. |
| `cgc/cgc/mixin/KeyboardHandlerMixin.java` | Observe `KeyboardHandler.keyPress` press/repeat/release events and forward them to `PhysicalInputTracker`; never consume or alter the event. |
| `cgc/cgc/mixin/MouseHandlerMixin.java` | Observe `MouseHandler.onButton`, `onMove`, and `onScroll`; forward physical events without cancelling or changing vanilla handling. |

Puzzle-specific files:

| Package/file | Responsibility |
|---|---|
| `.../autopuzzles/blaze/BlazeSubModule.kt` | Frozen Blaze settings/defaults and controller ownership. |
| `.../blaze/BlazeSolver.kt` | Bounded room-entity observation, label parsing/association, Higher/Lower ordering, partial-state support, and immutable render/action solution. |
| `.../blaze/BlazeController.kt` | Start-latch, center-facing sneak-to-edge, burst/removal/retry state machine, outside-removal detection, and chat-only terminal reasons. |
| `.../autopuzzles/creeperbeams/CreeperBeamsSubModule.kt` | Frozen Beams settings/defaults and controller ownership. |
| `.../creeperbeams/CreeperBeamData.kt` | Load, schema-check, and pin the 14 source pairs; normalize only the one exact duplicate while preserving first occurrence. |
| `.../creeperbeams/CreeperBeamSolver.kt` | Transform/predicate/disjoint-pair logic, untouched-board eligibility, stable solver order, and immutable render plan. |
| `.../creeperbeams/CreeperBeamsController.kt` | Ordered endpoint aim/fire, pair result classification, bounded retry ceilings, room-green wait, and same-run lockout. |
| `.../autopuzzles/waterboard/WaterboardSubModule.kt` | Frozen Waterboard settings/defaults and controller ownership. |
| `.../waterboard/WaterboardData.kt` | Load/validate all four patterns, ten gate keys per pattern, repeated timestamps, lever geometry, old Etherwarp support-block positions, and deterministic legacy tie order. |
| `.../waterboard/WaterboardSolver.kt` | Pure pattern/gate recognition and flattening into the exact scheduled action list. |
| `.../waterboard/WaterboardRoutePlanner.kt` | Prove whether a short straight adjustment has an unobstructed swept player box and continuous four-corner support; `WaterboardController` otherwise chooses Etherwarp and owns the exactly-one-key/frozen-camera execution. |
| `.../waterboard/WaterboardController.kt` | Entry ownership wait, pre-water serial actions, time-zero schedule, early item selection, acknowledgement/retry, warp/fall policy, one countdown, completion, and same-run lockout. |
| `.../autopuzzles/icefill/IceFillSubModule.kt` | Frozen Ice Fill settings/buttons/defaults and calibration feedback. |
| `.../icefill/IceFillCalibrationStore.kt` | Versioned load/validate/save of the fall Y and three canonical non-ice start/support records at `config/cgc/autopuzzles/ice_fill_calibration.json`; a rejected capture never overwrites valid data. |
| `.../icefill/IceFillSolver.kt` | Main-thread immutable cell snapshot plus pure three-cluster/checkpoint/Hamiltonian solving. If solving is moved off-thread, only immutable data crosses threads and stale room-generation results are discarded. |
| `.../icefill/IceFillPlanCompiler.kt` | `IMPLEMENTATION ADJUSTMENT`: no separate file was needed. `IceFillController.kt` deterministically consumes the unchanged emitted path into lateral runs, forward Etherwarps, centered turns, and two preflight-validated single-stair transitions. |
| `.../icefill/IceFillController.kt` | Activation/preflight, continuous crouch, traversal, deviation/interference detection, active-floor recovery/reset budget, room-green wait, and same-run lockout. |

File splitting may be mechanically adjusted during implementation only when responsibilities remain identical and this document is updated; puzzle algorithms must not be collapsed into the parent.

### Run identity, completion state, and lockout

1. Add `DungeonState.runSequence: Long`, initially `0`. Every non-duplicate qualifying Mort dungeon-start line increments it and resets puzzle tab states. The parser trims formatting and retains the pre-existing tolerant prefix match so harmless suffixes do not suppress a real start; a `30 s` duplicate window prevents retransmitted lines from manufacturing a run. `DungeonState.started` remains the eligibility flag.
2. A controller records the sequence on activation. Creeper Beams, terminal Ice Fill, and Waterboard lock that exact sequence after cancellation. Blaze records its step-off requirement against that sequence. Toggling a module/group, leaving/re-entering a room, or a transient world load cannot manufacture a new sequence.
3. A positively observed later Mort start sequence clears all prior-run latches/lockouts. A reconnect into an already-running dungeon without a newly observed start remains safely ineligible rather than risking a same-run restart.
4. `DungeonPuzzleStateTracker` strips formatting and parses the same symbols used by the trusted solver source: `✦` discovered/unopened, `✖` failed, and `✔` green. Blaze still completes from live Blaze removal; Waterboard still completes from its verified final programmed action. Creeper Beams and Ice Fill alone require their tab entry to become `GREEN` after local work finishes.
5. A `FAILED` tab transition is terminal. An absent/unknown tab line never counts as green. After all local work is done, the controller holds no keys/camera/use action and may passively wait for the authoritative line until room exit/world cleanup; this idle observation is intentionally not an action-holding timeout.
6. Unit fixtures prove parsing and run resets, but an in-client observation of both Creeper Beams and Ice Fill changing to `✔` is a release gate. If the current server/client does not expose this signal reliably, neither controller may claim successful completion; revise the plan before substituting map parsing.

### Physical input and exclusive control

The input tracker records events globally but changes no behavior without an active Auto Puzzles input session. Each activation follows this order:

1. Verify parent/group/run/room/player/world/GUI/item/board prerequisites and that no other Auto Puzzles lease exists.
2. Blaze, Creeper Beams, and Ice Fill do not activate while any tracked physical key or mouse button is held. Waterboard arms on room entry and explicitly waits for the complete held set to become empty.
3. Snapshot input event sequences and acquire the exclusive lease. The `1000 ms` mouse-motion grace starts at acquisition—not room detection or preflight waiting.
4. A later keyboard press/repeat, mouse-button press, or scroll event cancels on the next client-tick-start dispatch. A physical mouse-move event cancels only once elapsed time is `>= 1000 ms`. Releases update held sets but are not themselves cancellation events.
5. On cancellation, first invalidate every future action token/timeout, then clear aim, then release owned use/movement/sneak keys to their real physical states. Only after cleanup does the controller send its single chat reason and enter its puzzle-specific latch/lock state.
6. Parent disable, group disable, GUI/container opening, player death/missing player, room/signature change, unknown rotation, disconnect/world loss, and exceptions use the same idempotent cleanup. Run-scoped lock/re-arm state survives a simple toggle where the puzzle contract requires it.

### Vanilla-client-possible action mapping

| Requested action | Planned vanilla path and mandatory guard |
|---|---|
| Camera movement | Time-based desired rotation → sensitivity-correct whole raw counts → `LocalPlayer.turn`; no direct yaw/pitch assignment. A live target/raycast is revalidated before acting. |
| Hotbar switch | Select one real slot `0..8` and use the normal carried-item synchronization path already demonstrated by `ItemInteractionUtils`; never swap inventory slots or restore the former slot. |
| Movement/sneak | Own ordinary `KeyMapping` states. Blaze uses sneak+forward only for its edge approach; Ice Fill uses crouch plus one permitted horizontal key; Waterboard uses exactly one horizontal key after a stationary turn. No simultaneous incompatible actions. |
| Shortbow shot | With the selected lore-confirmed shortbow ready and the projectile ray plan still safe, emit one ordinary use action. Burst timing schedules two serial actions, never simultaneous packets. |
| Lever click | Require the fresh player raycast to hit the exact lever voxel within vanilla reach and LOS, then call the ordinary client use-on-block path with that exact live hit and swing. |
| Etherwarp | Select a real supported item, hold ordinary sneak, aim at an actually visible loaded solid support block within ability range, press ordinary use once, then verify the observed landing. No direct position change or assumed/occluded target. |
| Completion/failure | Observe live entities, block states, player position, schedule state, and/or the authoritative tab line. Elapsed time may bound a wait but cannot fabricate success. |

The shared interaction component serializes generated hotbar/camera/key/use work. Rendering and pure solving may coexist with an action, but two use actions, two camera owners, or movement plus a forbidden in-motion turn cannot.

### Targeting and confirmed Terminator fan model

- Candidate aim points come from the live visible portion of the intended entity AABB or block voxel shape, biased toward the center and searched outward in deterministic rings. Block collision and the relevant unintended Blaze/endpoint volumes are clipped from the live eye position.
- Ordinary shortbows require their single predicted first collision to be the intended target. Terminator requires at least one of three calibrated rays to hit the intended target first and none to hit a different active target first.
- The user visually calibrated the deterministic Terminator fan at exactly `5°` of yaw on each side of its ordinary center arrow. `TerminatorFanGeometry.FAN_ANGLE_DEGREES` hardcodes that value; side rotations preserve the center direction's vertical component. There is no user-facing calibration state or version gate.
- Replanning is allowed while aiming if a Blaze moves or geometry changes, but the new target must be reached through another smooth mouse path. A stale safe candidate cannot authorize a shot.

#### Implemented Terminator targeting workflow

1. A lore-confirmed item with SkyBlock ID `TERMINATOR` is selected through the same leftmost-hotbar rule as every other shortbow. Blaze and Creeper Beams no longer reject it before acquiring input.
2. For each sampled point on the intended entity AABB or endpoint voxel, planning evaluates center-aligned camera aim first. If that is unsafe, it evaluates camera headings compensated by `+5°` or `-5°` so the corresponding left or right arrow—not the center arrow—intersects that same sampled point.
3. From each candidate camera heading, the planner derives all three projectile directions: center unchanged, left `-5°`, right `+5°`. Each direction receives an independent live block clip and relevant entity/block first-hit classification.
4. The fan is accepted only if at least one ray's first relevant collision is the intended target and no ray's first relevant collision is an unintended target. A wall or unrelated block on a non-hitting side ray is harmless because it stops that arrow; another active Blaze or any other selected Creeper endpoint is not harmless and rejects the complete camera candidate.
5. Blaze repeats this complete test while acquiring aim, immediately before the first shot, immediately before the second shot, and when replanning a retry. Creeper Beams repeats it for every endpoint plan and immediately before firing, with all seven other selected endpoints supplied as unintended targets.
6. The fixed model performs no extra Minecraft action. Camera movement and each use remain ordinary sensitivity-correct turns and serial vanilla-client actions under the existing cancellation/ownership rules.
7. `/cgc termcal` no longer exists, no preview renders, and no calibration hook runs. The next gate is direct live validation of correct center/side hits and conservative refusal around occlusion or another target.

### Waterboard movement decision rule

Waterboard uses its proven transformed landing/approach data rather than free pathfinding:

1. Cross-platform, cross-side, and every water-lever positioning action uses Etherwarp. No ordinary segment is permitted in the unsupported opening region in front of the water lever.
2. A material-lever adjustment may walk only when the current supported position and that lever's confirmed approach point form one straight segment of at most `3.0` horizontal blocks, the swept player AABB is unobstructed, and support exists beneath the full sampled footprint. Otherwise use the confirmed Etherwarp landing.
3. For an allowed walk, evaluate forward/back/left/right camera frames and select the single key requiring the smallest stationary camera turn. Finish that turn, freeze yaw and pitch, hold only that key, and release it using live position/velocity feedback before any new turn.
4. Failure to prove either a safe walk or a valid Etherwarp route cancels before movement. The `3.0`-block internal ceiling is a safety interpretation of the user-delegated “short straight line,” covers the confirmed landing-to-approach adjustments, is not a user setting, and must be reduced—not expanded silently—if live collision/hole validation demands it.

### Rendering changes and exact visual defaults

- Extend only `lineList` with a width parameter; existing callers such as `EnderPearlTrajectory` retain the current `3.0f` default. Reject non-finite/non-positive widths at enqueue time. Auto Puzzles settings already constrain values to `0.5–5.0`.
- Add queued world billboard text with text, position, scale, color, and depth mode. Waterboard uses it for one no-depth current-action label only: `CLICK` for a due/zero-time action, otherwise remaining seconds to one decimal place. There are no later-action labels.
- Renderer queues remain frame-local and are cleared even if one task fails. Controllers publish immutable render snapshots so extraction never iterates a mutating solver collection.
- Freeze these configurable first-launch RGB defaults (alpha `255`), using CGC's existing friendly palette where the trusted solver did not require pure colors:

| Setting(s) | Default `Colour(r,g,b)` |
|---|---|
| Blaze first box / first line | `(85,255,85)` |
| Blaze second box | `(255,213,79)` |
| Blaze other box | `(255,85,85)` |
| Blaze second line | `(255,170,0)` |
| Blaze start waypoint | `(0,255,255)` |
| Beams pair 1/2/3/4 | `(255,0,0)`, `(0,255,0)`, `(0,0,255)`, `(255,255,0)` |
| Beams start waypoint | `(0,255,255)` |
| Ice Fill path | `(0,255,0)` |
| Waterboard countdown | `(85,255,85)` |

### Resources and provenance manifest

Add these bundled resources; there is no runtime download:

| Target resource | Exact source/provenance | Validation |
|---|---|---|
| `assets/cgc/autopuzzles/creeperBeamSolutions.json` | `Noamm9/NoammAddons` data commit `004c8a0022995da6e17a0a5da7e6452e576df7df`, root `creeperBeamSolutions.json` | Exactly 14 two-endpoint integer entries; exactly one duplicate; normalization yields 13 entries without reordering. |
| `assets/cgc/autopuzzles/waterSolutions.json` | Same pinned data commit, root `waterSolutions.json` | Exactly patterns `0..3`; each has keys `012,013,014,023,024,034,123,124,134,234`; only known lever keys, finite nonnegative times, and preserved repeated actions/ties. |
| `assets/cgc/autopuzzles/waterboardPositions.json` | Exact local source `Example Skyblock mods/RSZ/src/main/resources/assets/rsz/dungeons/Waterboardpositions.json`, audited SHA-256 `B813BFBCFE907F315F79452D128C8933185417046CA6ACE2069E4A9333BCFCDB` | Seven known lever keys and finite landing points. Loader combines them with the frozen lever/approach geometry in this document after the one-time legacy `-15 X/Z` normalization. |
| `assets/cgc/autopuzzles/LICENSE-RSZ.txt` | Exact local `Example Skyblock mods/RSZ/LICENSE` (`BSD-3-Clause`, copyright `2026 rice.who`) | Full unmodified license text is packaged with the derived Waterboard position/workflow material. |

Pin the upstream commit and source hash in comments/tests or an adjacent resource manifest. Resource parse/schema failures make the affected puzzle ineligible before it owns input and produce a precise development/runtime reason; they never fall back to network data or the stale old solutions.

### Test-file and validation manifest

Add focused Kotlin tests mirroring the source packages. At minimum:

| Test file/group | Required proof |
|---|---|
| `DungeonPuzzleStateTrackerTest.kt`, `DungeonRunSequenceTest.kt` | Formatted/unformatted full-tab fixtures, all four names/symbols, absent/stale lines, failed/green transitions, duplicate Mort guard, new-run reset, reconnect behavior. |
| `PhysicalInputTrackerTest.kt`, `AutoPuzzleInputSessionTest.kt`, `AutoPuzzleInputOwnerTest.kt` | Held baselines, new key/button/scroll events, exact `999/1000 ms` mouse boundary, generated exclusion, exclusive lease, physical-state restoration, and idempotent cleanup. |
| `AutoPuzzleRoomCoordinatesTest.kt` | Fixed and continuous coordinate round trips for all four rotations, legacy Waterboard `-15` normalization exactly once, Blaze/Beams start points, Ice checkpoints, and all Waterboard points. |
| `AutoPuzzleAimProfileTest.kt`, existing `SimonSaysAimControllerTest.kt`, `VanillaMouseMotionTest.kt` | AutoSS regression parity, all puzzle speed bounds, Ice `baseline/speed` timing without snaps, raw-count quantization, and readiness/error limits. |
| `ProjectileAimPlannerTest.kt` | Single/three-ray first hits, calibrated side-arrow solution, unintended target/obstruction rejection, moving targets, and no-candidate failure. |
| `TerminatorFanGeometryTest.kt` | Hardcoded `5°` side offsets, pitch preservation, lane order/symmetry, and camera compensation for deliberate side-arrow hits. |
| `CgcCommandRegistryTest.kt` | Both the Fabric `/cgc` tree and CGC's prefixed local tree omit the retired `termcal` branch. |
| `CgcRenderer3DTaskTest.kt` | Default/custom width validation, old-caller compatibility, one billboard label task, immutable copies, and queue clearing. Actual billboard orientation/depth also needs in-client visual proof. |
| Blaze test group | Label parsing/association/order, partial boards, start latch/edge feedback, burst spacing/removal/retry, outside removal, render order/colors, and every cleanup reason. |
| Creeper Beams test group | Pinned resource schema/hash, duplicate normalization, transforms, disjoint selection, endpoint ordering, both/one/neither results, ceilings, room-green, lockout, and rendering. |
| Waterboard test group | All 40 schedules, pattern/gate recognition, legacy ties/zero-time order, transformed geometry, route selection/hole rejection, items, raycasts, timing/ack/retry, warp/fall bounds, current countdown, and lockout. |
| Ice Fill test group | Three-floor scanning and exact Hamiltonian paths, action compilation, calibration persistence, fresh/packed states, turns/stairs, deviation, every recovery branch/budget/reset, room-green, and rendering. |

Run from `cgc-1.21.10` after each implementation slice:

```powershell
.\gradlew.bat compileKotlin compileTestKotlin
.\gradlew.bat test
.\gradlew.bat build
```

Use focused `--tests` filters while iterating, but the complete `test` and `build` tasks are required before handoff. Also run `git diff --check` from the workspace root and perform the per-puzzle live matrices already listed. Automated tests cannot replace the in-client gates for room transforms, physical event mixins, Terminator spread, real reach/raycast, Waterboard hole/landings, Ice calibration/recovery, or tab-list green transitions.

## Shared cross-puzzle requirements — reconciled

These topics were resolved while specifying the individual puzzles and are retained as the final consistency checklist.

### Activation and eligibility

- [x] Parent and all four puzzle defaults are explicitly `false`.
- [x] Each puzzle's room/start trigger is frozen in its controller specification.
- [x] Fresh/partial-board eligibility is frozen independently for each puzzle.
- [x] Re-entry/restart behavior is frozen: Blaze uses step-off/re-entry; the other automatic cancellation policies use the stated same-run locks; explicit Ice fall recovery is the sole exception.
- [x] Dungeon/run/recognized-room/known-rotation gating is mandatory; no debug/force activation mode is planned.
- [x] Delayed/ambiguous/unknown room recognition produces no automatic input and invalidates stale work.
- [x] Unattributed party-member changes use each puzzle's confirmed abort/interference rule.

### Input ownership and human feasibility

- [x] Per-puzzle camera/movement/sneak/use/hotbar ownership is explicit; no controller owns inventory swaps.
- [x] Manual input always cancels under the shared immediate-input/`1000 ms` mouse-grace policy; there is no pause or blending.
- [x] All camera motion uses the confirmed AutoSS-derived, sensitivity-correct bounded profiles.
- [x] Every shot/click/use delay and retry ceiling is frozen in the relevant puzzle section.
- [x] Live reach, LOS, collision, movement physics, and item prerequisites are mandatory before actions.
- [x] The shared action owner serializes incompatible generated actions; only vanilla-possible concurrency is allowed.
- [x] Cleanup restores/releases generated controls idempotently; confirmed hotbar selections intentionally remain selected.

### Dynamic-world safety

- [x] Loaded player/world/chunk/target checks precede ownership and every action.
- [x] Every interaction and movement segment is revalidated immediately before emission.
- [x] Lag, changed blocks/entities, party interference, partial state, and fall behavior are puzzle-specifically frozen.
- [x] Every action-holding wait/retry has a finite budget; passive room-green observation owns no input.
- [x] Abort/retry/re-solve rules are explicit per puzzle.
- [x] Room signature/rotation changes invalidate all pending work before another action.

### Configuration and UX

- [x] Settings, types, ranges, steps, units, and defaults are frozen in each puzzle table.
- [x] No conditional setting visibility is currently required; every tab contains genuine visible settings.
- [x] Feedback is frozen: requested world guidance, chat-only abnormal reasons, and silent success.
- [x] No normal-user debug overlay is planned; calibration feedback and development/test diagnostics remain scoped.
- [x] No global `General` group or parent-level setting is wanted.
- [x] Module/group/setting names are frozen persistence keys; a future rename requires an explicit migration plan.

### Performance

- Avoid full-room scans every tick when block-change events or cached observations suffice.
- Put explicit bounds on entity/block searches and pathfinding/search spaces.
- Cache only by a stable room/board signature and invalidate on relevant changes.
- Keep rendering extraction separate from mutable solver state as required by the current rendering API.
- Profile only after behavior is correct, but add counters/logging if a solver can become expensive.

## Per-puzzle specification template

Every puzzle section must answer the following before its implementation status can become `READY FOR IMPLEMENTATION`:

1. Player-visible goal and exact scope.
2. Supported room variants and starting/partially completed states.
3. Activation trigger and all eligibility gates.
4. Deactivation, cancellation, and reset triggers.
5. World observations used to identify state.
6. Solver/route algorithm, including what makes a solution valid.
7. Exact vanilla-possible actions the controller performs.
8. Movement/aiming/click timing and manual-input policy.
9. Progress, completion, and failure verification.
10. Divergence, timeout, retry, and re-solve behavior.
11. Settings with names, defaults, ranges, units, and visibility.
12. Visual/audio/status feedback.
13. Debug/recording needs for gathering unknown coordinates or mechanics.
14. Unit-test fixtures and edge cases.
15. In-client validation matrix.
16. Concrete acceptance criteria.
17. Explicit exclusions/non-goals.

## Requirements interview sequence

The interview order starts with Blaze, then proceeds through the other four puzzles unless the user changes it. Questions are stored here before answers so nothing is lost between discussions.

### Common decision C-001 — solver versus automation ownership

Resolution: `CONFIRMED`.

- Each puzzle tab is one connected feature with one enabled state.
- When a puzzle tab is enabled, its solver observations/visuals are active and its automatic controller is armed whenever that puzzle's activation conditions are met.
- When the tab is disabled, its solver visuals and automatic behavior are both off and all owned state/input is cleared.
- Do not add a separate `Automation`, `Solver Only`, or mode toggle.
- Each tab may expose visual customization such as colors and line thickness, alongside its behavioral/timing settings.

## Puzzle 1 — Blaze

- Implementation status: `SOURCE COMPLETE — automated checks pass; ordinary shortbows await the live matrix; the Terminator recorder is ready but capture/collision integration remain gated`
- UI group label: `Blaze` (`CONFIRMED`)
- Known room identities: `Higher Blaze`, `Lower Blaze` (`CONFIRMED` from room data)
- Trusted solver baseline: recognize Blaze labels/entities continuously; sort Higher ascending by maximum HP and Lower descending; render ordered targets/lines as configured
- Desired automatic behavior: wait for the player to reach the correct room-relative start block, automatically select a hotbar shortbow, dynamically aim and fire two shots at each live Blaze in solver order, wait for confirmed Blaze removal, and continue until no Blazes remain
- Modes/options: no behavior modes and no separate solver/automation toggle; both room variants share one settings set
- Activation and reset: room recognition plus standing on the correct start block arms the run; after any cancellation the player must step off and back onto the block to re-arm
- Observation and variant detection: retain the trusted solver's live label-to-Blaze association and room-name/core recognition
- Solver ordering/target rules: Higher ascending by maximum HP; Lower descending; dynamically re-sort remaining entities; abort on unexpected outside interference
- Aiming, movement, weapon/item, and attack behavior: player navigates to start; controller selects the first valid hotbar shortbow from left to right without later restoring the old slot, turns toward the room center, sneaks forward to the block edge, aims at live entities, accounts for all three Terminator arrows, and sends a two-shot shortbow burst
- Timing and human-feasibility limits: start from AutoSS's sensitivity-correct aim system; separate the two shots by a random `40–50 ms`; use a configurable removal timeout defaulting to `500 ms`; permit one revalidated retry burst
- Progress/completion/failure verification: the target Blaze entity must be removed before advancing; all Blazes removed completes the core solver
- Recovery and interruption behavior: physical keyboard or mouse-button input cancels immediately; physical mouse movement is ignored for the first second after activation and cancels thereafter; inability to find a safe/valid shot aborts; another player changing the Blaze sequence cancels; one failed retry burst aborts
- Settings/defaults: one shared settings set; frozen below
- Rendering/feedback/debug: outline the start block, draw 3D bounding-hitbox boxes around Blazes using three order-based colors, draw exactly two ordered relationship lines, and send abnormal-stop reasons only to chat
- Tests and acceptance criteria: frozen below
- Non-goals for the first Blaze implementation: navigating the player to the start block and the post-puzzle Etherwarp/chest-collection sequence

### Confirmed room-variant mapping

The two variants require no separate user settings. Detect the variant and apply its data automatically.

| Room | Known room cores/IDs | Order | Confirmed old room-local standing position |
|---|---|---|---|
| `Higher Blaze` | `-1752027278`, `-1477567187` | Lowest maximum HP to highest | `(-6.5, 97.0, 0.5)` |
| `Lower Blaze` | `-441650545`, `911747293` | Highest maximum HP to lowest | `(-6.5, 47.0, 1.5)` |

The position values are player-foot/standing coordinates in the older room transform convention, not yet CGC `BlockPos` values. During implementation, transform and validate them in all room rotations before deriving the block-under-player activation test. The user confirmed that both source positions are correct.

### Blaze answer record

| ID | Recorded answer | Status |
|---|---|---|
| BZ-001 | Do not navigate automatically. Always wait for the player to navigate to the room-specific starting block. | `CONFIRMED` |
| BZ-002 | Activate automatically by standing on that starting block. Higher and Lower have different start blocks. After a stopped run, stepping off and back on is required to restart. | `CONFIRMED` |
| BZ-003 | Higher uses low-to-high health and Lower uses high-to-low health. | `CONFIRMED` |
| BZ-004 | Support both rooms automatically with shared settings; only start position and firing order differ. | `CONFIRMED` |
| BZ-005 | Normally all ten Blazes are alive, but dynamically support a partially completed room if necessary. | `CONFIRMED` |
| BZ-006 | The old starting positions for both variants are correct. After activation, face toward the room center and sneak forward to the edge of the block before aiming. | `CONFIRMED` |
| BZ-007 | Always render a waypoint at the correct start position and remain idle until the player stands there. | `CONFIRMED` |
| BZ-008 | Support every shortbow. Terminator requires special aim because its three-arrow fan may require hitting the target with a side arrow. | `CONFIRMED`; exact safe-ray invariant is FBZ-005 |
| BZ-009 | Scan the entire hotbar left-to-right and select the first shortbow. If none is present, do not activate. Do not restore the previously selected slot afterward. | `CONFIRMED` |
| BZ-010 | Aim dynamically at each live Blaze. For Terminator, offset aim when necessary so its fan does not hit the wrong Blaze. | `CONFIRMED` |
| BZ-011 | From the correct start position every Blaze should be hittable; if no valid shot can be found, abort. | `CONFIRMED` |
| BZ-012 | Fire two shots `40–50 ms` apart. If the Blaze remains after `500 ms`, revalidate/re-aim and fire one more two-shot burst. If it remains after the second wait, abort. The `500 ms` value remains configurable as previously requested. | `CONFIRMED`; early-removal progression is FBZ-010 |
| BZ-013 | Advance only after the target Blaze entity is removed. | `CONFIRMED` |
| BZ-014 | Use AutoSS as the starting point for aim speed and motion behavior. | `CONFIRMED`; exact setting is frozen below |
| BZ-015 | Physical keyboard and mouse-button input cancel immediately. Physical mouse movement is ignored for one second after activation, then cancels. Module-generated mouse counts never count as player input. | `CONFIRMED` |
| BZ-016 | If another player changes the sequence, cancel entirely. Treat removal of the current target after this controller's burst as expected; abort if another Blaze disappears or the current target disappears before its burst. | `CONFIRMED observable rule` |
| BZ-017 | Core Blaze completes after all Blazes die. A later phase must perform an Etherwarp sequence to collect the chest, but that is deferred until the shooting implementation works. | `CONFIRMED and deferred` |
| BZ-018 | Draw 3D bounding-hitbox boxes—not entity glow—around ordered Blazes using configurable first, second, and remaining colors. Draw a green first-to-second line and orange second-to-third line. Highlight the start block with a block bounding box. Send every abnormal-stop reason to chat; do not send a completion message. | `CONFIRMED`; exact visual defaults/ranges are FBZ-011 |

### Blaze controller lifecycle

This is the frozen planned interpretation of the confirmed answers:

1. `OUTSIDE_ROOM`: do no scanning/rendering beyond normal cleanup.
2. `WAITING_FOR_START`: recognize the correct room variant, continuously maintain the trusted ordered Blaze list, scan/render the matching start waypoint, and perform no automatic input.
3. `ACTIVATING`: edge-trigger on the player standing on the correct block; require no canceling physical key/button input; snapshot the input baseline; require at least one valid shortbow; choose the first valid hotbar slot from left to right; select it; validate the current Blaze set; turn toward the room center; hold sneak+forward until the transformed block edge is reached and horizontal movement settles; release both keys.
4. `AIMING`: revalidate room, player position, intended entity, order, shortbow, and world state; calculate a vanilla-possible safe aim; drive the AutoSS-style aim path through sensitivity-correct mouse counts.
5. `FIRING_BURST`: when the selected aim is sufficiently settled and still valid, send exactly two ordinary shortbow use actions with a separately sampled `40–50 ms` interval.
6. `WAITING_FOR_REMOVAL`: hold no movement input; observe live entities for up to the configured removal timeout (default `500 ms`). Advance immediately when the intended Blaze is removed. If it remains after the first timeout, revalidate the target/order/aim and permit exactly one second burst. If it remains after the second timeout, abort.
7. `RETARGETING`: re-scan/re-sort remaining Blazes, detect unexpected removals, and chain into the next AutoSS-style aim plan.
8. `CORE_COMPLETE`: clear aim/input ownership and stop after all Blazes are gone. The chest route is a separately gated future extension.
9. `ABORTED`: immediately clear aim, release every owned input, leave the selected shortbow slot unchanged, render no active aim, report one precise reason in chat, and require the player to step off and back onto the start block before re-arming.

Every active state also aborts on room/world change, module/submodule disable, missing client/player/level, opened GUI, player death, qualifying physical user input, loss of the selected shortbow, or an exception. Leaving the trigger block during the confirmed sneak-to-edge step is intentional and must not itself abort the run.

### Dynamic Terminator aim requirement

For a Terminator, “the aimed arrow hits the intended Blaze” is not sufficient: neither side arrow may hit a different Blaze first. The planned implementation contract is:

- Identify the held item specifically as `TERMINATOR`; other lore-confirmed shortbows use a single center ray.
- Use the user-confirmed deterministic fan: the ordinary center ray plus symmetric `5°` left/right yaw offsets.
- Use the player's live eye position, loaded block collision, and live Blaze hit volumes to evaluate candidate yaw/pitch aim points around the intended target.
- Accept a candidate only when at least one of the three predicted arrow rays first intersects the intended Blaze and none first intersects any other live Blaze or an occluding block.
- Prefer center-arrow hits, then choose the smallest safe offset that lets a side arrow hit.
- Abort with a specific reason if no safe candidate exists from the confirmed start position.
- Revalidate the chosen ray set immediately before both shots because Blazes and the camera can move.

The safety invariant is `CONFIRMED`. The exact fan angle, ray origin, projectile collision details, and candidate-search tolerances remain implementation measurements that must be calibrated and fixture-tested rather than assumed.

### Shared shortbow recognition contract

To support all current and future shortbows, do not rely only on IDs containing `BOW`. Require a bow item whose lore contains the authoritative in-game shortbow marker `Shortbow: Instantly shoots!`, cache the result by SkyBlock ID, and special-case `TERMINATOR` only for its fan geometry. Fixture-test Terminator, Juju, at least one other shortbow, a conventional bow, and a misleading item ID/display name.

### AutoSS aim reuse contract

Adapt the existing components rather than the old Blaze controller's direct rotations:

- Reuse or generalize `SimonSaysAimController` for quintic, curved, chained retarget motion with bounded angular velocity/acceleration.
- Continue applying desired motion through `VanillaMouseMotion` so rotations use sensitivity-correct whole raw-mouse counts.
- Begin with an `Aim Speed` setting matching AutoSS: `0.5x–2.0x`, default `1.0x`, step `0.05x`.
- Use the normal/chained-retarget profiles for Blaze targets; no Simon-specific grid-move context should leak into Blaze.
- Recalculate the final live-entity aim during the plan when meaningful movement makes the selected hit unsafe; do not snap directly.
- Keep AutoSS's physical-screen/mouse-grab safety checks and exact actual-angle readiness checks.

### Blaze settings

These names/ranges are frozen for implementation and config-persistence tests.

| Setting | Type | Behavior/default | Status |
|---|---|---|---|
| `Aim Speed` | Number | `0.5x–2.0x`, default `1.0x`, step `0.05x` | `CONFIRMED` |
| `Blaze Removal Wait` | Number | Maximum post-burst removal timeout: `100–2000 ms`, default `500 ms`, step `50 ms` | `CONFIRMED` |
| `First Blaze Color` | Colour | Outline-box color for current first ordered Blaze; default green | `CONFIRMED` |
| `Second Blaze Color` | Colour | Outline-box color for current second ordered Blaze; default yellow | `CONFIRMED` |
| `Other Blaze Color` | Colour | Shared outline-box color for third and later Blazes; default red | `CONFIRMED` |
| `First Line Color` | Colour | First-to-second relationship line; default green | `CONFIRMED` |
| `Second Line Color` | Colour | Second-to-third relationship line; default orange | `CONFIRMED` |
| `Line Thickness` | Number | Shared thickness for exactly two lines: `0.5–5.0`, default `2.0`, step `0.5` | `CONFIRMED` |
| `Start Waypoint Color` | Colour | Outline color for the transformed starting block; default cyan | `CONFIRMED` |

Do not add a bow-slot setting: the confirmed behavior is automatic hotbar scanning.

### Resolved Blaze follow-ups

| ID | Resolution | Status |
|---|---|---|
| FBZ-001 | After activation, face the room center and sneak forward to the edge of the block before shooting. Use transformed position/velocity feedback, not the old fixed timer, to detect arrival. | `CONFIRMED behavior`; feedback method is an implementation safety refinement |
| FBZ-002 | After any stopped run, the player must step off and back onto the start block before it can restart. “Standing on the block” will be implemented as grounded feet over the calibrated transformed block; validate its exact local block during implementation. | `CONFIRMED re-arm`; explicit low-risk activation interpretation |
| FBZ-003 | Choose the first valid shortbow from the left of the hotbar. Do not restore the previous slot after completion or abort. | `CONFIRMED` |
| FBZ-004 | Separate shots by a random `40–50 ms`. Wait up to `500 ms` for removal, then fire exactly one revalidated retry burst; abort if the Blaze survives the second wait. Keep the removal wait configurable. | `CONFIRMED`; FBZ-010 decides early-removal timing |
| FBZ-005 | At least one Terminator arrow must hit the target and none may hit another Blaze; abort if no safe candidate exists. | `CONFIRMED` |
| FBZ-006 | Keyboard and mouse-button input cancel immediately. Ignore physical mouse movement only for the first second after activation; thereafter it cancels. Never mistake generated mouse counts for physical input. | `CONFIRMED` |
| FBZ-007 | Removal of the fired-at target during its post-burst window is expected. Any other Blaze disappearing, or the target disappearing before its burst, is treated as outside interference and cancels the run. | `CONFIRMED` |
| FBZ-008 | Use 3D bounding-hitbox boxes around Blaze entities and a bounding box around the start block; do not use entity glow. Keep exactly the two requested relationship lines. | `CONFIRMED`; defaults/ranges FBZ-011 |
| FBZ-009 | Send abnormal-stop reasons only to chat. Do not send a successful-completion message. | `CONFIRMED` |

### Final Blaze choices — resolved

| ID | Decision | Resolution |
|---|---|---|---|
| FBZ-010 | Advance immediately upon confirmed target removal; the configured removal wait is a maximum timeout, not a mandatory pause. | `CONFIRMED` |
| FBZ-011 | Use the proposed box/line/waypoint colors and ranges, `0.5–5.0` line thickness with default `2.0`, and `100–2000 ms` removal timeout with default `500 ms`. | `CONFIRMED` |

### Blaze acceptance criteria

The Blaze specification is execution-ready only while all of the following acceptance criteria remain true:

1. Enabling/disabling the Blaze tab jointly enables/disables solver observations, rendering, and automatic eligibility; disabling it releases all owned inputs immediately.
2. Higher and Lower Blaze are detected through the recognized room data with no user-facing variant setting. Their confirmed standing coordinates transform correctly for every supported room core and rotation.
3. While outside a Blaze room, no waypoint, Blaze box, relationship line, hotbar change, camera movement, movement key, or shot is produced.
4. Inside a Blaze room, the correct cyan start-block outline is rendered and no automatic input occurs until the player newly steps onto that block.
5. Activation requires the calibrated start block beneath grounded player feet. After an abort, remaining on the block cannot restart the controller; the player must step off and back on.
6. At activation, any physically held keyboard key or mouse button prevents/aborts the run. Physical mouse movement is ignored only for the first `1000 ms`, then cancels; generated mouse counts never self-cancel.
7. The first lore-confirmed shortbow from hotbar slots `0..8` is selected. With none, the controller performs no positioning/shooting and reports the reason once in chat. The previous slot is never restored automatically.
8. Positioning faces the transformed room center and uses ordinary sneak+forward input until the player reaches the start block's forward edge and horizontal motion settles. It does not use teleportation, packet-only movement, or a fixed-duration assumption; keys are released on every exit.
9. Higher targets remaining Blazes by ascending maximum HP; Lower uses descending maximum HP. A partially completed board is supported by re-sorting remaining live labeled Blaze entities.
10. First, second, and later ordered Blazes render green, yellow, and red configurable hitbox-outline boxes by default. Draw only first-to-second and second-to-third lines, using configurable green/orange defaults and the configured shared thickness. Do not use entity glow.
11. Normal shortbows aim dynamically at the live target. Terminator candidates are accepted only when at least one calibrated fan ray hits the intended target first and no fan ray hits any other Blaze or an occluding block first. No safe aim causes an abort before firing.
12. Aim paths use the AutoSS-derived quintic/chained controller through `VanillaMouseMotion`, obey actual-angle readiness, and never snap directly to a target.
13. Each attempt sends two ordinary use-item actions separated by a sampled `40–50 ms`. Aim/target safety is revalidated before both actions.
14. Target removal advances immediately. If still alive at the configured timeout, perform exactly one newly revalidated two-shot retry. If still alive after the second timeout, abort and report the reason.
15. Removal of the current post-burst target is expected. Removal of a different Blaze, or removal of the current target before its burst, is treated as outside interference and aborts.
16. Completion occurs silently when no valid Blaze remains. No completion chat/HUD message is emitted.
17. Every abnormal stop emits one concise reason in chat and does not repeatedly spam it. Routine cleanup after completion does not emit a failure.
18. World/room change, death, missing client state, opened GUI, module disable, selected-shortbow loss, physical user cancellation, invalid target, or exception clears aim state and every owned movement/use action.
19. The initial implementation stops after shooting. It must not attempt the deferred reward-chest Etherwarp route.

### Blaze verification plan

Automated tests should cover:

- Higher/Lower order, tied/invalid HP labels, partial sets, entity removal, and unexpected non-target removal.
- Every known Blaze core through every supported `DungeonRoomRotation`, including standing-position and room-center transforms.
- Shortbow lore recognition, left-to-right selection, Terminator identification, no-shortbow behavior, and no slot restoration.
- Single-ray and three-ray collision fixtures: center hit, safe left/right side hit, wrong-Blaze collision, block occlusion, no safe candidate, and movement invalidating a candidate before the second shot.
- Deterministic fake-clock transitions for `40–50 ms` spacing, early removal, first `500 ms` timeout, one retry, second timeout abort, and configurable boundary values.
- Start-block edge-trigger/re-arm, grounded eligibility, sneak-to-edge completion, velocity settling, and idempotent input cleanup.
- Physical keyboard/button cancellation, the exact `1000 ms` mouse grace boundary, physical-versus-generated mouse distinction, GUI/world/module cleanup, and one-shot chat reporting.
- Render-plan contents for zero/one/two/three-plus targets, configurable colors, exactly two possible relationship lines, thickness, and start-block box.

In-client validation must cover both room variants, all encountered room cores/rotations, Terminator plus at least two other shortbows, realistic latency, partial/external kills, player cancellation at every controller phase, failed retry, and leaving/reloading the room. The deferred chest route is excluded from this validation phase.

## Puzzle 2 — Creeper Beams

- Implementation status: `SOURCE COMPLETE — automated checks pass; in-client start/raycast/update/room-green validation pending; the Terminator recorder is ready but capture/collision integration remain gated`
- UI group label: `Creeper Beams` (`CONFIRMED`)
- Known room identity: `Creeper Beams` (`CONFIRMED` from room data)
- Known room cores/IDs: `1264110624`, `866773091`, `-1655263707` (`CONFIRMED` from room data)
- Trusted solver baseline: transform the audited 14-pair dataset, retain candidate pairs whose endpoints are prismarine/sea lantern, render candidates while at least one endpoint remains a sea lantern, and reset when the room becomes green
- Desired behavior: while enabled, preserve the trusted solver visuals and arm one automatic run. The player manually navigates to and stands on the room-relative middle start block. From that same position, solve four beam pairs with a hotbar shortbow, completing each pair in its dataset-defined first-endpoint/second-endpoint order and verifying both block updates before advancing.
- Modes/options: no separate solver/automation mode under common decision C-001; enabled means solver visuals plus automation are active/armed, and disabled means both are off.
- Activation and reset: always outline the transformed `(0.5, 75.0, 0.5)` middle block while enabled in the recognized room. Activate only when the grounded player's feet are over that manually reached block and all prechecks pass. There is no walk, edge adjustment, sneak, teleport, or Etherwarp phase. Any cancellation after an attempt activates locks Creeper Beams automation for the remainder of that dungeon run; stepping off/on, leaving/re-entering the room, or toggling the feature must not re-arm it. A genuinely new dungeon run clears the lockout. No-shortbow ineligibility before activation is not itself an active-attempt cancellation.
- Observation and beam/target representation: use `CreeperBeamSolver.kt` as the authority for pair coordinates, room-relative transforms, endpoint block types, and room-green completion. The untouched activation board must have sea lanterns at every selected endpoint. If any selected pair is already mixed prismarine/sea-lantern at activation, abort with a reason and retain solver visuals for manual completion.
- Pairing/solution algorithm: vendor and validate the audited 14-entry snapshot, normalize its one exact duplicate while preserving first occurrence order, and iterate the normalized dataset in order. Select only transformed pairs matching the trusted solver's block predicate, require exactly four unambiguous disjoint active pairs, and cancel rather than guess if the count, overlap, or board state is invalid. Process the four pairs in normalized solver/dataset order. Within every pair, process the stored first endpoint and then stored second endpoint with no reversal.
- Aiming and interaction behavior: do not generate movement. Use the AutoSS-derived aim controller and sensitivity-correct vanilla mouse motion to acquire a dynamically validated visible point near the center of each endpoint's block shape. Require ordinary line of sight, projectile geometry, and item readiness. For Terminator, validate the complete three-arrow fan: at least one ray must hit the intended endpoint and no ray may hit any unintended active endpoint first; abort if no safe aim exists.
- Weapon behavior: use the same contract as Blaze—recognize every lore-confirmed shortbow, scan the hotbar left-to-right, select the first match, do not restore the previous slot, and do not activate without a shortbow. Revalidate the held shortbow before every use action.
- Firing behavior: aim and fire one ordinary use action at the pair's first endpoint, then begin the human-feasible aim transition and fire one at its second endpoint as soon as that aim and the bow are ready; do not wait for the first block result. After the second shot, observe the pair as a unit for up to `500 ms`. Advance immediately if both endpoints become prismarine. If only one has changed when the `500 ms` window expires, cancel and leave the board to the player. If neither has changed, revalidate and retry the complete first-then-second pair.
- Retry limit: allow at most three total full-pair attempts, including the initial attempt, and at most `5 s` elapsed from the first shot of the initial attempt, whichever ceiling is reached first. Any endpoint update ends eligibility for a neither-updated retry. If both do not update before the applicable ceiling, cancel and lock out the run.
- Timing and human-feasibility limits: camera movement and use actions are serial, sensitivity-correct, and possible through ordinary vanilla input. “Immediately” means no artificial result wait between endpoints; it does not permit simultaneous shots, an instant camera snap, use before the bow is ready, or actions through occlusion. The `500 ms` result window is a maximum: both confirmed updates permit early progression, while one update waits through the remaining window for its partner before being classified as a partial failure.
- Progress/completion/failure verification: expected sea-lantern-to-prismarine transitions verify the active pair. After the fourth pair succeeds, stop firing and passively require the authoritative room-green state for normal completion. Do not send a completion message. Send exactly one chat-only reason for an abnormal stop.
- Recovery and interruption behavior: any out-of-sequence endpoint/pair change cancels and locks automation for that dungeon run instead of re-solving. Physical keyboard or mouse-button input cancels immediately. Physical mouse motion is ignored only during the first second after activation and cancels thereafter; generated mouse motion is excluded. Disabling the module during an active attempt also cancels and locks that run. Every exit releases owned inputs and invalidates scheduled aim, shot, timeout, and retry work idempotently. Solver visuals remain available while the puzzle is enabled even when automation is locked out.
- Settings/defaults: confirmed in the table below. Pair result timing and retry ceilings are fixed behavior for this initial specification, not extra settings.
- Rendering/feedback/debug: render a 3D outline box around both endpoints of each active pair and a same-color line between them. Use four independently configurable pair colors assigned stably in action order, a configurable cyan start-block outline, and configurable line thickness. Do not use glow. Completed pairs disappear according to the trusted solver predicate; the start block remains outlined while enabled in the room even after automation locks out.
- Tests and acceptance criteria: finalized below for later implementation.
- Non-goals: no automatic navigation, movement, edge adjustment, Etherwarp, other teleport, reward-chest collection, automatic partial-board recovery, separate render-only mode, or feature implementation during this planning phase.

### Creeper Beams source facts and resolved risks

- The current solver's audited dataset contains 14 entries, including one exact duplicate pair. Some candidate pairs can also share an endpoint in the full candidate list.
- The current solver accepts prismarine or sea lantern at both endpoints and renders a pair while at least one endpoint is still a sea lantern. This supports partially changed pairs visually.
- The older automation used room-local standing position `(0.5, 75.0, 0.5)`, waited for the player to reach it, selected at most four non-overlapping pairs whose endpoints were both sea lanterns, and fired at a deduplicated queue of their endpoints.
- The older controller did not navigate or adjust player position after activation.
- It fired the entire endpoint queue without waiting for each block result, then reset. Its prismarine block-change bookkeeping could misclassify the current/overlapping pair, so that progression logic is not trusted.
- The attached `Beams.java` has no Etherwarp code. Its only positioning behavior is waiting at `(0.5, 75.0, 0.5)`. The user corrected the earlier statement: this is the middle shooting block reached manually, and Creeper Beams must not Etherwarp.

### Creeper Beams controller

The controller is modeled as these mutually exclusive states:

1. `OUTSIDE_ROOM`: clear transient observations; retain a same-run cancellation lockout.
2. `WAITING_FOR_START`: render solver output and the start-block outline; do not generate input.
3. `PRECHECK`: snapshot the normalized solver-order result, require four untouched unambiguous disjoint pairs, and find the leftmost lore-confirmed shortbow.
4. `AIM_FIRST` / `FIRE_FIRST`: safely acquire and shoot the selected pair's first endpoint once.
5. `AIM_SECOND` / `FIRE_SECOND`: without waiting for the first result, safely acquire and shoot that pair's second endpoint once.
6. `VERIFY_PAIR`: wait up to `500 ms` and classify the result as both changed, neither changed, or exactly one changed.
7. `RETRY_PAIR`: only for neither changed and only below both ceilings; repeat from `AIM_FIRST` without reversing endpoint order.
8. `WAITING_FOR_ROOM_GREEN`: after all four pair results succeed, produce no more shots and require the authoritative room completion state.
9. `FINISHED`: stop generating input for the completed run.
10. `LOCKED_OUT_THIS_RUN`: reached by every active-attempt cancellation path and exited only by positive detection of a new dungeon run.

Every transition revalidates room identity, player/world availability, active-pair block states, line of sight, the held shortbow, retry ceilings, and controller ownership before sending another action. Leaving the room, unloading the world, disabling the module during an attempt, losing the shortbow, losing a safe aim solution, unexpected endpoint changes, timeout exhaustion, or physical cancellation stops pending work safely and enters the same-run lockout.

### Creeper Beams questionnaire

| ID | Decision/question | Proposed answer where useful | Answer |
|---|---|---|---|
| CB-001 | Is the old room-local standing position `(0.5, 75.0, 0.5)` the correct start block? Should the feature always render that block and activate only when the player stands on it, like Blaze? | Reuse and validate it in all three room cores/rotations; use start-block entry as the only trigger. | `CONFIRMED`: yes to the coordinate, rendering, and standing-only trigger. |
| CB-002 | After activation, should it shoot directly from that position, sneak to an edge, Etherwarp, or perform another positioning step? | Shoot from the manually reached middle start block because that matches the audited source. | `CONFIRMED/CORRECTED`: shoot directly from `(0.5, 75.0, 0.5)`; there is no Etherwarp or other automatic positioning. |
| CB-003 | After cancellation, must the player step off and back onto the start block to restart? | Use a stronger per-run lockout for this puzzle. | `CONFIRMED`: no restart of any kind after any cancellation in the same dungeon run; only a new run re-arms it. |
| CB-004 | Should weapon handling match Blaze: all lore-confirmed shortbows, select the first from the left, do not restore the old slot, and do not activate without one? | Reuse the tested shared shortbow detector/selection contract. | `CONFIRMED`: yes. |
| CB-005 | For Terminator, must all three predicted rays avoid every unintended active endpoint/obstacle, with one ray safely hitting only the intended endpoint; abort if no safe aim exists? | Reuse calibrated fan simulation, adjusted for block hit volumes. | `CONFIRMED`: yes; expected to be less constraining here than in Blaze. |
| CB-006 | Should the current solver be authoritative for which coordinate pairs are relevant, including mixed prismarine/sea-lantern partial pairs? | Preserve its observation result, then add stricter action validation without changing solver output. | `CONFIRMED`: yes; `CreeperBeamSolver.kt` is authoritative. |
| CB-007 | Is a normal board always four disjoint active pairs? May automation remove the exact duplicated dataset entry, reject overlapping candidate pairs, and require one unambiguous logical pairing before firing? | Normalize exact duplicates and abort ambiguity rather than guess between overlapping pairs. | `CONFIRMED`: yes to all. |
| CB-008 | In what order should pairs be solved, and should the two endpoints of one pair always be completed consecutively before starting another? Within a pair, should endpoint order follow the data, minimize camera movement, or use another rule? | Keep each pair consecutive and preserve dataset endpoint order. | `CONFIRMED`: normalized solver/dataset order among pairs; stored first endpoint then stored second endpoint within each pair. |
| CB-009 | For an endpoint that is already prismarine, should it be treated as complete so only its remaining sea-lantern partner is shot? | Abort an initially mixed board while retaining solver visuals. | `CONFIRMED`: initial prismarine is not expected; if observed, do not automate that board. |
| CB-010 | How many shots per endpoint and what spacing should be used? Should it wait for the endpoint's block-state transition before aiming at the next endpoint? | Fire the pair consecutively, then verify the pair as one unit. | `CONFIRMED`: one shot first, immediately aim and shoot second, then allow up to `500 ms`; both advance, exactly one cancels, and neither retries the full ordered pair subject to three total attempts or `5 s`, whichever occurs first. |
| CB-011 | What exactly confirms a pair and the whole puzzle: each endpoint becoming prismarine, another beam state/entity/particle, the room turning green, or a combination? | Endpoint state confirms local progress; room-green confirms final completion. | `CONFIRMED`: yes. |
| CB-012 | If another player changes an endpoint or the observed pair set unexpectedly, should automation re-solve or cancel entirely? | Cancel on an out-of-sequence change, matching Blaze's interference policy. | `CONFIRMED`: cancel. |
| CB-013 | Should block aiming use the AutoSS-derived aim controller and sensitivity-correct mouse counts? Aim at a dynamically validated visible point on the block shape or always its center? | Use AutoSS motion and choose the smallest safe visible point near center; require real line of sight and ordinary projectile geometry. | `CONFIRMED`: yes. |
| CB-014 | Apart from the already-confirmed whole-run lockout, should physical input cancellation match Blaze exactly: keys/buttons cancel immediately, mouse movement has a one-second activation grace, and generated input is excluded? If the module is disabled after activation, should that also count as a cancellation for the run? | Reuse Blaze's physical/generated input distinction; every active-attempt cancellation enters the whole-run lockout. | `CONFIRMED`: yes to all. |
| CB-015 | Which visuals should remain: colored bounding boxes around each pair's two endpoints, a same-color line connecting them, and the start-block box? How many pair colors should be configurable, and what line-thickness range/default should be used? | Four configurable pair colors for the expected four pairs, configurable line thickness `0.5–5.0` default `2.0`, and cyan start box. | `CONFIRMED`: yes. |
| CB-016 | What marks a normal stop, is any reward-chest collection in scope, and should abnormal stops be chat-only with no completion message like Blaze? | Stop on room-green/all pairs complete, omit chest collection, use chat-only failure reasons, and remain silent on success. | `CONFIRMED`: yes. |
| CB-017 | Should CGC bundle the audited 14-pair JSON snapshot, or download mutable solver data at runtime? | Bundle the reviewed snapshot and cover it with validation tests for reproducible/offline behavior. | `CONFIRMED`: yes. |

### Resolved Creeper Beams follow-ups

| ID | Resolution | Status |
|---|---|---|
| FCB-001 | The earlier Etherwarp statement was mistaken. The start coordinate is the manually reached middle shooting block; remove the entire Etherwarp requirement. | `CONFIRMED/CORRECTED` |
| FCB-002 | Process the four pairs in normalized solver/dataset order. | `CONFIRMED` |
| FCB-003 | Use a `500 ms` maximum pair-result window after the second shot. | `CONFIRMED` |
| FCB-004 | Permit three total full-pair attempts or `5 s` from the initial first shot, whichever ceiling occurs first. | `CONFIRMED` |
| FCB-005 | Abort if an initially mixed prismarine/sea-lantern pair is observed; keep rendering for manual completion. | `CONFIRMED` |
| FCB-006 | Give Creeper Beams an independent Aim Speed setting with the same range/default as Blaze. | `CONFIRMED` |

### Creeper Beams settings

| Setting | Type | Behavior/default | Status |
|---|---|---|---|
| `Aim Speed` | Number | `0.5x–2.0x`, default `1.0x`, step `0.05x` | `CONFIRMED` |
| `Pair 1 Color` | Colour | First solver-order pair's endpoint boxes and line; default red | `CONFIRMED`; preserves the trusted solver palette |
| `Pair 2 Color` | Colour | Second solver-order pair's endpoint boxes and line; default green | `CONFIRMED`; preserves the trusted solver palette |
| `Pair 3 Color` | Colour | Third solver-order pair's endpoint boxes and line; default blue | `CONFIRMED`; preserves the trusted solver palette |
| `Pair 4 Color` | Colour | Fourth solver-order pair's endpoint boxes and line; default yellow | `CONFIRMED`; preserves the trusted solver palette |
| `Line Thickness` | Number | All four pair lines: `0.5–5.0`, default `2.0`, step `0.5` | `CONFIRMED`; step reuses the shared visual increment |
| `Start Waypoint Color` | Colour | Outline color for the transformed middle start block; default cyan | `CONFIRMED` |

Do not add settings for a bow slot, pair order, pair-result window, retry count, retry duration, separate solver mode, line visibility, or Etherwarp.

### Creeper Beams acceptance criteria

The Creeper Beams specification is execution-ready only while all of the following acceptance criteria remain true:

1. Enabling Creeper Beams activates its trusted solver rendering and arms its automation as one connected feature; disabling it turns both off.
2. In each recognized core and rotation, the transformed room-local `(0.5, 75.0, 0.5)` block is outlined and is the only automatic-start trigger; the feature never navigates the player to it.
3. The manually reached start block is also the shooting position; the controller sends no movement, sneak, jump, Etherwarp, or other positioning input.
4. Standing on the start block begins at most one active controller attempt in a dungeon run. Any active-attempt cancellation prevents all automatic restart until a new run is positively detected.
5. Automation does not activate without four untouched, unambiguous, disjoint pairs and a lore-confirmed hotbar shortbow. No shortbow before activation produces no slot change, camera motion, shot, or cancellation.
6. The bundled dataset matches the audited 14-entry snapshot, its exact duplicate normalizes to its first occurrence, and all selected pairs transform correctly for every supported core/rotation.
7. Pair selection and execution preserve normalized solver/dataset order. Every pair is acted on consecutively in stored endpoint order: first endpoint once, a human-feasible AutoSS-derived turn, then second endpoint once, without waiting for the first result.
8. A Terminator shot is allowed only when its full predicted fan has a safe solution for the intended endpoint and no harmful ray reaches another active endpoint first.
9. After the second shot, both expected updates advance immediately. One update is allowed the remainder of the `500 ms` window, then cancels if still partial. Neither update permits only a full ordered-pair retry.
10. A pair receives no more than three total attempts and no retry after `5 s` from its initial first shot. Reaching either ceiling cancels and locks out.
11. The controller never reverses a pair, skips to another pair mid-attempt, or retries after either endpoint has updated.
12. An initially mixed board, an unexpected endpoint change, or an out-of-sequence pair-set change cancels and locks out rather than skipping or re-solving.
13. Local progress is accepted only from the expected endpoint block transitions; successful overall completion requires the authoritative room-green state after all four pairs.
14. Physical keys/buttons, post-grace physical mouse movement, module disable during an attempt, room/world loss, unsafe aim, lost shortbow, and retry exhaustion each cancel cleanly and lock the active run. Generated mouse counts never self-cancel.
15. Every stop path invalidates scheduled actions and prevents a late aim, shot, verification callback, or retry. No previous hotbar slot is restored.
16. Pair boxes/lines retain stable configured colors in action order, use the configured thickness, disappear as the solver marks pairs complete, and coexist with the cyan middle-block outline without entity glow.
17. A partial/abnormal stop sends exactly one chat reason and no success message; solver visuals stay usable while automation is locked. Normal room-green completion is silent and performs no chest action.
18. All generated camera, hotbar, and firing actions are serial and technically reproducible in a vanilla client.

### Creeper Beams verification plan

Automated tests must cover:

- The vendored snapshot's entry count/content, exact-duplicate normalization, stable first-occurrence order, all three known cores, and every supported `DungeonRoomRotation`.
- Fresh four-pair selection, count mismatch, overlapping candidates, initial mixed pairs, endpoint sharing, completed pairs, and deterministic solver/action/color order.
- Left-to-right lore-confirmed shortbow selection, no-shortbow ineligibility, held-item loss, no slot restoration, and ordinary item-readiness gating.
- Single-ray and Terminator-fan fixtures for center/offset hits, another endpoint first, block occlusion, and no safe candidate.
- Fake-clock boundaries immediately before/at/after `500 ms` and `5 s`, early dual updates, delayed second update, neither update, exactly-one update, three-attempt exhaustion, and prohibition on retry after either update.
- One-run activation/lockout, positive new-run reset, every physical-input boundary including exactly `1000 ms`, generated-versus-physical mouse input, disable/room/world cleanup, and one-shot chat reporting.
- Render-plan contents and stable colors for four/three/one/zero remaining pairs, configured thickness, completed-pair removal, the always-visible in-room start outline, and locked-out solver-only rendering.

In-client validation must cover all known room cores/rotations, the exact middle-block trigger and shooting reach, Terminator plus at least two other shortbows, realistic delayed block updates, all retry outcomes, outside interference, every active cancellation phase, same-run lockout, new-run reset, and room-green completion. Chest interaction and all positioning automation are explicitly excluded.

## Puzzle 3 — Ice Fill

- Implementation status: `SOURCE COMPLETE — automated solver/calibration checks pass; live calibration, traversal, recovery, and room-green validation pending`
- UI group label: `Ice Fill` (`CONFIRMED`; normalized capitalization from the user's `Ice fill` wording)
- Known room identity: `Ice Fill` (`CONFIRMED` from room data)
- Trusted solver baseline: detect three connected floors and compute/render a full entrance-to-exit Hamiltonian path for each
- Desired behavior: on a completely fresh three-floor board, activate once when the player stands on the calibrated non-ice launch tile immediately before floor one, hold crouch throughout, and follow the trusted solver's exact Hamiltonian route. Etherwarp onto the first ice block directly ahead, traverse long lateral portions with left/right strafe, Etherwarp every on-ice forward advance and onto rare turn cells, make slow human-like camera turns, and crouch-walk straight up each stone-brick stair into the next floor. If lag causes a fall below the calibrated Y threshold, Etherwarp back to the active floor's configured non-ice launch/recovery block, wait for the whole floor to regenerate, and retry that floor.
- Modes/options: no separate solver/automation mode under common decision C-001; enabled means solver visuals plus automatic eligibility. The first version also needs calibration buttons for a fall threshold and three floor start/recovery locations.
- Activation and reset: activate only when the grounded player's feet are over the calibrated non-ice Floor 1 Start block and the solver confirms that its first ice cell is exactly one horizontal block directly ahead in the derived camera frame. Initial eligibility requires all four calibration values, a leftmost hotbar Etherwarp-capable item, all three solver paths, and a completely fresh board with no packed ice. Packed ice during the initial check locks automation out for that dungeon run; only the controller's own recovery state may accept a later reset. One activation owns all three floors and both stair transitions. Interference/manual cancellation locks automation for the run; a detected below-threshold fall is an internal recoverable failure.
- Observation and floor/board representation: regular ice means unvisited and packed ice means already walked. If packed ice is present at initial eligibility evaluation, do not activate. During an active route, do not pause for each ice-to-packed-ice acknowledgement; advance from legal player-position/path progress while continuing to observe block states for interference, reset, and failure detection.
- Route/coverage algorithm: preserve the trusted solver's three floor clusters, checkpoint-derived entrances/exits, and exact emitted Hamiltonian cell order. Abort before generating movement unless exactly three complete valid paths exist. Do not shorten, smooth, recompute from a partial route, skip cells, or substitute another Hamiltonian solution.
- Movement behavior: hold generated sneak for the entire active traversal, including stairs, planned Etherwarps, and recovery, and release it on every terminal exit. Do not sprint, jump, or use backward movement. Execute lateral route runs using continuous camera-relative left/right strafe, with player position/velocity controlling segment endpoints. Every on-ice route edge classified as forward uses Etherwarp; ordinary crouch-forward is reserved for the two non-ice stair transitions. Etherwarp is the centering/speed-control action; do not add ordinary centering or corrective detours.
- Etherwarp behavior: scan the hotbar left-to-right using CGC's existing Etherwarp-item predicate, select the first match before activation, keep it selected throughout, never activate without one, and do not restore the prior slot. While already crouching, dynamically ray-trace the intended route block, aim through sensitivity-correct vanilla mouse input, and right-click once to Etherwarp onto its center. A route Etherwarp has no dedicated `500 ms` landing wait or same-tile use retry: subsequent position observations either continue the exact route, detect a non-fall deviation and terminally cancel, or detect a below-threshold fall and recover. Revalidate the held item, target, line of sight, range, and ordinary item readiness before every right-click.
- Rotation behavior: deterministically derive a room-aligned camera frame from the exact emitted path so its long runs map to left/right strafe and its connectors map to forward Etherwarps. When an unavoidable turn is required, stop horizontal movement, Etherwarp to the intended turn block's center, then use the AutoSS-derived sensitivity-correct motion system before resuming in the next derived frame. Turn Speed is `10%–80%` of normal AutoSS speed, default `40%`, step `1%`, with higher values turning faster. Instant rotation assignment is forbidden.
- Floor-transition behavior: at each floor exit, continue walking straight while crouched across the short non-ice connector and up its one stone-brick stair; the immediately following block begins the next Hamiltonian floor path. Do not jump or pause for a separate floor activation. Validate entry into the configured/solver-derived next-floor start before continuing its path.
- Timing, physics assumptions, and human-feasibility limits: generated strafe/forward keys, sneak, turns, hotbar selection, and right-clicks must be serial and technically reproducible on a vanilla client. Path execution uses player position and velocity to release keys at segment boundaries but never snaps position or camera. Do not wait for per-tile packed-ice acknowledgement or add a route-Etherwarp retry pause. A recovered floor normally regenerates as one unit after roughly `3 s`; wait up to `10 s` for every route cell on that floor to become regular ice.
- Progress/completion/failure verification: player position moving through the expected cell sequence is the live traversal cursor; ice-to-packed-ice is the puzzle's server acknowledgement but does not gate every next move. Reaching the next configured floor entrance confirms a stair transition. Room-green confirms final success. Successful completion is silent and performs no reward-chest action; abnormal terminal stops send one chat-only reason.
- Recovery and interruption behavior: another player entering/changing the board, physical cancellation, disabling during an active attempt, or any non-fall route deviation cancels and locks for the dungeon run. A fall is detected when player Y crosses below the one calibrated room-relative threshold: stop horizontal input, identify the active floor, dynamically ray-trace back/up to that floor's calibrated non-ice recovery support block, and Etherwarp onto it. If the recovery use does not return the player safely, it consumes recovery budget rather than generating an unbounded use loop. Once safe, wait until every route cell on only the failed floor is regular ice, re-solve/validate it, and retry from its saved start. Allow at most three fall recoveries per floor and `10 s` for each floor reset; exhaustion terminally cancels and locks.
- Settings/defaults: four persisted calibration actions/data values, a `10%–80%` Turn Speed setting, one green Path Color, and `0.5–5.0` Line Thickness are confirmed in the final table below.
- Rendering/feedback/debug: preserve only the current solver presentation—one Hamiltonian path line across the ice, using a configurable shared color (default green) and thickness (`0.5–5.0`, default `5.0`, step `0.5`). Do not add start, recovery, next-tile, per-floor, or execution overlays.
- Tests and acceptance criteria: finalized below for later implementation.
- Non-goals: sprinting, jumping within/on transitions, ordinary centering, route smoothing, per-tile acknowledgement stalls, partial-board activation, reward-chest collection, and implementation during this planning phase.

### Ice Fill source facts and resolved constraints

- The trusted solver scans absolute Y levels `68..73` within `22` blocks horizontally of the room center. It treats both ice and packed ice with air above as walkable foot-space cells.
- It separates those cells into horizontally connected clusters, orders the clusters by distance from the first checkpoint, and solves only the first three clusters/floors.
- Its transformed room-local checkpoints are `(0, 69, -8)`, `(0, 70, -3)`, `(0, 71, 4)`, and `(0, 71, 11)`. For floor `i`, it chooses the cell nearest checkpoint `i` as the entrance and the cell nearest checkpoint `i + 1` as the exit.
- Each floor path is Hamiltonian: start at its entrance, visit every detected cell exactly once through horizontal neighbors, and finish at its exit. The current solver renders these paths but does not track the player or tile transitions.
- The audited DFS orders candidate neighbors by their number of unvisited exits; it does not explicitly score or minimize camera turns. Any low-turn behavior is an emergent property of the board and deterministic neighbor order. IF-005 nevertheless confirms that automation must preserve the emitted path rather than introduce a new turn-optimized solver.
- Because ice and packed ice are combined into one graph, the solver does not itself decide whether a packed-ice cell represents normal progress, an initially partial board, or a state that automation must reject.
- The current visual uses one configurable green path color and hard-codes line thickness `5.0`; it has no next-tile, start-tile, or per-floor visual distinction.
- CGC already supports config-menu action buttons, but a `ButtonSetting` does not persist data by itself. The calibration design must therefore make each button capture into separate persisted, room-relative calibration data rather than store transient world coordinates in the button.
- There is no attached older Ice Fill automation. Start detection, vanilla-feasible movement on ice, centering, turning, floor transitions, progress acknowledgement, recovery, cancellation, and completion behavior must be specified rather than copied.

### Ice Fill controller

The frozen planned controller states are:

1. `OUTSIDE_ROOM`: clear transient solve/movement state; preserve any same-run terminal lockout.
2. `BOARD_PRECHECK`: solve all three floors and require zero initially packed route blocks, all persisted calibration values, and a leftmost eligible hotbar Etherwarp item; initial packed ice enters the same-run lockout.
3. `WAITING_FOR_START`: render the solver path and wait for the grounded player on the calibrated Floor 1 non-ice launch block without generating input.
4. `STARTING`: select the Etherwarp item, acquire input ownership, hold sneak, establish the route's initial camera frame, and start the physical-input grace clock.
5. `STRAFE_SEGMENT`: hold the required left/right key while position progresses through the exact consecutive route cells; release at its planned endpoint.
6. `ETHERWARP_ADVANCE`: stop horizontal input, dynamically ray-trace and aim at the next forward/turn route block, right-click once while sneaking, then let subsequent position/fall observations classify the result without an in-place use retry.
7. `SLOW_TURN`: while centered and stationary, perform the sensitivity-correct slow camera turn, then revalidate the next route segment.
8. `FLOOR_TRANSITION`: crouch-walk straight over the connector and up the stone-brick stair into the next floor entrance.
9. `RECOVER_FALL`: after crossing below the calibrated Y threshold, stop horizontal path input, aim back/up at the active floor's configured recovery block, and Etherwarp onto it within the three-per-floor recovery budget.
10. `WAIT_FOR_FLOOR_RESET`: remain safe at the recovery block for up to `10 s` until every active-floor route cell is regular ice again; then validate/re-solve and restart that floor.
11. `WAITING_FOR_ROOM_GREEN`: after floor three, release action input and require authoritative completion.
12. `FINISHED`: silent normal stop.
13. `LOCKED_OUT_THIS_RUN`: terminal non-fall cancellation; only a positively detected new dungeon run exits this state.

Fall detection must pre-empt every active movement/aim state. All exits must release crouch and horizontal keys, clear pending mouse/use work, and prevent a late Etherwarp. A fall-recovery attempt remains under controller ownership, so physical user input during recovery follows the shared cancellation rule and converts it to a terminal same-run lockout.

### Ice Fill questionnaire

| ID | Decision/question | Proposed answer where useful | Answer |
|---|---|---|---|
| IF-001 | Is the first floor's solver-derived entrance cell—the cell nearest transformed checkpoint `(0, 69, -8)`—the start block? Should it always be outlined and activate only when the player manually stands on it? | Clarify in plain terms whether activation is on the first ice tile or the non-ice block immediately before it. | `CONFIRMED/CORRECTED`: activate while standing on the calibrated first non-ice tile; the first solver ice block must be exactly one block ahead and is reached by Etherwarp. No start outline. |
| IF-002 | Once activated on floor one, should automation own the entire three-floor solve, including transitions, or should each floor wait for a separate manual start? | One activation should cover all three floors if transitions can be observed and performed safely. | `CONFIRMED`: one continuous three-floor attempt; walk straight up each stair into the next path. |
| IF-003 | May the controller gently center the player on the first route cell before advancing, or must the player arrive already centered? What position tolerance is acceptable? | Use Etherwarp, not ordinary centering movement. | `CONFIRMED/CORRECTED`: remain crouched and right-click an Etherwarp target block to land on its center whenever centering is needed. |
| IF-004 | Should route movement use normal walking, sprinting, sneaking, or a mixture? May it use forward plus strafing, and should it ever jump within a floor? | Crouch for the complete run; primarily strafe and use Etherwarp for most forward advances. | `CONFIRMED/CORRECTED`: hold crouch throughout, use left/right strafing and occasional forward, usually Etherwarp for an on-ice forward block; no sprint or jump. |
| IF-005 | Should the trusted current Hamiltonian route be followed exactly in its emitted cell order, with no movement-specific path shortening or alternate route selection? | Preserve the solver route exactly; automation only executes and validates it. | `CONFIRMED`: yes. |
| IF-006 | At straight segments and turns, should it target every tile center, only center before direction changes, or follow a smoothed line while ensuring every tile registers? | Use Etherwarp to center at the rare turn locations, then turn slowly. | `CONFIRMED/CORRECTED`: the route is mostly strafing; at rare turns, Etherwarp onto the intended turn block's center before rotating and continuing. |
| IF-007 | What exact movement connects floor one's exit to floor two's entrance and floor two's exit to floor three's entrance? Does the player walk up, jump, fall, or use a fixed room feature? | Crouch-walk straight up the connector stair. | `CONFIRMED`: a short non-ice connector ends in one stone-brick stair; continue walking straight up it, and the immediate next block starts the next Hamiltonian floor. |
| IF-008 | Should the current solver remain authoritative for floor clustering, entrance/exit selection, and Hamiltonian path choice? If it finds fewer/more than three valid floors or cannot solve one, should automation abort? | Preserve it and abort before movement unless exactly three complete valid paths are available. | `CONFIRMED`: yes. |
| IF-009 | What do ice versus packed-ice states mean during this puzzle? On activation, must every route tile be untouched ice, or may automation resume a partially progressed floor containing packed ice? | Require a fresh board and reject partial state. | `CONFIRMED`: ice is unvisited, packed ice is visited; any packed ice at initial entry/eligibility means automation must not activate. |
| IF-010 | What observable event confirms that the current tile was successfully visited: its block changing from ice to packed ice, player position alone, another block state, sound, or a combination? How long may that acknowledgement lag? | Do not gate each move on acknowledgement. | `CONFIRMED/CORRECTED`: walking changes ice to packed ice, but the controller should continue the path without waiting for or requiring per-tile confirmation. |
| IF-011 | If the player drifts off the expected segment, reaches a future route cell early, revisits a cell, or misses a tile, should the controller re-center/recompute or cancel? | Do not add correction/replanning to the fixed route. | `CONFIRMED`: any non-fall deviation terminally cancels and locks; only a below-threshold fall enters recovery. |
| IF-012 | If another player enters the board or changes any route tile outside the expected sequence, should automation cancel for the run? | Cancel rather than attempting to merge two players' progress. | `CONFIRMED`: yes. |
| IF-013 | If the player falls, leaves the detected walkable cells, or a floor transition does not land correctly, should it attempt rescue/repositioning or stop immediately? | Recover a lag-induced fall using calibrated Etherwarp data. | `CONFIRMED`: below the global threshold, target the active floor's saved non-ice start/recovery block, wait up to `10 s` for the whole floor to return (normally about `3 s`), and allow three recoveries per floor. |
| IF-014 | After cancellation, may Ice Fill restart by returning to its start, or must it remain locked until a new dungeon run like Creeper Beams? | Separate recoverable falls from terminal cancellation. | `CONFIRMED`: interference/other terminal cancellation locks until a new dungeon run; fall recovery from IF-013 retries the floor in the same run. |
| IF-015 | Should physical input use the shared cancellation contract: keys/buttons cancel immediately, physical mouse motion has a one-second activation grace, generated input is excluded, and disabling during an active attempt cancels? | Reuse the shared contract; an active generated movement key must never be mistaken for player input. | `CONFIRMED`: yes. |
| IF-016 | Should camera turns use the AutoSS-derived, sensitivity-correct controller and an independent Turn Speed setting? | Use the same motion method with a dedicated much-slower profile. | `CONFIRMED`: `10%–80%` of normal AutoSS speed, default `40%`, step `1%`; higher values turn faster. |
| IF-017 | Which visuals should remain: the full path line, a separately colored next segment/tile, the start-block box, and perhaps different colors per floor? Which colors and line-thickness range/default should be configurable? | Preserve only the current Hamiltonian path rendering. | `CONFIRMED`: one shared configurable green path and thickness; no start, recovery, next-tile, or per-floor visual additions. |
| IF-018 | What confirms floor completion and final success? Should it stop silently on room-green, omit reward-chest automation, and send only chat failure reasons? | Use observed transition into each next-floor entrance for floor progress, room-green for final completion, no chest action, silent success, and one chat-only abnormal-stop reason. | `CONFIRMED`: yes. |

### Ice Fill follow-up decisions

| ID | Required decision | Proposed answer where useful | Status |
|---|---|---|---|
| FIF-001 | In plain terms, where should automation begin: while standing on the non-ice block immediately before floor one's first ice tile, or only after standing on that first ice tile? Is that location always rendered? | Begin on the saved pre-floor-one block, then Etherwarp into the solver's first ice cell. | `CONFIRMED`: start on the first non-ice tile with the first ice cell exactly one block ahead; do not add a start outline. |
| FIF-002 | Confirm the first-version calibration controls: `Set Fall Y`, `Set Floor 1 Start`, `Set Floor 2 Start`, and `Set Floor 3 Start`. Should each button capture the player's current feet position, convert it into room-relative data, persist it across restarts, and work only inside a recognized Ice Fill room? | Yes; room-relative storage makes one calibration work across room placement/rotation rather than saving unsafe absolute X/Z coordinates. | `CONFIRMED` |
| FIF-003 | For each `Set Floor N Start`, should the player stand on the non-ice recovery/launch block immediately before that floor, or on its first ice tile? Is the same saved position both the normal floor-entry reference and the fall-recovery Etherwarp landing? | Save the safe non-ice block before each floor and use it as both entry reference and recovery landing; let the solver provide the first ice tile separately. | `CONFIRMED` |
| FIF-004 | Which Etherwarp items qualify, how are they selected, and is the prior slot restored? | Scan the hotbar left-to-right using CGC's existing Etherwarp-item check, select the first match before activation, keep it selected throughout, do not activate without one, and do not restore the old slot. | `CONFIRMED` |
| FIF-005 | Exactly which path steps use Etherwarp? | Etherwarp every on-ice step classified as forward; use ordinary crouch-forward only for the two stair transitions, and use continuous crouch-strafe for lateral route runs. | `CONFIRMED` |
| FIF-006 | How should the initial camera frame and rare turn direction be chosen? | Derive a deterministic room-aligned yaw from the exact path so its long runs map to left/right strafe and connectors map to forward; at an unavoidable turn, stop, Etherwarp to its cell, then rotate to the next such frame. | `CONFIRMED` |
| FIF-007 | Should each route Etherwarp have a dedicated landing wait and same-tile use retry? | Let normal position/fall handling classify the result instead. | `CONFIRMED/CORRECTED`: no in-place retry; a wrong landing is expected to break the floor and enter fall recovery, while a wrong non-fall cell follows terminal deviation policy. |
| FIF-008 | If a non-fall route deviation occurs despite the fixed controller—wrong/future/revisited cell, sideways slip, or missed stair entry—should it terminally cancel and lock for the run? | Yes; only the explicitly detected below-threshold fall is recoverable. | `CONFIRMED` |
| FIF-009 | On a fall, is there one global calibrated Y threshold, with the active floor determining which of the three saved recovery blocks is targeted? Should the aim use the saved block dynamically rather than a fixed literal 180-degree/up rotation? | Yes; stop horizontal input immediately, dynamically ray-trace the active floor's saved safe block, and never right-click unless it is the actual visible Etherwarp target. | `CONFIRMED` |
| FIF-010 | When waiting after recovery, must every route cell of the failed floor return to regular ice before retry? What reset timeout and maximum number of fall recoveries should apply? | Require the whole floor fresh, re-solve/validate it, wait at most `10 s`, and permit at most three recoveries for that floor before terminal cancellation. | `CONFIRMED`; normal observed reset is roughly `3 s` |
| FIF-011 | If packed ice is present during the initial board check, should automation remain ineligible for that entire dungeon run, or may it activate later if the complete board becomes fresh without a fall recovery? | Lock it out for the run; only the controller's own explicit fall-recovery state may wait for and accept a reset. | `CONFIRMED` |
| FIF-012 | Choose a slow-turn range/default and define its unit/direction. | Percentages of normal AutoSS speed (`0.10x–0.80x`, default `0.40x`), with larger values turning faster. | `CONFIRMED`: UI `10%–80%`, default `40%`, step `1%`. |
| FIF-013 | Confirm the minimal visual settings: one shared `Path Color` (current default green) and `Line Thickness` (`0.5–5.0`, current-compatible default `5.0`, step `0.5`), with no start/next-tile/per-floor outlines or colors. | Keep only the two shared path settings. | `CONFIRMED` |

All Ice Fill follow-ups are resolved.

### Ice Fill settings

| Setting | Type | Behavior/default | Status |
|---|---|---|---|
| `Turn Speed` | Number | `10%–80%` of normal AutoSS speed, default `40%`, step `1%`; higher is faster | `CONFIRMED` |
| `Path Color` | Colour | Shared Hamiltonian-line color for all three floors; default green | `CONFIRMED` |
| `Line Thickness` | Number | Shared path thickness: `0.5–5.0`, default `5.0`, step `0.5` | `CONFIRMED` |
| `Set Fall Y` | Button | In a recognized Ice Fill room, capture the player's current room-relative feet Y as the strict below-threshold recovery boundary and persist it separately | `CONFIRMED` |
| `Set Floor 1 Start` | Button | Capture/persist the room-relative non-ice support block and expected centered foot-space immediately before floor one | `CONFIRMED` |
| `Set Floor 2 Start` | Button | Capture/persist the room-relative non-ice support block and expected centered foot-space immediately before floor two | `CONFIRMED` |
| `Set Floor 3 Start` | Button | Capture/persist the room-relative non-ice support block and expected centered foot-space immediately before floor three | `CONFIRMED` |

The action buttons themselves are transient UI controls; their captured values must live in serialized calibration data. Reject a capture outside a recognized Ice Fill room without overwriting the previous value. Each successful capture should expose enough confirmation to verify what was stored. Do not add start/recovery colors, a per-floor palette, next-cell visuals, movement-speed settings, tile-acknowledgement timing, or a manual Etherwarp slot setting.

### Ice Fill acceptance criteria

The Ice Fill specification is execution-ready only while all of the following acceptance criteria remain true:

1. Enabling Ice Fill jointly enables the trusted three-floor solver/path render and automatic eligibility; disabling it releases all generated input immediately.
2. Outside a recognized Ice Fill room, no path, calibration overwrite, hotbar change, crouch, movement, camera turn, or Etherwarp is produced.
3. All four calibration actions capture persisted room-relative values and transform correctly across room placement/rotation. Missing or invalid calibration prevents activation without generating input.
4. Automation requires exactly three complete solver paths, a board containing no initially packed route cell, and a hotbar Etherwarp-capable item. Any initial packed ice locks automation for that dungeon run.
5. The only activation position is the calibrated Floor 1 non-ice launch tile. The solver's first ice cell must transform to exactly one horizontal block ahead in the derived camera frame.
6. The controller selects the leftmost Etherwarp-capable hotbar item, keeps it selected, revalidates it before every use, and never restores the previous slot.
7. Activation holds crouch continuously until normal completion, terminal cancellation, or safe cleanup. No sprint, jump, backward movement, or ordinary centering is generated.
8. The action plan covers every solver cell exactly once and in emitted order on each floor; it does not replace, shorten, smooth, or resume the Hamiltonian route.
9. Lateral runs use only the required crouched left/right strafe and position/velocity feedback. On-ice forward edges use one validated-target Etherwarp; ordinary crouch-forward is reserved for the two stair connectors.
10. Every Etherwarp target is the intended visible route/recovery support block within normal range and is reached with one ordinary right-click while sneaking. No impossible targeting, packet-only teleport, or in-place route-use retry is allowed.
11. Route progress does not wait for each block to become packed ice. The controller continues from observed player movement while monitoring the exact route order and fall threshold.
12. Rare turns stop horizontal movement, arrive centered on the intended turn cell by Etherwarp, use the finalized slow sensitivity-correct camera profile, and revalidate the next segment before resuming.
13. Floor exits crouch-walk straight over the connector and up exactly one stone-brick stair into the immediately following floor entrance, without jump or separate reactivation.
14. Any unexpected packed cell/player involvement, wrong non-fall cell, skipped/revisited cell, missed stair entry, or other non-fall route deviation terminally cancels and locks the rest of the dungeon run.
15. Physical keys/buttons cancel immediately; physical mouse motion cancels after the exact one-second activation grace; generated keys/mouse/use input never self-cancels. Module disable during an attempt terminally locks the run.
16. Crossing strictly below the one calibrated Y threshold pre-empts every traversal/aim state and enters recovery instead of terminal cancellation. The controller retains the correct active-floor identity.
17. Recovery stops horizontal route input, dynamically ray-traces the active floor's calibrated non-ice support block, Etherwarps back/up to it, and never guesses with a fixed rotation or unseen target.
18. After safe recovery, the controller waits for every route cell of only the failed floor to become regular ice, re-solves/validates that floor, and retries from its saved start. A normal approximately `3 s` reset succeeds within the `10 s` ceiling.
19. Each floor permits at most three fall recoveries. A recovery-target failure, reset timeout, or budget exhaustion terminally cancels, releases inputs, and locks the run.
20. A wrong route Etherwarp that drops below the threshold enters recovery; a wrong landing that remains above the threshold is a non-fall route deviation and terminally locks. No duplicate route right-click is sent from the previous tile.
21. After floor three, no further movement/use is produced; room-green alone confirms silent normal completion. There is no reward-chest action. Every terminal abnormal stop sends exactly one chat reason.
22. Rendering remains one configurable green-by-default Hamiltonian line using the configured thickness, with no start/recovery/next-tile boxes and no per-floor color assignment.
23. Every generated key, camera count, hotbar selection, and right-click remains serial and technically reproducible through a vanilla client.

### Ice Fill verification plan

Automated tests must cover:

- Three-floor scan/cluster ordering, checkpoint-derived starts/ends, exact Hamiltonian coverage/order, unsolvable or non-three-floor boards, and every supported room transform.
- The fact that execution consumes the emitted path unchanged, including fixtures with long lateral runs, single forward connectors, rare turns, and route shapes that cannot be represented safely.
- Calibration capture rejection outside the room, no-overwrite-on-failure, room-relative round trips, persistence, unset/invalid values, support-block versus foot-space derivation, and the strict fall-Y boundary.
- Fresh ice, every possible initial packed-ice location, expected active packed transitions without per-tile stalls, unexpected/out-of-sequence packed transitions, whole-floor reset, and room-green completion.
- Leftmost Etherwarp-item selection, missing/lost item, no slot restoration, target ray trace/range/occlusion, ordinary readiness, one-use route behavior, wrong safe landing, and falling wrong landing.
- Deterministic path-to-action compilation: initial forward Etherwarp, continuous left/right runs, no backward/jump/sprint, forward Etherwarp classification, centered turn state, and both stair transitions.
- Position/velocity boundaries for strafe release, expected cell order, future/skipped/revisited cells, missed transition, and no corrective/replanning behavior.
- Fall pre-emption from every active state, correct active-floor recovery target, failed recovery warp, approximately `3 s` reset, exact `10 s` timeout, three-per-floor budget, fresh-floor re-solve, and terminal fourth failure.
- Input ownership at the exact `1000 ms` mouse boundary, physical versus generated keys/mouse/use, disable/room/world cleanup, scheduled-action invalidation, same-run terminal lockout, and new-run reset.
- Turn Speed boundaries/default/direction, path color/thickness serialization, path-only render plans, and calibration-button feedback.

In-client validation must first use the four calibration buttons to capture the real threshold and three non-ice launch/recovery blocks. It must then cover every encountered room core/rotation, every floor and stair, low/high latency, all supported Etherwarp items, repeated lateral segments, rare turns at multiple speed values, wrong/missed Etherwarps, all three floor recoveries, reset timing, other-player interference, manual cancellation during every controller state, terminal lockout/new-run reset, and silent room-green completion. No implementation may claim the calibrated recovery route works until these values and sight lines are observed in the real room.

## Puzzle 4 — Waterboard

- Implementation status: `SOURCE COMPLETE — all 40 schedules and bundled assets pass automated checks; live movement, landing, lever, and hole-safety validation pending`
- UI group label: `Waterboard` (`CONFIRMED` from the user's requested label)
- Known room identity: `Water Board` (`CONFIRMED` from room data; keep this detection name separate from the UI label)
- Trusted solver baseline: detect four marker variants and ten three-gate combinations, then render/track the audited timed lever schedule relative to opening water
- Desired behavior: automatically execute the mechanically proven Waterboard pattern while replacing the old controller's visibly direct/inhuman aiming and tightening interaction, interruption, and failure safety
- Modes/options: one connected solver-and-automation state under C-001; no separate assistance/automatic mode
- Activation and reset: arm and begin recognition immediately upon entering a recognized `Water Board` room; no keybind/start block; any abnormal cancellation locks automation for the remainder of that dungeon run
- Observation and board/lever state representation: preserve the current solver's four marker variants, exact three-of-five closed-gate key, per-lever click counts, and trusted schedule lookup; observe every real lever `powered` state for safety
- Solution selection and action scheduling: bundle `waterSolutions.json`; preserve the proven legacy flattening/order, including serial zero-time setup before the first water click; no new same-timestamp/lateness policy is currently wanted
- Movement, aiming, and interaction: use Etherwarp for cross-room/platform movement and the normal water-lever approach; ordinary walking may face any direction but must pre-rotate while stationary, freeze the camera, hold exactly one directional movement key along one short unobstructed continuously supported line, release it, and only then rotate again; reuse every old position, avoid the central hole, and permit a lever click only through a real vanilla-valid crosshair hit
- Held-item policy: select the leftmost supported Etherwarp item for warps; after reaching and aiming at a lever, equip the leftmost hotbar shortbow or, if none exists, an item named `Dungeon Breaker` well before the click becomes due; do not restore the previous slot
- Water-flow timing: the first actual water-lever use action is time zero; retain the bundled schedule and the old controller's proven serial timing rather than inventing simultaneous actions
- Progress/completion verification: require expected lever-state acknowledgement as a bounded fail-safe; normal completion occurs after the final programmed pattern action is successfully completed, not from room-green or gate scanning
- Recovery and interruption: outside lever/gate changes and physical user input terminally cancel; generated input is excluded; a bounded fall/missed-warp safety path is specified below under the discretion granted in WB-010
- Data assets and versioning: bundle a reviewed `waterSolutions.json` resource and reuse the old position resource/values; no runtime download and no position-capture settings
- Settings/defaults: one Waterboard Aim Speed controls lever and Etherwarp aiming; Action Delay retains the old range/default; the single current-click countdown has one configurable green-by-default color
- Rendering/feedback: render one countdown above only the lever for the current global programmed click; no countdowns for later actions, lever boxes, or tracers; abnormal terminal stops produce one chat reason and normal completion is silent
- Tests and acceptance criteria: frozen below
- Non-goals: no fabricated block-hit result, runtime solver-data download, position calibration UI, same-run restart after cancellation, reward-chest automation, or separate manual-solver mode

### Waterboard questionnaire

| ID | Decision/question | Proposed answer where useful | Answer |
|---|---|---|---|
| WB-001 | What starts automation: recognizing a valid board immediately, a keybind, or standing on a specific start block? Before opening water, should it complete every scheduled zero-time non-water toggle and click the water lever last? | Require an intentional start position or keybind, finish all zero-time preparation serially, verify it, then make the water click time zero. | `CONFIRMED/CORRECTED`: activate as soon as the player enters the room. Preserve the working controller's zero-time preparation order and water-last behavior. |
| WB-002 | Should CGC bundle the audited `waterSolutions.json` snapshot, or download/update solution data at runtime? | Bundle and test a reviewed snapshot for offline, deterministic behavior; update it only through a later code/resource release. | `CONFIRMED`: bundle `waterSolutions.json` as a mod resource. |
| WB-003 | Where will the player be when automation starts, and how should it get within reach of each of the six material levers and the water lever: ordinary movement, Etherwarp, or a mixture? | Use only validated room-relative approach/landing positions; do not start until the complete selected schedule has a feasible route. | `CONFIRMED`: Etherwarp for cross-room/platform and normal water-lever travel. A walking segment may face any direction but holds exactly one directional key on a short supported straight line while the camera remains completely fixed. |
| WB-004 | Is Etherwarp required, optional, or forbidden? Which Etherwarp-capable items are supported, how is one selected, what may be held while clicking levers, and should the previous hotbar slot be restored afterward? | If Etherwarp is approved, reuse leftmost-hotbar item detection and explicit landing validation; choose a safe click slot deliberately. | `CONFIRMED`: Etherwarp is required; select the leftmost supported item and do not restore the previous slot. For lever clicks, prefer any shortbow and fall back to the item named `Dungeon Breaker`. Reach and aim first, then equip the click item early even when the scheduled click is still far away. Missing-item details are FWB-003. |
| WB-005 | Should the old controller's seven lever positions, approach points, Etherwarp landing points, and rescue point be used after validation in all four rotations, or should the first version expose capture buttons for any of them? | Treat all old coordinates as candidates until each transformed position, sight line, and landing is observed in client. | `CONFIRMED`: all old positions still work and must be reused; no capture buttons. Implementation validation still covers every transform. |
| WB-006 | Should lever aiming use AutoSS-derived sensitivity-correct mouse movement? Confirm that a click is allowed only when the actual crosshair raycast hits that lever within vanilla reach and line of sight, with no fabricated fallback hit. What Aim Speed range/default is wanted? | Enforce the real raycast/reach rule and give Waterboard an independent Aim Speed setting. | `CONFIRMED`: yes, including the proposed real-raycast constraint and one `0.5x–2.0x`, default `1.0x`, step `0.05x` Aim Speed governing both lever and Etherwarp camera motion. |
| WB-007 | Dataset entries can share the same timestamp, but a vanilla client must perform them serially. What delay should separate same-time setup toggles, and how late may a scheduled post-water click be before the run cancels? | Preserve dataset order with a small configurable action delay; define a finite lateness ceiling rather than silently dropping old steps. | `CONFIRMED`: preserve the working controller's serial order/timing with configurable `0–1000 ms` Action Delay, default `120 ms`, step `10 ms`; add no special simultaneous-click/lateness system and prioritize already-due actions. |
| WB-008 | After each click, must the lever's `powered` state match the expected next state before advancing? How long should CGC wait, how many click retries are allowed, and should an already-correct lever be skipped? | Skip an already-correct state, otherwise send one click, wait up to `650 ms`, retry once after complete revalidation, then cancel. | `CONFIRMED/CORRECTED`: unattributed/pre-existing unexpected state cancels rather than skips; after this controller clicks, wait `650 ms`, retry once after full revalidation, then terminally lock. |
| WB-009 | If another player toggles a lever, the initial lever states are unexpected, a gate changes unexpectedly, or the observed state no longer matches the schedule, should CGC rebuild remaining steps or terminally cancel? | Cancel whenever action ownership or the timed schedule becomes ambiguous; only skip a state that exactly matches the next expected result. | `CONFIRMED`: cancel everything and lock Waterboard for the rest of that dungeon run. |
| WB-010 | If an Etherwarp misses or the player falls below the lever floor, should automation cancel or use the old room-relative rescue Etherwarp? If rescue is allowed, what retry/time limit applies and may the timed solution continue afterward? | Cancel a safe missed landing; use rescue only if its target is calibrated and the schedule can still meet every remaining deadline. | `DELEGATED`: the event is very unlikely; implement a bounded safety-first fail-safe. The selected design is recorded below. |
| WB-011 | What proves completion: no colored gate remains closed, room-green, reward-chest availability, or a combination? Should automation open the reward chest? | Stop lever actions when the gate condition is satisfied, require room-green for final success, and omit chest automation unless requested. | `CONFIRMED/CORRECTED`: completion is the verified end of the programmed pattern; do not automate the reward chest. |
| WB-012 | Should physical-input cancellation match Blaze/Creeper/Ice Fill (keys/buttons immediately, mouse after a one-second activation grace, generated input excluded)? After cancellation, can it restart in the same run? Which existing solver visuals should remain, and should failures be chat-only with silent success? | Reuse shared input ownership, terminal same-run lockout, next/later lever boxes/tracers plus timer, one abnormal chat reason, and silent success. | `CONFIRMED/CORRECTED`: arm on room entry but wait for held inputs to release before ownership; then use shared cancellation, same-run lockout, one current-click countdown in one configurable green-by-default color, one abnormal chat reason, and silent success. |

### Confirmed Waterboard source positions

Reuse these exact values from the working old controller. They are shown in that controller's room-local convention; implementation must map them once through CGC's room transform rather than treating them as global coordinates.

| Lever | Lever block | Approach point | Etherwarp landing point |
|---|---|---|---|
| Quartz | `(20, 61, 20)` | `(17.55, 60.0, 19.65)` | `(19.5, 60.0, 20.5)` |
| Gold | `(20, 61, 15)` | `(17.55, 60.0, 16.35)` | `(19.5, 60.0, 15.5)` |
| Coal | `(20, 61, 10)` | `(17.55, 60.0, 11.35)` | `(19.5, 60.0, 10.5)` |
| Diamond | `(10, 61, 20)` | `(12.45, 60.0, 19.65)` | `(11.5, 60.0, 20.5)` |
| Emerald | `(10, 61, 15)` | `(12.45, 60.0, 16.35)` | `(11.5, 60.0, 15.5)` |
| Terracotta | `(10, 61, 10)` | `(12.45, 60.0, 11.35)` | `(11.5, 60.0, 10.5)` |
| Water | `(15, 60, 5)` | `(15.5, 59.0, 7.35)` | `(15.5, 59.0, 6.5)` |

The confirmed old rescue target is room-local `(0.5, 59.0, -0.5)`. The implementation must also treat the live non-solid opening near the middle/front of the water lever as a navigation hazard; no ordinary movement route may cross an unsupported cell.

### Waterboard controller

```text
INACTIVE
  -> RECOGNIZING immediately on valid Water Board entry
  -> PREFLIGHT
  -> POSITIONING
  -> AIMING
  -> CLICK_ITEM_READY
  -> WAITING_DUE (only for post-water future steps)
  -> CLICKING
  -> VERIFYING
  -> POSITIONING for the next programmed action
  -> COMPLETE after the final programmed action

Any active state
  -> RESCUE_ATTEMPT (self-detected fall only)
  -> TERMINAL_LOCKED (manual input, interference, invalid state, unsafe/missed action, or failed verification)
```

1. `RECOGNIZING` waits only for stable room, transform, marker-pattern, and three-gate recognition; it does not wait for a start block or keybind.
2. `PREFLIGHT` loads the bundled four-by-ten solution, flattens its actions by scheduled time and the proven lever order `Quartz, Gold, Coal, Diamond, Emerald, Terracotta, Water`, resolves every transformed position, and checks required hotbar items. Water therefore remains last among zero-time entries.
3. Before the first water click, every zero-time non-water step is positioned, aimed, equipped, clicked, and verified serially. The actual first water use action establishes monotonic time zero; later water entries remain ordinary scheduled actions.
4. `POSITIONING` uses Etherwarp for cross-room/platform transitions and the normal water-lever approach. For an ordinary segment, it chooses any safe world-space heading, completes the required camera rotation while stationary, freezes the camera, holds exactly one of forward/back/left/right, and releases that key before any further camera motion. The full straight-line footprint must be unobstructed and continuously supported on one platform; otherwise it Etherwarps.
5. Every Etherwarp selects the leftmost supported hotbar item, uses sensitivity-correct visible camera motion and an ordinary crouch-right-click ability action, and validates the landing before progressing.
6. `AIMING` dynamically targets a visible point on the real lever shape. Direct yaw/pitch assignment and fabricated hit results are forbidden.
7. After the player is correctly positioned and the actual crosshair raycast identifies the intended lever, `CLICK_ITEM_READY` immediately selects the preferred click item. It remains selected while waiting, so the hotbar switch never occurs suspiciously on the final tick before a scheduled action.
8. `CLICKING` sends exactly one ordinary use-on-block action when due. `VERIFYING` compares the real lever state with the expected parity for up to `650 ms`; an unrelated already-correct state cancels rather than skips. One fully revalidated retry is permitted, after which failure terminally locks the run.
9. Any observed lever/gate transition that cannot be attributed to the current expected action terminally cancels and locks Waterboard for the dungeon run. It never re-solves around another player.
10. After the final programmed step passes verification, the controller releases inputs, becomes silently complete, does not wait for room-green, and does not interact with the reward chest.
11. On entry, arm but wait for already-held physical keys/buttons to be released before taking ownership and starting the one-second mouse grace. Thereafter new physical keys/buttons cancel immediately, physical mouse motion cancels outside the grace, and generated camera/key/use actions do not self-cancel.

### Selected Waterboard warp/fall fail-safe

WB-010 explicitly delegates the rare failure policy. Use this bounded, safety-first design unless later in-client evidence shows the old rescue sight line requires a small mechanical adjustment:

1. Before every Etherwarp, require the transformed target block to be loaded, solid, unobstructed above, within ability range, and hit by the real crosshair raycast. Never right-click toward an assumed or occluded landing.
2. Allow `900 ms` for an expected landing. If a pre-water warp fails but the player remains safely supported, perform one fully revalidated retry after at least `250 ms`; if water has already started, do not spend schedule time retrying and terminally cancel.
3. If the player's Y drops below the old proven `59.0` floor threshold without physical user cancellation, immediately release route movement and pre-empt the normal schedule. After the old `500 ms` rescue delay, make at most one real Etherwarp attempt toward the confirmed room-local rescue target.
4. Require a supported landing at/near the rescue target within `1,000 ms`. Whether rescue succeeds or fails, do not resume the timed pattern: terminally lock Waterboard for that run because its timing and action ownership are no longer trustworthy.
5. Never trigger automated rescue after a physical-input cancellation; the user owns control immediately in that case. There is no recursive rescue or unbounded Etherwarp loop.

### Waterboard settings

| Setting | Type | Range/default | Status |
|---|---|---|---|
| `Aim Speed` | Number | `0.5x–2.0x`, default `1.0x`, step `0.05x` | `CONFIRMED`; one value governs lever and Etherwarp aiming |
| `Action Delay` | Number | `0–1000 ms`, default `120 ms`, step `10 ms` | `CONFIRMED`; an already-due scheduled action takes priority over adding optional delay |
| `Countdown Color` | Color | Default green | `CONFIRMED`; one static configurable color with no time-band changes |

No `Use Etherwarp`, slot-number, position-capture, solver-only, box, tracer, or same-run restart setting is planned.

### Waterboard acceptance criteria

1. With the Waterboard tab enabled and the run unlocked, entering the recognized room begins stable recognition without a keybind or start tile; no action occurs in any other room.
2. Every one of the four patterns and ten three-gate keys loads the exact bundled `waterSolutions.json` entry; invalid/missing data cancels before any click.
3. The controller transforms and uses the confirmed seven lever/approach/Etherwarp-support positions in every room rotation without global-coordinate assumptions or calibration buttons. Fractional local points are transformed before block flooring.
4. Zero-time preparation follows the proven serial lever order, verifies each action, and opens water only after all preceding zero-time actions are complete.
5. The first water use action alone establishes time zero, and every remaining action uses its bundled timestamp/order, including repeated toggles of one lever and later water-lever toggles.
6. Etherwarp is required and always uses the leftmost supported hotbar item; if Etherwarp or every approved click item is missing/lost, send one chat reason and terminally lock rather than substituting an unknown item.
7. Lever clicks prefer the selected shortbow and fall back only to `Dungeon Breaker`; selection occurs as soon as positioning and genuine lever aim are ready, even several seconds before the click.
8. All lever and Etherwarp camera changes are sensitivity-correct raw-mouse-count motion at the configured speed; direct camera assignment is absent.
9. A lever click is impossible unless the live crosshair raycast hits that exact lever within vanilla reach and line of sight.
10. Before walking, the controller rotates while stationary to a safe arbitrary heading; it then freezes the camera, holds exactly one directional key for the entire short, unobstructed, continuously supported straight segment, releases the key, and only then permits another camera movement. It never combines directional keys or walks across/beside the unsupported opening near the water lever.
11. Every expected lever update follows the final finite acknowledgement/retry rule; the controller cannot produce an unbounded click loop.
12. Another player's interaction, an unexpected lever/gate transition, an invalidated pattern, or ambiguous action ownership causes one cleanup and same-run terminal lockout.
13. A safe missed pre-water warp permits at most the selected one retry; a post-water miss cancels; a below-floor fall permits only the single bounded rescue attempt and never resumes the pattern.
14. New physical input follows the shared cancellation policy, releases every generated input, sends one chat reason, and prevents reactivation until a new dungeon run.
15. Completion occurs only after the final programmed action satisfies its final verification, then releases inputs and produces no completion chat message.
16. Rendering contains one green-by-default, configurable-color countdown above only the lever for the current global programmed action and no time-band color changes, later-action countdowns, boxes, tracers, path lines, start waypoint, or debug overlay in normal use.
17. Every hotbar switch, movement key, mouse count, crouch, Etherwarp use, and lever click remains serial and technically reproducible on a vanilla Minecraft client.

### Waterboard verification plan

Automated tests must cover all forty pattern/gate schedule selections; missing/malformed data; stable recognition; legacy flattening/tie order; zero-time water-last behavior; repeated lever/water actions; monotonic scheduling; all room transforms and position mappings; hotbar permutations for Etherwarp/shortbow/`Dungeon Breaker`; early click-item selection; real raycast/reach rejection; lever parity and retry bounds; interference; entry/manual-input ownership; arbitrary-heading pre-rotation; exactly-one-directional-key walking with zero camera motion; every warp/fall branch; terminal run lockout; single-current-click countdown/color generation; and cleanup on disable, room exit, world change, death, and disconnect.

In-client validation must cover all encountered patterns, gate combinations, and four room rotations; every old landing/approach/rescue coordinate; arbitrary-direction supported straight walks with one key and a locked camera; rejected unsafe/non-straight paths around the central hole; low/high latency; close and distant next levers; multiple supported Etherwarp items; every shortbow class and the `Dungeon Breaker` fallback; long pre-aim waits; repeated lever toggles; other-player interference; physical cancellation in every state; missed warps/falls; final-pattern completion; green/default and changed countdown colors; shared Aim Speed at multiple values; and silent success/chat-only failure. The old controller is the mechanical comparison baseline, while the new camera/hotbar timing must be visibly less abrupt.

### Focused Waterboard follow-ups

| ID | Decision/question | Proposed answer | Answer |
|---|---|---|---|
| FWB-001 | What deterministic rule should choose ordinary movement versus Etherwarp, including near the central hole? | Etherwarp for cross-room/platform changes and every water-lever approach; ordinary walking only for a short collision-checked adjustment entirely on one supported platform, with no sprint/jump and no path adjacent to or across the hole. | `CONFIRMED/REFINED`: yes; walking is exclusively one safe straight line in any direction, with the exact input/camera contract in FWB-008. |
| FWB-002 | Because activation occurs while the player may still be holding the key used to enter the room, should pre-held keys/buttons cancel immediately? | Arm on entry but wait for all already-held physical keys/buttons to be released before taking control; begin the one-second physical-mouse grace when control actually starts, and let any new key/button after the release baseline cancel immediately. | `CONFIRMED`. |
| FWB-003 | Confirm click-item lookup and missing-item behavior: leftmost hotbar shortbow first, otherwise leftmost formatting-insensitive display name `Dungeon Breaker`; if either the click item or Etherwarp item is unavailable, what happens? | Scan hotbar only; if either requirement is missing at preflight or disappears later, send one chat reason and terminally lock the run. | `CONFIRMED`. |
| FWB-004 | Confirm the exact lever fail-safe and its interaction with WB-009: should an unexpected already-correct state cancel instead of being silently skipped, while a click sent by this controller waits up to `650 ms`, retries once after full revalidation, then terminally locks? | Cancel any pre-existing/unattributed mismatch; acknowledge only this controller's expected transition. Use one `650 ms` wait and one retry. Time zero remains the original water use timestamp and is not shifted by acknowledgement lag. | `CONFIRMED`. |
| FWB-005 | Should the controller interact with the reward chest after the final programmed pattern action? | No; stop silently at the end of the pattern and leave reward collection to the player. | `CONFIRMED`: no chest automation. |
| FWB-006 | For countdowns, show only the next remaining scheduled click per lever or stack every remaining click? Should colors remain time-based (`red <2 s`, `yellow <6 s`, otherwise green), become one configurable color, or expose configurable urgent/soon/later colors? Does the confirmed Aim Speed control both lever and Etherwarp turns? | Show the next remaining click per lever, expose three configurable time-band colors, and use the one Aim Speed for all Waterboard camera motion. | `CONFIRMED/CORRECTED`: render only the single current global click above its lever; use the one-color and shared-Aim-Speed resolutions in FWB-009/FWB-010. |
| FWB-007 | Should the old humanizing `Action Delay` setting remain configurable at `0–1000 ms`, default `120 ms`, step `10 ms`, or should the proven default become fixed? | Keep the setting, while scheduled actions that are already due take priority over adding optional delay. | `CONFIRMED`. |
| FWB-008 | Does “walking only in straight lines” mean only room-axis/cardinal forward lines, or may one pre-rotate and walk a collision-checked diagonal line? | Permit only room-axis/cardinal straight segments; Etherwarp whenever the destination is not reachable by one such supported segment. | `CONFIRMED/CORRECTED`: any direction is valid. Complete camera alignment first, then hold exactly one directional movement key and produce no camera motion until that key is released. |
| FWB-009 | What countdown color configuration is wanted for the single current-click label? | One configurable color, default green; do not add time-band color changes unless requested. | `CONFIRMED`. |
| FWB-010 | Should the confirmed Waterboard Aim Speed control both lever aiming and Etherwarp aiming, or should they have separate settings? | Use one setting for all Waterboard camera motion to keep behavior consistent and the UI compact. | `CONFIRMED`. |

## Final cross-puzzle questionnaire

| ID | Decision/question | Proposed answer | Answer |
|---|---|---|---|
| X-001 | What should the saved first-launch defaults be for the parent and four puzzle tabs? | Disable the parent and every internal puzzle tab by default. Each must be explicitly enabled and persisted independently. | `CONFIRMED`; the user rejected the proposed enabled-child defaults and made all five states opt-in. |
| X-002 | Should Auto Puzzles have a `General` tab in addition to the four named puzzle tabs? | No. There are no genuine parent-level settings, and CGC automatically omits `General` when the parent registers none. | `CONFIRMED`. |
| X-003 | In what order should the four puzzle controllers be implemented and validated? | Blaze, Creeper Beams, Waterboard, then Ice Fill. | `CONFIRMED`. |
| X-004 | Confirm the module description shown in CGC. | `Automatically solves selected Dungeon puzzles with human-like movement, aiming, interactions, and configurable guidance.` | `CONFIRMED`. |

## Implementation sequence and remaining gates

No phase below starts until its entry gate is satisfied.

### Phase 0 — Complete requirements

- [x] Specify Blaze and update this plan.
- [x] Specify Creeper Beams and update this plan.
- [x] Specify Ice Fill and update this plan.
- [x] Specify Waterboard and update this plan.
- [x] Reconcile shared activation, input ownership, timing, UX, and safety decisions.
- [x] Resolve every implementation-affecting `TBD`, or explicitly defer it as out of scope.

Exit gate: each puzzle has testable acceptance criteria and the cross-puzzle behavior is internally consistent.

### Phase 1 — Final design review

- [x] Convert confirmed requirements into concrete class/file responsibilities.
- [x] Decide which runtime hooks the parent actually needs.
- [x] Define stable module/group/setting IDs and defaults.
- [x] Define shared context, input arbitration, and lifecycle only where requirements justify them.
- [x] Map each action to a vanilla-client-possible input/interaction path.
- [x] Identify required data files and their provenance/versioning.
- [x] Review the complete plan with the user and receive explicit approval to execute (`2026-08-26`).

Exit gate: user approval of the complete implementation plan.

### Phase 2 — Module shell and configuration

- [x] Add `AutoPuzzles` to the Dungeons category and register it in `CgcModules.bootstrap()`.
- [x] Add the four groups in the confirmed display order.
- [x] Implement only the confirmed settings, enabled defaults, descriptions, and visibility rules.
- [ ] Confirm all four tabs select/toggle correctly in the config GUI. Source structure/default tests pass; live GUI interaction remains.
- [ ] Verify config save/load for the parent, groups, and every setting. Stable names/defaults are pinned, but a real restart round trip remains.
- [x] Add safe parent/submodule disable and world-load cleanup.

Exit gate: UI/config/lifecycle shell works without puzzle automation.

### Phase 3 — Shared foundations

- [x] Add room-context/rotation helpers only if the final design needs behavior beyond `DungeonRoomScanner`.
- [x] Add an input owner/arbitrator if more than one controller can request the same controls.
- [x] Add reusable rotation/movement/interaction helpers only for confirmed shared semantics.
- [x] Add immutable observation/plan types where justified; no normal-user debug snapshot/overlay was needed.
- [x] Unit-test the isolated transforms, physical-input policy, run state, and dispatch/default foundations; live input ownership/cleanup remains in Phase 5.
- [x] Add the command-armed observational Terminator recorder, pure capture/analyzer policy, versioned persistence, and deterministic fixtures without weakening the active Terminator safety gate.

Exit gate: shared foundations are tested and contain no puzzle-specific policy.

### Phase 4 — Puzzle implementations

The confirmed implementation order is **Blaze → Creeper Beams → Waterboard → Ice Fill**. For each puzzle independently:

- [x] Implement observation/recognition.
- [x] Implement and unit-test pure solving/route logic where applicable.
- [x] Implement controller state transitions and action validation.
- [x] Implement completion, divergence, timeout, retry, abort, and cleanup behavior.
- [x] Add only the approved feedback/debug UI.
- [x] Run its automated tests and compile checks.
- [ ] Validate every supported room variant/rotation and required edge case in client.
- [x] Record automated behavior and remaining live limitations in this plan.

Exit gate: the puzzle meets its own acceptance criteria without regressing completed puzzles.

### Phase 5 — Integration hardening

- [x] Verify source dispatch selects only the live-enabled controller for the recognized exact room name; live room-entry confirmation remains below.
- [ ] Verify switching rooms, disabling a tab, disabling the parent, disconnecting, dying, and reloading the world always releases control.
- [ ] Verify another player changing/finishing the puzzle produces the agreed re-solve/abort behavior.
- [ ] Verify no controller acts on stale coordinates, stale entities, unknown rotation, unloaded state, or after leaving its room.
- [x] Verify Auto Puzzles controllers cannot overlap through their shared exclusive lease and exact-room dispatcher; coexistence with every unrelated automation module remains an in-client integration case.
- [ ] Verify lag/correction and partially completed puzzle scenarios.
- [x] Review tick-time cost and bound entity/block scans, chunk prerequisites, candidate searches, waits, retries, and recovery loops.
- [x] Complete the source-level vanilla-feasibility audit: normal key states, raw-count turns, ordinary use paths, real ray/reach checks, and observed Etherwarp landings. Live mechanics remain required.
- [x] Hardcode the user-confirmed `5°` Terminator fan, integrate all three first-collision rays, add collision fixtures, remove the calibration surface, and remove the source eligibility gate.
- [ ] Complete live Terminator Blaze/Creeper shots, including a deliberate side-arrow solution and a conservative unintended-target refusal.

Exit gate: all four features coexist safely and the human-feasibility audit passes.

### Phase 6 — Final validation and handoff

- [x] Run the complete automated test suite: `71` tests, no failures/errors/skips.
- [x] Run the relevant Gradle build/static checks: clean and final builds succeeded; scoped `git diff --check` passes.
- [ ] Perform the agreed in-client scenario matrix.
- [ ] Confirm config persistence across restart and compatibility with an absent/older AutoPuzzles config.
- [x] Review build/test output for unexpected exceptions and ensure no Auto Puzzles debug overlay or uncontrolled diagnostic output ships. The remaining `sun.misc.Unsafe` notice comes from the existing JOML dependency on Java `25`.
- [x] Update this document with actual files changed, tests run, manual cases verified, known limitations, and implementation adjustments.
- [x] Provide the user a concise completion report that distinguishes automated verification from manual/in-client verification.

## Baseline validation strategy

### Automated tests

Add focused tests for all pure behavior that can be isolated from Minecraft, including as applicable:

- Room-name/variant eligibility and room-relative coordinate transforms.
- Board/entity observations constructed from small fixtures.
- Solver correctness, determinism, unsolvable/ambiguous input, and already-complete input.
- Route validity and invariants specific to each puzzle.
- Controller state transitions, timeouts, retry limits, and unexpected world changes.
- Input arbitration and idempotent cleanup.
- Config defaults and serialization-sensitive names where practical.
- Regression fixtures for every bug found during in-client testing.

### Build/static verification

- Run the project's Gradle test task after focused tests.
- Run a full build before handoff.
- Treat warnings/errors honestly; do not report unrun checks as passing.

### In-client validation dimensions

The completed per-puzzle specs must turn this into concrete cases:

- Every supported puzzle variant and room rotation.
- Fresh and partially progressed puzzle states.
- Parent enabled/disabled and each group enabled/disabled.
- Entering, leaving, and rapidly re-entering the room.
- World load/disconnect/death during each active controller phase.
- Low/high but playable latency and delayed entity/block updates.
- Another player modifying or completing the puzzle.
- Manual input during automation according to the agreed policy.
- Missing tool/item, wrong hotbar slot, obstructed line of sight, and out-of-reach target where relevant.
- Unknown room/rotation, unloaded chunks, missing entities, and unexpected block layouts.
- Completion, puzzle failure, bounded retry, and clean abort.
- Config save, restart, load, and changed settings.

## Risk register

| Risk | Why it matters | Planned mitigation | Status |
|---|---|---|---|
| Requirements drift across four solvers | Similar names can hide materially different desired automation | Capture and approve each puzzle independently in this file | Closed for implemented scope; live findings require a recorded revision |
| Fixed coordinates fail under room rotation | Dungeon rooms can be rotated and placed at different world coordinates | Use recognized room transforms and test all supported rotations | Implemented and fixture-tested; live rotations pending |
| Stale state causes actions in the wrong place | Rooms, worlds, and puzzle boards can change asynchronously | Key caches by room/board signature; validate before actions; reset aggressively | Implemented; live transition matrix pending |
| Controllers retain player input | Stuck keys/rotation can disrupt normal play | Central ownership, idempotent release, and reset tests for every exit path | Implemented; live exit-state matrix pending |
| Party members change puzzle state | A precomputed plan can become invalid | Observe progress, revalidate each action, and define re-solve/abort rules | Implemented as terminal divergence; live interference pending |
| Lag makes timed sequences diverge | Time alone may not reflect server state | Prefer state acknowledgements; use bounded timeouts/retries | Bounded in source; latency matrix pending |
| Solver scanning/search affects frame time | Four solvers may inspect many blocks/entities | Room-gate work, cache immutable observations, react to changes, bound searches | Mitigated with exact-room and loaded-area gates plus bounded scans |
| GUI tabs with no settings cannot be normally selected | Current config UI only selects non-empty groups | Give each group genuine confirmed settings or explicitly revise GUI behavior | Closed; every group has confirmed visible settings |
| Config names change after release | Persistence is name-based within groups | Freeze IDs/names during final design and test save/load | Names/defaults pinned; restart round trip pending |
| Example code is stale or incompatible | Examples may target another version or mechanics | Treat as reference only; re-derive mappings and test against the configured Minecraft `26.1.2` target | Closed at compile/build level; live mechanics pending |
| Automation exceeds vanilla-client capabilities | Violates workspace constraint and can create invalid behavior | Document every action path and audit reach, LOS, rates, physics, and concurrency | Source audit passes; live reach/movement validation pending |
| CGC has no existing puzzle-green API | Creeper Beams and Ice Fill require authoritative room completion | Add the minimal full-tab snapshot tracker, pin parser fixtures, and require live `✔` proof for both puzzles before release; do not silently substitute a map parser | Tracker/parser implemented and tested; live `✔` proof pending |
| Generated input is mistaken for physical input | Controllers could immediately cancel themselves or ignore a real takeover | Observe vanilla keyboard/mouse callbacks without consuming them; keep event sequences separate from generated `KeyMapping`/`LocalPlayer.turn` work; test exact grace boundaries | Mixins/policy implemented and boundary-tested; live callbacks pending |
| Renderer has fixed line width and no world text | Confirmed thickness settings/countdown cannot be represented by the current queue | Extend `lineList` compatibly and add one billboard task; retain defaults for existing callers and validate queue clearing/depth/orientation | Implemented with exception-safe queue/pose cleanup; live orientation/depth pending |
| Terminator fan geometry or collision handling is incorrect | A side arrow could hit the wrong Blaze/endpoint even when the center ray is safe | Use the user-confirmed symmetric `5°` fan, model first collision for all rays, pin center/side/unsafe fixtures, and require live puzzle scenarios before release | Geometry and three-ray collision integration are fixture-tested; direct live Blaze/Creeper shots remain pending |
| Waterboard straight walking crosses the central hole | A visually simple segment can be unsupported or suspicious | Restrict walking to short swept-AABB-validated single-key lines, prohibit water-lever/hole-region walking, and Etherwarp or cancel otherwise | Implemented; live hole/landing validation pending |
| Ice Fill calibration is missing or stale | Recovery/activation could target an unsafe block or threshold | Persist versioned room-relative values, reject invalid captures without overwrite, validate support/sight lines, and require live calibration before activation | Implemented; real calibration/recovery remains mandatory |

## Open decision register

| ID | Decision needed | Affects | Resolution |
|---|---|---|---|
| D-001 | Which puzzle should be specified first? | Planning order | `RESOLVED`: Blaze, following the confirmed tab order |
| D-002 | Parent and group default enabled states | UI/config/safety | `RESOLVED`: parent `false`; Blaze `false`; Creeper Beams `false`; Ice Fill `false`; Waterboard `false` |
| D-003 | Global activation model and per-puzzle overrides | All controllers | `RESOLVED`: each enabled puzzle is one connected solver+automation feature; Blaze/Creeper activate on transformed fixed start blocks, Ice Fill on its calibrated non-ice launch block, and Waterboard immediately on recognized room entry |
| D-004 | Manual-input cancellation/pause/blending policy | Movement/aim/action safety | `RESOLVED`: all four puzzles use immediate new key/button cancellation, a one-second physical-mouse grace, and generated-input exclusion; Creeper, terminal Ice Fill, and Waterboard cancellations add same-run lockout; room-entry Waterboard waits for pre-held inputs to release before taking ownership and starting its mouse grace |
| D-005 | Whether a `General` tab is wanted in addition to the four named tabs | UI architecture | `RESOLVED`: no General tab and no parent-level settings |
| D-006 | Common status/debug presentation | UX/testing | `RESOLVED`: all four puzzles send one terminal abnormal-stop reason to chat and no completion message; Waterboard renders one current-click countdown in one configurable green-by-default color |
| D-007 | Puzzle implementation order | Delivery sequence | `RESOLVED`: Blaze → Creeper Beams → Waterboard → Ice Fill |
| D-008 | Whether each tab always exposes its solver with automation separately controlled | Module/submodule UX and lifecycle | `RESOLVED`: one connected per-puzzle on/off state; no separate solver/automation mode; see C-001 |
| D-009 | Vendor solver JSON snapshots or support runtime updates | Creeper Beams/Waterboard reproducibility | `RESOLVED`: bundle reviewed Creeper Beams and `waterSolutions.json` snapshots as mod resources; no runtime data download |
| D-010 | Whether Creeper Beams has any post-start positioning/Etherwarp route | Creeper Beams positioning, items, transforms, and safety | `RESOLVED/CORRECTED`: no route exists or is wanted; manually stand and shoot from the middle start block |
| D-011 | Bound the Creeper Beams neither-endpoint retry loop | Creeper Beams timing and cancellation safety | `RESOLVED`: `500 ms` per-attempt result window; three total attempts or `5 s`, whichever ceiling occurs first |
| D-012 | Define persisted Ice Fill calibration capture and what each floor marker represents | Ice Fill activation, rotations, and fall recovery | `RESOLVED`: one room-relative fall Y plus three room-relative non-ice launch/recovery support blocks, captured by four buttons |
| D-013 | Define exact Ice Fill Etherwarp item, movement-step, landing-validation, and retry contracts | Ice Fill traversal and vanilla feasibility | `RESOLVED`: leftmost supported item; forward ice/turn blocks use one right-click; no route-use retry; wrong results branch through deviation/fall handling |
| D-014 | Bound and verify Ice Fill fall recovery | Ice Fill reset waiting and runaway prevention | `RESOLVED`: whole failed floor fresh, `10 s` reset timeout, three recoveries per floor; normal reset is about `3 s` |
| D-015 | Calibrate the much-slower Ice Fill camera profile | Ice Fill turn appearance and route safety | `RESOLVED`: `10%–80%` of normal AutoSS speed, default `40%`, step `1%`, higher is faster |
| D-016 | Freeze the remaining Waterboard details | Waterboard final specification | `RESOLVED`: arbitrary-direction straight walking uses exactly one directional key with a frozen camera; countdown uses one configurable green-by-default color; one Aim Speed governs lever and Etherwarp camera motion |
| D-017 | Final Auto Puzzles module description | UI copy | `RESOLVED`: `Automatically solves selected Dungeon puzzles with human-like movement, aiming, interactions, and configurable guidance.` |

Puzzle-specific decisions will be added here or in their puzzle section as they arise.

## Decision log

| Date | Decision | Source/reason |
|---|---|---|
| 2026-08-25 | Plan the entire feature before implementation and refine it one puzzle at a time | User requirement |
| 2026-08-25 | Feature name is Auto Puzzles and category is Dungeons | User requirement |
| 2026-08-25 | Initial top-tab scope was later superseded | See the 2026-08-26 scope correction recorded below |
| 2026-08-25 | Use TriggerBot's group/submodule tab mechanism as the UI integration model | User comparison, verified against current code |
| 2026-08-25 | Keep all unspecified puzzle behavior as TBD | Workspace requirement not to assume missing intent |
| 2026-08-25 | Treat current Kotlin solvers as the trusted solution baseline and older Java automation only as reference material | User clarification |
| 2026-08-25 | Begin the detailed requirements interview with Blaze and proceed in tab order unless redirected | Established one-at-a-time workflow |
| 2026-08-25 | Make every puzzle tab one connected solver-and-automation feature: enabled means both are active/armed; disabled means both are off | User answer C-001 |
| 2026-08-25 | Blaze never navigates to its start; it renders the variant-specific waypoint and activates only when the player stands there | User answers BZ-001, BZ-002, BZ-006, BZ-007 |
| 2026-08-25 | Blaze supports Higher and Lower automatically with shared settings, reversed health order, and dynamic partial-state handling | User answers BZ-003 through BZ-005 |
| 2026-08-25 | Blaze supports all hotbar shortbows and dynamically selects live-entity aim; Terminator aim must account for its complete three-arrow fan | User answers BZ-008 through BZ-011 |
| 2026-08-25 | Blaze fires two shots, advances on Blaze removal after a configurable lag allowance, and starts from AutoSS-style aim behavior | User answers BZ-012 through BZ-014 |
| 2026-08-25 | Blaze cancels on player interference or outside sequence changes, reports abnormal-stop reasons, and defers the chest Etherwarp sequence until core shooting works | User answers BZ-015 through BZ-018 |
| 2026-08-26 | Blaze faces the room center and sneaks to the block edge before firing; stopped runs require stepping off and back on to re-arm | User answers FBZ-001 and FBZ-002 |
| 2026-08-26 | Blaze selects the leftmost hotbar shortbow and intentionally leaves that slot selected afterward | User answer FBZ-003 |
| 2026-08-26 | Blaze uses two-shot bursts with `40–50 ms` spacing, a configurable `500 ms` removal window, one retry burst, then aborts | User answer FBZ-004 |
| 2026-08-26 | Terminator requires a target-safe three-ray solution; keyboard/buttons cancel immediately and physical mouse movement has a one-second activation grace | User answers FBZ-005 and FBZ-006 |
| 2026-08-26 | Observable unexpected Blaze removal cancels the run; Blaze/start visuals use 3D bounding boxes rather than glow; abnormal stops are chat-only and completion is silent | User answers FBZ-007 through FBZ-009 |
| 2026-08-26 | Blaze advances immediately when the target is removed; the configured `500 ms` value is only a maximum removal timeout | User answer FBZ-010 |
| 2026-08-26 | Blaze uses the proposed green/yellow/red boxes, configurable green/orange lines, cyan start block, line-thickness range, and removal-timeout range/defaults | User answer FBZ-011 |
| 2026-08-26 | Blaze requirements are complete; begin the Creeper Beams interview while all implementation remains gated | Planning milestone |
| 2026-08-26 | Creeper Beams renders and activates at room-local `(0.5, 75.0, 0.5)`, uses Blaze's leftmost-hotbar shortbow contract, and applies safe Terminator fan validation | User answers CB-001, CB-004, and CB-005 |
| 2026-08-26 | Creeper Beams follows the current Kotlin solver, normalizes the exact duplicate, requires four unambiguous disjoint pairs, and never guesses through overlap | User answers CB-006 and CB-007 |
| 2026-08-26 | Each Creeper Beams pair is fired first endpoint then second endpoint, and pairs follow normalized solver/dataset order | User answers CB-008 and FCB-002 |
| 2026-08-26 | Creeper Beams fires each endpoint once without an intermediate result wait; both updates advance, a neither-updated result permits a full-pair retry, and exactly one update cancels | User answer CB-010 |
| 2026-08-26 | Endpoint states verify pair progress, room-green verifies final completion, outside changes cancel, and aiming uses AutoSS-derived visible-point motion | User answers CB-011 through CB-013 |
| 2026-08-26 | Any Creeper Beams cancellation locks automation for the remainder of that dungeon run; only a new run re-arms it | User answer CB-003 |
| 2026-08-26 | The earlier Creeper Beams Etherwarp statement was mistaken; `(0.5, 75.0, 0.5)` is the manually reached middle shooting block and there is no positioning automation | User correction to CB-002, consistent with the source audit |
| 2026-08-26 | Creeper Beams uses a `500 ms` maximum pair-result window and at most three total attempts or `5 s`; initial mixed state aborts | User confirmations FCB-003 through FCB-005 |
| 2026-08-26 | Creeper Beams reuses shared input cancellation, has independent Aim Speed and four-pair visual settings, omits chest automation, reports only abnormal chat reasons, and bundles its solver data | User confirmations CB-014 through CB-017 and FCB-006 |
| 2026-08-26 | Creeper Beams requirements are complete; begin the Ice Fill interview while all implementation remains gated | Planning milestone |
| 2026-08-26 | Ice Fill owns all three floors after one activation and crouch-walks straight up each connector's stone-brick stair without jumping | User answers IF-002, IF-004, and IF-007 |
| 2026-08-26 | Ice Fill preserves the trusted Hamiltonian route, requires a fresh all-ice board, treats packed ice as visited, and does not stall for per-tile acknowledgements | User answers IF-005 and IF-008 through IF-010 |
| 2026-08-26 | Ice Fill remains crouched, primarily strafes, Etherwarps for most forward advances and turn-block centering, and uses very slow AutoSS-derived turns | User answers IF-003, IF-004, IF-006, and IF-016; later frozen by FIF-004 through FIF-006 and FIF-012 |
| 2026-08-26 | Ice Fill terminally locks on interference/ordinary cancellation but treats a below-threshold lag fall as recoverable by Etherwarping to the active floor's calibrated pre-floor block and waiting for reset | User answers IF-012 through IF-015; later frozen by FIF-002, FIF-003, FIF-009, and FIF-010 |
| 2026-08-26 | Ice Fill keeps only the current Hamiltonian path rendering, uses room-green final completion, omits chest automation, is silent on success, and reports terminal failures in chat | User answers IF-017 and IF-018 |
| 2026-08-26 | Ice Fill activates on the calibrated first non-ice launch tile and Etherwarps to the first ice block exactly one block ahead | User answer FIF-001 |
| 2026-08-26 | Four config actions capture one room-relative fall threshold and three persisted non-ice floor launch/recovery blocks | User answers FIF-002 and FIF-003 |
| 2026-08-26 | Ice Fill selects the leftmost Etherwarp-capable hotbar item, uses Etherwarp for every forward on-ice step, strafes lateral runs, and crouch-walks only the stair transitions | User answers FIF-004 through FIF-006 |
| 2026-08-26 | A route Etherwarp gets no in-place retry; a wrong result is handled as a terminal non-fall deviation or a recoverable below-threshold fall | User answers FIF-007 and FIF-008 |
| 2026-08-26 | Ice Fill dynamically targets the active floor's saved recovery block, waits up to `10 s` for the whole floor to reset, and permits three fall recoveries per floor | User answers FIF-009 and FIF-010; normal reset is roughly `3 s` |
| 2026-08-26 | Initial packed ice locks Ice Fill for the run; visuals are only one configurable green path with configurable thickness | User answers FIF-011 and FIF-013 |
| 2026-08-26 | Ice Fill Turn Speed is `10%–80%` of normal AutoSS speed, default `40%`, step `1%`, with higher values turning faster | User final confirmation FIF-012 |
| 2026-08-26 | Remove Ice Path from Auto Puzzles entirely; the four tabs are Blaze, Creeper Beams, Ice Fill, and Waterboard | User scope correction; Waterboard becomes the final requirements interview |
| 2026-08-26 | Treat the old Waterboard automation as mechanically reliable and reuse all of its positions; humanize its camera/input presentation rather than replacing its successful sequence | User Waterboard baseline correction |
| 2026-08-26 | Waterboard activates immediately on recognized room entry, bundles `waterSolutions.json`, requires Etherwarp, and uses a contextual movement/Etherwarp mixture that explicitly avoids the hole in front of the water lever | User answers WB-001 through WB-003 |
| 2026-08-26 | Waterboard selects the leftmost Etherwarp item and pre-equips a shortbow—or `Dungeon Breaker` fallback—after reaching and aiming at a lever, well before its scheduled click | User answer WB-004 |
| 2026-08-26 | Waterboard reuses all old lever/approach/landing positions without capture buttons and replaces direct-looking behavior with AutoSS-derived, real-raycast aiming | User answers WB-005 and WB-006 |
| 2026-08-26 | Waterboard preserves the working controller's proven schedule ordering/timing, adds bounded lever-state confirmation, and terminally locks on outside state changes | User answers WB-007 through WB-009 |
| 2026-08-26 | Waterboard uses a safety-first bounded warp/fall fail-safe, completes at the end of the programmed pattern, shares physical-input cancellation, and locks after every cancellation | User answers WB-010 through WB-012 and subanswer 13 |
| 2026-08-26 | Waterboard renders only countdowns above levers, reports abnormal stops in chat, and remains silent on success | User subanswers 14 and 15 |
| 2026-08-26 | Waterboard uses Etherwarp for cross-room/platform and water-lever travel; ordinary walking is restricted to short, supported straight lines to avoid the central hole | User answer FWB-001; direction/input interpretation later frozen by FWB-008 |
| 2026-08-26 | Waterboard arms on room entry, waits for already-held controls to release before ownership, then applies the shared cancellation/grace contract | User answer FWB-002 |
| 2026-08-26 | Missing required hotbar items terminally lock Waterboard; unexpected lever state uses one `650 ms` wait and one retry before lockout; time zero remains the original water click | User answers FWB-003 and FWB-004 |
| 2026-08-26 | Waterboard has no chest automation, shows only the single current global click above its lever, and retains configurable `0–1000 ms` Action Delay with default `120 ms` | User answers FWB-005 through FWB-007 |
| 2026-08-26 | A Waterboard walking segment may face any direction, but it must finish rotating first, hold exactly one directional key, keep the camera fixed for the complete segment, and release movement before rotating again | User answer FWB-008 |
| 2026-08-26 | Waterboard uses one configurable green-by-default countdown color and one Aim Speed for both lever and Etherwarp camera motion | User answers FWB-009 and FWB-010 |
| 2026-08-26 | All four puzzle-specific specifications are complete; begin the final cross-puzzle product/default/order review while implementation remains gated | Planning milestone |
| 2026-08-26 | Disable the Auto Puzzles parent and every individual puzzle tab by default; all five states are opt-in | User answer X-001 |
| 2026-08-26 | Omit a General tab, implement Blaze → Creeper Beams → Waterboard → Ice Fill, and use the approved module description | User answers X-002 through X-004 |
| 2026-08-26 | Use a minimal full-tab-list puzzle-state tracker for authoritative Creeper Beams/Ice Fill green state rather than introducing a full dungeon-map parser | Final codebase design review; current CGC has no puzzle completion API |
| 2026-08-26 | Observe real keyboard/mouse callbacks for takeover detection so generated keys/raw-count camera motion cannot self-cancel | Final codebase design review and shared cancellation requirement |
| 2026-08-26 | Extend the renderer compatibly for per-line width and one Waterboard billboard countdown; preserve existing line callers such as Ender Pearl Trajectory | Final codebase design review and confirmed visual requirements |
| 2026-08-26 | Approve execution of the complete Auto Puzzles plan | User: “Approved—implement Auto Puzzles” |
| 2026-08-26 | Complete all four source controllers, shared foundations, bundled resources, automated tests, and production build; retain every named in-client gate and the Terminator calibration lock | Implementation and safety audit |
| 2026-08-26 | Implement a command-driven Terminator capture tool; the user confirms its three-arrow fan has no randomization and is identical on every shot | User approval of the proposed `/cgc termcal` calibration workflow |
| 2026-08-26 | Replace packet-owner-only Terminator collection after repeated live `0/3` failures with client entity-load plus live-world observation, retained origins, conservative missing-owner correlation, and rejection diagnostics | User live result and capture-path investigation |
| 2026-08-26 | Discard automatic Arrow observation after the fallback also fails; manually set one symmetric per-side fan angle and visualize center/left/right rays on each real Terminator click | User-requested simplified calibration method |
| 2026-08-27 | Fix the Terminator fan at the visually confirmed `5°` per side, remove the calibration command/preview, and use the constant directly in Blaze/Creeper three-ray safety planning | User-confirmed calibration and cleanup request |

## Revision history

### Revision 0.21 — 2026-08-27

- Recorded the user's completed visual calibration: Terminator's deterministic side arrows are each offset by exactly `5°` of yaw from the ordinary center arrow.
- Hardcoded that value in the backend fan geometry and removed every user-facing calibration command, preview class, renderer/tick/world/packet hook, and help reference.
- Removed the Blaze, Creeper Beams, and projectile-planner calibration rejection gates so a leftmost hotbar Terminator is now eligible.
- Implemented center-first plus left/right-compensated candidate search and independent three-ray first-collision checks. At least one arrow must hit the intended target first and none may hit another active Blaze or selected beam endpoint first.
- Applied live fan revalidation before both Blaze burst shots and each Creeper endpoint shot, not only during initial aim planning.
- Replaced preview tests with hardcoded-angle/lane-compensation fixtures, added center-hit/side-hit/unintended/no-hit fan-safety fixtures, and changed both command-tree tests to prove `termcal` is absent.
- A fresh clean build passes `71/71`; after removing the final obsolete runtime wording, the replacement JAR SHA-256 is `522A41017EA2ADB02A8A2215CC85B24CCDFBB98DFE2F43C819E34C18C44B17E9`. Terminator puzzle behavior remains pending direct in-client validation.

### Revision 0.20 — 2026-08-26

- Recorded that revision 0.19 also failed in the user's live client and removed the complete entity-capture/report pipeline instead of adding another speculative observer.
- Added `/cgc termcal set <degrees>` to both client command paths, removed `/save`, and converted start/status/cancel/help to a manual fan-preview session.
- Each real main-hand Terminator use now freezes its exact packet yaw/pitch and eye origin; the renderer displays a `40`-block green vanilla center ray and mathematically symmetric orange side rays at the configured per-side yaw angle.
- Kept the preview world-anchored, made later angle changes update it immediately, and clear it on another click, cancel, player/world/dimension change, or disconnect as appropriate.
- Replaced the old analyzer/capture fixtures with seven tests covering vanilla center direction, symmetric side rotation, retained pitch, center invariance while adjusting, yaw wrap tolerance, common origin/length, and command bounds. Both command trees now prove `set|start|status|cancel|help`.
- A fresh clean build followed by the final incremental verification passes `64/64`; the replacement JAR SHA-256 is `D79B054810ECDA18D1EAD8F5D7B292F6452CC93F5D194A777C077B003FE8D0BB`.
- Kept Terminator automation safety-gated until the user visually selects the final angle and the three-ray collision model and fixtures are implemented.

### Revision 0.19 — 2026-08-26

- Recorded the user's live result: every sample in the original recorder timed out with zero accepted Arrow packets, so the 20-shot workflow could not begin.
- Added Fabric's official client entity-load event as the primary Arrow observation path and a baseline-filtered live-world scan as a fallback; retained the add-entity packet as supplemental metadata rather than the sole capture mechanism.
- Preserved the first observed launch position while waiting for velocity, reconciled owner information across observations, continued to reject known foreign owners, and allowed missing owner metadata only when the strict time, proximity, and direction correlation succeeds.
- Upgraded reports to format version 2 with owner association, capture source, and entity age, and added compact timeout diagnostics for candidate count, sources, owners, and filter outcomes.
- Expanded the capture-policy fixture to prove missing-owner acceptance and exact rejection reasons. The complete clean build passes `63/63`; the replacement JAR SHA-256 is `9DE0E8C25694A9163E82B226C61538FF378A0D5852FE163593040A37F1502CFB`.
- Kept the Terminator automation safety gate closed. Revision 0.19 still requires a one-shot live confirmation before the user spends time completing all 20 samples.

### Revision 0.18 — 2026-08-26

- Added `/cgc termcal start|status|cancel|save|help` to both CGC client command paths; the recorder operates while Auto Puzzles is disabled and never emits aim, movement, slot, use, or packet actions.
- Added strict association between a real uncancelled Terminator use and exactly three timely, nearby, aligned Arrow spawn packets owned by the local player, plus safe cancellation on session/world/player changes.
- Added the 20-shot interleaved level/up/down capture schedule, raw origin/velocity report, camera-local trajectory measurements, deterministic lane/symmetry/variance/yaw-coverage validation, atomic persistence, previous-file backup, and failed-write retry.
- Added seven focused tests for both command trees, the capture schedule, stable fan, angle wrapping, changing-spread rejection, pose-coverage rejection, and Arrow candidate policy. The complete suite now passes `63/63`; a fresh clean build and final incremental build produced JAR SHA-256 `1B59C04E5775D8397048995440CAF5EDCAEA1F052AD3BB0FA313709E0478DF96`.
- Kept Terminator ineligible for Blaze/Creeper automation after capture-tool implementation. A live passing JSON report still must be reviewed and integrated into full three-ray first-collision fixtures and live puzzle validation before that safety gate can be removed.

### Revision 0.17 — 2026-08-26

- Recorded completion of the parent module, shared foundations, four puzzle solvers/controllers, resources, mixins, renderer additions, calibration persistence, and automated build work.
- Recorded the final `56/56` passing-test result, successful clean/final Gradle builds, production JAR path/hash, and packaged-resource verification.
- Marked source/automated checklist items complete while leaving GUI restart behavior and all Minecraft in-client scenario matrices open.
- Reconciled actual implementation details: Ice Fill action compilation lives in its controller, Waterboard position data targets support blocks after transforming fractional coordinates, and normal post-water goal-gate openings are distinguished from interference.
- Preserved the explicit Terminator safety gate: no fan constant was guessed, so Blaze/Creeper automation remains ineligible for Terminator until live measurement and fixtures are complete.
- Kept Blaze chest collection and every puzzle reward-chest action deferred/out of scope as approved.

### Revision 0.16 — 2026-08-26

- Recorded the user's explicit approval to implement Auto Puzzles.
- Closed the Phase 1 execution gate and began Phase 2 with the shared module/config and safety foundations.
- Kept the frozen puzzle requirements and implementation order unchanged.

### Revision 0.15 — 2026-08-26

- Recorded X-001 through X-004: parent and all four puzzle tabs default disabled, no General tab, implementation order Blaze → Creeper Beams → Waterboard → Ice Fill, and the approved module description.
- Resolved every implementation-affecting `TBD`, reconciled the shared requirements, and closed D-002, D-005, D-007, and D-017.
- Completed a concrete source/resource/test manifest, exact runtime-hook plan, run identity and same-run lock semantics, physical/generated input separation, exclusive input ownership, and vanilla-client action mapping.
- Identified and designed the missing minimal tab-list puzzle-green tracker required by Creeper Beams and Ice Fill, with an explicit live-client validation gate rather than an assumed completion signal.
- Defined compatible `CgcRenderer3D` line-width and billboard-text extensions, exact RGB defaults, pinned resource paths/provenance, Ice Fill calibration persistence, and the Waterboard route-selection safety rule.
- Verified CC0 provenance for CGC/Noamm data and BSD-3-Clause provenance for the RSZ Waterboard positions/workflow; required packaging of RSZ's complete license notice with the derived resource.
- Corrected the target-version record: the source directory remains named `cgc-1.21.10`, while its current Gradle configuration and mappings target Minecraft `26.1.2`.
- Inspected the attached `EnderPearlTrajectory.kt` as confirmation of CGC's `WorldRenderExtractModule`/queued-line integration; no change to that module is planned beyond preserving compatibility with its existing call.
- Kept all feature implementation gated on the user's explicit approval of this revision; no feature source/resource code was added or modified.

### Revision 0.14 — 2026-08-26

- Recorded FWB-008 through FWB-010 and completed the Waterboard specification.
- Finalized arbitrary-heading straight movement with pre-rotation, exactly one held directional key, a fully frozen in-motion camera, and movement release before any new turn.
- Froze one green-by-default configurable current-click countdown color and one shared Waterboard Aim Speed for lever and Etherwarp aiming.
- Updated the controller, settings, acceptance criteria, automated/in-client verification plan, Phase 0 checklist, and decision register; promoted the plan to final cross-puzzle review.
- Rechecked `CgcModule`, `SubModule`, and TriggerBot's real default/group behavior and added X-001 through X-004 for the only remaining product-level defaults, General-tab, implementation-order, and description decisions.
- Did not implement or modify feature code.

### Revision 0.13 — 2026-08-26

- Recorded FWB-001 through FWB-007: straight-line-only ordinary walking, safe entry-input baselining, hotbar-only required-item failure, finite lever confirmation/retry, no chest automation, single-current-click rendering, and the retained Action Delay setting.
- Updated the Waterboard controller, settings, acceptance criteria, validation matrix, shared input decision, and status/debug decision to match.
- Reduced the Waterboard interview to three final details: cardinal versus diagonal straight walking, countdown color, and whether one Aim Speed governs both lever and Etherwarp camera motion.
- Did not implement or modify feature code.

### Revision 0.12 — 2026-08-26

- Recorded WB-001 through WB-012 plus the user's three WB-012 subanswers and corrected the source assessment: the old Waterboard controller is mechanically reliable but visually inhuman.
- Confirmed immediate room-entry activation, bundled solution data, required Etherwarp, contextual movement around the central hole, early shortbow/`Dungeon Breaker` selection, reuse of every old position, AutoSS-derived real-raycast aiming, terminal same-run lockout, pattern-end completion, countdown-only rendering, chat-only failures, and silent success.
- Added the exact old lever/approach/Etherwarp/rescue coordinates, a provisional controller, the delegated bounded warp/fall fail-safe, draft settings, seventeen acceptance criteria, and a full automated/in-client verification outline.
- Isolated seven focused follow-ups instead of guessing about movement selection, entry-time held input, missing items, acknowledgement bounds, chest handling, countdown details, or Action Delay configuration.
- Did not implement or modify feature code.

### Revision 0.11 — 2026-08-26

- Removed Ice Path from the product scope, source inventory, architecture, questionnaire, implementation sequence, risk/open-decision wording, and validation counts.
- Renumbered Waterboard as Puzzle 4, promoted it to the active final interview, and expanded its twelve source-grounded questions into answerable decisions with proposed defaults.
- Preserved only this revision record and the matching decision-log entry so the scope change remains auditable.
- Did not implement or modify feature code.

### Revision 0.10 — 2026-08-26

- Resolved the last Ice Fill question: Turn Speed is a percentage of normal AutoSS speed, `10%–80%`, default `40%`, step `1%`, and higher is faster.
- Froze the Ice Fill settings, acceptance criteria, and verification plan and marked its Phase 0 requirements item complete.
- Did not implement or modify feature code.

### Revision 0.9 — 2026-08-26

- Resolved the activation point as the calibrated non-ice launch tile, with the first ice block one step ahead reached by Etherwarp.
- Confirmed the four persisted room-relative calibration actions and the shared floor-entry/recovery meaning of each floor marker.
- Finalized leftmost Etherwarp-item selection, exact forward-Etherwarp/lateral-strafe/stair-walk classification, deterministic camera framing, and no in-place retry after a wrong route warp.
- Bounded fall recovery to three attempts per floor and a `10 s` whole-floor reset wait, incorporating the observed approximately `3 s` normal reset.
- Confirmed same-run initial-packed lockout and minimal path-only visuals.
- Added the draft Ice Fill settings table, twenty-three acceptance criteria, and full automated/in-client verification coverage.
- Left only the `10–80` Turn Speed unit/direction interpretation unresolved; no implementation or feature code change was made.

### Revision 0.8 — 2026-08-26

- Recorded IF-002 through IF-018, leaving IF-001 open because the original activation wording was unclear.
- Replaced the proposed ordinary walking controller with the requested crouched strafe/Etherwarp model and a much-slower AutoSS-derived turn profile.
- Specified continuous stair transitions, fresh-board-only eligibility, exact solver-route preservation, position-led progress without per-tile acknowledgement waits, and terminal interference lockout.
- Added the recoverable-fall state: calibrated Y detection, active-floor recovery Etherwarp, wait for regenerated ice, and same-floor retry.
- Added a provisional thirteen-state controller plus thirteen focused questions for activation, persisted calibration, Etherwarp selection/step classification/landing validation, deviation response, recovery bounds, slow-turn settings, and minimal visuals.
- Did not implement or modify feature code.

### Revision 0.7 — 2026-08-26

- Corrected CB-002: the player manually reaches the middle start/shooting block and Creeper Beams has no Etherwarp or other positioning phase.
- Recorded all eight follow-up confirmations: solver-order pairs, `500 ms` result window, three-attempt/`5 s` ceiling, initial-mixed-board abort, shared input cancellation, confirmed visuals/Aim Speed, silent room-green completion without a chest action, and bundled solver data.
- Finalized the Creeper Beams state machine, settings, eighteen acceptance criteria, and automated/in-client verification plan.
- Marked Creeper Beams complete in Phase 0 and promoted Ice Fill to the active requirements interview with eighteen source-specific questions.
- Did not implement or modify feature code.

### Revision 0.6 — 2026-08-26

- Recorded the user's CB-001 through CB-013 answers and converted the confirmed portions into a provisional Creeper Beams controller state machine.
- Defined the one-dungeon-run cancellation lockout, shared shortbow/Terminator safety contract, trusted pair normalization, strict first-then-second endpoint order, pair-unit result verification, and outside-change cancellation.
- Preserved the distinction between pair endpoint order (confirmed) and ordering among the four pairs (still unresolved).
- Audited the remembered Etherwarp claim against every located Creeper Beams controller. Neither attached `Beams.java` nor its RSZ copy contains Etherwarp behavior, so no route coordinate, item sequence, or landing check was invented.
- Added focused decisions for the missing Etherwarp route, update window, finite retry ceiling, rare initial mixed state, Aim Speed setting, and CB-014 through CB-017.
- Added fourteen draft Creeper Beams acceptance criteria without marking the puzzle specification complete or starting implementation.

### Revision 0.5 — 2026-08-26

- Recorded FBZ-010 and FBZ-011 and froze the Blaze settings table.
- Added nineteen Blaze acceptance criteria plus automated and in-client verification coverage.
- Marked the Blaze requirements checklist complete without starting implementation.
- Promoted Creeper Beams to the active interview and replaced its queued prompts with seventeen source-specific questions.
- Documented the trusted/mixed endpoint logic, old start coordinate, duplicate/overlap concern, and unreliable old progression behavior that the Creeper controller must resolve.
- Did not implement or modify feature code.

### Revision 0.4 — 2026-08-26

- Recorded all FBZ-001 through FBZ-009 answers.
- Finalized start-block re-arming, sneak-to-edge positioning, leftmost shortbow selection, and no hotbar restoration.
- Defined the two-burst retry budget and `40–50 ms` intra-burst timing around a configurable `500 ms` removal timeout.
- Confirmed complete Terminator fan safety and the physical input cancellation/grace rules.
- Replaced entity glow with 3D Blaze hitbox boxes and added start-block box rendering.
- Restricted abnormal-stop feedback to chat and made successful completion silent.
- Reduced the remaining Blaze interview to FBZ-010 and FBZ-011.
- Did not implement or modify feature code.

### Revision 0.3 — 2026-08-25

- Recorded C-001 and all BZ-001 through BZ-018 answers.
- Resolved the one-connected-feature UX: each puzzle tab jointly controls solver visuals and automatic behavior.
- Mapped confirmed Higher/Lower room IDs, order, and old standing coordinates.
- Drafted the Blaze lifecycle, dynamic Terminator-safe aim contract, all-shortbow recognition, and AutoSS aim reuse.
- Added the initial Blaze settings table and isolated nine focused follow-ups rather than guessing at ambiguous behavior.
- Split the post-puzzle Etherwarp/chest collection into a known deferred Blaze extension.
- Did not implement or modify feature code.

### Revision 0.2 — 2026-08-25

- Read and fingerprinted the supplied files in `Example puzzles Solvers`.
- Verified that the in-scope attached Kotlin solvers are identical to the workspace's NoammAddons source copies.
- Traced and inspected the current remote Beams and Waterboard solver data at NoammAddons data commit `004c8a0022995da6e17a0a5da7e6452e576df7df`.
- Established the reference-source authority order.
- Recorded trusted solver behavior, reusable old coordinates/ideas, and concrete defects or missing contracts in the old automation for every puzzle.
- Added the common solver-versus-automation question, the full Blaze questionnaire, and queued question sets for the remaining puzzles.
- Did not implement or modify feature code.

### Revision 0.1 — 2026-08-25

- Created the planning source of truth.
- Recorded the confirmed feature shell and tab order.
- Documented verified module, config, UI, room-scanner, and room-data integration points.
- Added the proposed modular architecture, planning gates, validation strategy, risk register, and per-puzzle requirement templates.
- Left puzzle behavior uncommitted pending one-by-one specification with the user.

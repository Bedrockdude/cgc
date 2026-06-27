# Feature Map

Use this file as the high-level map from feature to owning files in `cgc`.

## Current Features

### Mod bootstrap

- Owner files:
  - `src/main/kotlin/cgc/cgc/Cgc.kt`
- Responsibility:
  - Fabric mod initialization
  - shared mod id helpers

### Module framework and Click GUI

- Owner files:
  - `src/main/kotlin/cgc/cgc/module/`
  - `src/main/kotlin/cgc/cgc/config/CgcSettings.kt`
  - `src/main/kotlin/cgc/cgc/config/CgcConfigStore.kt`
  - `src/main/kotlin/cgc/cgc/client/CgcClient.kt`
  - `src/main/kotlin/cgc/cgc/client/gui/CgcConfigScreen.kt`
- Responsibility:
  - common module/category/grouped settings registry based on RSM
  - concrete setting types under `module/setting/`: boolean, mode, number, string, multi-bool, keybind, button, colour, sound, drag, and save-backed settings
  - grouped JSON config save/load for module settings and mod-level settings
  - mod-level GUI settings separate from modules
  - client `/cgc` command
  - left-side expandable category GUI inspired by RSM, with shared controls for all setting types
- Related example references:
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/ui/clickgui/`
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/module/`
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/ui/clickgui/settings/`
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/utils/ConfigUtils.java`

### Auto SS Specsafe

- Owner files:
  - `src/main/kotlin/cgc/cgc/module/impl/dungeon/AutoSSSpecsafe.kt`
  - `src/main/kotlin/cgc/cgc/module/ClientTickModule.kt`
  - `src/main/kotlin/cgc/cgc/data/Keybind.kt`
- Responsibility:
  - first real module registered under Dungeons for testing module settings in `/cgc`
  - CGC-native port of RSZ's Auto SS Specsafe settings and Simon Says state machine
  - client tick runtime hook and keybind polling
- Related example references:
  - `../Example Skyblock mods/RSZ/src/main/java/com/Bedrock/module/impl/dungeon/AutoSSSpecsafe.java`
- Notes:
  - RSM-only systems still need CGC equivalents before every copied hook can behave exactly: dungeon location/phase detection, block-change events, render events, and AutoLeap completion callback.

### SS Trigger-bot

- Owner files:
  - `src/main/kotlin/cgc/cgc/module/impl/dungeon/SSTriggerBot.kt`
  - `src/main/kotlin/cgc/cgc/module/CgcModules.kt`
- Responsibility:
  - dungeon module that tracks spectator safe Simon Says patterns
  - auto-starts only when the player is already hovering the start button
  - clicks the next correct button only when the player hovers it within the configured aim tolerance
- Related example references:
  - `../Example Skyblock mods/RSZ/src/main/java/com/Bedrock/module/impl/dungeon/AutoSSSpecsafe.java`

### Runtime 3D Renderer

- Owner files:
  - `src/main/kotlin/cgc/cgc/runtime/CgcRenderer3D.kt`
  - `src/main/kotlin/cgc/cgc/client/CgcClient.kt`
- Responsibility:
  - queued 3D box rendering for modules that need RSM-style outline and filled-outline tasks
  - flushes queued tasks after module world rendering
- Related example references:
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/component/impl/Renderer3D.java`
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/utils/render/render3d/VertexRenderer.java`
  - `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0/src/main/java/com/ricedotwho/rsm/utils/render/render3d/type/`

## Planned Features

Add sections as the mod grows:

```md
### <feature name>
- Status: planned | in progress | complete
- Owner files:
  - <path>
- Related resources:
  - <path>
- Related example references:
  - <example path>
- Notes:
  - <key constraints or design rules>
```

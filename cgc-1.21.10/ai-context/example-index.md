# Example Mod Index

Use this file to explain which example project demonstrates which idea.

## Available Reference Folders

### `../Example Skyblock mods/RSZ`

Use for:

- module layout ideas
- command structure ideas
- component organization
- movement/pathing examples
- mixin examples

Notes:

- This folder contains build output and generated files. Prefer `src/main/` content over `build/` or `bin/`.

### `../Example Skyblock mods/rsa-main/rsa-main`

Use for:

- larger-scale project organization
- utility layout
- pathfinding structures
- screen/gui examples
- packet/network-related patterns

Notes:

- Prefer source files under `src/main/java/`.

### `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0`

Use for:

- older or alternate module/component patterns
- resource/data layout examples
- puzzle and dungeon helper implementations

Notes:

- This project also includes compiled output. Ignore `build/` and `bin/` unless debugging artifact contents.

## Mapping Template

When you identify a useful example, add an entry like this:

```md
## Feature: <name>
- Example project: <folder>
- Source file(s): <path>
- Why it matters: <short reason>
- What to copy vs what to avoid: <short note>
```

## Feature: Module Click GUI

- Example project: `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0`
- Source file(s):
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/RSMConfig.java`
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/impl/Panel.java`
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/impl/category/CategoryComponent.java`
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/impl/module/ModuleComponent.java`
  - `src/main/java/com/ricedotwho/rsm/module/api/Category.java`
  - `src/main/java/com/ricedotwho/rsm/module/Module.java`
- Why it matters: RSM shows the target module/category/settings organization and dark config UI style.
- What to copy vs what to avoid: Copy the module/category/component ideas and visual language. Avoid copying NanoVG-specific rendering until CGC intentionally adopts that renderer.

## Feature: Module Settings Framework

- Example project: `../Example Skyblock mods/rsm-1.0.0/rsm-1.0.0`
- Source file(s):
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/settings/Setting.java`
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/settings/group/GroupSetting.java`
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/settings/group/DefaultGroupSetting.java`
  - `src/main/java/com/ricedotwho/rsm/ui/clickgui/settings/impl/`
  - `src/main/java/com/ricedotwho/rsm/utils/ConfigUtils.java`
- Why it matters: CGC's module settings should use the same grouped Setting/SubModule model and JSON persistence shape.
- What to copy vs what to avoid: Copy the framework concepts and active setting types. Avoid relying on RSM's event bus/NanoVG-specific code until CGC has equivalent systems.

## Feature: Auto SS Specsafe

- Example project: `../Example Skyblock mods/RSZ`
- Source file(s):
  - `src/main/java/com/Bedrock/module/impl/dungeon/AutoSSSpecsafe.java`
- Why it matters: This is the first real dungeon module copied into CGC and exercises keybind, boolean, number, and colour settings.
- What to copy vs what to avoid: Copy the module settings and Simon Says state machine. RSM/RSZ infrastructure dependencies need CGC-native replacements before the port can be completely identical at runtime.

## Feature: DN Yapper

- Example project: `../Example Skyblock mods/RSZ`
- Source file(s):
  - `src/main/java/com/Bedrock/module/impl/other/DNYapper.java`
  - `src/main/java/com/Bedrock/mixins/accessor/AccessorAbstractContainerScreen.java`
- Why it matters: This is the first non-SS module ported from an example mod into CGC-native module registration.
- What to copy vs what to avoid: Port the module into CGC's own `CgcModule`, `KeybindSetting`, and config lifecycle. Keep only the hovered-slot accessor mixin needed by the feature; do not bundle or initialize the legacy RSM/RSZ runtime.

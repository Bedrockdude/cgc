# Project Overview

## Primary Project

- Name: `cgc`
- Target project folder: `cgc-1.21.10`
- Platform: Fabric
- Language: Kotlin for main entrypoint, Java/Kotlin may both appear as the mod grows

## Current State

- This project is in setup/early structure phase with an RSM-inspired module, grouped setting, config, and Click GUI framework.
- Current entrypoint: `src/main/kotlin/cgc/cgc/Cgc.kt`
- Current client entrypoint: `src/main/kotlin/cgc/cgc/client/CgcClient.kt`
- Current resources:
  - `src/main/resources/fabric.mod.json`
  - `src/main/resources/cgc.mixins.json`

## Agent Expectations

- Treat the current codebase as the source of truth.
- Use example mods only to understand patterns, APIs, or implementation approaches.
- When introducing a new package or subsystem, add a short note here describing its purpose.

## Important Paths

- Mod entrypoint: `src/main/kotlin/cgc/cgc/Cgc.kt`
- Client entrypoint: `src/main/kotlin/cgc/cgc/client/CgcClient.kt`
- Module framework: `src/main/kotlin/cgc/cgc/module/`
- Mod settings: `src/main/kotlin/cgc/cgc/config/CgcSettings.kt`
- Config persistence: `src/main/kotlin/cgc/cgc/config/CgcConfigStore.kt`
- Setting framework: `src/main/kotlin/cgc/cgc/module/setting/`
- Config GUI: `src/main/kotlin/cgc/cgc/client/gui/CgcConfigScreen.kt`
- Java source root: `src/main/java/`
- Kotlin source root: `src/main/kotlin/`
- Resources root: `src/main/resources/`

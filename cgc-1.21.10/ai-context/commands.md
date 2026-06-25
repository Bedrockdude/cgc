# Agent Commands And Conventions

Use this file for commands, routines, and repo-specific operating rules that an AI agent should follow.

## Basic Commands

### Build

```powershell
.\gradlew.bat build
```

### Run client

```powershell
.\gradlew.bat runClient
```

## Search Priorities

When looking for examples:

1. Search inside `cgc-1.21.10/src/main/` first.
2. Then search source files inside example mods.
3. Ignore `build/`, `bin/`, and generated outputs unless specifically needed.

## Local Conventions

- Prefer small, documented subsystems.
- Keep package names consistent with feature ownership.
- If a new folder structure is introduced, update `feature-map.md`.
- If a new example file becomes a key reference, update `example-index.md`.

## Useful Placeholder Sections

Add repo-specific instructions here over time, for example:

- naming rules
- event bus conventions
- module registration rules
- mixin naming rules
- logging rules

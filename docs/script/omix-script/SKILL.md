---
name: omix-script
description: Develop and debug trusted Java 21 source scripts for the Omix Minecraft 1.21.11 client, using its live MCP or in-game Agent tools. Use for new modules, module modes, commands, HUDs, and client modifications; includes API discovery, source version checks, compile/apply jobs, logs, and screenshots.
---

# Omix script development

Read [client knowledge](references/common-knowledge.md) and [API guide](references/api-guide.md) before changing game behavior. This client uses Minecraft 1.21.11 and its locked Yarn names; do not copy old 1.8 packet names or Raven interfaces. Read [mode rules](references/modes.md) when extending a built-in module, [lifecycle](references/lifecycle.md) for ownership and reloads, [MCP setup](references/mcp.md) when connecting externally.

## Workflow

1. Call omix_status and script_status; identify the actual game instance, running generations, and disk hashes. Offline docs remain usable without Minecraft.
2. Search script_api for exact current declarations. Read script_reference for detailed rules. Use script_templates and the bundled examples as executable starting points.
3. Read existing source and retain its hash. Write a Java class-body fragment with imports, fields and onLoad, without package or outer class. Register independent features in onLoad; do gameplay work in their lifecycle/events.
4. Call script_write with expectedHash from the read (empty only for new files). On conflict, reread and reconcile; never silently overwrite another editor.
5. Call script_action check, then poll script_job. Fix source line diagnostics. A saved or checked script is not running.
6. Call load/reload, poll to loaded, and verify actual generation/runningHash through script_status. Failed compilation must leave the old version usable.
7. Inspect script_logs, game state, and script_screenshot. Test the requested behavior and unload/reload cleanup. Report tested behavior and any untested game/platform cases accurately.
8. Call `release_game_session` when finished using exclusive game tools.

## Implementation rules

- Register stable local IDs; one source may own multiple modules, modes, commands and HUDs.
- Use feature-owned resources for enable/disable cycles and script-owned resources for a whole generation.
- Network callbacks remain on their emitting thread; synchronous cancel must happen there. Copy immutable data and schedule world/UI operations through tasks.client.
- Submit rotations through RotationRequestEvent with a unique owner; preserve movement correction. Use owner-scoped Blink/Delay/Timer; never globally reset another module's resources.
- Use modes.register for whole-module takeover and typed ModeHooks for synchronous return values. Check references/mode-hosts.json for actual selectors.
- Keep rendering inside the matching event/HUD callback and use current frame objects.
- Temporary evaluation is trusted Java on the client thread after background compilation. Keep it short and bounded; persistent features belong in source files.
- Native Java is permitted, but untracked threads, I/O and sent network operations cannot be automatically undone.

[Tool schemas and job states](references/tools.md) are shared with the client. [API index](references/api.json) contains actual source declarations; search narrowly instead of loading the entire SDK. [Examples](examples/index.json) covers modules, Speed/Behavior modes, commands, HUD, rotation, packet observation, FisProxy and native Java.

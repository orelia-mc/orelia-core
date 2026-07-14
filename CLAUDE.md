# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`orelia-core` is a Paper 1.21.x (Java 21) Minecraft plugin — the foundation of a 3-plugin RPG suite:

- **orelia-core** (this repo): Core, Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Gathering, Database, API, Util
- `orelia-world` (separate repo): Quest, NPC, Dialogue, Story, Dungeon, Region, CutScene, Event
- `orelia-extra` (separate repo, not yet implemented): Party, Guild, Trade, ...

The other two plugins depend on this one (`depend: [OreliaCore]`) and talk to it **only** through `rpg.api`, published via Bukkit's `ServicesManager` — never by reaching into this plugin's internal module classes.

## Commands

```
./gradlew build
```

Produces `build/libs/orelia-core-1.0.0.jar` (shadowJar, relocated sqlite/mysql/protobuf under `rpg.database.libs.*`). Requires network access to `repo.papermc.io` (Paper API) and `jitpack.io` (Vault API).

```
./gradlew test                              # all tests
./gradlew test --tests "rpg.status.*"       # a package
./gradlew test --tests "FooTest.someMethod" # a single test method
```

In-game: `/oladmin reload` reloads every module's config file without a server restart.

## Architecture

### Module system

`OreliaPlugin` (`rpg/core/OreliaPlugin.java`) is the single entry point. It owns process-wide singletons — `ConfigManager`, `SchedulerService`, `PlayerDataManager`, `ModuleManager` — and registers every top-level feature as an `RpgModule` (`rpg/core/module/RpgModule.java`) in a fixed order in `onEnable()`.

- **Registration order is dependency order.** A module may look up an earlier-registered module via `ModuleManager#get(Class)`, never a later one. Current order: Database → Status → Job → Gathering → Item → Skill → Accessory → Effect → Economy → Monster → Boss → Gui → **Api (always last)**.
- Modules are enabled in registration order, **disabled in reverse order**.
- Each module's `onEnable` typically: registers its config file with `ConfigManager`, loads a repository from that YAML, builds its services/managers, registers Bukkit listeners, and registers its player-facing subcommand into `PlayerCommandRegistry`.
- `onReload()` is optional (default no-op); implement it to re-read config and rebuild repositories in place — see `ItemModule.reloadWeapons()` for the pattern.
- Do not let one module reach into another module's internal classes (managers/services/repositories) directly — go through the other module's public getters on its `RpgModule`, or through `rpg.api` if the consumer is an external plugin.

### Per-module package shape

Most feature packages (`item`, `skill`, `job`, `status`, `accessory`, `monster`, `boss`, `effect`, `economy`, `gui`, `gathering`) follow the same internal layering:

- `repository/` — pure data access: either config-driven (parses a `*.yml` into in-memory templates) or DB-backed (implements `SchemaOwner` from `rpg/database/repository/SchemaOwner.java` and talks only to `DatabaseManager`). Never touches Bukkit events or game logic.
- `model/` — plain data holders (templates, per-player components).
- `service/` or `manager/` — business logic sitting on top of the repository.
- `listener/` — Bukkit event handlers wired in `onEnable`.
- `command/` — `CommandExecutor`s registered into the shared `/ol` or `/oladmin` dispatcher (see below), not their own top-level Bukkit commands.

### Config

Every module reads its own file under `src/main/resources/`: `config.yml`, `items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `messages.yml`. `ConfigManager.register(name)` copies the bundled default out of the jar on first use and returns a cached `ConfigFile`; `ConfigManager` never inspects module-specific keys. Reload all of them via `/oladmin reload`, which calls `ConfigManager.reloadAll()` then `ModuleManager.reloadAll()`.

### Player data

`PlayerData` (`rpg/core/player/`) is the runtime container for one online player's cross-module state, keyed by UUID. Core only manages identity (UUID/name) plus a `Map<Class<? extends PlayerDataComponent>, PlayerDataComponent>` — it has no idea what a component contains. Each module defines its own `PlayerDataComponent` (e.g. `PlayerJobComponent`, `PlayerSkillComponent`, `PlayerStatusComponent`) and a loader (`PlayerDataComponentLoader`) that attaches it on join. Use `PlayerData.require(Class)` when a module can guarantee its own loader ran; it throws loudly instead of allowing silent null-stat bugs.

### Database

`DatabaseModule` builds one `DatabaseManager` (SQLite or MySQL, via `ConnectionProvider` — see `DatabaseType`) that every module's repository shares to obtain a JDBC `Connection`. `DatabaseManager` owns no schemas itself: each repository creates/migrates its own tables (`SchemaOwner`) on top of the shared connection, keeping data-access ownership with the module that needs it.

### Commands

There are exactly two top-level Bukkit commands, both dispatchers: `/ol` (player-facing, `OlRootCommand` + `PlayerCommandRegistry`) and `/oladmin` (admin-gated, `AdminCommand` + `AdminCommandRegistry`). Both registries (`OlCommandRegistry` subclasses) are published via `ServicesManager` so `orelia-world`/`orelia-extra` can register their own subcommands into the same two entry points instead of claiming new top-level commands. When adding a player command to a module, register it into `plugin.getPlayerCommandRegistry()` inside that module's `onEnable`, not as a new `plugin.yml` command.

### Public API (`rpg.api`)

`ApiModule` is always the last module enabled. It wraps each module's service in a narrow `*Api`/`*ApiImpl` pair (`OreliaApi`, `StatusApi`, `JobApi`, `ItemApi`, `AccessoryApi`, `SkillApi`, `GuiApi`, `EffectApi`, `CombatApi`) and publishes them — plus the generic `PlayerDataManager` and `DatabaseManager` — through Bukkit's `ServicesManager`. This is the **only** integration surface for other plugins; when adding a new cross-plugin capability, add/extend an `*Api` interface here rather than exposing an internal manager class.

### Cross-module dependency conventions

- `ItemModule` depends on `JobModule` + `StatusModule` (weapon requirement checks).
- `GatheringModule` depends on `JobModule` (looks up the player's current job display name for level-up messages).
- `ApiModule` depends on nearly everything (it's last).
- When a module needs another module's service, fetch it once in `onEnable` via `plugin.getModuleManager().get(OtherModule.class).orElseThrow(...)` — fail fast with a clear `IllegalStateException` if the dependency isn't registered yet, rather than deferring the lookup.

## Committing changes

When committing, also update README.md and README_EN.md accordingly.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`orelia-core` is a Paper 1.21.x (Java 21) Minecraft plugin — the foundation of a 3-plugin RPG suite:

- **orelia-core** (this repo): Core, Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Gathering, Region, Town, Database, API, Util
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

- **Registration order is dependency order.** A module may look up an earlier-registered module via `ModuleManager#get(Class)`, never a later one. Current order: Database → Region → Status → Job → Gathering → Item → Skill → Effect → Economy → Accessory → Town → Monster → Boss → Gui → **Api (always last)**. (Accessory sits after Economy, not alphabetically - relics' upgrade cost needs Vault's `Economy`, which `EconomyModule` only registers with Vault once it enables. Region sits right after Database since Gathering's fishing-area detection needs its `RegionQueryService` before it exists as a module dependency; Town sits right before Monster since monster spawn suppression needs `TownDetectionService` already built.)
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

`ApiModule` is always the last module enabled. It wraps each module's service in a narrow `*Api`/`*ApiImpl` pair (`OreliaApi`, `StatusApi`, `JobApi`, `ItemApi`, `AccessoryApi`, `SkillApi`, `GuiApi`, `EffectApi`, `CombatApi`, `TownApi`) and publishes them — plus the generic `PlayerDataManager` and `DatabaseManager` — through Bukkit's `ServicesManager`. This is the **only** integration surface for other plugins; when adding a new cross-plugin capability, add/extend an `*Api` interface here rather than exposing an internal manager class.

### WorldGuard region lookup (`rpg.region.service.RegionQueryService`) and town detection (`rpg.town`)

`RegionQueryService` is the one place orelia-core talks to WorldGuard to find out *which*
region IDs apply at a `Location` (`getRegionIds`, highest priority first) - reflection-only,
same rationale as `rpg.gathering.service.RegionProtectionService` (this build environment
can't reach WorldGuard's Maven repo, so there's no compile-time dependency on its jar/API):
fail-open, an empty list if WorldGuard isn't installed, its API doesn't match, or nothing
applies there. `RegionModule` owns it and registers right after `DatabaseModule` since
`GatheringModule` (fishing's area-based loot, see below) needs it before `TownModule` exists.

`rpg.town.service.TownDetectionService` builds "is this location inside a town" on top of
`RegionQueryService`: a location counts as a town if any applicable region ID is listed in
`config.yml: town-detection.town-regions` (a flat, case-insensitive allow-list). A single
logical town spread across disjoint areas just needs one WorldGuard region per area, with all
of their IDs listed - WorldGuard region IDs are unique per world, so there's no way to make
two separate shapes share one ID. `TownModule` registers right before `MonsterModule` and
publishes `TownDetectionService` via `getDetectionService()`; `MonsterSpawnService#spawn`
refuses to spawn at all inside a town (the single choke point every caller - spawn points,
`/oladmin spawn` - goes through), and `TownApi` publishes `isInTown(Location)` for
orelia-world/orelia-extra.

Fishing's per-area loot table (`rpg.gathering.listener.FishingListener`,
`rpg.gathering.repository.FishingLootRepository`) is a separate, lower-level consumer of the
same `RegionQueryService` - it doesn't go through `rpg.town` at all, since a fishing spot
doesn't have to be a "town". `fishing.yml`'s `towns:` section keys are free-form strings tried
in order by `FishingLootRepository#lootFor(List)`: WorldGuard region IDs at the bobber's
location (most specific), then the fishing world's name, then `default`. No schema change was
needed - a WorldGuard region ID is just another string key under `towns:`, and the whole chain
degrades to the original world-name-only behavior when WorldGuard isn't installed.

### Combat damage math (`rpg.status.combat.DamageFormula`)

`rpg.monster.listener.CombatDamageListener` is the single listener for every melee/monster
`EntityDamageByEntityEvent` (`EventPriority.LOW`) — it replaced the old
`WeaponUseListener`/`CombatStatusListener`/`MonsterCombatListener` trio, whose damage-setting
logic was split across listeners at the same priority and relied on Bukkit's *undefined*
same-priority ordering to land crit before ATK%/DEF instead of after. `DamageFormula.compute`
is the fixed-order pipeline: base attack power → ATK% (`applyAttackBonus`) → DEF
(`mitigate`) → crit roll/multiplier (`rollCrit`/`criticalMultiplier`, folding a `CRT_DMG` stat
bonus onto a weapon's/monster's own base crit multiplier) → elemental weakness
(`applyElementalWeakness`). It's pure (no Bukkit dependency) and unit-tested — when changing
how damage is calculated anywhere, add/extend a method here rather than duplicating the math
inline in a listener.

`SkillDamage` computes only the base-attack-power stage once per cast (weapon attack power ×
enhancement × the skill's own damage multiplier × ATK%) since AOE/cone skills apply the same
amount to multiple targets — it sets `DamageFormula.SKILL_OVERRIDE_METADATA` on the caster and
delivers that amount via `target.damage(amount, caster)`. `CombatDamageListener` detects that
metadata and resolves the remaining per-target stages (DEF/crit/weakness) against *that*
event's specific victim, rather than skipping the event entirely.

`DamageFormula.CRIT_METADATA_KEY` is the Bukkit metadata key `CombatDamageListener` sets on
the *attacker* after a crit (clearing it on a non-crit hit so a stale flag never leaks into
the next attack) — `rpg.monster.listener.DamageDisplayListener` reads it to color/scale the
floating damage number.

### Scaled health (`rpg.status.service.ScaledHealthService`)

A player's real vanilla health stays fixed at (or near) 20 hearts, but their meaningful HP pool
is `StatType.HP` (`currentHp` on `PlayerStatusComponent`) — potentially in the hundreds/thousands
depending on level and gear. `ScaledHealthService` (static, pure Bukkit-entity utility, no
`rpg.status` dependency) is the only place that converts between the two:
`syncVanillaHealth(entity, scaledCurrent, scaledMax)` sets vanilla health to the same
percentage, and `convertDamageToVanilla(entity, scaledDamage, scaledMax)` returns the
vanilla-equivalent amount for `EntityDamageEvent#setDamage` (letting Bukkit's own event
resolution - knockback, hurt sound, death - apply naturally, rather than this class calling
`setHealth` itself mid-event). `syncVanillaHealth` no-ops for a dead entity - a player between
death and respawn is still in `Bukkit.getOnlinePlayers()`, so a periodic caller like
`StatusService#tickRegen` can still reach them mid-death-screen; calling `setHealth` with a
nonzero value there was observed to partially "revive" them server-side (health > 0, still
targetable by mobs) while their client stayed stuck on the death screen, needing a second
respawn attempt to actually recover.

Every place `currentHp` can change keeps vanilla health in step:

- **Combat** (`CombatDamageListener`) - converts the final scaled damage to a vanilla-equivalent
  for `event.setDamage`, and separately calls `StatusService#applyScaledCombatDamage` (reduces
  `currentHp` only, no `setHealth` call - Bukkit's own event resolution handles vanilla). Also
  stamps `DamageFormula.SCALED_DAMAGE_METADATA_KEY` on the victim so
  `DamageDisplayListener` shows the meaningful scaled number instead of the tiny vanilla one.
- **Everything else that isn't a Bukkit damage event** - `StatusService#damage`/`heal`/
  `tickRegen`/`addExperience` (level-up refill) call `syncVanillaHealth` directly after mutating
  `currentHp`. `setEquipmentContribution`/`clearEquipmentContribution`/`addBuff`/
  `removeBuffsFromSource` call a private `reconcileScaledHealth` that clamps `currentHp` to the
  (possibly changed) max and re-syncs — it does **not** preserve `currentHp`'s percentage of the
  old max, same tradeoff vanilla Minecraft's own max-health attribute changes have.
- **Vanilla healing** (food/natural regen, golden apples, potions) - `ScaledHealthRegenListener`
  (`EntityRegainHealthEvent`, `MONITOR`, `ignoreCancelled`) leaves the vanilla amount untouched
  (vanilla's own regen math is correct on its own terms) and mirrors the same *percentage* gain
  into `currentHp`.
- **Environmental damage** (fall/fire/drowning/...) - these fire a plain `EntityDamageEvent`,
  never an `EntityDamageByEntityEvent`, so `CombatDamageListener` never sees them and
  `currentHp` would otherwise sit unchanged while vanilla health visibly drops - the next sync
  from that stale `currentHp` would then push vanilla health right back up, looking like an
  instant heal after fall damage. `ScaledHealthEnvironmentalDamageListener`
  (`EntityDamageEvent`, `MONITOR`, `ignoreCancelled`, skips `EntityDamageByEntityEvent`) calls
  `StatusService#applyEnvironmentalDamage` to mirror the vanilla percentage lost onto
  `currentHp` - same idea as `MonsterSpawnService#applyEnvironmentalDamage` below, and does not
  touch vanilla health itself since Bukkit already applied it naturally.
- **Join/respawn** - `ScaledHealthJoinListener` re-syncs vanilla health on join (nothing updates
  an offline player's vanilla health) and resets `currentHp` to max on respawn (Bukkit resets
  vanilla health to full on respawn, but nothing else resets the *scaled* side - without this,
  the next regen tick would read the stale near-0 `currentHp` from the killing blow and drag the
  freshly-respawned player's vanilla health back down).

Tagged monsters (`MonsterSpawnService`) get the same treatment, since a fully-scaled
high-difficulty boss's true HP (`monsters.yml` `hp:`) can run well past what's safe to put
directly into vanilla's `MAX_HEALTH` attribute - vanilla health is capped to
`config.yml: combat.scaled-health.vanilla-cap` (default 1024) at spawn instead of being set to
the full scaled value, and the true current HP lives in a PDC value
(`MonsterKeys#scaledCurrentHp`) rather than a database row (monsters aren't tracked there the
way players are). `CombatDamageListener` handles the combat-event path exactly like it does for
players; `MonsterHealthBarListener` handles the one path that never reaches
`CombatDamageListener` - environmental damage (fall/fire/...), which only fires a plain
`EntityDamageEvent` - via `MonsterSpawnService#applyEnvironmentalDamage` (mirrors the vanilla
percentage lost onto the scaled side, same idea as `ScaledHealthRegenListener`). The nametag HP
bar (`MonsterHealthBarRenderer`) always renders the scaled current/max, not vanilla, so its
numbers stay meaningful past the vanilla cap. `BossEncounterListener`'s phase/enrage
percentage thresholds needed no changes - vanilla and scaled health are kept in the exact same
proportion by construction, so reading vanilla percentage is equivalent to reading scaled
percentage.

### Weapon level vs. enhancement (`rpg.item.service.WeaponIdentityService`)

Two independent, PDC-backed per-instance counters live on a weapon `ItemStack`, both distinct
from the plain `WeaponData` template:

- **Enhancement level** (`enhancementLevel`/`enhance()`, unlimited) - the "強化屋" NPC's
  upgrade, +10% base attack power per level.
- **Weapon level** (`weaponLevel`/`levelUp()`) - starts at the weapon type's `items.yml`
  `level:` (`WeaponData.getWeaponLevel()`) and can be raised further via `ItemApi#levelUpWeapon`
  (`/ol item levelup` for now - no NPC/GUI trigger exists yet, that's an orelia-world follow-up),
  gated by the wielder's own character level via `WeaponLevelConfig#weaponLevelCap`
  (`config.yml: weapon-level.*`). Adds `attack-power-factor` (default 5%) per weapon level.

`WeaponIdentityService#baseAttackPower(stack, data)` is the one place both factors compose:
`attack-power * (1 + weaponLevel * weaponLevelFactor) * enhancementMultiplier`. Both
`CombatDamageListener` and `SkillDamage` call this rather than reading `WeaponData.attackPower`
directly.

### Cross-module dependency conventions

- `ItemModule` depends on `JobModule` + `StatusModule` (weapon requirement checks).
- `GatheringModule` depends on `JobModule` (looks up the player's current job display name for level-up messages).
- `ApiModule` depends on nearly everything (it's last).
- When a module needs another module's service, fetch it once in `onEnable` via `plugin.getModuleManager().get(OtherModule.class).orElseThrow(...)` — fail fast with a clear `IllegalStateException` if the dependency isn't registered yet, rather than deferring the lookup.

## Committing changes

When committing, also update README.md and README_EN.md accordingly.

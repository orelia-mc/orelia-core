# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`orelia-core` is a Paper 1.21.x (Java 21) Minecraft plugin — a single-jar RPG suite covering
everything that used to be split across three separate plugins (orelia-core, orelia-world,
orelia-extra). As of the 3-repo merge, all of it lives here:

- **Foundation** (formerly orelia-core): Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Gathering, Region, Town, Database, Relic, Api, Util
- **Content layer** (formerly orelia-world): Quest, NPC, Dialogue, Story, Dungeon, CutScene, Event, PlayerInfo, WorldApi
- **Social/economy layer** (formerly orelia-extra): Party, Friend, Guild, Chat, Trade, Mail, Auction, Housing, Pet, Mount, Ranking, Achievement, ExtraApi

`rpg.api`/`rpg.world.api`/`rpg.extra.api` are kept as internal facade layers (module-boundary
contracts) and still published via Bukkit's `ServicesManager`, because a genuinely separate
plugin (`orelia-debug`, testplay/debug tooling) depends on this jar and consumes several of
those `*Api` interfaces at runtime (`depend: [OreliaCore]`, `softdepend: [OreliaWorld,
OreliaExtra]` in orelia-debug's own plugin.yml, from before the merge — orelia-debug still
needs those two service names present for its soft-dependency checks to resolve, even though
they're no longer separate plugins on this end). Modules *inside* this jar, however, no longer
need to go through `ServicesManager`-lookup-with-null-guard for what used to be "is the other
plugin loaded yet" races — the whole suite now enables in one deterministic order (see below),
so a module can rely on `ModuleManager#get` for another in-jar module the same way core
modules always could.

## Commands

```
./gradlew build
```

Produces `build/libs/orelia-core-<version>.jar` (shadowJar, relocated sqlite/mysql/protobuf under `rpg.database.libs.*`). Requires network access to `repo.papermc.io` (Paper API) and `jitpack.io` (Vault API).

```
./gradlew test                              # all tests
./gradlew test --tests "rpg.status.*"       # a package
./gradlew test --tests "FooTest.someMethod" # a single test method
```

In-game: `/oladmin reload` reloads every module's config file without a server restart.
`/oladmin worldreload` and `/oladmin extrareload` are temporary aliases of the same command,
kept for one release cycle for operator muscle-memory from the pre-merge 3-plugin setup —
safe to remove in a later cleanup.

## Architecture

### Module system

`OreliaPlugin` (`rpg/core/OreliaPlugin.java`) is the single entry point. It owns process-wide singletons — `ConfigManager`, `SchedulerService`, `PlayerDataManager`, `ModuleManager` — and registers every top-level feature as an `RpgModule` (`rpg/core/module/RpgModule.java`) in a fixed order in `onEnable()`. This one `RpgModule`/`ModuleManager` pair covers all 37 modules from every former plugin — the old parallel `WorldModule`/`WorldModuleManager` and `ExtraModule`/`ExtraModuleManager` types were removed in the merge, since they were structurally identical to `RpgModule`/`ModuleManager` (only the plugin-class type parameter differed).

- **Registration order is dependency order.** A module may look up an earlier-registered module via `ModuleManager#get(Class)`, never a later one. Current order:

  ```
  Database → Region → Status → Job → Gathering → Item → Skill → Effect → Economy →
  Accessory → Town → Monster → Boss → Gui → Api
  → Dialogue → Story → Event → CutScene → Quest → Dungeon → Npc → PlayerInfo → WorldApi
  → Party → Friend → Guild → Chat → Trade → Mail → Auction → Housing → Pet → Mount →
  Ranking → Achievement → ExtraApi (always last)
  ```
  Within the foundation block: Accessory sits after Economy, not alphabetically - relics' upgrade cost needs Vault's `Economy`, which `EconomyModule` only registers with Vault once it enables. Region sits right after Database since Gathering's fishing-area detection needs its `RegionQueryService` before it exists as a module dependency; Town sits right before Monster since monster spawn suppression needs `TownDetectionService` already built. Within the content block: Quest registers before Dungeon (not alphabetically) since `DungeonEncounterService` calls `QuestProgressService#onDungeonCleared`. Within the social/economy block: modules with no dependency on each other register in roughly alphabetical order; Ranking/Achievement register last since they read state produced by earlier modules rather than owning anything themselves. Each former-plugin block's own `*Api` module (`Api`, `WorldApi`, `ExtraApi`) registers right after the last module in that block, so a later block can safely depend on an earlier block's published `*Api` (e.g. Achievement, in the social/economy block, sees a fully-populated `QuestApi` from `WorldApi`) — but a module cannot see a `*Api` published by a *later* block (e.g. `DungeonModule`, in the content block, still sees a null `PartyApi` at its own enable time, since `PartyModule`/`ExtraApiModule` register afterward; this is unchanged from the pre-merge behavior and already null-guarded).
- Modules are enabled in registration order, **disabled in reverse order**.
- Each module's `onEnable` typically: registers its config file with `ConfigManager`, loads a repository from that YAML, builds its services/managers, registers Bukkit listeners, and registers its player-facing subcommand into `PlayerCommandRegistry`.
- `onReload()` is optional (default no-op); implement it to re-read config and rebuild repositories in place — see `ItemModule.reloadWeapons()` for the pattern.
- Do not let one module reach into another module's internal classes (managers/services/repositories) directly — go through the other module's public getters on its `RpgModule`, or through `rpg.api`/`rpg.world.api`/`rpg.extra.api` if the consumer is a genuinely external plugin (e.g. orelia-debug).

### Per-module package shape

Most feature packages (`item`, `skill`, `job`, `status`, `accessory`, `monster`, `boss`, `effect`, `economy`, `gui`, `gathering`, `quest`, `dungeon`, `npc`, `world/dialogue`, `world/story`, `world/event`, `world/cutscene`, `world/playerinfo`, `extra/party`, `extra/friend`, `extra/guild`, `extra/chat`, `extra/trade`, `extra/mail`, `extra/auction`, `extra/housing`, `extra/pet`, `extra/mount`, `extra/ranking`, `extra/achievement`) follow the same internal layering:

- `repository/` — pure data access: either config-driven (parses a `*.yml` into in-memory templates) or DB-backed (implements `SchemaOwner` from `rpg/database/repository/SchemaOwner.java` and talks only to `DatabaseManager`). Never touches Bukkit events or game logic. Modules with no persistence (Party, in-memory only) keep state directly in a `manager/` instead.
- `model/` — plain data holders (templates, per-player components).
- `service/` or `manager/` — business logic sitting on top of the repository.
- `listener/` — Bukkit event handlers wired in `onEnable`.
- `command/` — `CommandExecutor`s registered into the shared `/ol` or `/oladmin` dispatcher (see below), not their own top-level Bukkit commands.
- `gui/` — present where the module has a GUI screen (Quest, NPC, Dungeon, Relic, Auction, Mail, Ranking, Housing, Pet, Achievement, PlayerInfo, ...).

Note: `rpg.dungeon`, `rpg.npc`, and `rpg.quest` sit directly under top-level `rpg.*` rather than under `rpg.world.*` like their siblings (`rpg.world.dialogue`, `rpg.world.story`, ...) - a pre-existing inconsistency from before the merge, intentionally left as-is (cosmetic, not worth the diff/risk to fix alongside everything else).

### Config

Every module reads its own file under `src/main/resources/`: `config.yml`, `messages.yml`, `items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `crafting.yml`, `fishing.yml`, `gathering.yml`, `relics.yml`, `quests.yml`, `npc.yml`, `dungeons.yml`, `dialogues.yml`, `story.yml`, `cutscenes.yml`, `events.yml`, `achievements.yml`, `housing.yml`, `mounts.yml`, `pets.yml`. `config.yml` and `messages.yml` are the two files that used to exist independently in all three former plugins - they were merged into one each during the repo merge (per-domain sections, e.g. `config.yml`'s `quest:`/`dungeon:`/`party:`/`friend:` blocks), not split into separate per-domain files. `ConfigManager.register(name)` copies the bundled default out of the jar on first use and returns a cached `ConfigFile`; `ConfigManager` never inspects module-specific keys. Reload all of them via `/oladmin reload`, which calls `ConfigManager.reloadAll()` then `ModuleManager.reloadAll()`.

Two PDC keys carry a **legacy-namespace fallback** for the same merge: a `NamespacedKey`'s namespace comes from the owning plugin's name, so `npc_id` (`rpg/npc/service/NpcKeys.java`) and `player_info_item` (`rpg/world/playerinfo/service/PlayerInfoItemKeys.java`) moved from `oreliaworld:` to `oreliacore:` when orelia-world stopped being its own plugin. Anything stamped before the merge - every NPC entity already standing in a world, every Nether Star already in a player's inventory - still carries the old namespace, and would otherwise stop being recognized entirely (NPCs going inert and getting duplicated by `/oladmin npc spawnall`; stars losing their menu/drop/move handling and being pushed out of the hotbar on the next join). `NpcSpawnService#idOf` reads the legacy key as a fallback and **re-stamps the entity** under the current namespace, so each NPC heals once on sight; `PlayerInfoItemService#isPlayerInfoItem` only reads both (an `ItemStack`'s container is reached through a copied `ItemMeta`, so healing would need every caller to write the meta back, and reading both costs nothing). Both are removable once no pre-merge world/item is in use. Core's own keys (`WeaponKeys`, `MonsterKeys`, `RelicKeys`, `AccessoryKeys`, `ProjectileKeys`) were always `oreliacore:` and needed no such handling.

`LegacyDataFolderMigrator` (`rpg/core/config/`, called first thing in `OreliaPlugin#onEnable`) is the merge's one-shot migration aid: it copies any `*.yml` still sitting in `plugins/OreliaWorld/` or `plugins/OreliaExtra/` into `plugins/OreliaCore/`, since every config now resolves under this plugin's folder. It never overwrites a file already present in the target (idempotent, safe on every startup) and deliberately **skips `config.yml`/`messages.yml`** - those two were content-merged rather than moved, so copying a former plugin's version over this one's would drop every core setting; their new sections reach an existing file through `ConfigMigrator`'s missing-key splice instead. Temporary, same as the `worldreload`/`extrareload` aliases - deletable once every server has booted once on a merged jar.

### Player data

`PlayerData` (`rpg/core/player/`) is the runtime container for one online player's cross-module state, keyed by UUID. Core only manages identity (UUID/name) plus a `Map<Class<? extends PlayerDataComponent>, PlayerDataComponent>` — it has no idea what a component contains. Each module defines its own `PlayerDataComponent` (e.g. `PlayerJobComponent`, `PlayerSkillComponent`, `PlayerStatusComponent`) and a loader (`PlayerDataComponentLoader`) that attaches it on join. Use `PlayerData.require(Class)` when a module can guarantee its own loader ran; it throws loudly instead of allowing silent null-stat bugs.

### Database

`DatabaseModule` builds one `DatabaseManager` (SQLite or MySQL, via `ConnectionProvider` — see `DatabaseType`) that every module's repository shares to obtain a JDBC `Connection`. `DatabaseManager` owns no schemas itself: each repository creates/migrates its own tables (`SchemaOwner`) on top of the shared connection, keeping data-access ownership with the module that needs it.

### Commands

There are exactly two top-level Bukkit commands, both dispatchers: `/ol` (player-facing, `OlRootCommand` + `PlayerCommandRegistry`) and `/oladmin` (admin-gated, `AdminCommand` + `AdminCommandRegistry`). Both registries (`OlCommandRegistry` subclasses) are published via `ServicesManager` (kept for `orelia-debug` and any other genuinely external plugin) and are also just plain fields any module in this jar registers into directly. When adding a player command to a module, register it into `plugin.getPlayerCommandRegistry()` inside that module's `onEnable`, not as a new `plugin.yml` command. Every former top-level command from the pre-merge plugins (`/rpgworldadmin`, `/rpgquest`, `/dialoguechoice`, `/extrareload`) was already funneled through this same `/ol`/`/oladmin` convention before the merge - the only leftover duplication was `reload`/`worldreload`/`extrareload` all doing the same full-plugin reload under different names (`worldreload`/`extrareload` are now kept as temporary aliases, see Commands above).

### Public API (`rpg.api` / `rpg.world.api` / `rpg.extra.api`)

`ApiModule`, `WorldApiModule`, and `ExtraApiModule` are each the last module enabled in their former-plugin's block. They wrap each module's service in a narrow `*Api`/`*ApiImpl` pair — `rpg.api`: `OreliaApi`, `StatusApi`, `JobApi`, `ItemApi`, `AccessoryApi`, `SkillApi`, `GuiApi`, `EffectApi`, `CombatApi`, `TownApi`, `EconomyApi`, `RelicApi`, `DebugApi`; `rpg.world.api`: `QuestApi`, `WorldDebugApi`; `rpg.extra.api`: `GuildApi`, `PartyApi`, `AchievementApi`, `ExtraDebugApi` — and publish them, plus the generic `PlayerDataManager` and `DatabaseManager`, through Bukkit's `ServicesManager`. This publication is kept specifically because `orelia-debug` (a separate, still-independent plugin/repo) depends on it at runtime. Modules inside this jar may still use these facades for a clean, documented module-boundary contract, but are not required to route through `ServicesManager` to reach another in-jar module - `ModuleManager#get` is available and doesn't have the "is it loaded yet" uncertainty a genuinely separate plugin would. When adding a new cross-plugin capability, add/extend an `*Api` interface here rather than exposing an internal manager class.

### WorldGuard region lookup (`rpg.region.service.RegionQueryService`) and town detection (`rpg.town`)

`RegionQueryService` is the one place orelia-core talks to WorldGuard to find out *which*
region IDs apply at a `Location` (`getRegionIds`, most specific first) - reflection-only,
same rationale as `rpg.gathering.service.RegionProtectionService` (this build environment
can't reach WorldGuard's Maven repo, so there's no compile-time dependency on its jar/API):
fail-open, an empty list if WorldGuard isn't installed, its API doesn't match, or nothing
applies there. `RegionModule` owns it and registers right after `DatabaseModule` since
`GatheringModule` (fishing's area-based loot, see below) needs it before `TownModule` exists.

`getRegionIds` also resolves each applicable region's WorldGuard parent (`ProtectedRegion#getParent()`)
and orders the result so a child region always sorts before its ancestors, regardless of their raw
`priority` values - matching WorldGuard's own convention that a child overrides its parent. Regions
with no ancestor/descendant relationship still fall back to `priority` descending, same as before this
existed. The ordering itself lives in the package-private, WorldGuard-independent
`RegionQueryService.orderByEffectivePriority` (a priority-guided topological sort, not a `Comparator` -
"child always beats its ancestors" plus "otherwise compare by priority" is not a transitive relation in
general, so a plain comparator can make `List#sort` throw at runtime), which is what
`RegionQueryServiceTest` exercises directly.

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

`rpg.gathering.service.RegenExclusionService` is the third consumer of `RegionQueryService`,
same shape as `TownDetectionService` but reading its own list (`gathering.yml:
regen-exclusion.regions`) - a forest that shouldn't regenerate isn't necessarily a town, so the
two lists are deliberately separate. Inside a listed region `GatherBlockBreakListener` returns
immediately: no regen, no gathering XP, no min-level gate. This **replaced** a per-coordinate
`PlacedBlockTrackingService`/`gathering_placed_block` table that tracked which blocks a player
placed by hand; keying off the *location* rather than a block's provenance removed the whole
subsystem (tracking service, repository, table, `BlockPlaceEvent` listener, and a
burn/explode/piston listener that existed only to keep the table in step) and fixed the cases
provenance-tracking structurally couldn't see - naturally-grown trees standing inside a build,
and WorldEdit/schematic pastes, which never fire `BlockPlaceEvent`. The tradeoff is granularity
(a build outside every listed region isn't protected) and a harder WorldGuard dependency (the
fail-open contract means nothing is excluded while WorldGuard is down). `BlockRegenService`
checks exclusion again at restore time, so defining a region plus `/oladmin reload`
retroactively cancels regens already queued inside it; its `isRestorable` guard is the separate,
WorldGuard-independent backstop that refuses to restore over a block someone else has since put
at that coordinate.

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
- `ApiModule` depends on nearly everything in the foundation block (it's last in that block).
- When a module needs another module's service, fetch it once in `onEnable` via `plugin.getModuleManager().get(OtherModule.class).orElseThrow(...)` — fail fast with a clear `IllegalStateException` if the dependency isn't registered yet, rather than deferring the lookup.

### Content layer (formerly orelia-world): Quest, NPC, Dialogue, Story, Dungeon, CutScene, Event, PlayerInfo

Reward-granting modules (`QuestModule`, `DungeonModule`, `NpcModule`) reach the foundation layer through `rpg.api` (`StatusApi`/`ItemApi`/`AccessoryApi`/`SkillApi`/`CombatApi`/`RelicApi`/`GuiApi`) rather than foundation module internals directly, same convention as every other module - money (quest rewards, NPC shop) goes straight through Vault's `Economy`, not a custom EconomyApi wrapper. `Dialogue`, `Story`, and `Event` are self-contained and need no foundation-layer API at all.

`QuestModule`/`DungeonModule`/`NpcModule` follow the same `repository/model/service/listener/command/gui` layering as foundation modules. Player-state repositories (`PlayerQuestRepository`, ...) implement `SchemaOwner` and own their own SQL tables via the shared `DatabaseManager`, same as foundation. `PlayerDataComponent`-owning modules (Quest) register their loader with `plugin.getPlayerDataManager().registerLoader(...)` in `onEnable`, exactly like foundation modules - there is no separate join/quit lifecycle for former-orelia-world modules anymore.

`DungeonModule` optionally resolves a challenger's real party via `rpg.extra.api.PartyApi` (soft - null-guarded, since `PartyModule` registers later in the merged order, see Module system above). `PlayerInfoModule`'s GUI optionally shows an achievement icon via `rpg.extra.api.AchievementApi` (same soft/null-guarded pattern, for the same ordering reason).

### Social/economy layer (formerly orelia-extra): Party, Friend, Guild, Chat, Trade, Mail, Auction, Housing, Pet, Mount, Ranking, Achievement

Modules with no persistence (Party, in-memory only) keep state directly in a `manager/`; DB-backed (`Guild`, `Trade` sessions, `Mail`, `Auction`, `Housing` ownership, `Pet`/`Mount` ownership, `Achievement` progress) and config-driven (`Achievement`, `Mount`, `Pet` templates) modules layer a `service/` on top of a `repository/`, same `SchemaOwner`-on-shared-`DatabaseManager` convention as every other layer.

`AchievementModule` is the widest-reaching module in this layer: it requires `rpg.api.StatusApi`/`SkillApi` (hard - `IllegalStateException` if missing, which can no longer actually happen since the foundation layer always enables first) plus Vault's `Economy` and `rpg.world.api.QuestApi` (soft - `QuestApi` happens to always be non-null in the merged registration order since `WorldApiModule` registers well before `AchievementModule`, but the code still null-guards it rather than assuming that ordering is permanent). `RankingModule` reads `rpg.api.StatusApi` directly for level data. Auction settlement and other money-moving code goes straight through Vault's `Economy`, same convention as the content layer - no custom EconomyApi.

## Committing changes

When committing, also update README.md and README_EN.md accordingly.

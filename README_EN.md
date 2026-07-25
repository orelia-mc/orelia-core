<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Foundation Plugin of Orelia-MC</p>

## About

`orelia-core` is the foundation plugin (Paper 1.21.x / Java 21) of the Minecraft RPG plugin suite **Orelia**, providing combat, player, and status systems.

Orelia is split into the following plugins:

- **orelia-core** (this repo) — Core, Item, Skill, Job, Status, Accessory, Relic, Monster, Boss, Effect, Economy, GUI, Gathering, Database, API, Util
- [orelia-world](https://github.com/orelia-mc/orelia-world) — Quest, NPC, Dialogue, Story, Dungeon, Region, CutScene, Event
- [orelia-extra](https://github.com/orelia-mc/orelia-extra) — later MMORPG features (Party, Guild, Trade, ...)
- [orelia-debug](https://github.com/orelia-mc/orelia-debug) — admin-only testplay/debug tooling for orelia-core/world/extra
- [orelia-serverutil](https://github.com/orelia-mc/orelia-serverutil) — gameplay-independent server operations/UX plugin (hub transfer, scoreboard/tab-list API, join messages, ...)

## Setup

```bash
./gradlew build
```

Produces `build/libs/orelia-core-1.0.0.jar`. Requires network access to `repo.papermc.io` (Paper API) and `jitpack.io` (Vault API).

## Structure

- Public API — other plugins (including orelia-world/orelia-extra) integrate through `rpg.api`, published via Bukkit's `ServicesManager` — never through this plugin's internal module classes. See `rpg.api.OreliaApi` and the narrower `StatusApi`/`JobApi`/`ItemApi`/`AccessoryApi`/`SkillApi`/`GuiApi`/`EffectApi`/`CombatApi`/`RelicApi` interfaces.
- Config — every module reads its own file under `src/main/resources/` (`items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `relics.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `crafting.yml`, `gathering.yml`, `messages.yml`, `config.yml`). Reload all of them with `/oladmin reload`. Every file is tracked by a top-of-file `config-version`; newly added keys (including ones nested inside a section you already have) are automatically spliced into an existing file at the correct position on next startup (`rpg.core.config.ConfigMigrator`). Bump a file's `config-version` whenever you add a new top-level section or key.
- Relics (rollable accessories) — unlike `accessories.yml`'s fully static items, a relic is generated on a dungeon boss kill (`rpg.api.RelicApi#generateRelic`, called from orelia-world) with a random part and one random main stat rolled from that part's `relics.yml` pool. Accessory slots expanded from 4 to 6 (charm/ring/necklace/wing/earring/belt). Up to 4 substats start empty and grow via `/ol relic upgrade` every 3 levels (max 15, 5 upgrades total) — the player picks which stat to add or grow themselves (only the +1~2 magnitude is randomized), a deliberate departure from a pure-RNG artifact system. Wearing 2+ relics from the same source dungeon grants that dungeon's set bonus automatically (`relics.yml`'s `dungeon-set-bonuses`). Separately, `relics.yml`'s `shop-relics:` section defines fixed, already-max-level (but deliberately weaker) relics NPC shops can sell via a new `RELIC` shop-entry kind (`npc.yml` shop-stock).
- Boss bars — spawning a boss shows a vanilla boss bar (HP progress) to any player within 7 blocks, reading the same scaled HP the nametag health bar already uses so the numbers always agree.
- Versioning — every push to `main` (i.e. every merged PR) auto-bumps `build.gradle.kts`'s `version` by PATCH and tags the commit, via `.github/workflows/version-bump.yml`. Label a PR `bump:minor` for a breaking/compatibility change, or `bump:major` for a large rework, before merging.
- Monster strength — `/oladmin spawnpoint add <monsterId> [intervalSeconds] [maxAlive] [targetLevel]` lets a spawn point carry an optional target level, scaling that spawned monster's `monsters.yml` hp/attack-power/defense using the factors in `config.yml: monster-level-scaling` (omitted = unchanged template values, same as before this feature).
- Crafting — `/ol craft` opens the recipe list defined in `crafting.yml`; consuming the listed materials crafts one weapon.
- Debug mode — a per-player admin-toggleable flag (`rpg.api.DebugApi#isDebugMode`/`setDebugMode`, flipped via `orelia-debug`'s `/oladmin debugmode`). While enabled, it bypasses a weapon's job/level requirement and a skill's weapon-type-match/socketed/learned/cooldown/SP-cost checks entirely (growth gates like the weapon level-up cap or skill-point costs are out of scope). In-memory only - resets on rejoin.
- Status GUI (`/ol status`) — split into 3 items (base: HP/SP/ATK/DEF/speed/SP recovery, plus current attack power factoring in the held weapon; crit: crit rate/crit damage; elemental damage: the 6 elements) instead of one cramped lore, with Japanese labels and the viewer's own skin on the head icon. Also shows money and a read-only preview of equipped accessories/relics on the same screen (equip/unequip still happens in your own inventory slots underneath).
- Skill GUI (`/ol skill`) — right-click to socket, **shift+right-click to unsocket**. A socketed skill's lore shows which socket slot it occupies, and the header explains the cast keys (right-click/shift+right-click/swap-hands). Cast feedback (success, on cooldown, not enough SP, etc.) now appears on the action bar (next to the HP/SP/ATK readout) instead of chat.
- See [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) (Japanese) for a detailed walkthrough of the combat damage calculation.
- See [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) (Japanese) for a list of features not yet implemented across orelia-core/world/extra.

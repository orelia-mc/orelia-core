<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Foundation Plugin of Orelia-MC</p>

## About

`orelia-core` is the foundation plugin (Paper 1.21.x / Java 21) of the Minecraft RPG plugin suite **Orelia**, providing combat, player, and status systems.

Orelia is split into the following plugins:

- **orelia-core** (this repo) — Core, Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Gathering, Database, API, Util
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

- Public API — other plugins (including orelia-world/orelia-extra) integrate through `rpg.api`, published via Bukkit's `ServicesManager` — never through this plugin's internal module classes. See `rpg.api.OreliaApi` and the narrower `StatusApi`/`JobApi`/`ItemApi`/`AccessoryApi`/`SkillApi`/`GuiApi`/`EffectApi`/`CombatApi` interfaces.
- Config — every module reads its own file under `src/main/resources/` (`items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `config.yml`). Reload all of them with `/oladmin reload`.
- Monster strength — `/oladmin spawnpoint add <monsterId> [intervalSeconds] [maxAlive] [targetLevel]` lets a spawn point carry an optional target level, scaling that spawned monster's `monsters.yml` hp/attack-power/defense using the factors in `config.yml: monster-level-scaling` (omitted = unchanged template values, same as before this feature).
- See [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) (Japanese) for a detailed walkthrough of the combat damage calculation.
- See [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) (Japanese) for a list of features not yet implemented across orelia-core/world/extra.

# orelia-core

RPG foundation plugin (Paper 1.21.x / Java 21) - combat, player, and status systems.

Part of the Orelia 3-plugin split:

- **orelia-core** (this repo) - Core, Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Database, API, Util
- [orelia-world](https://github.com/rasp1220/orelia-world) - Quest, NPC, Dialogue, Story, Dungeon, Region, CutScene, Event
- [orelia-extra](https://github.com/rasp1220/orelia-extra) - later MMORPG features (Party, Guild, Trade, ...), not yet implemented

## Building

```
./gradlew build
```

Produces `build/libs/orelia-core-1.0.0.jar`. Requires network access to
`repo.papermc.io` (Paper API) and `jitpack.io` (Vault API).

## Public API

Other plugins (including orelia-world/orelia-extra) integrate through `rpg.api`,
published via Bukkit's `ServicesManager` - never through this plugin's internal module
classes. See `rpg.api.OreliaApi` and the narrower `StatusApi`/`JobApi`/`ItemApi`/
`AccessoryApi`/`SkillApi`/`GuiApi`/`EffectApi`/`CombatApi` interfaces.

## Config

Every module reads its own file under `src/main/resources/` (`items.yml`, `skills.yml`,
`jobs.yml`, `accessories.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`,
`config.yml`). Reload all of them with `/oladmin reload`.

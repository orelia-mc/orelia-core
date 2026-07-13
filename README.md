<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Foundation Plugin of Orelia-MC</p>

## About

`orelia-core` は Minecraft RPG プラグイン群 **Orelia** の基盤プラグイン(Paper 1.21.x / Java 21)です。戦闘・プレイヤー・ステータス関連のシステムを提供します。

Orelia は 3 プラグイン構成です。

- **orelia-core**(本リポジトリ) — Core, Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Database, API, Util
- [orelia-world](https://github.com/orelia-mc/orelia-world) — Quest, NPC, Dialogue, Story, Dungeon, Region, CutScene, Event
- [orelia-extra](https://github.com/orelia-mc/orelia-extra) — 後発の MMORPG 系機能(Party, Guild, Trade, ...)

## Setup

```bash
./gradlew build
```

`build/libs/orelia-core-1.0.0.jar` が生成されます。ビルドには `repo.papermc.io`(Paper API)と `jitpack.io`(Vault API)へのネットワークアクセスが必要です。

## Structure

- 公開 API — 他プラグイン(orelia-world / orelia-extra を含む)は `rpg.api` 経由(Bukkit の `ServicesManager` で公開)でのみ本プラグインと連携します。内部モジュールクラスへ直接アクセスすることはありません。`rpg.api.OreliaApi` と、より narrow な `StatusApi` / `JobApi` / `ItemApi` / `AccessoryApi` / `SkillApi` / `GuiApi` / `EffectApi` / `CombatApi` を参照してください。
- 設定ファイル — 各モジュールが `src/main/resources/` 配下の専用ファイル(`items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `config.yml`)を読み込みます。`/oladmin reload` で一括リロードできます。

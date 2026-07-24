<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Foundation Plugin of Orelia-MC</p>

## About

`orelia-core` は Minecraft RPG プラグイン群 **Orelia** の基盤プラグイン(Paper 1.21.x / Java 21)です。戦闘・プレイヤー・ステータス関連のシステムを提供します。

Orelia は以下のプラグイン群で構成されています。

- **orelia-core**(本リポジトリ) — Core, Item, Skill, Job, Status, Accessory, Monster, Boss, Effect, Economy, GUI, Gathering, Database, API, Util
- [orelia-world](https://github.com/orelia-mc/orelia-world) — Quest, NPC, Dialogue, Story, Dungeon, Region, CutScene, Event
- [orelia-extra](https://github.com/orelia-mc/orelia-extra) — 後発の MMORPG 系機能(Party, Guild, Trade, ...)
- [orelia-debug](https://github.com/orelia-mc/orelia-debug) — orelia-core/world/extra のテストプレイを助ける管理者向けデバッグツール
- [orelia-serverutil](https://github.com/orelia-mc/orelia-serverutil) — RPG機能非依存のサーバー運用・UXプラグイン(ハブ転送、スコアボード/タブリストAPI、joinメッセージ等)

## Setup

```bash
./gradlew build
```

`build/libs/orelia-core-1.0.0.jar` が生成されます。ビルドには `repo.papermc.io`(Paper API)と `jitpack.io`(Vault API)へのネットワークアクセスが必要です。

## Structure

- 公開 API — 他プラグイン(orelia-world / orelia-extra を含む)は `rpg.api` 経由(Bukkit の `ServicesManager` で公開)でのみ本プラグインと連携します。内部モジュールクラスへ直接アクセスすることはありません。`rpg.api.OreliaApi` と、より narrow な `StatusApi` / `JobApi` / `ItemApi` / `AccessoryApi` / `SkillApi` / `GuiApi` / `EffectApi` / `CombatApi` を参照してください。
- 設定ファイル — 各モジュールが `src/main/resources/` 配下の専用ファイル(`items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `config.yml`)を読み込みます。`/oladmin reload` で一括リロードできます。
- モンスターの強さ — `/oladmin spawnpoint add <monsterId> [intervalSeconds] [maxAlive] [targetLevel]` でスポーンポイントごとに目安レベルを設定でき、そのレベルに応じて `monsters.yml` の HP・攻撃力・防御力が `config.yml: monster-level-scaling` の係数でスケールします(未指定なら従来通りテンプレート値のまま)。
- 合成 — `/ol craft` で `crafting.yml` に定義されたレシピの一覧を開き、素材を消費して武器を1個作成できます。
- 戦闘ダメージ計算式の詳細は [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) を参照してください。
- orelia-core/world/extra 3リポジトリを横断した未実装機能一覧は [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) を参照してください。

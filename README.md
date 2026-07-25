<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Foundation Plugin of Orelia-MC</p>

## About

`orelia-core` は Minecraft RPG プラグイン群 **Orelia** の基盤プラグイン(Paper 1.21.x / Java 21)です。戦闘・プレイヤー・ステータス関連のシステムを提供します。

Orelia は以下のプラグイン群で構成されています。

- **orelia-core**(本リポジトリ) — Core, Item, Skill, Job, Status, Accessory, Relic, Monster, Boss, Effect, Economy, GUI, Gathering, Database, API, Util
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

- 公開 API — 他プラグイン(orelia-world / orelia-extra を含む)は `rpg.api` 経由(Bukkit の `ServicesManager` で公開)でのみ本プラグインと連携します。内部モジュールクラスへ直接アクセスすることはありません。`rpg.api.OreliaApi` と、より narrow な `StatusApi` / `JobApi` / `ItemApi` / `AccessoryApi` / `SkillApi` / `GuiApi` / `EffectApi` / `CombatApi` / `RelicApi` を参照してください。
- 設定ファイル — 各モジュールが `src/main/resources/` 配下の専用ファイル(`items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `relics.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `crafting.yml`, `gathering.yml`, `messages.yml`, `config.yml`)を読み込みます。`/oladmin reload` で一括リロードできます。全ファイルが先頭の `config-version` で管理されており、新しいjarで起動すると新規追加されたキー(ネストした階層のキーも含む)は既存ファイルの正しい位置へ自動で追記されます(`rpg.core.config.ConfigMigrator`)。新しいトップレベルセクション・キーを追加したら、そのファイルの `config-version` を1つ上げてください。
- レリック(厳選アクセサリー) — `accessories.yml`の完全固定ステータスとは別に、ダンジョンのボス討伐でランダムな部位・メインステータスを1本持って生成される個体差ありのアクセサリー(`rpg.api.RelicApi#generateRelic`、orelia-worldから利用)。アクセサリー枠は4→6種(お守り/指輪/ネックリス/羽根/耳飾り/ベルト)に拡張。メインステータスは部位ごとの固定プール(`relics.yml`)からランダムに1本、最大4本のサブステータスは初期状態では0本で、`/ol relic upgrade`で3レベル毎(最大15、計5回)にプレイヤー自身が「新規追加するステータス」または「強化する既存ステータス」を選んで伸ばせます(値の増加量のみ1〜2のランダム — 完全ランダム厳選との差別化)。同じダンジョン産のレリックを2つ以上装備すると、そのダンジョン専用のセットボーナスが自動で付与されます(`relics.yml`の`dungeon-set-bonuses`)。
- バージョン管理 — `main` への push(=PRマージ)ごとに `.github/workflows/version-bump.yml` が `build.gradle.kts` の `version` を自動でPATCHインクリメントし、タグを打ちます。互換性が壊れる変更は `bump:minor`、大規模な改修は `bump:major` ラベルをPRに付けてからマージしてください。
- モンスターの強さ — `/oladmin spawnpoint add <monsterId> [intervalSeconds] [maxAlive] [targetLevel]` でスポーンポイントごとに目安レベルを設定でき、そのレベルに応じて `monsters.yml` の HP・攻撃力・防御力が `config.yml: monster-level-scaling` の係数でスケールします(未指定なら従来通りテンプレート値のまま)。
- 合成 — `/ol craft` で `crafting.yml` に定義されたレシピの一覧を開き、素材を消費して武器を1個作成できます。
- デバッグモード — プレイヤーごとにon/offできる管理者用フラグ(`rpg.api.DebugApi#isDebugMode`/`setDebugMode`、`orelia-debug`の`/oladmin debugmode`から操作)。有効な間は武器の職業/レベル要件と、スキルの武器種一致・ソケット・習得済み・クールダウン・SP消費の各チェックを全てバイパスして自由に使用できます(武器レベルアップやスキル習得ポイントの上限などの成長系ゲートは対象外)。インメモリのみで再ログインするとリセットされます。
- ステータスGUI(`/ol status`) — 全ステータスを1アイテムのloreに詰め込むのではなく、基礎(HP/SP/ATK/DEF/移動速度/SP回復効率)・会心(会心率/会心ダメージ)・属性ダメージ増加(6属性)の3アイテムに分けて表示。日本語ラベル表示、頭アイコンは自分のスキンで表示されます。
- 武器スキルGUI(`/ol skill`) — 右クリックで装着、**Shift+右クリックで装着解除**。装着中のスキルは何番目のソケットに入っているかloreに表示され、発動キー(右クリック/Shift+右クリック/持ち替えFキー)の説明もヘッダーに表示されます。スキル発動時のフィードバック(発動成功・クールダウン中・SP不足等)はチャットではなくアクションバー(HP/SP/ATK表示の隣)に一時表示されます。
- 戦闘ダメージ計算式の詳細は [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) を参照してください。
- orelia-core/world/extra 3リポジトリを横断した未実装機能一覧は [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) を参照してください。

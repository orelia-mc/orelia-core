<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Foundation Plugin of Orelia-MC</p>

## About

`orelia-core` は Minecraft RPG プラグイン群 **Orelia** の基盤プラグイン(Paper 1.21.x / Java 21)です。戦闘・プレイヤー・ステータス関連のシステムを提供します。

Orelia は以下のプラグイン群で構成されています。

- **orelia-core**(本リポジトリ) — Core, Item, Skill, Job, Status, Accessory, Relic, Monster, Boss, Effect, Economy, GUI, Gathering, Region, Town, Database, API, Util
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

- 公開 API — 他プラグイン(orelia-world / orelia-extra を含む)は `rpg.api` 経由(Bukkit の `ServicesManager` で公開)でのみ本プラグインと連携します。内部モジュールクラスへ直接アクセスすることはありません。`rpg.api.OreliaApi` と、より narrow な `StatusApi` / `JobApi` / `ItemApi` / `AccessoryApi` / `SkillApi` / `GuiApi` / `EffectApi` / `CombatApi` / `RelicApi` / `TownApi` を参照してください。
- 設定ファイル — 各モジュールが `src/main/resources/` 配下の専用ファイル(`items.yml`, `skills.yml`, `jobs.yml`, `accessories.yml`, `relics.yml`, `monsters.yml`, `bosses.yml`, `effects.yml`, `gui.yml`, `crafting.yml`, `gathering.yml`, `fishing.yml`, `messages.yml`, `config.yml`)を読み込みます。`/oladmin reload` で一括リロードできます。全ファイルが先頭の `config-version` で管理されており、新しいjarで起動すると新規追加されたキー(ネストした階層のキーも含む)は既存ファイルの正しい位置へ自動で追記されます(`rpg.core.config.ConfigMigrator`)。新しいトップレベルセクション・キーを追加したら、そのファイルの `config-version` を1つ上げてください。
- レリック(厳選アクセサリー) — `accessories.yml`の完全固定ステータスとは別に、ダンジョンのボス討伐でランダムな部位・メインステータスを1本持って生成される個体差ありのアクセサリー(`rpg.api.RelicApi#generateRelic`、orelia-worldから利用)。アクセサリー枠は4→6種(お守り/指輪/ネックリス/羽根/耳飾り/ベルト)に拡張。メインステータスは部位ごとの固定プール(`relics.yml`)からランダムに1本、最大4本のサブステータスは初期状態では0本で、`/ol relic upgrade`で3レベル毎(最大15、計5回)にプレイヤー自身が「新規追加するステータス」または「強化する既存ステータス」を選んで伸ばせます(値の増加量のみ1〜2のランダム — 完全ランダム厳選との差別化)。同じダンジョン産のレリックを2つ以上装備すると、そのダンジョン専用のセットボーナスが自動で付与されます(`relics.yml`の`dungeon-set-bonuses`)。ボスドロップとは別に、`relics.yml`の`shop-relics:`セクションで固定ステータス・最大レベル(だが控えめな数値)のレリックをNPCショップに並べることもできます(`npc.yml`のshop-stockで`kind: RELIC`)。
- ボスバー — ボスをスポーンさせると、7ブロック以内にいるプレイヤーにバニラのボスバー(HP進捗)が表示されます。名札のHPバーと同じスケール済みHPを参照するため数値は一致します。
- バージョン管理 — `main` への push(=PRマージ)ごとに `.github/workflows/version-bump.yml` が `build.gradle.kts` の `version` を自動でPATCHインクリメントし、タグを打ちます。互換性が壊れる変更は `bump:minor`、大規模な改修は `bump:major` ラベルをPRに付けてからマージしてください。
- モンスターの強さ — `/oladmin spawnpoint add <monsterId> [intervalSeconds] [maxAlive] [targetLevel]` でスポーンポイントごとに目安レベルを設定でき、そのレベルに応じて `monsters.yml` の HP・攻撃力・防御力が `config.yml: monster-level-scaling` の係数でスケールします(未指定なら従来通りテンプレート値のまま)。
- 合成 — `/ol craft` で `crafting.yml` に定義されたレシピの一覧を開き、素材を消費して武器を1個作成できます。
- デバッグモード — プレイヤーごとにon/offできる管理者用フラグ(`rpg.api.DebugApi#isDebugMode`/`setDebugMode`、`orelia-debug`の`/oladmin debugmode`から操作)。有効な間は武器の職業/レベル要件と、スキルの武器種一致・ソケット・習得済み・クールダウン・SP消費、釣りざおの職業要件の各チェックを全てバイパスして自由に使用できます(武器レベルアップやスキル習得ポイントの上限などの成長系ゲートは対象外)。インメモリのみで再ログインするとリセットされます。
- 職業「釣り人」— 釣りざおは他の職業の武器種制限と同様、職業が釣り人でないと使用できません(`rpg.gathering.listener.FishingListener`、ヴァニラの釣りざおは`items.yml`の武器データを持たないため`WeaponRequirementService`ではなく`PlayerFishEvent`ベースで判定)。釣り人レベル(採掘師/木こり/農民と同じ仕組みで独立してレベルが上がります。`/ol gathering`で確認可能)が上がるほど、浮きが沈むまでの待ち時間(`fishing.yml: catch-time`)が少しずつ短くなります。釣れるアイテムは`fishing.yml: towns`の下で重み付き抽選テーブルとして定義でき、浮きの位置にあるWorldGuardリージョンID(最優先)→プレイヤーがいるワールド名→`default`の順で一致するキーが使われます。エリアの追加や釣れるアイテムの変更は`fishing.yml`編集と`/oladmin reload`だけで反映され、コード変更は不要です。
- 町判定・WorldGuard連携(`rpg.region` / `rpg.town`) — WorldGuardを導入している場合(ソフト依存、`plugin.yml: softdepend`、リフレクションのみでコンパイル時依存なし)、`config.yml: town-detection.town-regions`に列挙したWorldGuardリージョンID内を「町」として扱えます。1つの町が離れた複数エリアにまたがる場合は、各エリアを別々のWorldGuardリージョンとして作成し、そのIDを全て`town-regions`に列挙してください(WorldGuardのリージョンIDはワールド内で一意なため、1つのIDを複数の離れた形状に使い回すことはできません)。町判定はOrelia自身のモンスタースポーン(スポーンポイント/`/oladmin spawn`)を町の中では発生させないようにする目的で使われ、`rpg.api.TownApi#isInTown`としてorelia-world/orelia-extraにも公開されます。WorldGuard未導入時は判定処理自体が無効化され、既存の挙動に影響しません。
- 採取ノードの再生成と建築の共存(`rpg.gathering`) — 天然の採取ノードは伐採/採掘後に`gathering.yml`のクールダウンを経て自動で再生成されますが、プレイヤーが**手で設置した原木**は追跡され(`PlacedBlockTrackingService`)、再生成・経験値・レベルゲートの対象外になります(鉱石は設置有無に関わらず従来通り再生成。WorldEditの一括ペーストはBlockPlaceEventを発火しないため天然ノード扱いのまま)。加えて、再生成は「その座標が再生成待ちブロックのまま(または空気)である間」だけ実行されます — 待機中の座標に誰かが別のブロックを建てていた場合はタスクを破棄し、建築物を上書きしません。手動設置の追跡は延焼・爆破では破棄され、ピストンで押された場合は移動先へ引き継がれます(`GatherBlockCleanupListener`)。管理者は`/oladmin gathering resetregen confirm`で再生成待ちタスクのみを、`/oladmin gathering resetplaced confirm`で手動設置の追跡のみを個別にリセットできます(後者は手で置いた原木の再生成除外が全て外れる破壊的操作)。
- ステータスGUI(`/ol status`) — 全ステータスを1アイテムのloreに詰め込むのではなく、基礎(HP/SP/ATK/DEF/移動速度/SP回復効率、手持ち武器を加味した「現在攻撃力」も表示)・会心(会心率/会心ダメージ)・属性ダメージ増加(6属性)の3アイテムに分けて表示。日本語ラベル表示、頭アイコンは自分のスキンで表示されます。所持金と、装備中のアクセサリー/レリックの読み取り専用プレビューも同じ画面に表示されます(装備の付け外し自体はこのGUIの下に表示される自分のインベントリの該当スロットで行います)。
- 武器スキルGUI(`/ol skill`) — 右クリックで装着、**Shift+右クリックで装着解除**。装着中のスキルは何番目のソケットに入っているかloreに表示され、発動キー(右クリック/Shift+右クリック/持ち替えFキー)の説明もヘッダーに表示されます。スキル発動時のフィードバック(発動成功・クールダウン中・SP不足等)はチャットではなくアクションバー(HP/SP/ATK表示の隣)に一時表示されます。
- 戦闘ダメージ計算式の詳細は [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) を参照してください。
- orelia-core/world/extra 3リポジトリを横断した未実装機能一覧は [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) を参照してください。

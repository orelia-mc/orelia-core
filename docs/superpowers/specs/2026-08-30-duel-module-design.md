# DuelModule 設計仕様

- 日付: 2026-08-30
- 対象リポジトリ: orelia-core
- ステータス: ユーザー承認済み設計 → 実装計画待ち

## 背景・目的

`UNIMPLEMENTED_FEATURES.md`の「一般的なMMORPGプラグインとして考えられる追加候補」に挙がっていた
「PvP専用機能」（決闘・闘技場・レーティング）を実装する。現状の戦闘システムはPvE中心の設計で、
`CombatDamageListener`はPvPダメージも計算上は通すが、専用のマッチメイキング・観戦・報酬の仕組みは
存在しない。

決闘・闘技場・レーティングは独立した3機能ではなく、**1つの`DuelModule`にまとまる自然な塊**と判断した
（闘技場＝決闘の実行場所、レーティング＝決闘結果の集計）。

## スコープ

**含む**:
- 1対1の決闘申請・承認フロー
- 複数登録可能な専用アリーナ（同時決闘対応）
- HPが0に達した時点で決闘終了・実際の死亡は起こさせない（アイテムロスト等のペナルティなし）
- 勝者へのサーバー側からの少額のお金報酬（Vault経由）
- 単純な勝数/敗数カウントによる簡易ランキング画面

**含まない（今回は見送り）**:
- ELOレーティング（本格的な相対レーティング計算）
- プレイヤー同士の賭け金
- 観戦機能
- GvG・ギルド対抗戦（別途「実装対象外」と既に判断済み）

## アーキテクチャ

### モジュール配置

`rpg/extra/duel/`配下に新設。他の`extra`配下モジュール（Party/Guild/Friend等）と同じ
`repository/model/service/manager/listener/command/gui`層構造に従う。

`Economy`（Vaultの`Economy`、フェーズ0で銀行対応も追加済み）に依存するのみで、他の`extra`モジュールへの
依存は無い。モジュール登録順序としては、社会/経済レイヤーの中で（他モジュールと同様）アルファベット順
付近に配置してよい（既存モジュールへの強い依存が無いため）。

### データモデル

```java
// rpg.extra.duel.model.DuelArena
public record DuelArena(String world, double x, double y, double z, float yaw, float pitch) {}
```

`DungeonArena`と同じレコード形式だが、決闘は「ダンジョンID」のような親エンティティを持たない単一の
概念なので、`dungeons.yml`の`dungeon-id`ごとのアリーナリストとは異なり、`duels.yml`直下にフラットな
アリーナリストを持つ（`DungeonRepository`のような親エンティティ紐付けの構造は不要）。

```yaml
# duels.yml
config-version: 1
arenas:
  0:
    world: "world"
    x: 100.5
    y: 64.0
    z: 200.5
    yaw: 0.0
    pitch: 0.0
```

### DB永続化: `duel_stats`テーブル

`SchemaOwner`実装の`DuelStatsRepository`（他モジュールの`SchemaOwner`パターンと同様）。

```sql
CREATE TABLE IF NOT EXISTS duel_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    wins INTEGER NOT NULL DEFAULT 0,
    losses INTEGER NOT NULL DEFAULT 0
)
```

### 決闘申請フロー

`PartyModule`/`GuildModule`/`FriendModule`が共有する`rpg.core.util.PendingQueue<UUID>`をそのまま再利用
（決闘申請の宛先ごとに送信元プレイヤーのUUIDをキューイングする、既存と全く同じ形）。

- `/duel <player>` - 申請を送る（クールダウンあり、`config.yml`の`duel.cooldown-seconds`で設定可能）
- `/duel accept [player]` - 最も古い申請（または指定した相手）を承認 → 決闘開始
- `/duel decline [player]` - 拒否
- `/duel cancel` - 自分が送った申請を取り消し
- 双方の申請に対応するGUIも用意（`PartyGuiScreen`等と同じ、承認/拒否ボタン付きの一覧画面）

空いてるアリーナが無い場合は、申請の承認時点で失敗させ、両者にメッセージを送る。

### 決闘の開始・進行・終了

**`DuelSession`**（`manager`層、`PartyModule`同様インメモリのみ、DB永続化しない - 決闘は瞬発的なセッション
なのでサーバー再起動を跨いで復元する必要が無い）:

- 開始時: 両プレイヤーの現在位置を記憶 → 空いてるアリーナへテレポート → セッションを`Map<UUID, DuelSession>`
  （両プレイヤーそれぞれのUUIDをキーに同じセッションを指す）で管理
- 進行中: 通常の近接/スキルダメージがそのまま`CombatDamageListener`のパイプラインを経由する（新しいダメージ
  計算ロジックは書かない）
- 終了時（後述の`DuelDamageListener`がトリガー）: 両者を元の位置へテレポート、HPを全回復、勝者に報酬付与、
  `duel_stats`を更新、両者とセッション参加者全員に結果をブロードキャスト、アリーナを解放

**離脱・切断時の扱い**: プレイヤーが決闘中にログアウト/`/duel forfeit`した場合は不戦敗扱いで即座に決闘終了
処理を実行（アリーナ解放、相手を元の位置へ）。

### 死亡防止の仕掛け（技術的に一番デリケートな部分）

`rpg.monster.listener.CombatDamageListener`は`EntityDamageByEntityEvent`を`EventPriority.LOW`で処理し、
`victim instanceof Player`の場合、スケールドダメージをvanilla換算した`event.setDamage()`と、
`StatusService`経由のスケールドcurrentHP減算を**同期的に**両方行う（`resolveFinalDamage`参照）。つまり
このリスナーが走った時点で、被弾者の`currentHp`（`StatusApi#getCurrentHp`で読める値）は既に更新済みになる。

新設する`rpg.extra.duel.listener.DuelDamageListener`は同じ`EntityDamageByEntityEvent`を
**`EventPriority.HIGH`**（`CombatDamageListener`のLOWより後）で購読する:

1. 被害者・加害者の両方が「同じアクティブな決闘セッション」に所属しているか確認（そうでなければ何もしない）
2. `StatusApi#getCurrentHp(victimId)`を読み、0以下なら「致死判定」
3. 致死判定なら`event.setCancelled(true)`（vanillaの実際の死亡・ノックバック確定を防ぐ）
4. その場で`DuelSession`の終了処理を呼ぶ（テレポート・全回復・報酬・統計・ブロードキャスト）

`StatusApi#heal`で両プレイヤーを全回復させる。`getFinalStats(playerId).get("HP")`（最大HP）と
`getCurrentHp(playerId)`（現在HP、致死判定直後なので0以下の可能性がある）の差分を計算し、その量を
`heal`に渡す。

### 報酬

`config.yml`の`duel.reward-money`（デフォルト50。`EconomyService`ではなく`rpg.api`層は`extra`モジュール
からは直接使えないため、既存の`Economy`（Vault）を`ServicesManager`から取得して
`economy.depositPlayer(winner, amount)`する - `AchievementModule`等、他の`extra`モジュールが金銭を
直接Vault経由で動かしている既存パターンと同じ）。

### ランキング表示

既存の`RankingModule`（`StatusApi`のレベルランキング専用、カテゴリ切り替えの仕組みを持たない）には相乗り
せず、`DuelModule`独自の`/ol duel ranking`コマンド＋GUI画面を新設する（`RankingGuiScreen`と同じ形、
`duel_stats`の勝数降順で上位N人を表示）。

### アリーナ管理コマンド（管理者）

`/oladmin duelarena add|set <index>|remove <index>|list`（`DungeonArenaAdminCommand`と同じパターン、
ただしダンジョンIDのような親引数は無い、フラットなインデックスのみ）。

### config.yml / messages.yml

- `config.yml`: `duel.cooldown-seconds`（デフォルト60）、`duel.reward-money`（デフォルト50）
- `messages.yml`: `duel.*`名前空間に申請/承認/拒否/開始/終了/報酬/エラー各種メッセージキーを追加
- 両ファイルとも`config-version`を1つ上げる

## テスト方針

`DuelSession`のアリーナ空き状況判定・報酬計算等、Bukkit型に依存しない純粋ロジック部分があれば
package-private staticメソッドとして切り出しテストする（既存の`orelia-conventions`スキルの方針通り）。
`DuelDamageListener`自体はBukkit実体・イベント依存のためテスト対象外（既存の`CombatDamageListener`同様）。

## オープンな確認事項（実装時に検証すべき点）

- `CombatDamageListener`のPvP経路（`victim instanceof Player`分岐）が本当に近接・スキル両方のPvP
  ダメージ全パスを通ることを、実装着手時に改めてコードで確認する（今回の設計調査ではJavadocと
  `resolveFinalDamage`の該当分岐のみ確認済み）。
- ワールド単位のPvP無効設定（既存の「PvPがオフのワールドでは近接スキルのノックバックが他プレイヤーに
  効かない」仕組み）とアリーナが同じワールドにある場合の相互作用を確認する。アリーナ自体は決闘用に
  意図的にPvP許可された場所である前提。

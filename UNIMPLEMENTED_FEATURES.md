# 未実装機能一覧

orelia-core / orelia-world / orelia-extra 3リポジトリを横断して、コード内のコメント(`SOW section X` という形で元の仕様書の章番号を参照している箇所が多数ある - その仕様書自体はこの3リポジトリのどこにもコミットされていないため、直接は参照できない)と各リポジトリの`CLAUDE.md`のモジュール一覧を突き合わせて洗い出したリストです。

**注意**: この一覧は「コード中に明示的に『未実装』『today, but a future...』『hook for future』等と書かれている箇所」を機械的に拾ったものが中心です。元の仕様書(SOW)そのものを参照できていないため、**SOWに書かれているが痕跡が全く無い機能は原理的に見つけられません**。後半の「一般的なMMORPGプラグインとして考えられる追加候補」は、あくまで一般論としての示唆であり、実際にSOWで要求されているかどうかは未確認です。実装の優先度判断には、まず元の仕様書と突き合わせることをおすすめします。

（2026-07-30時点でorelia-core側を再調査し、内容を更新。orelia-world/orelia-extraのセクションは前回調査時点のまま未検証です。）

## orelia-core

- **武器レベルアップの実際のトリガー(NPC/GUI)**: 「武器レベル」システム(強化とは別、プレイヤーレベルでゲートされる)は、API(`ItemApi#levelUpWeapon`)とロジック(`WeaponIdentityService#levelUp`)のみ実装済みで、実際にプレイヤーが操作する導線(専用NPCやGUI)がまだ無い。現状`/oladmin item levelup`という管理者向け手動コマンドで代用しており、コマンドのJavadoc自身が「orelia-world側のNPC実装待ちの暫定入口」と明記している(`rpg/item/command/ItemCommand.java`)。
- **採取レベル上限(50)の引き上げ**: `GatheringLevelingConfig`の経験値カーブは`NextXP = a * level^b + c`という汎用式で実装されており、コメントで「将来のレベル上限引き上げ(SOW 3.3)は設定変更だけで対応できる」と明記されている。現状の上限は1〜50固定(`gathering.yml`)で、実際に引き上げた実績・導線はまだ無い。
- **ジョブ/アクセサリー種別の追加が設定ファイルだけで完結しない**: アイテム・スキルはYAML定義だけで新規追加できるのに対し、`JobType`(`rpg/job/model/JobType.java`)と`AccessoryType`(`rpg/accessory/model/AccessoryType.java`)は列挙型がコード側にハードコードされており、新しいジョブ/アクセサリー種別を追加するにはコード変更(+再ビルド)が必須。`JobType`側はJavadocで「ジョブ識別子が武器制限・スキルツリーのロジックを駆動するため」と設計上の理由が明記されている。
- **Vaultの銀行(Bank)機能が未対応**: `OreliaVaultEconomy`(`rpg/economy/vault/OreliaVaultEconomy.java`)はプレイヤー個人残高のみをモデル化しており、`createBank`/`deleteBank`/`bankBalance`/`bankDeposit`等のVault銀行系メソッドは全て固定で`NOT_IMPLEMENTED`を返す実装になっている。意図的なスコープ縮小だが、銀行機能を前提にする外部プラグインとの互換性上のギャップとして残っている。
- **モンスター/ボスの行動パターンが2種類のみ**: `MonsterAbilityType`/`BossAbilityType`は現状`AOE_SLAM`と`FIREBALL_BARRAGE`の2種類しかなく、`MonsterAbilityCastService`/`BossAbilityCastService`もこの2種を実行するのみ。`BossPhase`によるマルチフェーズ演出の枠組み自体は既に対応済みなので、召喚・デバフ・テレポート等の行動バリエーションを増やす余地がある。
- **`RelicModule`が存在しない**: 他の主要機能が全て`RpgModule`単位で登録されている(`CLAUDE.md`記載の規約)のに対し、relic(遺物)関連のロジックは`AccessoryModule`/`ItemModule`/`GuiModule`にまたがって実装されており、独立した`RelicModule`が無い。動作上の不具合ではないが、モジュール構成の一貫性という観点での整理余地。
- **WorldGuard連携がリフレクションのみ・フェイルオープン**: `RegionQueryService`をはじめ、`TownDetectionService`・`RegenExclusionService`・釣りエリア別ドロップ(`FishingListener`)は全てWorldGuardへのコンパイル時依存を持たず、リフレクション越しにAPIを呼び出している(ビルド環境からWorldGuardのMavenリポジトリに到達できないため)。WorldGuard未導入時・API形状が変わった時は静的に失敗せず「何も除外・検出しない」方向に静かにフェイルオープンする設計であり、これ自体は意図的なトレードオフとして維持する。ただし「気づかれにくい」点については2026-08-30に緩和済み: 起動時のAPI不一致は元々`RegionQueryService`のコンストラクタでWARNINGログ済みだったが、起動後にAPI形状が壊れて`getRegionIds`呼び出し自体が失敗するケースは完全に無言だった箇所に、セッション中1回だけのWARNINGログを追加した(呼び出し頻度が高いため常時ログはスパムになる)。
- **テストカバレッジの偏り**: `src/test/java/rpg/`配下にテストがあるのは`core/config`・`gathering`(config/repository/serviceの一部)・`monster/config`・`status/combat`・`town`・`util`のみで、`accessory`・`api`・`boss`・`database`・`economy`・`effect`・`gui`・`item`・`job`・`region`・`relic`・`skill`の合計12パッケージにはテストが1件も無い。武器同一性(強化・レベル)、ソケット、GUIフレームワーク、DB接続層、Vault連携など、他プラグインからも参照されるコア機能がテスト無しで動いている状態。

### 解消済み（前回調査からの訂正）

前回のドラフトで未実装として挙げていた以下の2項目は、現在のコードでは解消されていることを確認しました。

- ~~モンスターの能動スキル/AI行動~~ → `MonsterData.abilities`(`List<MonsterAbility>`)として実装され、`MonsterAbilityCastService`が`tick()`・クールダウン管理込みで実際に実行している。ボスと同じ仕組み(`BossAbilityCastService`)がベースモンスターにも適用済み。
- ~~アクセサリー枠31〜35の追加種別~~ → `AccessoryType`は現在`CHARM`/`RING`/`NECKLACE`/`WING`/`EARRING`/`BELT`の6種に拡張されており、未使用の予約スロットは見当たらない。
- ~~ダンジョン⇔クエストの自動連携~~ → `DungeonEncounterService#forceEnd`が`DungeonEndReason.CLEARED`時にパーティ全員分`QuestProgressService#onDungeonCleared`を呼んでおり、「ダンジョンクリア」を条件とするクエスト目標は既に自動進行する。`QuestProgressService`側のJavadocが「未接続のフック」という古い記述のまま残っていたのを2026-08-25に修正済み(実装自体は既に繋がっていた)。
- ~~`GuiApi#openEquipment`が非推奨のリダイレクトのまま残存~~ → 2026-08-30に、下流の唯一の参照だった`orelia-debug`の`GuiDebugCommand`(`equipment`サブコマンド)を`openStatus`直呼びに変更(PR #17)した上で、`orelia-core`側の`GuiApi`/`GuiApiImpl`から削除。

## orelia-world
- **NPC経由のギルド機能**: `NpcInteractListener`は現状ダイアログ機能のみを扱っており、「ギルド機能は将来のモジュール向けのフック」とコメントされている。ギルド自体(`GuildModule`)はorelia-extra側に実装済みだが、orelia-world側のNPC(受付NPC等)からギルド関連操作(入会・管理等)を行う導線はまだ無い。
- **カットシーンのBGM再生**: `CutSceneStepType`はカメラ/メッセージ/エフェクト/タイトル表示の演出タイプを持つが、BGM再生は「future addition」とコメントされており未実装。

（この2項目・以下のorelia-extra項目は今回orelia-core側のみ再調査したため未検証です。orelia-worldリポジトリ側で改めて確認することを推奨します。）

## orelia-extra

現時点で未実装として挙げられる項目はありません。

（前回の「オークション落札時の自動メール通知」は、`AuctionService#buy`/`expireOverdueListings`が`sold-mail-*`/`expired-mail-*`キーで既にメール送信していることを2026-08-25に確認し、解消済みとして削除しました。同日追加した入札機能でも`won-mail-*`/`outbid-mail-*`で同様に自動送信します。この節はorelia-core側のみ再調査したため、他の項目が無いこと自体は未検証です。orelia-extraリポジトリ側で改めて確認することを推奨します。）

## 一般的なMMORPGプラグインとして考えられる追加候補(未確認・参考情報)

以下は、3リポジトリの`CLAUDE.md`のモジュール一覧を見る限り「言及が見当たらない」機能です。SOWで実際に要求されているかは未確認なので、あくまで参考程度に留めてください。

- **PvP専用機能**: 決闘(duel)申請、闘技場(アリーナ)、レーティング等。現状の戦闘システムはPvE中心の設計(`CombatDamageListener`はPvPも計算上は通るが、専用のマッチメイキング/観戦/報酬の仕組みは無い)。
- **ブロックリスト**: `FriendModule`(`rpg/extra/friend/`)でフレンドリスト自体は実装済みだが、迷惑なプレイヤーを拒否するブロックリストに相当する機能は見当たらない。
- **称号システムの活用箇所**: `QuestReward`は「称号」をクエスト報酬の一種として保持しているが、称号自体をチャット・タブリスト等に表示する仕組みが今回のセッションで実装したorelia-serverutil側のプレースホルダー(`{job}`等)には含まれていない。

### 前回リストアップ時から実装が追いついていた項目（2026-08-30訂正）

- ~~フレンドリスト~~ → `FriendModule`として実装済み(`FriendCommand`/`FriendGuiScreen`)。フレンド申請・承認・テレポート申請まで対応。ブロックリストのみ未対応(上記参照)。
- ~~ギルド間の関係(GvG等)~~ → 実装対象外と判断(2026-08-30、ユーザー確認済み)。
- ~~クラフト/レシピシステム~~ → `CraftingService`/`CraftCommand`/`crafting.yml`として実装済み(`/ol craft`、CHANGELOG.md記載)。

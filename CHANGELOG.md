# Changelog

このファイルは `orelia-core` の変更履歴です。旧 `orelia-core`/`orelia-world`/`orelia-extra` の3リポジトリ統合以降の主な変更を、新しいものから順に記載しています。個々のバージョン番号・日付は `git log` を参照してください。

## 更新履歴

- 各種GUIで表示名の代わりに生ID・英語Material名が出ていた箇所を修正: NPCショップのバニラ装備・レリック、クラフト画面の必要材料、クエストログの討伐/収集/納品/攻略対象、属性表示(FIRE等の英語enum名)。バニラ素材名はクライアント言語で自動翻訳されるAdventureのtranslatable componentを採用。
- ネザースターのプレイヤー情報GUIのボタン配置を、5行の枠内でカテゴリ行とソーシャル行が縦方向に均等になるよう調整(従来は上2行に偏っていた)。
- Vaultの銀行(Bank)機能に対応(`createBank`/`bankDeposit`/`bankWithdraw`等)。プレイヤーが名前付き銀行を作成し所有できるように。
- モンスター/ボスのアビリティに`TELEPORT`(位置攪乱)・`DEBUFF`(状態異常付与)・`SUMMON`(小モンスター増援)を追加(既存の`AOE_SLAM`/`FIREBALL_BARRAGE`と合わせ計5種)。
- `RegionQueryService` の実行時WorldGuard連携失敗(起動後のAPI形状変化等)を、セッション中1回だけWARNINGログするように(従来は完全に無言でフェイルオープンしていた)。
- 非推奨だった `GuiApi#openEquipment` を削除(下流の `orelia-debug` 参照を `openStatus` 直呼びに移行済み)。
- `/auction` のGUIで、自分以外の出品を購入/入札する際に確認画面を挟むように変更。
- `/oladmin dungeonarena set <dungeon-id> <index>` を追加(既存インデックスのアリーナを現在地で上書き)。
- `/ol help <サブコマンド名>` でサブコマンド単体のヘルプを表示できるように。
- メールGUIの本文折り返しを追加(`LoreTextWrap`、全角/半角幅・色コード継承に対応)。
- Auction/Tradeで取引可能なアイテムをOrelia製の武器/アクセサリー/レリックのみに制限(`TradeableItemService`)。
- ダンジョン入場カウントダウンに効果音を追加。
- `auction`/`house`/`gathering`/`job`/`mount`/`pet`/`ranking`/`relic` を `/ol` のサブコマンドからトップレベルコマンド化。
- プレイヤー情報アイテム(ネザースター)GUIを5段に拡張、ギルド/パーティー/フレンド/チャットへのショートカットを追加。
- `/guild`・`/party`・`/friend` を引数無しで実行するとGUIを直接開くように変更(エイリアス `/g`/`/p`/`/f` 追加)、`/chat gui`(`/c`)を新設。
- ギルド名/タグの変更コマンドを追加、固定階級(OFFICER/MEMBER)を廃止しカスタムロール制(最大7個)に置き換え。
- チャット入力待ち処理のバグを修正(非同期スレッドでの例外がGUIフローを壊していた問題)。
- フレンド申請/パーティー招待/ギルド招待を複数同時に保持できるよう修正(`PendingQueue` 導入)、未実装だった `/guild decline` のバグも修正。
- Guild/Party GUIの「チャットを送信」ボタンを「チャットに切り替え」ボタンに変更。
- オークションに入札形式を追加(`/ol auction start`/`bid`、即時エスクロー・落札時メール通知)。
- ペット/乗り物に育成要素を追加(討伐でキル経験値を獲得しレベルアップ、召喚中のみステータスボーナス)。
- 未実装機能一覧を `UNIMPLEMENTED_FEATURES.md` に整理。
- 住宅区画/ダンジョンアリーナの現地登録コマンドを追加(`/oladmin houseplot`/`dungeonarena`)。
- 戦闘ダメージ計算式の詳細を `DAMAGE_FORMULA.md` に分離。
- ダンジョン入場カウントダウンをチャット連投からTitle表示に変更。
- タグ付きOreliaモンスターのスライム分裂を無効化。
- `/oladmin item levelup [amount]` — デバッグモード中はレベル上限を無視して複数レベル分を一括適用できるように。
- `/ol help`/`/oladmin help` の表示を改行・選択肢表記で見やすく整形。
- 各種コマンドのタブ補完を追加(spawn系ID、auction/mail/houseのサブコマンド等)。
- `/oladmin worldreload`/`extrareload` エイリアスを削除し `reload` に一本化。
- `/oladmin reload` が変更・追加・削除されたconfigキーを一覧表示するように。
- `/oladmin config <core|world|extra> view` でconfigをYAML木構造のままクリック編集可能な形式で表示できるように。
- `/chat mute <public|party|guild>` でチャンネル別ミュートを追加(招待等のシステム通知はミュート対象外)。
- ギルド/パーティー/フレンドGUIを追加(招待・承認・拒否・追放・昇格/降格等をGUIから操作可能)。
- クエストログGUI(`/ol quest gui`)を追加、ロック理由付きで一覧表示。
- 経験値バーの色抜けバグを修正。
- プレイヤーの初期CRT/CRT_DMGを10/50に変更(旧デフォルト5/5)。
- レベルアップ時のステータス上昇メッセージの属性名を日本語化。
- 武器ごとの会心倍率設定(`items.yml`の`crit-multiplier`)を廃止、`CRT_DMG`ステータス一本に統一。
- モンスター被弾時のバニラ被ダメージ演出(赤黒いハートエフェクト)を緩和(`vanilla-cap`を1024→20に変更)。
- モンスター/ボスのアビリティ攻撃(AOE_SLAM/FIREBALL_BARRAGE等)がDEF/会心/属性弱点のパイプラインを正しく経由するよう修正。
- 職業変更GUIのページング境界バグを修正(8職業目以降でスロットが崩れていた問題)。
- モンスター専用の追加成長倍率を導入(`config.yml: monster.target-level-bonus`)。
- アクションバーに常時レベル/経験値バーを表示するように。
- レベルアップ演出を段階化・統一(`LevelUpFeedbackService`、職業/採取レベルも含む)。
- HP/SP/ATK/DEFの成長を指数関数化、レベル上限を100→80に変更(データ上は300まで対応)。
- GUI確認画面(`ConfirmGuiScreen`)・共通ページング(`GuiPaginator`/`GuiPageLayout`)の基盤を整備。
- バニラ防具の装備を禁止し、防御力をDEFステータス一本に統一。矢インフィニティは防具枠を利用した専用アイテムで実現。
- PvPがオフのワールドでは、近接スキルのノックバックが他プレイヤーに効かないように修正(モンスターへの効果は変わらず)。
- 遠距離武器(弓・クロスボウ)を近接で振った場合は素手扱いのダメージになるよう修正、属性弓の弱点ボーナスは射撃時にも正しく適用されるよう修正。
- 武器スキルGUI(`/ol skill`)を追加(ホットバー1〜3番目の武器にスキルを装着)。
- ステータスGUI(`/ol status`)を追加、アクセサリー/レリックの装備枠6つを統合表示。
- 採取ノードの再生成をWorldGuardリージョン単位で除外できるように(`gathering.yml: regen-exclusion.regions`、建築保護目的)。
- WorldGuard連携で町リージョン内のOreliaモンスタースポーンを抑制するように(`config.yml: town-detection`)。
- 職業「魔法使い」と専用武器種`WAND`を追加(氷弾/AOE/3連射レーザーの3アクション)。
- 職業「釣り人」を追加(レベルで釣り時間短縮、`fishing.yml`でエリア別ドロップ抽選)。
- デバッグモード(管理者用フラグ、`orelia-debug`から切替)を追加 — 武器/スキルの各種要件チェックを全バイパス。
- `/ol craft` による武器合成を追加。
- スポーンポイントに目安レベル(`targetLevel`)を設定できるように(HP/ATK/DEFが自動スケール)。
- マージごとの自動バージョンアップ(`version-bump.yml`)とGitHub Release自動公開のワークフローを整備。
- 弓スキル(パワーショット/マルチショット)で放たれた矢を拾えなくし、着弾後に自動消滅するように。
- モンスターの名札にレベル表示を追加、HPバー装飾が混入しない専用の死亡メッセージを追加。
- ボスのスポーン時にバニラのボスバー(HP進捗)表示を追加。
- レリック(個体差ありアクセサリー)システムを追加(`/ol relic upgrade`、ダンジョンボス討伐で入手)。
- 3プラグイン統合に伴う設定ファイルの自動移行を追加(`LegacyDataFolderMigrator`、`config.yml`/`messages.yml`は対象外)。
- 3プラグイン統合に伴うNPC/ネザースターの名前空間互換フォールバックを追加(`oreliaworld:` → `oreliacore:`)。
- 全設定ファイルに `config-version` による自動マイグレーションを導入(`ConfigMigrator`)。
- 公開APIを `rpg.api`/`rpg.world.api`/`rpg.extra.api` に限定する方針を明文化(`ServicesManager` 経由)。

旧orelia-core/orelia-world/orelia-extraの3リポジトリ統合そのものの経緯は [CLAUDE.md](CLAUDE.md) を参照してください。

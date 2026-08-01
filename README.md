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
- レリック(厳選アクセサリー) — `accessories.yml`の完全固定ステータスとは別に、ダンジョンのボス討伐でランダムな部位・メインステータスを1本持って生成される個体差ありのアクセサリー(`rpg.api.RelicApi#generateRelic`、orelia-worldから利用)。アクセサリー枠は4→6種(お守り/指輪/ネックリス/羽根/耳飾り/ベルト)に拡張。メインステータスは部位ごとの固定プール(`relics.yml`)からランダムに1本、サブステータス(最大4本)は生成時点で`initial-substat-count-min/max`(既定3〜4本)だけ既に付いた状態で出現します。`/ol relic upgrade`(タブ補完対応)で3レベル毎(最大15、計5回)にプレイヤー自身が「新規追加するステータス」または「既存ステータスを強化」のどちらかを自由に選んで伸ばせます(4本目までは両方の選択肢が同時に提示されます)。1回の増加量は`relics.yml`の`substat-upgrade-min/max`(既定1〜2)からランダム — 完全ランダム厳選との差別化です。同じダンジョン産のレリックを2つ以上装備すると、そのダンジョン専用のセットボーナスが自動で付与されます(`relics.yml`の`dungeon-set-bonuses`)。ボスドロップとは別に、`relics.yml`の`shop-relics:`セクションで固定ステータス・最大レベル(だが控えめな数値)のレリックをNPCショップに並べることもできます(`npc.yml`のshop-stockで`kind: RELIC`)。厳選(`/ol relic upgrade`)はNPC経由でも開けます(`npc.yml`の`type: RELIC_UPGRADE`、`rpg.api.RelicApi#openUpgradeGui`)。
- ボスバー — ボスをスポーンさせると、7ブロック以内にいるプレイヤーにバニラのボスバー(HP進捗)が表示されます。名札のHPバーと同じスケール済みHPを参照するため数値は一致します。
- モンスターの名札には、スポーンポイントの`targetLevel`で目安レベルが設定されている場合、名前の横にレベルが表示されます(未設定の個体には表示されません)。プレイヤーがモンスターに倒された際の死亡メッセージも、名札のHPバー装飾が混入しないよう専用のメッセージ(`messages.yml`の`monster.death-message`)に差し替えられます — 近接攻撃・モンスターのスキルで放たれた矢などの遠距離攻撃に加え、範囲斬撃(AOE_SLAM)・火球連射(FIREBALL_BARRAGE)のようなダメージ元エンティティを持たないモンスター/ボスのアビリティ攻撃で倒された場合も対象です。
- 弓スキル(パワーショット/マルチショット)で放たれた矢は拾えず、着弾から一定時間(`config.yml`の`skill.arrow-despawn-ticks`、既定5秒)で自動的に消えます。
- バージョン管理 — `main` への push(=PRマージ)ごとに `.github/workflows/version-bump.yml` が `build.gradle.kts` の `version` を自動でPATCHインクリメントし、タグを打ちます。互換性が壊れる変更は `bump:minor`、大規模な改修は `bump:major` ラベルをPRに付けてからマージしてください。
- モンスターの強さ — `/oladmin spawnpoint add <monsterId> <intervalSeconds> <maxAlive> <targetLevel>` でスポーンポイントごとに目安レベルを設定します(`targetLevel`は必須 — `monsters.yml`自体にはレベルという概念が無く、HP・攻撃力・防御力はあくまでテンプレート値で、実際のレベルは必ずスポーンポイント側から与えられる設計のため)。指定したレベルに応じて `monsters.yml` の HP・攻撃力・防御力が `config.yml: monster-level-scaling` の係数でスケールされ、名札にもレベルが表示されます。この仕組みが導入される前に登録済みのスポーンポイントはレベル未設定のまま残るため、再登録するまで従来通りレベル無しで湧きます。ダンジョン産のモンスター/ボスは選択した難易度がそのままレベルとして使われるため常にレベルが付き、`/oladmin spawn <monsterId> [targetLevel]`(GM用の単発スポーン)は引き続きレベル省略可です。
- 合成 — `/ol craft` で `crafting.yml` に定義されたレシピの一覧を開き、素材を消費して武器を1個作成できます。
- デバッグモード — プレイヤーごとにon/offできる管理者用フラグ(`rpg.api.DebugApi#isDebugMode`/`setDebugMode`、`orelia-debug`の`/oladmin debugmode`から操作)。有効な間は武器の職業/レベル要件と、スキルの武器種一致・ソケット・習得済み・クールダウン・SP消費、釣りざおの職業要件の各チェックを全てバイパスして自由に使用できます(武器レベルアップやスキル習得ポイントの上限などの成長系ゲートは対象外)。インメモリのみで再ログインするとリセットされます。
- 職業「釣り人」— 釣りざおは他の職業の武器種制限と同様、職業が釣り人でないと使用できません(`rpg.gathering.listener.FishingListener`、ヴァニラの釣りざおは`items.yml`の武器データを持たないため`WeaponRequirementService`ではなく`PlayerFishEvent`ベースで判定)。釣り人レベル(採掘師/木こり/農民と同じ仕組みで独立してレベルが上がります。`/ol gathering`で確認可能)が上がるほど、浮きが沈むまでの待ち時間(`fishing.yml: catch-time`)が少しずつ短くなります。釣れるアイテムは`fishing.yml: towns`の下で重み付き抽選テーブルとして定義でき、浮きの位置にあるWorldGuardリージョンID(最優先、親子リージョンが重なっている場合は子リージョンのIDが常に親より優先されます)→プレイヤーがいるワールド名→`default`の順で一致するキーが使われます。エリアの追加や釣れるアイテムの変更は`fishing.yml`編集と`/oladmin reload`だけで反映され、コード変更は不要です。
- 職業「魔法使い」と魔法の杖 — 木こりの`HATCHET`とウォーリアーの`AXE`が同じ斧素材を共有しつつ完全に別カテゴリなのと同じ考え方で、`WeaponType.WAND`は農民の`HOE`と同じクワ素材を使いながら完全に別カテゴリとして扱われ、魔法使い以外は装備できません(`items.yml`の`magic_wand`)。杖は汎用のスキルソケットを持たず(`skill-slot-count: 0`)、代わりに3つの固定アクションを持ちます — 右クリックで雪玉ベースの氷弾を発射(命中した単体にダメージ、SP消費・クールタイムとも低めで連射が効く速射オプション)、左クリックで自分を中心とした半径5ブロックにエヴォーカーの牙のAOE攻撃、そして**杖をオフハンドに持ち替えた瞬間**(デフォルトFキー)に足元へ魔法陣を展開し、水平全方位8方向へのレーザーを1秒間隔で3連射します。いずれも自分自身には当たりません。レーザーは壁を貫通せず(通過できないブロックに当たったビームはそこで停止)、各連射(ウェーブ)ごとに命中判定を独立して持つため、同じ敵でも複数のビームが重なった分は1回、3連射なら最大3回ダメージを受けます。魔法陣とレーザーの原点は発動した瞬間の位置に固定されるため、発動後に移動しても演出・3連射ともその場に残ります。クールタイム中やSP不足で発動しなかった場合でも持ち替え自体は通常どおり成立します(杖がオフハンドから出せなくなるのを防ぐため)。3アクションとも武器の基礎攻撃力とATK%からダメージを計算し、DEF・会心・属性弱点は通常の近接ダメージと同じ経路(`rpg.monster.listener.CombatDamageListener`)で解決されます。召喚されるエヴォーカーの牙自体は演出専用で、ネイティブのダメージは適用されません。SP消費とクールタイムは`MagicWandAbilityListener`が専用に管理します。素材がクワと共通のため、`rpg.gathering.listener.FarmingListener`のしゃがみ収穫判定も武器種で識別しており、魔法の杖を持っていても農民の一括収穫は誤発動しません。同じ理由で、杖を持った右クリックはメインハンド・オフハンドのどちらでもバニラの耕作に落ちないよう抑止されます。
- 町判定・WorldGuard連携(`rpg.region` / `rpg.town`) — WorldGuardを導入している場合(ソフト依存、`plugin.yml: softdepend`、リフレクションのみでコンパイル時依存なし)、`config.yml: town-detection.town-regions`に列挙したWorldGuardリージョンID内を「町」として扱えます。1つの町が離れた複数エリアにまたがる場合は、各エリアを別々のWorldGuardリージョンとして作成し、そのIDを全て`town-regions`に列挙してください(WorldGuardのリージョンIDはワールド内で一意なため、1つのIDを複数の離れた形状に使い回すことはできません)。町判定はOrelia自身のモンスタースポーン(スポーンポイント/`/oladmin spawn`)を町の中では発生させないようにする目的で使われ、`rpg.api.TownApi#isInTown`としてorelia-world/orelia-extraにも公開されます。WorldGuard未導入時は判定処理自体が無効化され、既存の挙動に影響しません。
- 採取ノードの再生成と建築の共存(`rpg.gathering`) — 天然の採取ノードは伐採/採掘後に`gathering.yml`のクールダウンを経て自動で再生成されますが、`gathering.yml: regen-exclusion.regions`に列挙したWorldGuardリージョン内では採取システムが丸ごと無効になります(再生成なし・経験値なし・レベルゲートなし)。街の装飾木を伐採して木こりレベルを稼ぐこともできません。「どうやって置かれたブロックか」ではなく「どの場所か」で判定するため、建築物の中にそのまま残した自然木も、WorldEdit/スキマティックで配置した木(これらは`BlockPlaceEvent`を発火しません)も同じ扱いになります。野外の拠点も、その範囲にリージョンを作れば同様に保護されます。町判定(`config.yml: town-detection`)とは別リストです — 再生成させたくない森が必ずしも町とは限らないため。加えてWorldGuardに依存しない保険として、再生成は「その座標が再生成待ちブロックのまま(または空気)である間」だけ実行されます — 待機中の座標に誰かが別のブロックを建てていた場合はタスクを破棄し、建築物を上書きしません。管理者用に`/oladmin gathering resetregen confirm`(再生成待ちタスクの一括取り消し)があります。除外範囲の変更自体は`gathering.yml`編集と`/oladmin reload`だけで反映され、既に予約済みのタスクも復元時に再判定されます。
- ステータスGUI(`/ol status`) — 全ステータスを1アイテムのloreに詰め込むのではなく、基礎(HP/SP/ATK/DEF/移動速度/SP回復効率、手持ち武器を加味した「現在攻撃力」も表示)・会心(会心率/会心ダメージ)・属性ダメージ増加(6属性)の3アイテムに分けて表示。日本語ラベル表示、頭アイコンは自分のスキンで表示されます。所持金と、アクセサリー/レリックの装備枠6つ(お守り/指輪/ネックレス/羽根/耳飾り/ベルト)も同じ画面に表示されます。装備の付け外しはこのGUIの装備枠を直接クリックして行います(空き枠は赤色ステンドグラスのプレースホルダーで、部位が一致しないアイテムは入りません)。装備枠は27スロット中の2段目に6つ並びますが、9列を6等分できないため中央(3+3の真ん中)に説明用アイテムを1つ配置し、左右対称に見えるレイアウトにしています。装備状態はプレイヤーの実インベントリとは独立した仮想スロットとしてDBに保存されるため、再ログインやサーバー再起動後も保持されます。
- 武器スキルGUI(`/ol skill`) — 対象となる武器はメインハンドではなく**ホットバーの一番左(1番目)のスロット**に入っている武器で判定・装着します(ネザースターのプレイヤー情報メニューからこの画面を開くにはネザースターをメインハンドに持つ必要があり、その時点でメインハンドは武器ではなくなるため)。武器をホットバー左端から動かせなくする制限はなく、あくまでプレイヤー側がそこに武器を入れておく前提の設計です。右クリックで装着、**Shift+右クリックで装着解除**。装着中のスキルは何番目のソケットに入っているかloreに表示され、発動キーの説明もヘッダーに表示されます。スキルソケットは武器ごとに`items.yml`の`skill-slot-count`で決まり(現状は最大2枠)。SWORD/AXE/PICKAXE/HATCHETは1番目が右クリック、2番目が持ち替え(デフォルトFキー、バニラの利き手切り替えをキャンセルして発動)。BOW(弓・クロスボウ)/SPEAR(トライデント)/HOE(クワ)は右クリックがそれぞれ弓を構える・投擲する・耕すという固有動作を持つため右クリックでは発動せず、代わりに1番目は持ち替え(Fキー)、2番目はShift+持ち替えで発動します。スキル発動時のフィードバック(発動成功・クールダウン中・SP不足等)はチャットではなくアクションバー(HP/SP/ATK表示の隣)に一時表示されます。
- 遠距離武器(弓・クロスボウ)を近接で振った場合、そのアイテムの遠距離用攻撃力ではなく素手扱いのダメージになります。また属性弓の弱点属性ボーナス(×1.5)は、近接で振った時だけでなく実際に矢を放った時にも正しく適用されます。
- 近接スキル(突進・範囲斬り等)のノックバックは、ワールドのPvP設定がオフの場合は他プレイヤーに対して発生しなくなりました(モンスターへのノックバックは変わりません)。
- バニラ防具は装備禁止です — 防御力はステータスのDEF一本で決まります(バニラの防具ポイント/耐久力による軽減とDEFが二重に効くのを防ぐため)。ヘルメット/チェストプレート/レギンス/ブーツを装備しようとすると、右クリックによる装備そのものがキャンセルされ(`rpg.status.listener.ArmorBanListener#onInteract`)、それ以外の経路(GUIクリック・ドラッグ・ディスペンサー等)で防具が乗った場合も即座に外されて持ち物に戻されます。エリトラやカボチャ・モブの頭などは防御力を持たないため対象外です。この仕組みを利用し、弓/クロスボウの矢インフィニティは`minecraft:equippable`コンポーネントを付与した矢をレギンス枠に直接装備させることで実現しています(バニラの弾薬探索は防具枠も含めた全41スロットを見るため成立します) — 防具が禁止されている以上、この枠は常に空いています。この矢は`item_model`でバリアブロックの見た目・「&%cNo Slot」という名前に変更してあり(装備中の見た目そのものは非表示のまま)、インベントリ画面でクリックしても取り出せません。
- キャラクター成長の指数関数化(`config.yml: status.growth` / `stat-scaling.growth-rate`) — HP/SP/ATK/DEFは`scaled = base * growthRate^(level-1)`の指数成長になり、モンスターのレベルスケーリング(`stat-scaling.growth-rate.HP/ATK/DEF`)と同じ値を共有します(同じレベルのモンスターとプレイヤーが同じ倍率で伸びる設計)。CRT/CRT_DMG/SPDは従来通り`base + per-level`の線形成長のままです。レベル上限のデフォルトは100→80に変更(データ上は300まで対応)。桁が大きくなるため、モンスターの名札HPバー表示も`int`から`long`に変更しています。
- レベルアップ演出の統一(`rpg.status.service.LevelUpFeedbackService`) — キャラクターレベルアップ時にタイトル・サウンド・パーティクル(`config.yml: status.level-up-effect`で設定、無効なSound/Particle名は黙ってスキップ)に加え、伸びたステータスだけをチャットに一覧表示します。職業/採取レベルのレベルアップ(`GatheringLevelService`)も同じサービス経由になり、演出が統一されました。
- 経験値の常時可視化 — アクションバー(`config.yml: action-bar.format`の`{level}`/`{exp_bar}`)に現在レベルと次レベルまでの進捗バーを常時表示。ステータスGUI(`/ol status`)の頭アイコンのloreにも経験値(現在値/必要値、上限到達時は`MAX`)を表示します。
- 戦闘ダメージ計算式の詳細は [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) を参照してください。
- orelia-core/world/extra 3リポジトリを横断した未実装機能一覧は [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) を参照してください。

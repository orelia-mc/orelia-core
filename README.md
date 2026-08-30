<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia Core</h1>
<p align="center">RPG Suite Plugin of Orelia-MC</p>

## About

`orelia-core` は Minecraft RPG プラグイン群 **Orelia** のメインプラグイン(Paper 1.21.x / Java 21)です。旧`orelia-core`/`orelia-world`/`orelia-extra` の3リポジトリは本リポジトリへ統合されており、以後の開発はすべてここで行われます(旧2リポジトリはアーカイブ済み)。

Orelia は以下のプラグイン群で構成されています。

- **orelia-core**(本リポジトリ) — RPGのコアゲームプレイ全般(戦闘・アイテム・スキル・職業・クエスト・ダンジョン・ギルド・オークション等)
- [orelia-debug](https://github.com/orelia-mc/orelia-debug) — orelia-core のテストプレイを助ける管理者向けデバッグツール(独立したプラグイン)
- [orelia-serverutil](https://github.com/orelia-mc/orelia-serverutil) — RPG機能非依存のサーバー運用・UXプラグイン(ハブ転送、スコアボード/タブリストAPI、joinメッセージ等)

統合の経緯や名前空間互換などの技術的な移行詳細は [CHANGELOG.md](CHANGELOG.md) を参照してください。

## Setup

```bash
./gradlew build
```

`build/libs/orelia-core-1.0.0.jar` が生成されます。ビルドには `repo.papermc.io`(Paper API)と `jitpack.io`(Vault API)へのネットワークアクセスが必要です。

## Features

### 戦闘・成長

- ダメージ計算はDEF軽減→会心→属性弱点の固定パイプラインで解決(詳細は [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md))。
- HP/SP/ATK/DEFはレベルに対して指数成長、レベル上限は80(データ上は300まで対応)。
- モンスター/ボスはスポーンポイントの目安レベル(`targetLevel`)に応じてHP/ATK/DEF・名札表示が自動スケール。ボスにはボスバーも表示。
- バニラ防具は装備不可 — 防御力はDEFステータス一本で決まります。

### 職業・アイテム

- 職業ごとに使える武器種が異なります(釣り人=釣りざお、魔法使い=専用武器`WAND`など)。
- 武器の強化値(強化屋NPC)とレベル(`/ol item levelup`)は独立した2つの成長要素として合成されます。
- `/ol craft` でレシピから素材を消費して武器を合成できます。
- 武器スキルGUI(`/ol skill`)— ホットバー1〜3番目に入れた武器にスキルを装着できます。

### レリック・アクセサリー

- レリック(`/ol relic upgrade`)— ダンジョンボス討伐で個体差ありのアクセサリーを入手し、レベルごとに好きなステータスへ強化できます。同ダンジョン産を複数装備するとセットボーナスも発生。
- ステータスGUI(`/ol status`)— 基礎/会心/属性ダメージを1画面で確認でき、アクセサリー/レリックの装備枠6つもここから管理します。

### ワールド・エリア

- WorldGuard連携(ソフト依存)で、町判定・採取ノードの再生成除外エリアを設定できます。
- 釣りはWorldGuardリージョン/ワールド単位でドロップの抽選テーブルを切り替えられます。

### ソーシャル・経済

- ギルド/パーティー/フレンドはコマンドとGUIの両方から操作できます(招待・承認・追放・ロール管理等)。
- チャットはチャンネル単位でミュートできます。
- オークションは即売り・入札の両形式に対応。
- 住宅区画の購入や、ペット/乗り物の育成要素もあります。

### 管理者向け

- デバッグモード(`orelia-debug`から切替)— 武器/スキルの各種要件チェックを一時的にバイパスできます。
- `/oladmin config view` でconfigをYAML木構造のまま閲覧・クリック編集の補助ができます。
- 住宅区画・ダンジョンアリーナはコマンドで現地登録できます。

## Structure

- 公開API — 外部プラグイン(`orelia-debug` 等)は `rpg.api` / `rpg.world.api` / `rpg.extra.api`(Bukkitの `ServicesManager` 経由)でのみ本プラグインと連携します。内部モジュールクラスへの直接依存はサポート対象外です。
- 設定ファイル — 各モジュールが `src/main/resources/` 配下の専用ファイル(`items.yml`, `skills.yml`, `jobs.yml`, `config.yml` 等)を読み込みます。`/oladmin reload` で一括リロードでき、新しく追加されたキーは `config-version` により既存ファイルへ自動で追記されます。
- モジュール構成・登録順序・DB管理など、開発者向けの詳細な設計は [CLAUDE.md](CLAUDE.md) を参照してください。

## Documentation

- [CHANGELOG.md](CHANGELOG.md) — 変更履歴
- [DAMAGE_FORMULA.md](DAMAGE_FORMULA.md) — 戦闘ダメージ計算式の詳細
- [UNIMPLEMENTED_FEATURES.md](UNIMPLEMENTED_FEATURES.md) — 未実装機能一覧
- [CLAUDE.md](CLAUDE.md) — アーキテクチャ・開発規約

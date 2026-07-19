# ダメージ計算式

orelia-coreの戦闘ダメージ計算は、`rpg.status.combat.DamageFormula`(純粋な計算ユーティリティ、単体テスト付き)の`compute()`という単一のオーケストレーションメソッドに集約されています。以前は複数のリスナーが同じ優先度(`EventPriority.LOW`)で個別にダメージへ手を加える方式でしたが、Bukkitの同一優先度リスナーの実行順序は保証されないため、この方式では計算順序が事実上不定でした。現在は`rpg.monster.listener.CombatDamageListener`という単一のリスナーが、必要な入力(基礎攻撃力・ATK%・DEF・クリティカル関連値・属性弱点の有無)をすべて集めた上で`DamageFormula.compute()`を1回呼ぶだけの構成になっています。

## 計算の全体像

```
基礎攻撃力 → ATK%加算(現在攻撃力) → DEF軽減 → クリティカル判定 → 属性弱点倍率
```

## 1. 基礎攻撃力

攻撃手段によって、基礎攻撃力の出どころが異なります。

| 攻撃手段 | 基礎攻撃力 | 該当コード |
|---|---|---|
| プレイヤーの武器攻撃 | `武器の攻撃力 × 強化倍率` | `CombatDamageListener.resolveAttack()` |
| プレイヤーの素手攻撃 | `プレイヤーのATKステータス`(そのまま基礎攻撃力として使う。ATK%加算はスキップ — 二重適用を避けるため) | `CombatDamageListener.resolveAttack()` |
| スキル攻撃 | `武器の攻撃力 × 強化倍率 × スキルのレベル別倍率`、その場でATK%まで適用済み(1キャストにつき1回だけ計算し、複数対象に同じ値を使う) | `SkillDamage.baseDamage()` |
| モンスターの攻撃 | `モンスターの攻撃力`(`monsters.yml`の`attack-power`) | `CombatDamageListener.resolveAttack()` |

スキル攻撃だけは基礎攻撃力とATK%を`SkillDamage`側で先に計算します(AOE/コーン系スキルは対象ごとに同じ基礎ダメージを使うため、対象が決まる前に1回だけ計算する必要があるからです)。`CombatDamageListener`はこれを検知すると、ATK%加算をスキップし、DEF軽減以降だけを対象ごとに個別に計算します。

## 2. ATK%加算(現在攻撃力)

攻撃者がプレイヤーで、かつ武器を持っている場合のみ、`ATK`ステータスがパーセンテージボーナスとして基礎攻撃力に乗算されます。素手の場合は基礎攻撃力そのものが既にATKステータス由来のため、このステップはスキップされます。モンスターの攻撃力には適用されません。

```java
public static double applyAttackBonus(double damage, double atkPercent) {
    return damage * (1 + atkPercent / 100.0);
}
```

例: `ATK: 10`のとき → `damage × 1.10`。

## 3. DEF軽減

被弾側の防御力(プレイヤーなら`DEF`ステータス、モンスターなら`monsters.yml`の`defense`)が、同じ曲線で軽減を計算します。

```java
public static double mitigate(double damage, double defense) {
    return damage * (1 - defense / (defense + 100.0));
}
```

`defense = 100`で50%軽減、`defense = 0`で軽減なし、という緩やかな逓減曲線です。防御力がどれだけ高くても100%軽減にはなりません。

## 4. クリティカル判定

DEF軽減の**後**にクリティカル判定を行います。

- **判定確率** = 武器/モンスター自身の`crit-rate` + 攻撃者の`CRT`ステータス(プレイヤーのみ加算、モンスターは自身の`crit-rate`のみ)
- **命中したときの倍率** = `武器/モンスター自身のcrit-multiplier + 攻撃者のCRT_DMGステータス ÷ 100`(プレイヤーのみ加算)

```java
public static boolean rollCrit(double critRatePercent) {
    return MathUtil.rollChance(critRatePercent);
}
public static double criticalMultiplier(double baseCritMultiplier, double critDmgPercent) {
    return baseCritMultiplier + critDmgPercent / 100.0;
}
```

例: 武器の`crit-multiplier: 1.5`、プレイヤーの`CRT_DMG: 20`のとき → `1.5 + 20/100 = 1.7倍`。

## 5. 属性弱点倍率(モンスターが被弾側の場合のみ)

攻撃者が装備している武器の属性が、モンスターの`weakness`(`monsters.yml`)と一致する場合、固定で**×1.5**が乗算されます(`DamageFormula.DEFAULT_WEAKNESS_MULTIPLIER`)。プレイヤーが被弾側の場合、属性弱点の概念は現状ありません。

```java
public static double applyElementalWeakness(double damage, boolean weak, double multiplier) {
    return weak ? damage * multiplier : damage;
}
```

## 実例

**条件**: プレイヤー(見習いの剣、`attack-power: 4.0` `crit-rate: 5.0` `crit-multiplier: 1.5`、強化倍率1.0)が、`森のスライム`(`defense: 0` `weakness: FIRE`、装備武器は無属性のため弱点不一致)を攻撃。プレイヤーの最終ステータスは`ATK: 10` `CRT: 5` `CRT_DMG: 20`。

### 通常時(クリティカル不発生)

| ステップ | 計算 | 結果 |
|---|---|---|
| 基礎攻撃力 | `4.0 × 1.0` | 4.0 |
| ATK%加算 | `4.0 × (1 + 10/100)` | 4.4 |
| DEF軽減 | `4.4 × (1 - 0/(0+100))` | 4.4 |
| クリティカル判定 | 確率 `5 + 5 = 10%` → 不発生 | 4.4(変化なし) |
| 属性弱点 | 不一致 | **4.4** |

### クリティカル発生時

| ステップ | 計算 | 結果 |
|---|---|---|
| 基礎攻撃力 | `4.0 × 1.0` | 4.0 |
| ATK%加算 | `4.0 × (1 + 10/100)` | 4.4 |
| DEF軽減 | `4.4 × (1 - 0/(0+100))` | 4.4 |
| クリティカル判定 | 確率10%で発生、倍率 `1.5 + 20/100 = 1.7` | `4.4 × 1.7 = 7.48` |
| 属性弱点 | 不一致 | **7.48** |

## 実装上の注意

- 計算式の実体は`rpg/status/combat/DamageFormula.java`の`mitigate`/`applyAttackBonus`/`criticalMultiplier`/`rollCrit`/`applyElementalWeakness`と、それらを固定順序でまとめた`compute()`に集約されている。計算式を変更する場合はここを直す(リスナーに直接式を書かない)。
- `DamageFormula.CRIT_METADATA_KEY`は、クリティカルが発生した攻撃側エンティティに一時的に立てるBukkit metadataキー。ダメージ数値表示(`DamageDisplayListener`)がこれを読んで色・サイズを変える。
- `DamageFormula.SKILL_OVERRIDE_METADATA`は、スキル攻撃中であることを示すBukkit metadataキー。`CombatDamageListener`がこれを見て、基礎攻撃力とATK%の再計算をスキップする。
- `src/test/java/rpg/status/combat/DamageFormulaTest.java`に、乱数を含まない部分(`mitigate`/`applyAttackBonus`/`criticalMultiplier`/`applyElementalWeakness`/`compute`)の単体テストがある。

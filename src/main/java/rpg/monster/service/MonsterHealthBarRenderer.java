package rpg.monster.service;

import rpg.util.MathUtil;

/**
 * Formats a monster's nametag as "{name} [health bar] {current}/{max}" (config-driven
 * template/colors/length), used both at spawn (full HP) and after every hit
 * ({@code MonsterHealthBarListener}). Pure/testable - takes already-resolved config values
 * rather than reading {@code config.yml} itself.
 *
 * <p>The bar itself is drawn with the classic "strikethrough space" trick ({@code &m} +
 * spaces renders as a solid colored line through the gap) rather than a block character, so
 * it looks consistent across fonts/resource packs. Each segment (filled/empty) ends with
 * {@code &r} so the strikethrough doesn't bleed into the following segment or the
 * {@code {current}}/{@code {max}} text.
 */
public final class MonsterHealthBarRenderer {

    public String render(String name, double currentHp, double maxHp, int length, String format,
                          String filledColor, String emptyColor) {
        double ratio = maxHp > 0 ? MathUtil.clamp(currentHp / maxHp, 0, 1) : 0;
        int filled = MathUtil.clamp((int) Math.round(ratio * length), 0, length);
        int empty = length - filled;

        String bar = filledColor + "&m" + " ".repeat(filled) + "&r"
                + emptyColor + "&m" + " ".repeat(empty) + "&r";

        return format
                .replace("{name}", name)
                .replace("{bar}", bar)
                .replace("{current}", String.valueOf((int) Math.ceil(Math.max(0, currentHp))))
                .replace("{max}", String.valueOf((int) Math.ceil(maxHp)));
    }
}

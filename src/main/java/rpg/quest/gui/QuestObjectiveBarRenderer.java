package rpg.quest.gui;

import rpg.util.MathUtil;

/**
 * Renders a quest objective's progress as a filled/empty bar - the "strikethrough space"
 * trick ({@code &m} + spaces renders as a solid colored line through the gap), same idea as
 * orelia-core's {@code MonsterHealthBarRenderer} but reimplemented here since that class is
 * gameplay-module-internal and off-limits to import across the repo boundary.
 */
public final class QuestObjectiveBarRenderer {

    public String render(int current, int max, int length, String filledColor, String emptyColor) {
        double ratio = max > 0 ? MathUtil.clamp((double) current / max, 0, 1) : 0;
        int filled = MathUtil.clamp((int) Math.round(ratio * length), 0, length);
        int empty = length - filled;

        return filledColor + "&m" + " ".repeat(filled) + "&r"
                + emptyColor + "&m" + " ".repeat(empty) + "&r";
    }
}

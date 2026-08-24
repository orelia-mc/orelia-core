package rpg.core.command;

import org.bukkit.command.CommandSender;
import rpg.core.config.ConfigManager;
import rpg.util.ColorUtil;

import java.util.List;

/**
 * Renders the leaf-key changes {@link rpg.core.OreliaPlugin#reload()} reports, right after
 * {@code /oladmin reload}/{@code worldreload}/{@code extrareload}'s plain "reloaded" message.
 * Reload previously gave no feedback about whether an on-disk yml edit actually took effect -
 * an admin had to either trust it silently worked or go re-check the value by hand. Capped at
 * {@link #MAX_LINES_SHOWN} total changed lines across every file so a reload that touches a
 * huge/regenerated file (or one edited by a tool that reformats unrelated values) doesn't flood
 * chat - the count in each file's header is the true total either way.
 */
public final class ConfigReloadReport {

    private static final int MAX_LINES_SHOWN = 25;
    private static final int MAX_VALUE_LENGTH = 40;

    private ConfigReloadReport() {
    }

    public static void send(CommandSender sender, List<ConfigManager.FileDiff> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        int shown = 0;
        int totalChanges = diffs.stream().mapToInt(diff -> diff.changes().size()).sum();
        for (ConfigManager.FileDiff diff : diffs) {
            if (shown >= MAX_LINES_SHOWN) {
                break;
            }
            sender.sendMessage(ColorUtil.component(
                    "&%6" + diff.fileName() + " &%7- " + diff.changes().size() + "件の変更:"));
            for (ConfigManager.KeyChange change : diff.changes()) {
                if (shown >= MAX_LINES_SHOWN) {
                    break;
                }
                sender.sendMessage(ColorUtil.component("&%7  " + change.path() + "&%8: " + describe(change)));
                shown++;
            }
        }
        if (shown < totalChanges) {
            sender.sendMessage(ColorUtil.component("&%7...他" + (totalChanges - shown) + "件"));
        }
    }

    private static String describe(ConfigManager.KeyChange change) {
        if (change.before() == null) {
            return "&%a+ " + truncate(change.after());
        }
        if (change.after() == null) {
            return "&%c- " + truncate(change.before());
        }
        return "&%c" + truncate(change.before()) + " &%7-> &%a" + truncate(change.after());
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_VALUE_LENGTH) + "...";
    }
}

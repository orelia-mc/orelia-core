package rpg.core.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import rpg.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a paginated {@code /ol help}/{@code /oladmin help} listing out of an
 * {@link OlCommandRegistry}'s registered {@link OlCommandRegistry.Entry entries}, so every
 * plugin registering subcommands into the shared registries gets a consistent, readable
 * listing instead of hand-rolled usage strings. Shared by core/world/extra/debug.
 *
 * <p>Each entry renders as 3 lines (command+usage, indented description, blank spacer) rather
 * than one crammed "{@code /cmd usage - description}" line - reads far easier when several
 * entries are on screen at once. A {@code <a|b|c>} alternation inside {@code usage} (this
 * codebase's convention for "pick one of these") is reformatted to {@code < a / b / c >} with
 * the brackets/slashes recolored separately from the options themselves, matching the color
 * split already used in this suite's hand-written {@code usage.*} messages.yml strings (e.g.
 * {@code "&%7<&%ecreate&%7|&%einvite&%7|...&%7>"}) - the auto-generated help listing just never
 * had it applied to it before.
 */
public final class CommandHelpUtil {

    private static final int ENTRIES_PER_PAGE = 8;
    private static final int LINES_PER_ENTRY = 3;

    private CommandHelpUtil() {
    }

    /**
     * Sends a one-page listing of {@code entries} (1-indexed {@code page}) to {@code sender},
     * formatted as {@code /<rootLabel> <name> <usage>} + description, with clickable prev/next
     * page navigation.
     */
    public static void sendHelp(CommandSender sender, String rootLabel, List<OlCommandRegistry.Entry> entries, int page) {
        List<Component> lines = new ArrayList<>();
        for (OlCommandRegistry.Entry entry : entries) {
            String usage = entry.usage() == null || entry.usage().isBlank() ? entry.name() : entry.usage();
            lines.add(ColorUtil.component("&%e/" + rootLabel + " " + formatAlternation(usage)));
            lines.add(ColorUtil.component("&%7  " + entry.description()));
            lines.add(Component.empty());
        }
        Pagination.send(sender, "&%6&l/" + rootLabel + " ヘルプ&%7 ({page}/{total}ページ)", lines,
                ENTRIES_PER_PAGE * LINES_PER_ENTRY, page, "/" + rootLabel + " help",
                "&%7登録されているサブコマンドはありません。");
    }

    /**
     * Recolors every top-level {@code <a|b|c>} alternation in {@code usage} into
     * {@code &%7< &%ea &%7/ &%eb &%7/ &%ec &%7>} - a bracket group with no {@code |} inside it
     * (e.g. a plain {@code <player>} placeholder) is left untouched. Depth-aware so a nested
     * bracket inside one alternative (e.g. {@code <... |chat <message>>}) doesn't get its own
     * {@code |} pulled into the outer split, even though none of this suite's current usage
     * strings actually nest a {@code |} that deep.
     */
    private static String formatAlternation(String usage) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < usage.length()) {
            char c = usage.charAt(i);
            if (c == '<') {
                int end = matchingClose(usage, i);
                if (end < 0) {
                    out.append(usage, i, usage.length());
                    break;
                }
                List<String> parts = splitTopLevel(usage.substring(i + 1, end));
                if (parts.size() > 1) {
                    out.append("&%7< &%e").append(String.join(" &%7/ &%e", parts)).append(" &%7>&%e");
                } else {
                    out.append(usage, i, end + 1);
                }
                i = end + 1;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Index of the {@code >} matching the {@code <} at {@code openIndex}, or -1 if unbalanced. */
    private static int matchingClose(String s, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Splits {@code inner} on {@code |} only at bracket-depth 0 (ignores a {@code |} inside a nested {@code <...>}). */
    private static List<String> splitTopLevel(String inner) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == '|' && depth == 0) {
                parts.add(inner.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(inner.substring(start));
        return parts;
    }
}

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
 */
public final class CommandHelpUtil {

    private static final int PAGE_SIZE = 8;

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
            lines.add(ColorUtil.component("&%e/" + rootLabel + " " + usage + "&%7 - " + entry.description()));
        }
        Pagination.send(sender, "&%6&l/" + rootLabel + " ヘルプ&%7 ({page}/{total}ページ)", lines, PAGE_SIZE, page,
                "/" + rootLabel + " help", "&%7登録されているサブコマンドはありません。");
    }
}

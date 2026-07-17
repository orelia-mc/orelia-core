package rpg.core.command;

import org.bukkit.command.CommandSender;
import rpg.util.ColorUtil;

import java.util.List;

/**
 * Renders a paginated {@code /ol help}/{@code /oladmin help} listing out of an
 * {@link OlCommandRegistry}'s registered {@link OlCommandRegistry.Entry entries}, so every
 * plugin registering subcommands into the shared registries gets a consistent, readable
 * listing instead of hand-rolled usage strings. Shared by core/world/extra/debug.
 */
public final class CommandHelpUtil {

    private static final int PAGE_SIZE = 8;
    private static final String DIVIDER = "&8&m----------------------------------------";

    private CommandHelpUtil() {
    }

    /**
     * Sends a one-page listing of {@code entries} (1-indexed {@code page}) to {@code sender},
     * formatted as {@code /<rootLabel> <name> <usage>} + description, with a divider header/
     * footer and a hint for the next page when there is one.
     */
    public static void sendHelp(CommandSender sender, String rootLabel, List<OlCommandRegistry.Entry> entries, int page) {
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int clampedPage = Math.min(Math.max(page, 1), totalPages);

        sender.sendMessage(ColorUtil.component(DIVIDER));
        sender.sendMessage(ColorUtil.component("&6&l/" + rootLabel + " ヘルプ"
                + "&7 (" + clampedPage + "/" + totalPages + "ページ)"));
        sender.sendMessage(ColorUtil.component(DIVIDER));

        if (entries.isEmpty()) {
            sender.sendMessage(ColorUtil.component("&7登録されているサブコマンドはありません。"));
        } else {
            int fromIndex = (clampedPage - 1) * PAGE_SIZE;
            int toIndex = Math.min(fromIndex + PAGE_SIZE, entries.size());
            for (OlCommandRegistry.Entry entry : entries.subList(fromIndex, toIndex)) {
                String usage = entry.usage() == null || entry.usage().isBlank() ? entry.name() : entry.usage();
                sender.sendMessage(ColorUtil.component("&e/" + rootLabel + " " + usage
                        + "&7 - " + entry.description()));
            }
        }

        sender.sendMessage(ColorUtil.component(DIVIDER));
        if (clampedPage < totalPages) {
            sender.sendMessage(ColorUtil.component("&7次のページ: &f/" + rootLabel + " help " + (clampedPage + 1)));
        }
    }
}

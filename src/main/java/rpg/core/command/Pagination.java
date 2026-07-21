package rpg.core.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import rpg.util.ColorUtil;

import java.util.List;

/**
 * Renders a paginated listing with clickable prev/next navigation, shared by every {@code /ol}/
 * {@code /oladmin} command that lists more entries than fit on one screen (help, manual,
 * achievement, guild list, ...). {@code titleTemplate} may contain {@code {page}}/{@code {total}}
 * tokens; navigation runs {@code <baseCommand> <pageNumber>} on click.
 */
public final class Pagination {

    private static final String DIVIDER = "&%8&m----------------------------------------";
    private static final String DEFAULT_EMPTY_TEXT = "&%7表示する項目はありません。";

    private Pagination() {
    }

    public static void send(CommandSender sender, String titleTemplate, List<Component> lines,
                             int pageSize, int page, String baseCommand) {
        send(sender, titleTemplate, lines, pageSize, page, baseCommand, DEFAULT_EMPTY_TEXT);
    }

    public static void send(CommandSender sender, String titleTemplate, List<Component> lines,
                             int pageSize, int page, String baseCommand, String emptyText) {
        int totalPages = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        int clampedPage = Math.min(Math.max(page, 1), totalPages);

        sender.sendMessage(ColorUtil.component(DIVIDER));
        sender.sendMessage(ColorUtil.component(titleTemplate
                .replace("{page}", String.valueOf(clampedPage))
                .replace("{total}", String.valueOf(totalPages))));
        sender.sendMessage(navRow(clampedPage, totalPages, baseCommand));
        sender.sendMessage(ColorUtil.component(DIVIDER));

        if (lines.isEmpty()) {
            sender.sendMessage(ColorUtil.component(emptyText));
        } else {
            int fromIndex = (clampedPage - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, lines.size());
            for (Component line : lines.subList(fromIndex, toIndex)) {
                sender.sendMessage(line);
            }
        }

        sender.sendMessage(ColorUtil.component(DIVIDER));
        sender.sendMessage(navRow(clampedPage, totalPages, baseCommand));
    }

    private static Component navRow(int page, int totalPages, String baseCommand) {
        Component prev = page > 1
                ? ColorUtil.componentWithCommand("&%e« 前へ", baseCommand + " " + (page - 1))
                : ColorUtil.component("&%8« 前へ");
        Component next = page < totalPages
                ? ColorUtil.componentWithCommand("&%e次へ »", baseCommand + " " + (page + 1))
                : ColorUtil.component("&%8次へ »");
        return prev.append(ColorUtil.component("   ")).append(next);
    }
}

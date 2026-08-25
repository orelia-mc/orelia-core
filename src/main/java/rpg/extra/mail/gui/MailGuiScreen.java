package rpg.extra.mail.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.mail.model.MailMessage;
import rpg.extra.mail.service.MailService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.gui.framework.GuiPageLayout;
import rpg.gui.framework.GuiPaginator;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;
import rpg.util.LoreTextWrap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Mailbox screen (SOW MailModule). Reuses orelia-core's generic {@code Gui}/{@code
 * GuiButton} framework classes directly - pure UI plumbing, not gameplay logic - the same
 * way orelia-world's QuestGuiScreen does; orelia-core's already-registered
 * {@code GuiListener} handles the clicks. Paginated via {@code GuiPaginator} (bottom row
 * reserved for prev/next) now that an inbox can hold more than 54 entries.
 */
public final class MailGuiScreen {

    private static final GuiPageLayout LAYOUT = new GuiPageLayout(IntStream.range(0, 45).toArray(), 45, 53);

    private final MailService mailService;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public MailGuiScreen(MailService mailService, GuiManager guiManager, MessageManager messages) {
        this.mailService = mailService;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public Gui build(Player player) {
        return build(player, 0);
    }

    private Gui build(Player player, int page) {
        Gui gui = new Gui(ColorUtil.colorize("&%8メール"), 54);
        List<MailMessage> inbox = mailService.getInbox(player.getUniqueId());

        if (inbox.isEmpty()) {
            gui.set(22, new GuiButton(new ItemBuilder(Material.BARRIER).name(messages.format("mail.no-mail")).build(), (clicker, clickType) -> {
            }));
            return gui;
        }

        GuiPaginator.placePage(guiManager, gui, LAYOUT, inbox, page,
                message -> mailButton(message, page), p -> build(player, p));
        return gui;
    }

    private GuiButton mailButton(MailMessage message, int page) {
        Material icon = message.hasAttachments() ? Material.CHEST : Material.PAPER;
        List<String> lore = new ArrayList<>();
        lore.add("&%7差出人: &%f" + message.getSenderName());
        // The body can run arbitrarily long (free-text mail, or a system-generated auction/
        // trade notice) - Minecraft never wraps lore on its own, so a long line just runs off
        // the tooltip. LoreTextWrap splits it into several shorter lines instead.
        lore.addAll(LoreTextWrap.wrap("&%7" + message.getBody()));
        lore.add(message.hasAttachments() ? (message.isClaimed() ? "&%7添付物: 受取済み" : "&%a添付物あり - クリックで受け取り") : "&%7添付物なし");
        lore.add("&%8クリックで既読にする / Shift+クリックで削除");
        return new GuiButton(new ItemBuilder(icon)
                .name((message.isRead() ? "&%7" : "&%e&l") + message.getSubject())
                .lore(lore)
                .build(), (clicker, clickType) -> {
            if (clickType != null && clickType.startsWith("SHIFT_")) {
                mailService.delete(message);
                messages.send(clicker, "mail.deleted");
                guiManager.open(clicker, build(clicker, page));
                return;
            }
            mailService.markRead(message);
            if (message.hasAttachments() && !message.isClaimed()) {
                boolean claimed = mailService.claim(clicker, message);
                messages.send(clicker, claimed ? "mail.claimed" : "mail.claim-failed");
            }
            // markRead/claim change this message's icon (read state, "受取済み" line) -
            // reopen so the player sees the update immediately instead of a stale icon.
            guiManager.open(clicker, build(clicker, page));
        });
    }
}

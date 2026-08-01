package rpg.extra.mail.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.mail.model.MailMessage;
import rpg.extra.mail.service.MailService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * Mailbox screen (SOW MailModule). Reuses orelia-core's generic {@code Gui}/{@code
 * GuiButton} framework classes directly - pure UI plumbing, not gameplay logic - the same
 * way orelia-world's QuestGuiScreen does; orelia-core's already-registered
 * {@code GuiListener} handles the clicks.
 */
public final class MailGuiScreen {

    private final MailService mailService;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public MailGuiScreen(MailService mailService, GuiManager guiManager, MessageManager messages) {
        this.mailService = mailService;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(ColorUtil.colorize("&%8メール"), 54);
        List<MailMessage> inbox = mailService.getInbox(player.getUniqueId());

        if (inbox.isEmpty()) {
            gui.set(22, new GuiButton(new ItemBuilder(Material.BARRIER).name(messages.format("mail.no-mail")).build(), (clicker, clickType) -> {
            }));
            return gui;
        }

        int slot = 0;
        for (MailMessage message : inbox) {
            if (slot >= 54) {
                break;
            }
            Material icon = message.hasAttachments() ? Material.CHEST : Material.PAPER;
            gui.set(slot++, new GuiButton(new ItemBuilder(icon)
                    .name((message.isRead() ? "&%7" : "&%e&l") + message.getSubject())
                    .lore(List.of(
                            "&%7差出人: &%f" + message.getSenderName(),
                            "&%7" + message.getBody(),
                            message.hasAttachments() ? (message.isClaimed() ? "&%7添付物: 受取済み" : "&%a添付物あり - クリックで受け取り") : "&%7添付物なし",
                            "&%8クリックで既読にする"))
                    .build(), (clicker, clickType) -> {
                mailService.markRead(message);
                if (message.hasAttachments() && !message.isClaimed()) {
                    boolean claimed = mailService.claim(clicker, message);
                    messages.send(clicker, claimed ? "mail.claimed" : "mail.claim-failed");
                }
                // markRead/claim change this message's icon (read state, "受取済み" line) -
                // reopen so the player sees the update immediately instead of a stale icon.
                guiManager.open(clicker, build(clicker));
            }));
        }
        return gui;
    }
}

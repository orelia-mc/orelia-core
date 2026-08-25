package rpg.extra.mail.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.mail.gui.MailGuiScreen;
import rpg.extra.mail.model.MailMessage;
import rpg.extra.mail.service.MailService;
import rpg.gui.framework.GuiManager;

import java.util.List;

/**
 * {@code /ol mail} opens the mailbox GUI (SOW MailModule). {@code /ol mail send <player>
 * <subject...>} sends a text-only message (attachments stay GUI/API-only, to keep this
 * command from becoming its own item-transfer surface with all the confirmation/scam-guard
 * work that would need). {@code /ol mail delete <index>} removes an entry from the sender's
 * own inbox by its position in {@code /ol mail unread}'s ordering.
 */
public final class MailCommand implements CommandExecutor, TabCompleter {

    private final MailService mailService;
    private final MailGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public MailCommand(MailService mailService, MailGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.mailService = mailService;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("unread")) {
            long unread = mailService.getInbox(player.getUniqueId()).stream().filter(m -> !m.isRead()).count();
            messages.send(player, "mail.unread-count", "count", unread);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("send")) {
            if (args.length < 3) {
                messages.send(player, "usage.mail-send");
                return true;
            }
            handleSend(player, args);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                messages.send(player, "usage.mail-delete");
                return true;
            }
            handleDelete(player, args[1]);
            return true;
        }
        guiManager.open(player, guiScreen.build(player));
        return true;
    }

    private void handleSend(Player sender, String[] args) {
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "command.player-not-found", "player", args[1]);
            return;
        }
        String subject = String.join(" ", List.of(args).subList(2, args.length));
        MailService.SendResult result = mailService.sendFromPlayer(sender, target, subject, "");
        if (result == MailService.SendResult.OK) {
            messages.send(sender, "mail.sent", "player", target.getName());
        } else {
            messages.send(sender, "mail.send-failed-inbox-full", "player", target.getName());
        }
    }

    private void handleDelete(Player player, String indexArg) {
        int index;
        try {
            index = Integer.parseInt(indexArg);
        } catch (NumberFormatException e) {
            messages.send(player, "mail.delete-failed");
            return;
        }
        List<MailMessage> inbox = mailService.getInbox(player.getUniqueId());
        if (index < 0 || index >= inbox.size()) {
            messages.send(player, "mail.delete-failed");
            return;
        }
        mailService.delete(inbox.get(index));
        messages.send(player, "mail.deleted");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(List.of("unread", "send", "delete"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            return TabCompletions.onlinePlayerNames(args[1]);
        }
        return List.of();
    }
}

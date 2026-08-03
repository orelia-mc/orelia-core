package rpg.quest.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.quest.model.PlayerQuestComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * {@code /ol title list|equip <title>|unequip} - manage which earned quest title (SOW
 * {@code QuestReward}'s "title" reward, see {@link PlayerQuestComponent#getTitles()}) is
 * currently equipped, shown by orelia-serverutil's {@code {title}} chat/tab-list placeholder.
 */
public final class TitleCommand implements CommandExecutor, TabCompleter {

    private final PlayerDataManager playerDataManager;
    private final MessageManager messages;

    public TitleCommand(PlayerDataManager playerDataManager, MessageManager messages) {
        this.playerDataManager = playerDataManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                .flatMap(d -> d.component(PlayerQuestComponent.class))
                .orElse(null);
        if (component == null) {
            messages.send(sender, "quest.data-not-loaded");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            listTitles(sender, component);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "equip" -> {
                if (args.length < 2) {
                    messages.send(sender, "title.usage");
                    return true;
                }
                String title = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (!component.getTitles().contains(title)) {
                    messages.send(sender, "title.not-earned");
                    return true;
                }
                component.setEquippedTitle(title);
                messages.send(sender, "title.equipped", "title", title);
            }
            case "unequip" -> {
                component.setEquippedTitle(null);
                messages.send(sender, "title.unequipped");
            }
            default -> messages.send(sender, "title.usage");
        }
        return true;
    }

    private void listTitles(CommandSender sender, PlayerQuestComponent component) {
        Set<String> titles = component.getTitles();
        if (titles.isEmpty()) {
            messages.send(sender, "title.no-titles");
            return;
        }
        messages.send(sender, "title.list-header");
        String equipped = component.getEquippedTitle();
        for (String title : titles) {
            boolean isEquipped = title.equals(equipped);
            messages.sendRaw(sender, "title.list-entry", "title", title,
                    "equipped", isEquipped ? messages.format("title.equipped-tag") : "");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(List.of("list", "equip", "unequip"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("equip") && sender instanceof Player player) {
            PlayerQuestComponent component = playerDataManager.get(player.getUniqueId())
                    .flatMap(d -> d.component(PlayerQuestComponent.class))
                    .orElse(null);
            if (component != null) {
                return TabCompletions.matching(component.getTitles(), args[1]);
            }
        }
        return List.of();
    }
}

package rpg.item.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.item.manager.ItemManager;
import rpg.item.model.WeaponData;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.service.StatusService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ol item give <player> <id> [amount]} - admin-facing weapon spawner used for
 * testing and manual reward grants until the shop/quest reward pipelines cover it.
 * {@code /ol item levelup} lets a player level up their held weapon themselves, gated by
 * their own character level (see {@code rpg.item.service.WeaponIdentityService#levelUp}) -
 * a placeholder trigger until orelia-world wires this into an NPC/GUI.
 */
public final class ItemCommand implements CommandExecutor, TabCompleter {

    private final ItemManager itemManager;
    private final StatusService statusService;
    private final MessageManager messages;

    public ItemCommand(ItemManager itemManager, StatusService statusService, MessageManager messages) {
        this.itemManager = itemManager;
        this.statusService = statusService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("levelup")) {
            return levelUp(sender);
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            messages.send(sender, "item.usage-give", "label", label);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "item.player-not-found", "player", args[1]);
            return true;
        }

        String weaponId = args[2];
        int amount = args.length >= 4 ? parseAmount(args[3]) : 1;

        ItemStack weapon = itemManager.createWeapon(weaponId).orElse(null);
        if (weapon == null) {
            messages.send(sender, "item.unknown-weapon");
            return true;
        }
        weapon.setAmount(amount);
        target.getInventory().addItem(weapon);
        messages.send(sender, "item.given", "amount", amount, "weapon", weaponId, "player", target.getName());
        return true;
    }

    private boolean levelUp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        WeaponData data = weapon.getType() == Material.AIR
                ? null
                : itemManager.getIdentityService().dataOf(weapon).orElse(null);
        if (data == null) {
            messages.send(player, "item.not-holding-weapon");
            return true;
        }

        int playerLevel = statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);
        int newLevel = itemManager.getIdentityService().levelUp(weapon, data, playerLevel);
        if (newLevel < 0) {
            messages.send(player, "item.weapon-level-capped");
        } else {
            itemManager.refreshWeaponLore(weapon, data);
            messages.send(player, "item.weapon-leveled-up", "level", newLevel);
        }
        return true;
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            return matching(List.of("give", "levelup"), args.length == 0 ? "" : args[0]);
        }
        if (!args[0].equalsIgnoreCase("give")) {
            return List.of();
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return matching(names, args[1]);
        }
        if (args.length == 3) {
            return matching(itemManager.getAllWeapons().keySet(), args[2]);
        }
        return List.of();
    }

    private List<String> matching(Iterable<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}

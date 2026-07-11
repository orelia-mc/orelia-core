package rpg.item.command;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import rpg.item.manager.ItemManager;

/**
 * {@code /ol item give <player> <id> [amount]} - admin-facing weapon spawner used for
 * testing and manual reward grants until the shop/quest reward pipelines cover it.
 */
public final class ItemCommand implements CommandExecutor {

    private final ItemManager itemManager;

    public ItemCommand(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " give <player> <id> [amount]");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " give <player> <id> [amount]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }

        String weaponId = args[2];
        int amount = args.length >= 4 ? parseAmount(args[3]) : 1;

        ItemStack weapon = itemManager.createWeapon(weaponId).orElse(null);
        if (weapon == null) {
            sender.sendMessage(ChatColor.RED + "Unknown weapon id: " + weaponId);
            return true;
        }
        weapon.setAmount(amount);
        target.getInventory().addItem(weapon);
        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + weaponId + " to " + target.getName());
        return true;
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

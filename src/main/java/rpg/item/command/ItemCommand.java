package rpg.item.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerData;
import rpg.core.player.PlayerDataManager;
import rpg.item.manager.ItemManager;
import rpg.item.model.WeaponData;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.service.StatusService;

import java.util.List;

/**
 * {@code /oladmin item give <player> <id> [amount]} - admin-facing weapon spawner used for
 * testing and manual reward grants until the shop/quest reward pipelines cover it.
 * {@code /oladmin item levelup [amount]} lets a player level up their held weapon themselves,
 * gated by their own character level (see {@code rpg.item.service.WeaponIdentityService#levelUp})
 * - the normal in-game path is the weapon-levelup NPC (orelia-world), this command is a
 * manual/testing entry point. With debugmode active ({@code rpg.api.DebugApi#isDebugMode}),
 * the character-level gate is bypassed entirely (feeds {@link Integer#MAX_VALUE} as the
 * effective player level into the same cap formula, rather than duplicating/forking it) and
 * {@code [amount]} lets several levels be applied in one call - both otherwise painful to test
 * by hand one `/oladmin item levelup` at a time while also having to actually level up a
 * character just to unlock the next weapon-level tier.
 */
public final class ItemCommand implements CommandExecutor, TabCompleter {

    private final ItemManager itemManager;
    private final StatusService statusService;
    private final MessageManager messages;
    private final PlayerDataManager playerDataManager;

    public ItemCommand(ItemManager itemManager, StatusService statusService, MessageManager messages,
                        PlayerDataManager playerDataManager) {
        this.itemManager = itemManager;
        this.statusService = statusService;
        this.messages = messages;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("levelup")) {
            int amount = args.length >= 2 ? parseAmount(args[1]) : 1;
            return levelUp(sender, amount);
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

    private boolean levelUp(CommandSender sender, int amount) {
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

        boolean debugMode = playerDataManager.get(player.getUniqueId()).map(PlayerData::isDebugMode).orElse(false);
        // debugmode bypasses the character-level gate by feeding the cap formula an effectively
        // unlimited player level instead of forking/duplicating WeaponIdentityService#levelUp's
        // own cap logic - see this class's own doc comment.
        int effectivePlayerLevel = debugMode
                ? Integer.MAX_VALUE
                : statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);

        int newLevel = -1;
        int levelsGained = 0;
        for (int i = 0; i < amount; i++) {
            int result = itemManager.getIdentityService().levelUp(weapon, data, effectivePlayerLevel);
            if (result < 0) {
                break;
            }
            newLevel = result;
            levelsGained++;
        }

        if (levelsGained == 0) {
            messages.send(player, "item.weapon-level-capped");
        } else {
            itemManager.refreshWeaponLore(weapon, data);
            messages.send(player, "item.weapon-leveled-up", "level", newLevel);
        }
        return true;
    }

    private int parseAmount(String raw) {
        try {
            return Math.min(1000, Math.max(1, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(List.of("give", "levelup"), args.length == 0 ? "" : args[0]);
        }
        if (!args[0].equalsIgnoreCase("give")) {
            return List.of();
        }
        if (args.length == 2) {
            return TabCompletions.onlinePlayerNames(args[1]);
        }
        if (args.length == 3) {
            return TabCompletions.matching(itemManager.getAllWeapons().keySet(), args[2]);
        }
        return List.of();
    }
}

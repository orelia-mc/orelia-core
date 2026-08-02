package rpg.monster.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.economy.service.EconomyService;
import rpg.item.manager.ItemManager;
import rpg.monster.model.DropEntry;
import rpg.monster.model.MonsterData;
import rpg.status.service.StatusService;
import rpg.util.MathUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls a monster's drop table on death: items dropped at the death location, EXP and
 * money credited directly to the killer. Level-up feedback (title/sound/chat/stat lines) is
 * entirely {@link StatusService#addExperience}'s job via {@code LevelUpFeedbackService} - this
 * class used to also send its own level-up chat line/sound here, which meant a monster kill
 * that leveled you up announced it twice.
 */
public final class MonsterDropService {

    private final ItemManager itemManager;
    private final EconomyService economyService;
    private final StatusService statusService;

    public MonsterDropService(ItemManager itemManager, EconomyService economyService, StatusService statusService) {
        this.itemManager = itemManager;
        this.economyService = economyService;
        this.statusService = statusService;
    }

    public void rewardKiller(MonsterData data, Player killer, Location deathLocation) {
        statusService.addExperience(killer.getUniqueId(), data.getExpReward());

        double money = MathUtil.lerp(data.getMoneyMin(), data.getMoneyMax(), ThreadLocalRandom.current().nextDouble());
        if (money > 0) {
            economyService.deposit(killer.getUniqueId(), money);
        }

        for (DropEntry drop : data.getDrops()) {
            if (!MathUtil.rollChance(drop.getChancePercent())) {
                continue;
            }
            int amount = ThreadLocalRandom.current().nextInt(drop.getMinAmount(), drop.getMaxAmount() + 1);
            if (amount <= 0) {
                continue;
            }
            ItemStack stack = resolveDropStack(drop, amount);
            if (stack != null) {
                deathLocation.getWorld().dropItemNaturally(deathLocation, stack);
            }
        }
    }

    private ItemStack resolveDropStack(DropEntry drop, int amount) {
        if (drop.getWeaponId() != null) {
            return itemManager.createWeapon(drop.getWeaponId())
                    .map(stack -> {
                        stack.setAmount(amount);
                        return stack;
                    })
                    .orElse(null);
        }
        if (drop.getVanillaMaterial() != null) {
            try {
                return new ItemStack(Material.valueOf(drop.getVanillaMaterial().trim().toUpperCase()), amount);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}

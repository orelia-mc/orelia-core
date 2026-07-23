package rpg.monster.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.economy.service.EconomyService;
import rpg.item.manager.ItemManager;
import rpg.job.manager.JobManager;
import rpg.job.model.Job;
import rpg.job.service.JobService;
import rpg.monster.model.DropEntry;
import rpg.monster.model.MonsterData;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.service.StatusService;
import rpg.util.MathUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls a monster's drop table on death: items dropped at the death location, EXP and
 * money credited directly to the killer. A kill that pushes the killer's character level
 * up also announces it, naming the killer's currently equipped (combat) job - e.g. a
 * Warrior levels up by defeating enemies, distinct from the mining/farming level-ups
 * {@code GatheringLevelService} announces for the miner/farmer/woodcutter jobs.
 */
public final class MonsterDropService {

    private static final String NO_JOB_LABEL = "無職";

    private final ItemManager itemManager;
    private final EconomyService economyService;
    private final StatusService statusService;
    private final JobService jobService;
    private final JobManager jobManager;

    public MonsterDropService(ItemManager itemManager, EconomyService economyService, StatusService statusService,
                               JobService jobService, JobManager jobManager) {
        this.itemManager = itemManager;
        this.economyService = economyService;
        this.statusService = statusService;
        this.jobService = jobService;
        this.jobManager = jobManager;
    }

    public void rewardKiller(MonsterData data, Player killer, Location deathLocation) {
        int previousLevel = currentLevel(killer);
        statusService.addExperience(killer.getUniqueId(), data.getExpReward());
        int newLevel = currentLevel(killer);
        if (newLevel > previousLevel) {
            announceLevelUp(killer, newLevel);
        }

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

    private int currentLevel(Player player) {
        return statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);
    }

    private void announceLevelUp(Player killer, int newLevel) {
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        String jobName = jobService.getCurrentJob(killer.getUniqueId())
                .flatMap(jobManager::getDefinition)
                .map(Job::getDisplayName)
                .orElse(NO_JOB_LABEL);
        killer.sendMessage(Component.text(killer.getName() + ":" + jobName + "のレベルが" + newLevel + "に上がりました", NamedTextColor.GREEN));
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

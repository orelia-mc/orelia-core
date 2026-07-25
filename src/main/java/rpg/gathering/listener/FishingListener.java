package rpg.gathering.listener;

import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerData;
import rpg.core.player.PlayerDataManager;
import rpg.gathering.config.FishingConfig;
import rpg.gathering.model.FishingLootEntry;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.repository.FishingLootRepository;
import rpg.gathering.service.GatheringLevelService;
import rpg.job.model.JobType;
import rpg.job.service.JobService;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gates fishing rod use to the Fisherman job, the same way every other job restricts its
 * own weapon type - a vanilla fishing rod never carries a {@code WeaponData} id though, so
 * it never reaches {@code WeaponRequirementService}/{@code CombatDamageListener} like a
 * custom weapon does; this is the {@link PlayerFishEvent} analogue of that same check.
 * Debug mode bypasses it the same way it bypasses every other job/level weapon requirement.
 *
 * <p>Also scales bobber wait time down with the player's fisherman level (their own
 * {@link GatherActionType#FISHING} level, tracked the same as mining/woodcutting/farming)
 * and replaces the vanilla catch with a weighted roll from that world's {@code fishing.yml}
 * loot table, so catchable items can differ per town and be changed later without a code
 * change.
 */
public final class FishingListener implements Listener {

    private final JobService jobService;
    private final PlayerDataManager playerDataManager;
    private final GatheringLevelService levelService;
    private final FishingConfig fishingConfig;
    private final FishingLootRepository lootRepository;
    private final MessageManager messages;
    private final Random random = new Random();

    public FishingListener(JobService jobService, PlayerDataManager playerDataManager, GatheringLevelService levelService,
                            FishingConfig fishingConfig, FishingLootRepository lootRepository, MessageManager messages) {
        this.jobService = jobService;
        this.playerDataManager = playerDataManager;
        this.levelService = levelService;
        this.fishingConfig = fishingConfig;
        this.lootRepository = lootRepository;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (!isFisherman(uuid) && !isDebugMode(uuid)) {
                event.setCancelled(true);
                messages.send(player, "job.rod-requires-fisherman");
                return;
            }
            FishHook hook = event.getHook();
            int level = levelService.getLevel(uuid, GatherActionType.FISHING);
            hook.setMinWaitTime(fishingConfig.minWaitTicks(level));
            hook.setMaxWaitTime(fishingConfig.maxWaitTicks(level));
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(isFisherman(uuid) || isDebugMode(uuid))) {
            return;
        }

        if (event.getCaught() instanceof Item caughtItem) {
            List<FishingLootEntry> loot = lootRepository.lootFor(player.getWorld().getName());
            if (!loot.isEmpty()) {
                caughtItem.setItemStack(rollLoot(loot));
            }
        }
        levelService.addExperience(uuid, GatherActionType.FISHING, fishingConfig.getXpGainPerCatch());
    }

    private ItemStack rollLoot(List<FishingLootEntry> loot) {
        int totalWeight = loot.stream().mapToInt(FishingLootEntry::weight).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        FishingLootEntry chosen = loot.get(loot.size() - 1);
        for (FishingLootEntry entry : loot) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                chosen = entry;
                break;
            }
        }
        int amount = chosen.minAmount() >= chosen.maxAmount()
                ? chosen.minAmount()
                : chosen.minAmount() + random.nextInt(chosen.maxAmount() - chosen.minAmount() + 1);
        return new ItemStack(chosen.item(), amount);
    }

    private boolean isFisherman(UUID uuid) {
        return jobService.getCurrentJob(uuid).filter(JobType.FISHERMAN::equals).isPresent();
    }

    private boolean isDebugMode(UUID uuid) {
        return playerDataManager.get(uuid).map(PlayerData::isDebugMode).orElse(false);
    }
}

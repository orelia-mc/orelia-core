package rpg.gathering.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import rpg.core.OreliaPlugin;
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.GatherBlockTemplate;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.BlockRegenService;
import rpg.gathering.service.GatheringLevelService;
import rpg.gathering.service.PlacedBlockTrackingService;
import rpg.gathering.service.RegionProtectionService;
import rpg.item.ItemModule;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.job.manager.JobManager;
import rpg.job.model.Job;

import java.util.Optional;

/**
 * Hooks ore/log breaks (SOW 3.1): every configured block always regenerates after its
 * cooldown. Mining keeps the original sneak-triggered bulk sweep, sized by the player's
 * job-level radius (SOW 3.3). Woodcutting instead triggers its bulk-chop sweep from the
 * equipped axe (see {@link #resolveAxeData}): no sneaking required, and the radius/level-gate
 * come from the {@code HATCHET}-type weapon's own {@code items.yml} configuration rather than
 * from {@link LevelRadiusConfig}. A plain vanilla axe (or any tool with no recognized axe
 * identity) never triggers a bulk sweep - single-block breaks only.
 *
 * <p>Blocks a player placed by hand (tracked by {@link PlacedBlockTrackingService}, populated
 * by {@link GatherBlockPlaceListener}) are excluded entirely from this listener - no regen, no
 * XP, no level gate - so building material doesn't grow back like a natural gathering node.
 *
 * <p>The cube search itself stays synchronous even though SOW 4.1 asks for async block
 * search: at the configured radius cap it is at most a few hundred {@code Block#getType()}
 * reads on chunks the breaking player is already standing in - negligible cost, and Paper does
 * not allow touching block/chunk state off the main thread anyway. Only the database
 * read/write for regen persistence is dispatched async.
 */
public final class GatherBlockBreakListener implements Listener {

    private final GatheringDefinitionRepository definitions;
    private final BlockRegenService regenService;
    private final GatheringLevelService levelService;
    private final LevelRadiusConfig radiusConfig;
    private final RegionProtectionService protectionService;
    private final JobManager jobManager;
    private final PlacedBlockTrackingService trackingService;
    private final OreliaPlugin plugin;

    public GatherBlockBreakListener(GatheringDefinitionRepository definitions, BlockRegenService regenService,
                                     GatheringLevelService levelService, LevelRadiusConfig radiusConfig,
                                     RegionProtectionService protectionService, JobManager jobManager,
                                     PlacedBlockTrackingService trackingService, OreliaPlugin plugin) {
        this.definitions = definitions;
        this.regenService = regenService;
        this.levelService = levelService;
        this.radiusConfig = radiusConfig;
        this.protectionService = protectionService;
        this.jobManager = jobManager;
        this.trackingService = trackingService;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        GatherBlockTemplate template = definitions.getGatherBlocks().get(block.getType());
        if (template == null) {
            return;
        }
        if (trackingService.isPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ())) {
            trackingService.clearPlaced(block.getWorld(), block.getX(), block.getY(), block.getZ());
            return;
        }

        Player player = event.getPlayer();
        int playerLevel = levelService.getLevel(player.getUniqueId(), template.actionType());
        if (playerLevel < template.minLevel()) {
            event.setCancelled(true);
            String jobName = jobManager.getDefinition(template.actionType().jobType()).map(Job::getDisplayName)
                    .orElse(template.actionType().jobType().name());
            player.sendMessage(Component.text(jobName + "レベルが不足しています。(必要Lv: " + template.minLevel() + ")", NamedTextColor.RED));
            return;
        }
        if (!protectionService.canModify(player, block)) {
            event.setCancelled(true);
            return;
        }

        Optional<WeaponData> axeData = Optional.empty();
        if (template.actionType() == GatherActionType.WOODCUTTING) {
            axeData = resolveAxeData(player);
            if (axeData.isPresent() && playerLevel < axeData.get().getGatherRequiredLevel()) {
                event.setCancelled(true);
                player.sendMessage(Component.text("この斧を使うには木こりレベルが不足しています。(必要Lv: "
                        + axeData.get().getGatherRequiredLevel() + ")", NamedTextColor.RED));
                return;
            }
        }

        block.getWorld().playSound(block.getLocation(), breakSound(template.actionType()), 1f, 1f);

        // Vanilla removes the block and spawns drops only after this handler returns, so
        // the replace-block swap for the block the event fired on has to wait a tick.
        regenService.scheduleNextTick(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                template.blockType(), template.replaceBlock(), template.cooldownSeconds());
        levelService.addExperience(player.getUniqueId(), template.actionType(), template.xpGain());

        if (template.actionType() == GatherActionType.WOODCUTTING) {
            axeData.filter(data -> data.getBulkChopRadius() > 0)
                    .ifPresent(data -> sweepAndBreak(player, block, template, data.getBulkChopRadius()));
        } else if (player.isSneaking()) {
            bulkBreak(player, block, template);
        }
    }

    /**
     * Resolves the equipped tool to its {@code HATCHET}-type weapon template, if any. {@code
     * ItemModule} is registered after {@code GatheringModule} (see {@code OreliaPlugin.onEnable}
     * ordering), so it can't be looked up during this module's own {@code onEnable} - this
     * lookup happens lazily here, at block-break time, by which point every module is already
     * enabled.
     */
    private Optional<WeaponData> resolveAxeData(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        return plugin.getModuleManager().get(ItemModule.class)
                .map(ItemModule::getItemManager)
                .flatMap(itemManager -> itemManager.getIdentityService().dataOf(tool))
                .filter(data -> data.getWeaponType() == WeaponType.HATCHET);
    }

    private void bulkBreak(Player player, Block center, GatherBlockTemplate template) {
        int radius = radiusConfig.radiusForLevel(levelService.getLevel(player.getUniqueId(), template.actionType()));
        if (radius <= 0) {
            return;
        }
        sweepAndBreak(player, center, template, radius);
    }

    private void sweepAndBreak(Player player, Block center, GatherBlockTemplate template, int radius) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Block target = center.getRelative(dx, dy, dz);
                    if (target.getType() != template.blockType()) {
                        continue;
                    }
                    if (trackingService.isPlaced(target.getWorld(), target.getX(), target.getY(), target.getZ())) {
                        continue;
                    }
                    if (!protectionService.canModify(player, target)) {
                        continue;
                    }
                    // breakNaturally() removes the block and spawns drops immediately, so
                    // scheduling the regen right after it is safe (no race, unlike the
                    // event-triggered break above). Unlike the event-triggered break,
                    // breakNaturally() does not play a break sound on its own, so it is
                    // added explicitly here.
                    target.breakNaturally(tool);
                    target.getWorld().playSound(target.getLocation(), breakSound(template.actionType()), 1f, 1f);
                    regenService.schedule(target.getWorld(), target.getX(), target.getY(), target.getZ(),
                            template.blockType(), template.replaceBlock(), template.cooldownSeconds());
                    levelService.addExperience(player.getUniqueId(), template.actionType(), template.xpGain());
                }
            }
        }
    }

    private Sound breakSound(GatherActionType actionType) {
        return actionType == GatherActionType.WOODCUTTING ? Sound.BLOCK_WOOD_BREAK : Sound.BLOCK_STONE_BREAK;
    }
}

package rpg.gathering.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import rpg.core.OreliaPlugin;
import rpg.gathering.config.MiningLuckConfig;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.GatherBlockTemplate;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.BlockRegenService;
import rpg.gathering.service.BulkRadiusResolver;
import rpg.gathering.service.GatheringLevelService;
import rpg.gathering.service.PlacedBlockTrackingService;
import rpg.gathering.service.RegionProtectionService;
import rpg.item.ItemModule;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.job.manager.JobManager;
import rpg.job.model.Job;
import rpg.util.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hooks ore/log breaks (SOW 3.1): every configured block always regenerates after its
 * cooldown. Woodcutting triggers its bulk sweep from the equipped hatchet (see
 * {@link #resolveToolData}) - no sneaking required, and the radius/level-gate come from the
 * {@code HATCHET}-type weapon's own {@code items.yml} configuration. Mining has no bulk-break
 * - it always breaks a single block, but an equipped pickaxe's own level-gate (also read via
 * {@link #resolveToolData}) still blocks use entirely below its required level, and its
 * {@code luck-level} rolls a bonus-drop chance per break (see {@link #applyMiningLuckBonus}).
 * A plain vanilla tool (or any tool with no recognized identity) triggers neither mechanic.
 *
 * <p>Whatever the activity, a break that vanilla would yield no drops for (too weak a tool for
 * the block's harvest tier) is rejected outright rather than silently costing the player the
 * node's regen cooldown for nothing. Creative players are exempt.
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
    private final RegionProtectionService protectionService;
    private final JobManager jobManager;
    private final PlacedBlockTrackingService trackingService;
    private final MiningLuckConfig miningLuckConfig;
    private final OreliaPlugin plugin;

    public GatherBlockBreakListener(GatheringDefinitionRepository definitions, BlockRegenService regenService,
                                     GatheringLevelService levelService, RegionProtectionService protectionService,
                                     JobManager jobManager, PlacedBlockTrackingService trackingService,
                                     MiningLuckConfig miningLuckConfig, OreliaPlugin plugin) {
        this.definitions = definitions;
        this.regenService = regenService;
        this.levelService = levelService;
        this.protectionService = protectionService;
        this.jobManager = jobManager;
        this.trackingService = trackingService;
        this.miningLuckConfig = miningLuckConfig;
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
        GatherActionType actionType = template.actionType();
        int playerLevel = levelService.getLevel(player.getUniqueId(), actionType);
        if (playerLevel < template.minLevel()) {
            event.setCancelled(true);
            String jobName = jobManager.getDefinition(actionType.jobType()).map(Job::getDisplayName)
                    .orElse(actionType.jobType().name());
            player.sendMessage(Component.text(jobName + "レベルが不足しています。(必要Lv: " + template.minLevel() + ")", NamedTextColor.RED));
            return;
        }
        if (!protectionService.canModify(player, block)) {
            event.setCancelled(true);
            return;
        }

        Optional<WeaponData> toolData = resolveToolData(player, requiredToolType(actionType));
        if (toolData.isPresent() && playerLevel < toolData.get().getGatherRequiredLevel()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("この" + toolLabel(actionType) + "を使うには" + jobLevelLabel(actionType)
                    + "が不足しています。(必要Lv: " + toolData.get().getGatherRequiredLevel() + ")", NamedTextColor.RED));
            return;
        }

        // A block broken with too weak a tool still breaks in vanilla, it just yields nothing.
        // Letting that through here would hand out gathering XP and lock the node behind its
        // regen cooldown for everyone else while the breaker got no drops at all. Creative
        // players are exempt - they break blocks bare-handed by design.
        List<ItemStack> drops = new ArrayList<>(block.getDrops(player.getInventory().getItemInMainHand()));
        if (drops.isEmpty() && player.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage(Component.text("このツールでは回収できません。適切なツールを装備してください。",
                    NamedTextColor.RED));
            return;
        }

        block.getWorld().playSound(block.getLocation(), actionType.breakSound(), 1f, 1f);

        if (actionType == GatherActionType.MINING) {
            applyMiningLuckBonus(event, drops, toolData.map(WeaponData::getLuckLevel).orElse(0));
        }

        // Vanilla removes the block and spawns drops only after this handler returns, so
        // the replace-block swap for the block the event fired on has to wait a tick.
        regenService.scheduleNextTick(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                template.blockType(), template.replaceBlock(), template.cooldownSeconds());
        levelService.addExperience(player.getUniqueId(), actionType, template.xpGain());

        if (actionType == GatherActionType.WOODCUTTING) {
            int radius = BulkRadiusResolver.equippedToolRadius(playerLevel,
                    toolData.map(WeaponData::getBulkChopRadius).orElse(0),
                    toolData.map(WeaponData::getGatherRequiredLevel).orElse(0));
            if (radius > 0) {
                sweepAndBreak(player, block, template, radius);
            }
        }
    }

    /**
     * Rolls {@code luckLevel} times (0 = no-op) at {@link MiningLuckConfig#getBonusChancePercent()}
     * each, adding +1 to a random entry of {@code drops} per success. {@code BlockBreakEvent}
     * has no way to override its drop list directly ({@code setDropItems} only takes a boolean),
     * so vanilla's own drops are suppressed and the bonus-adjusted list is dropped manually
     * instead - which is also why a zero luck level has to return before touching the event at
     * all, leaving vanilla to handle the drops as usual.
     */
    private void applyMiningLuckBonus(BlockBreakEvent event, List<ItemStack> drops, int luckLevel) {
        // drops can still be empty here despite the harvest guard above, since that guard
        // deliberately lets creative-mode players through.
        if (luckLevel <= 0 || drops.isEmpty() || !event.isDropItems()) {
            return;
        }
        for (int i = 0; i < luckLevel; i++) {
            if (!MathUtil.rollChance(miningLuckConfig.getBonusChancePercent())) {
                continue;
            }
            ItemStack boosted = drops.get(ThreadLocalRandom.current().nextInt(drops.size()));
            boosted.setAmount(boosted.getAmount() + 1);
        }
        event.setDropItems(false);
        Location location = event.getBlock().getLocation().toCenterLocation();
        for (ItemStack drop : drops) {
            location.getWorld().dropItemNaturally(location, drop);
        }
    }

    private WeaponType requiredToolType(GatherActionType actionType) {
        return actionType == GatherActionType.WOODCUTTING ? WeaponType.HATCHET : WeaponType.PICKAXE;
    }

    private String toolLabel(GatherActionType actionType) {
        return actionType == GatherActionType.WOODCUTTING ? "斧" : "つるはし";
    }

    private String jobLevelLabel(GatherActionType actionType) {
        return actionType == GatherActionType.WOODCUTTING ? "木こりレベル" : "採掘レベル";
    }

    /**
     * Resolves the equipped tool to its {@code requiredType}-type weapon template, if any.
     * {@code ItemModule} is registered after {@code GatheringModule} (see
     * {@code OreliaPlugin.onEnable} ordering), so it can't be looked up during this module's
     * own {@code onEnable} - this lookup happens lazily here, at block-break time, by which
     * point every module is already enabled.
     */
    private Optional<WeaponData> resolveToolData(Player player, WeaponType requiredType) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        return plugin.getModuleManager().get(ItemModule.class)
                .map(ItemModule::getItemManager)
                .flatMap(itemManager -> itemManager.getIdentityService().dataOf(tool))
                .filter(data -> data.getWeaponType() == requiredType);
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
                    target.getWorld().playSound(target.getLocation(), template.actionType().breakSound(), 1f, 1f);
                    regenService.schedule(target.getWorld(), target.getX(), target.getY(), target.getZ(),
                            template.blockType(), template.replaceBlock(), template.cooldownSeconds());
                    levelService.addExperience(player.getUniqueId(), template.actionType(), template.xpGain());
                }
            }
        }
    }
}

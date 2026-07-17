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
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.model.GatherBlockTemplate;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.BlockRegenService;
import rpg.gathering.service.GatheringLevelService;
import rpg.gathering.service.RegionProtectionService;

/**
 * Hooks ore/log breaks (SOW 3.1): every configured block always regenerates after its
 * cooldown; sneaking additionally clears every matching block within the player's
 * level-based radius (SOW 3.3), searched as a cube around the broken block so it covers
 * both flat ore veins and vertical tree trunks.
 *
 * <p>The cube search itself stays synchronous even though SOW 4.1 asks for async block
 * search: at the configured radius cap (9x9x9, level 40-50) it is at most a few hundred
 * {@code Block#getType()} reads on chunks the breaking player is already standing in -
 * negligible cost, and Paper does not allow touching block/chunk state off the main thread
 * anyway. Only the database read/write for regen persistence is dispatched async.
 */
public final class GatherBlockBreakListener implements Listener {

    private final GatheringDefinitionRepository definitions;
    private final BlockRegenService regenService;
    private final GatheringLevelService levelService;
    private final LevelRadiusConfig radiusConfig;
    private final RegionProtectionService protectionService;

    public GatherBlockBreakListener(GatheringDefinitionRepository definitions, BlockRegenService regenService,
                                     GatheringLevelService levelService, LevelRadiusConfig radiusConfig,
                                     RegionProtectionService protectionService) {
        this.definitions = definitions;
        this.regenService = regenService;
        this.levelService = levelService;
        this.radiusConfig = radiusConfig;
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        GatherBlockTemplate template = definitions.getGatherBlocks().get(block.getType());
        if (template == null) {
            return;
        }
        Player player = event.getPlayer();
        int playerLevel = levelService.getLevel(player.getUniqueId());
        if (playerLevel < template.minLevel()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("採取レベルが不足しています。(必要Lv: " + template.minLevel() + ")", NamedTextColor.RED));
            return;
        }
        if (!protectionService.canModify(player, block)) {
            event.setCancelled(true);
            return;
        }

        block.getWorld().playSound(block.getLocation(), breakSound(template.actionType()), 1f, 1f);

        // Vanilla removes the block and spawns drops only after this handler returns, so
        // the replace-block swap for the block the event fired on has to wait a tick.
        regenService.scheduleNextTick(block.getWorld(), block.getX(), block.getY(), block.getZ(),
                template.blockType(), template.replaceBlock(), template.cooldownSeconds());
        levelService.addExperience(player.getUniqueId(), template.xpGain());

        if (player.isSneaking()) {
            bulkBreak(player, block, template);
        }
    }

    private void bulkBreak(Player player, Block center, GatherBlockTemplate template) {
        int radius = radiusConfig.radiusForLevel(levelService.getLevel(player.getUniqueId()));
        if (radius <= 0) {
            return;
        }
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
                    levelService.addExperience(player.getUniqueId(), template.xpGain());
                }
            }
        }
    }

    private Sound breakSound(GatherActionType actionType) {
        return actionType == GatherActionType.WOODCUTTING ? Sound.BLOCK_WOOD_BREAK : Sound.BLOCK_STONE_BREAK;
    }
}

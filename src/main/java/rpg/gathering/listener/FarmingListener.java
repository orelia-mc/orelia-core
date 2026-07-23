package rpg.gathering.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import rpg.gathering.config.LevelRadiusConfig;
import rpg.gathering.model.CropTemplate;
import rpg.gathering.model.GatherActionType;
import rpg.gathering.repository.GatheringDefinitionRepository;
import rpg.gathering.service.GatheringLevelService;
import rpg.gathering.service.RegionProtectionService;

import java.util.ArrayList;
import java.util.List;

/**
 * Hooks manual crop planting and harvesting (SOW 3.2). A single right-click/break always
 * behaves like vanilla; sneaking additionally plants/harvests every eligible farmland tile
 * within the player's level-based radius (SOW 3.3), in the same horizontal plane since
 * farmland is inherently flat. Bulk planting is capped by the seed count in hand; bulk
 * harvesting requires a hoe in hand and is capped by that hoe's remaining durability - one
 * seed or one durability point per tile, matching SOW 3.2's shared cap sentence.
 */
public final class FarmingListener implements Listener {

    private final GatheringDefinitionRepository definitions;
    private final GatheringLevelService levelService;
    private final LevelRadiusConfig radiusConfig;
    private final RegionProtectionService protectionService;

    public FarmingListener(GatheringDefinitionRepository definitions, GatheringLevelService levelService,
                            LevelRadiusConfig radiusConfig, RegionProtectionService protectionService) {
        this.definitions = definitions;
        this.levelService = levelService;
        this.radiusConfig = radiusConfig;
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();
        if (clicked == null || clicked.getType() != Material.FARMLAND || !player.isSneaking()) {
            return;
        }
        ItemStack inHand = event.getItem();
        if (inHand == null) {
            return;
        }
        Material cropType = definitions.getSeedToCrop().get(inHand.getType());
        if (cropType == null) {
            return;
        }
        int radius = radiusConfig.radiusForLevel(levelService.getLevel(player.getUniqueId(), GatherActionType.FARMING));
        if (radius <= 0) {
            return;
        }
        // We plant the clicked tile ourselves below (it's included in the search grid), so
        // suppress vanilla's own single-tile planting to avoid double-consuming a seed.
        event.setCancelled(true);

        int available = inHand.getAmount();
        int planted = 0;
        for (int dx = -radius; dx <= radius && planted < available; dx++) {
            for (int dz = -radius; dz <= radius && planted < available; dz++) {
                Block farmland = clicked.getRelative(dx, 0, dz);
                if (farmland.getType() != Material.FARMLAND) {
                    continue;
                }
                Block above = farmland.getRelative(0, 1, 0);
                if (above.getType() != Material.AIR) {
                    continue;
                }
                if (!protectionService.canModify(player, above)) {
                    continue;
                }
                above.setType(cropType, false);
                planted++;
            }
        }
        if (planted <= 0) {
            return;
        }
        int remaining = available - planted;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(remaining);
            player.getInventory().setItemInMainHand(inHand);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        CropTemplate template = definitions.getCrops().get(block.getType());
        if (template == null || !isFullyGrown(block)) {
            return;
        }
        Player player = event.getPlayer();
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
        levelService.addExperience(player.getUniqueId(), GatherActionType.FARMING, template.xpGain());

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || !isHoe(tool.getType())) {
            return;
        }
        int radius = radiusConfig.radiusForLevel(levelService.getLevel(player.getUniqueId(), GatherActionType.FARMING));
        int durability = remainingDurability(tool);
        if (radius <= 0 || durability <= 0) {
            return;
        }

        List<Block> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block candidate = block.getRelative(dx, 0, dz);
                if (candidate.getType() == template.cropType() && isFullyGrown(candidate)) {
                    targets.add(candidate);
                }
            }
        }

        int harvested = 0;
        for (Block target : targets) {
            if (harvested >= durability) {
                break;
            }
            if (!protectionService.canModify(player, target)) {
                continue;
            }
            CropTemplate targetTemplate = definitions.getCrops().get(target.getType());
            target.breakNaturally(tool);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
            levelService.addExperience(player.getUniqueId(), GatherActionType.FARMING, targetTemplate.xpGain());
            harvested++;
        }
        if (harvested > 0) {
            damageTool(player, tool, harvested);
        }
    }

    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        return !(data instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge();
    }

    private boolean isHoe(Material material) {
        return material.name().endsWith("_HOE");
    }

    private int remainingDurability(ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable damageable)) {
            return 0;
        }
        return tool.getType().getMaxDurability() - damageable.getDamage();
    }

    private void damageTool(Player player, ItemStack tool, int amount) {
        if (!(tool.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        int newDamage = damageable.getDamage() + amount;
        if (newDamage >= tool.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return;
        }
        damageable.setDamage(newDamage);
        tool.setItemMeta((ItemMeta) damageable);
        player.getInventory().setItemInMainHand(tool);
    }
}

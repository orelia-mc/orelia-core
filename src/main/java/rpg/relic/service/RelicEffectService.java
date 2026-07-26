package rpg.relic.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.accessory.model.AccessoryType;
import rpg.accessory.model.PlayerAccessoryEquipmentComponent;
import rpg.core.player.PlayerDataManager;
import rpg.relic.config.RelicConfig;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicLine;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies/removes an equipped relic's stat lines (main + substats) via the same permanent-buff
 * mechanism {@code StatusService#addBuff}/{@code removeBuffsFromSource} already offers - see
 * the relic system's Context note on reusing this pipeline instead of extending
 * {@code StatusCalculatorService}. Also recomputes the "wear 2+ relics from the same dungeon"
 * set bonus ({@code relics.yml}: {@code dungeon-set-bonuses}) whenever any accessory slot
 * changes. Reads what's equipped from {@link PlayerAccessoryEquipmentComponent} (a virtual,
 * GUI-driven slot set), not the player's real inventory.
 */
public final class RelicEffectService {

    private final StatusService statusService;
    private final RelicIdentityService identityService;
    private final RelicConfig config;
    private final PlayerDataManager playerDataManager;

    public RelicEffectService(StatusService statusService, RelicIdentityService identityService, RelicConfig config,
                               PlayerDataManager playerDataManager) {
        this.statusService = statusService;
        this.identityService = identityService;
        this.config = config;
        this.playerDataManager = playerDataManager;
    }

    private static String sourceKey(AccessoryType type) {
        return "relic:" + type.name();
    }

    private static String setBonusKey(String dungeonId) {
        return "relic-set:" + dungeonId;
    }

    public void applyFromSlot(Player player, AccessoryType type) {
        ItemStack stack = equippedItem(player, type);
        statusService.removeBuffsFromSource(player.getUniqueId(), sourceKey(type));
        identityService.read(stack).ifPresent(instance -> {
            List<RelicLine> lines = new ArrayList<>();
            lines.add(instance.mainStat());
            lines.addAll(instance.substats());
            for (RelicLine line : lines) {
                StatType statType = RelicStatResolver.resolveStatType(line);
                statusService.addBuff(player.getUniqueId(), sourceKey(type), statType, line.type().getModifierType(), line.value(), 0);
            }
        });
        recomputeSetBonus(player);
    }

    public void clear(Player player, AccessoryType type) {
        statusService.removeBuffsFromSource(player.getUniqueId(), sourceKey(type));
        recomputeSetBonus(player);
    }

    public void syncAll(Player player) {
        for (AccessoryType type : AccessoryType.values()) {
            applyFromSlot(player, type);
        }
    }

    private void recomputeSetBonus(Player player) {
        Map<String, Integer> countByDungeon = new HashMap<>();
        for (AccessoryType type : AccessoryType.values()) {
            ItemStack stack = equippedItem(player, type);
            identityService.read(stack).ifPresent(instance ->
                    countByDungeon.merge(instance.sourceDungeonId(), 1, Integer::sum));
        }
        for (String dungeonId : config.getDungeonIdsWithSetBonus()) {
            String key = setBonusKey(dungeonId);
            statusService.removeBuffsFromSource(player.getUniqueId(), key);
            if (countByDungeon.getOrDefault(dungeonId, 0) >= 2) {
                config.setBonusFor(dungeonId).ifPresent(bonus ->
                        statusService.addBuff(player.getUniqueId(), key, bonus.stat(), bonus.modifier(), bonus.value(), 0));
            }
        }
    }

    private ItemStack equippedItem(Player player, AccessoryType type) {
        return playerDataManager.get(player.getUniqueId())
                .flatMap(data -> data.component(PlayerAccessoryEquipmentComponent.class))
                .map(component -> component.getSlot(type))
                .orElse(null);
    }
}

package rpg.accessory.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import rpg.accessory.model.AccessoryData;
import rpg.accessory.model.AccessoryType;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the physical {@link ItemStack} for an {@link AccessoryData} template.
 */
public final class AccessoryFactory {

    private static final Map<AccessoryType, Material> BASE_MATERIAL = Map.of(
            AccessoryType.CHARM, Material.EMERALD,
            AccessoryType.RING, Material.GOLD_NUGGET,
            AccessoryType.NECKLACE, Material.AMETHYST_SHARD,
            AccessoryType.WING, Material.FEATHER);

    private final AccessoryKeys keys;

    public AccessoryFactory(AccessoryKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(AccessoryData data) {
        List<String> lore = new ArrayList<>();
        lore.add("&%7" + data.getType());
        lore.addAll(data.getDescription());
        data.getStatBonus().asMap().forEach((stat, value) -> {
            if (value != 0) {
                lore.add("&%a+" + value + " &%7" + stat);
            }
        });

        return new ItemBuilder(BASE_MATERIAL.getOrDefault(data.getType(), Material.EMERALD))
                .name("&%d" + data.getName())
                .lore(lore)
                .customModelData(data.getCustomModelData())
                .tag(keys.accessoryId(), PersistentDataType.STRING, data.getId())
                .build();
    }
}

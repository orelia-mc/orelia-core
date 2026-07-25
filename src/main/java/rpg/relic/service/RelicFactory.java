package rpg.relic.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import rpg.accessory.model.AccessoryType;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicLine;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds/refreshes the physical {@link ItemStack} for a {@link RelicInstance}. Unlike
 * {@code rpg.accessory.service.AccessoryFactory} (one static template per config id), every
 * relic is a unique roll, so the lore is generated from the instance's actual state each time
 * (see {@link #refreshLore}) rather than read from a config template.
 */
public final class RelicFactory {

    private static final Map<AccessoryType, Material> BASE_MATERIAL = Map.of(
            AccessoryType.CHARM, Material.EMERALD,
            AccessoryType.RING, Material.GOLD_NUGGET,
            AccessoryType.NECKLACE, Material.AMETHYST_SHARD,
            AccessoryType.WING, Material.FEATHER,
            AccessoryType.EARRING, Material.QUARTZ,
            AccessoryType.BELT, Material.LEATHER);

    private final RelicIdentityService identityService;

    public RelicFactory(RelicIdentityService identityService) {
        this.identityService = identityService;
    }

    public ItemStack build(RelicInstance instance) {
        ItemStack stack = new ItemBuilder(BASE_MATERIAL.getOrDefault(instance.part(), Material.EMERALD))
                .name("&%d[レリック] " + instance.part().getDisplayName())
                .lore(buildLore(instance))
                .build();
        identityService.write(stack, instance);
        return stack;
    }

    /** Re-renders {@code stack}'s lore from {@code instance} in place, without changing its identity/material. */
    public void refreshLore(ItemStack stack, RelicInstance instance) {
        ItemMeta meta = stack.getItemMeta();
        meta.lore(buildLore(instance).stream().map(ColorUtil::component).toList());
        stack.setItemMeta(meta);
    }

    private List<String> buildLore(RelicInstance instance) {
        List<String> lore = new ArrayList<>();
        lore.add("&%7Lv. " + instance.level() + "/15");
        lore.add("&%7産出: &%f" + instance.sourceDungeonId());
        lore.add("");
        lore.add("&%eメイン: &%f" + RelicStatResolver.describe(instance.mainStat()) + " +" + formatValue(instance.mainStat().value()));
        if (!instance.substats().isEmpty()) {
            lore.add("");
            for (RelicLine substat : instance.substats()) {
                lore.add("&%a" + RelicStatResolver.describe(substat) + " +" + formatValue(substat.value()));
            }
        }
        return lore;
    }

    private String formatValue(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}

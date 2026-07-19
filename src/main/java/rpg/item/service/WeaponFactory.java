package rpg.item.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import rpg.item.model.ElementType;
import rpg.item.model.WeaponData;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the physical {@link ItemStack} for a {@link WeaponData} template: vanilla base
 * material chosen from the weapon type, display name/lore rendered from the template,
 * and the weapon id stamped into the PersistentDataContainer so {@link WeaponIdentityService}
 * can resolve it back later.
 */
public final class WeaponFactory {

    private final WeaponKeys keys;

    public WeaponFactory(WeaponKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(WeaponData data) {
        Material baseMaterial = data.getWeaponType().materialForRarity(data.getRarity());
        ItemStack stack = new ItemBuilder(baseMaterial)
                .name(data.getRarity().getColor() + data.getName())
                .customModelData(data.getCustomModelData())
                .unbreakable(data.isUnbreakable())
                .tag(keys.weaponId(), PersistentDataType.STRING, data.getId())
                .build();
        applyLore(stack, data, data.getWeaponLevel(), 0, data.getAttackPower());
        return stack;
    }

    /**
     * Re-renders {@code stack}'s lore against its *actual current* weapon level, enhancement
     * level, and computed attack power - call this after
     * {@link WeaponIdentityService#levelUp}/{@link WeaponIdentityService#enhance} so the held
     * item reflects the change immediately instead of only in {@code /ol status}-style reads
     * of the underlying data (the item itself doesn't otherwise re-render).
     */
    public void refreshLore(ItemStack stack, WeaponData data, WeaponIdentityService identityService) {
        int weaponLevel = identityService.getWeaponLevel(stack, data);
        int enhancementLevel = identityService.getEnhancementLevel(stack);
        double attackPower = identityService.baseAttackPower(stack, data);
        applyLore(stack, data, weaponLevel, enhancementLevel, attackPower);
    }

    private void applyLore(ItemStack stack, WeaponData data, int weaponLevel, int enhancementLevel, double attackPower) {
        List<String> lore = new ArrayList<>();
        lore.add(data.getRarity().getColor() + data.getRarity().name());
        lore.add(enhancementLevel > 0
                ? "&7Lv. " + weaponLevel + " &7(強化+" + enhancementLevel + ")"
                : "&7Lv. " + weaponLevel);
        lore.addAll(data.getDescription());
        lore.add("&c攻撃力 &f" + attackPower);
        if (data.getElement() != ElementType.NONE) {
            lore.add("&b属性 &f" + data.getElement());
        }
        lore.add("&e会心率 &f" + data.getCritRate() + "%");
        lore.add("&e会心倍率 &f" + data.getCritMultiplier() + "x");
        if (data.getRequiredJob() != null) {
            lore.add("&7必要職業 &f" + data.getRequiredJob());
        }
        lore.add("&7必要レベル &f" + data.getRequiredLevel());

        ItemMeta meta = stack.getItemMeta();
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        stack.setItemMeta(meta);
    }
}

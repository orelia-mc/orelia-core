package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import rpg.accessory.model.AccessoryType;
import rpg.accessory.model.PlayerAccessoryEquipmentComponent;
import rpg.core.player.PlayerDataManager;
import rpg.economy.service.EconomyService;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.item.model.WeaponData;
import rpg.item.service.WeaponIdentityService;
import rpg.status.combat.DamageFormula;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Overview of the player's final stats, held-weapon attack power, current money, and equipped
 * accessories/relics (SOW section 17 "ステータス", extended to fold in the equipment summary
 * previously only visible in a separate screen so a player can see everything at a glance).
 * Stats can change while the screen is open (level-up, buff apply/expire) - since there's no
 * single event that covers every case, {@code GuiModule} periodically calls {@link #refresh}
 * for any player with this screen open (tag {@link #TAG}) instead.
 *
 * <p>Unlike the stat category icons, the 6 accessory/relic slots ({@link #ACCESSORY_SLOTS}) are
 * where equip/unequip actually happens, backed by {@link PlayerAccessoryEquipmentComponent} - a
 * virtual slot set persisted independently of the player's real inventory, so no real inventory
 * slot carries hidden "this one is the ring slot" meaning any more. Type enforcement, the swap
 * itself and the resulting stat recompute all live in
 * {@code rpg.gui.listener.StatusEquipmentSlotListener}, not this class. {@code refresh()}
 * deliberately never re-renders these 6 slots: that listener already repaints the one slot it
 * changed, and a periodic overwrite here would fight with an in-progress click.
 *
 * <p>Stats are split into 3 category icons (base / crit / elemental damage) rather than either
 * "one icon per stat" (too many icons once relics added 7 more {@link StatType} values) or
 * "everything in one lore" (too cramped to read) - see the relic system's Part O follow-up.
 */
public final class StatusGuiScreen {

    public static final String TAG = "status";

    private static final int HEAD_SLOT = 4;
    private static final int MONEY_SLOT = 8;
    private static final int BASE_SLOT = 12;
    private static final int CRIT_SLOT = 13;
    private static final int ELEMENTAL_SLOT = 14;
    // Row 2 of the 27-slot (3x9) screen is 18-26; 6 equip slots can't split that 9-wide row
    // evenly, so a 7th "what is this" info icon fills the dead-center slot (22) instead,
    // leaving 3 equip slots symmetrically on either side and one filler slot at each end (18, 26).
    private static final int INFO_SLOT = 22;
    private static final int[] ACCESSORY_SLOTS = {19, 20, 21, 23, 24, 25};

    private static final List<StatType> BASE_STATS = List.of(
            StatType.HP, StatType.SP, StatType.ATK, StatType.DEF, StatType.SPD, StatType.SP_RECOVERY);
    private static final List<StatType> CRIT_STATS = List.of(StatType.CRT, StatType.CRT_DMG);
    private static final List<StatType> ELEMENTAL_STATS = List.of(
            StatType.FIRE_DMG, StatType.WATER_DMG, StatType.EARTH_DMG,
            StatType.WIND_DMG, StatType.LIGHT_DMG, StatType.DARK_DMG);

    /** Japanese display label per stat - shown instead of the raw enum name (e.g. "攻撃力" not "ATK"). */
    private static final Map<StatType, String> LABELS = Map.ofEntries(
            Map.entry(StatType.HP, "HP"), Map.entry(StatType.SP, "SP"), Map.entry(StatType.ATK, "攻撃力"),
            Map.entry(StatType.DEF, "防御力"), Map.entry(StatType.SPD, "移動速度"),
            Map.entry(StatType.CRT, "会心率"), Map.entry(StatType.CRT_DMG, "会心ダメージ"),
            Map.entry(StatType.SP_RECOVERY, "SP回復効率"),
            Map.entry(StatType.FIRE_DMG, "火属性ダメージ増加"), Map.entry(StatType.WATER_DMG, "水属性ダメージ増加"),
            Map.entry(StatType.EARTH_DMG, "地属性ダメージ増加"), Map.entry(StatType.WIND_DMG, "風属性ダメージ増加"),
            Map.entry(StatType.LIGHT_DMG, "光属性ダメージ増加"), Map.entry(StatType.DARK_DMG, "闇属性ダメージ増加"));

    /** Stats whose final value is genuinely a percentage (crit chance/damage, relic-granted elemental/SP-recovery bonuses). */
    private static final Set<StatType> PERCENT_STATS = Set.of(
            StatType.CRT, StatType.CRT_DMG, StatType.SP_RECOVERY,
            StatType.FIRE_DMG, StatType.WATER_DMG, StatType.EARTH_DMG,
            StatType.WIND_DMG, StatType.LIGHT_DMG, StatType.DARK_DMG);

    private final StatusService statusService;
    private final GuiConfig guiConfig;
    private final EconomyService economyService;
    private final WeaponIdentityService weaponIdentityService;
    private final PlayerDataManager playerDataManager;

    public StatusGuiScreen(StatusService statusService, GuiConfig guiConfig, EconomyService economyService,
                            WeaponIdentityService weaponIdentityService, PlayerDataManager playerDataManager) {
        this.statusService = statusService;
        this.guiConfig = guiConfig;
        this.economyService = economyService;
        this.weaponIdentityService = weaponIdentityService;
        this.playerDataManager = playerDataManager;
    }

    /**
     * The accessory/relic part a GUI slot equips, if any - the single mapping
     * {@code StatusEquipmentSlotListener} shares with {@link #build}, so slot numbers stay
     * defined in exactly one place.
     */
    public static Optional<AccessoryType> typeAtEquipSlot(int slot) {
        for (int i = 0; i < ACCESSORY_SLOTS.length; i++) {
            if (ACCESSORY_SLOTS[i] == slot) {
                return Optional.of(AccessoryType.values()[i]);
            }
        }
        return Optional.empty();
    }

    /**
     * Icon for one equip slot: the equipped item itself, or a labelled placeholder naming the
     * part when empty. Static so {@code StatusEquipmentSlotListener} repaints a slot it just
     * changed exactly the way {@link #build} painted it.
     */
    public static ItemStack equipSlotIcon(AccessoryType type, ItemStack equipped) {
        // Red, not the same gray as the screen's plain filler panes, so an empty equip slot
        // reads as "click me" rather than blending into the decorative background around it.
        return equipped == null || equipped.getType().isAir()
                ? new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&%7" + type.getDisplayName()).build()
                : equipped.clone();
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("status", "&%8ステータス"), 27).tag(TAG);
        for (int slot = 0; slot < 27; slot++) {
            gui.set(slot, GuiButton.display(fillerIcon()));
        }
        gui.set(HEAD_SLOT, GuiButton.display(headIcon(player)));
        gui.set(MONEY_SLOT, GuiButton.display(moneyIcon(player)));
        gui.set(BASE_SLOT, GuiButton.display(baseStatsIcon(player)));
        gui.set(CRIT_SLOT, GuiButton.display(categoryIcon(player, Material.BLAZE_POWDER, "&%c会心", CRIT_STATS)));
        gui.set(ELEMENTAL_SLOT, GuiButton.display(categoryIcon(player, Material.FIRE_CHARGE, "&%6属性ダメージ増加", ELEMENTAL_STATS)));
        gui.set(INFO_SLOT, GuiButton.display(equipInfoIcon()));
        AccessoryType[] types = AccessoryType.values();
        for (int i = 0; i < types.length; i++) {
            gui.set(ACCESSORY_SLOTS[i], GuiButton.display(accessoryIcon(player, types[i])));
        }
        return gui;
    }

    /** Re-renders every icon into {@code inventory} without rebuilding the whole {@link Gui}. */
    public void refresh(Player player, org.bukkit.inventory.Inventory inventory) {
        inventory.setItem(HEAD_SLOT, headIcon(player));
        inventory.setItem(MONEY_SLOT, moneyIcon(player));
        inventory.setItem(BASE_SLOT, baseStatsIcon(player));
        inventory.setItem(CRIT_SLOT, categoryIcon(player, Material.BLAZE_POWDER, "&%c会心", CRIT_STATS));
        inventory.setItem(ELEMENTAL_SLOT, categoryIcon(player, Material.FIRE_CHARGE, "&%6属性ダメージ増加", ELEMENTAL_STATS));
    }

    private ItemStack fillerIcon() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    }

    private ItemStack moneyIcon(Player player) {
        double balance = economyService.getBalance(player.getUniqueId());
        return new ItemBuilder(Material.GOLD_INGOT)
                .name("&%6所持金")
                .lore(List.of("&%f" + MoneyFormat.format(balance) + "G"))
                .build();
    }

    /** Sits in {@link #INFO_SLOT}, the dead center of the equip-slot row, explaining what the 6 surrounding slots do. */
    private ItemStack equipInfoIcon() {
        return new ItemBuilder(Material.ITEM_FRAME)
                .name("&%eアクセサリー・レリック装備")
                .lore(List.of(
                        "&%7左右のスロットに対応する部位のアイテムを",
                        "&%7置くと装備されます。",
                        "&%7取り出すと装備解除されます。"))
                .build();
    }

    private ItemStack accessoryIcon(Player player, AccessoryType type) {
        ItemStack equipped = playerDataManager.get(player.getUniqueId())
                .flatMap(data -> data.component(PlayerAccessoryEquipmentComponent.class))
                .map(component -> component.getSlot(type))
                .orElse(null);
        return equipSlotIcon(type, equipped);
    }

    /** Adds a "現在攻撃力" line on top of the plain base-stat lines - the same number {@code CombatDamageListener} would actually deal. */
    private ItemStack baseStatsIcon(Player player) {
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(StatSheet.empty());
        List<String> lore = new ArrayList<>();
        for (StatType type : BASE_STATS) {
            lore.add("&%7" + LABELS.getOrDefault(type, type.name()) + ": &%f" + formatStat(stats.get(type), type));
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        WeaponData data = weaponIdentityService.dataOf(weapon).orElse(null);
        double currentAttackPower = data != null
                ? DamageFormula.applyAttackBonus(weaponIdentityService.baseAttackPower(weapon, data), stats.get(StatType.ATK))
                : stats.get(StatType.ATK);
        lore.add("&%c現在攻撃力 &%f" + String.format(Locale.ROOT, "%.1f", currentAttackPower));
        return new ItemBuilder(Material.IRON_INGOT).name("&%f基礎ステータス").lore(lore).build();
    }

    private ItemStack headIcon(Player player) {
        int level = statusService.component(player.getUniqueId()).map(PlayerStatusComponent::getLevel).orElse(1);
        // Built directly rather than through ItemBuilder - setOwningPlayer is SkullMeta-specific
        // and ItemBuilder's generic ItemMeta wrapping has no hook for it. Without this, a plain
        // PLAYER_HEAD ItemStack always renders the default Steve skin instead of the viewer's own.
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(ColorUtil.component("&%e" + player.getName()));
        meta.lore(List.of(ColorUtil.component("&%7Lv. " + level)));
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack categoryIcon(Player player, Material icon, String title, List<StatType> types) {
        StatSheet stats = statusService.getFinalStats(player.getUniqueId()).orElse(StatSheet.empty());
        List<String> lore = new ArrayList<>();
        for (StatType type : types) {
            lore.add("&%7" + LABELS.getOrDefault(type, type.name()) + ": &%f" + formatStat(stats.get(type), type));
        }
        return new ItemBuilder(icon).name(title).lore(lore).build();
    }

    /** Whole numbers show without a trailing ".0"; anything else keeps one decimal place. Percent-natured stats append "%". */
    private String formatStat(double value, StatType type) {
        String formatted = value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
        return PERCENT_STATS.contains(type) ? formatted + "%" : formatted;
    }
}

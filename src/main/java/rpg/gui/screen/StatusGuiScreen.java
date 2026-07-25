package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.status.service.StatusService;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of the player's final stats (SOW section 17 "ステータス"). Stats can change
 * while this screen is open (level-up, buff apply/expire, equipment swap elsewhere) - since
 * there's no single event that covers all of those, {@code GuiModule} periodically calls
 * {@link #refresh} for any player with this screen open (tag {@link #TAG}) instead.
 *
 * <p>Split into 3 category icons (base / crit / elemental damage) rather than either "one icon
 * per stat" (too many icons once relics added 7 more {@link StatType} values) or "everything in
 * one lore" (too cramped to read) - see the relic system's Part O follow-up.
 */
public final class StatusGuiScreen {

    public static final String TAG = "status";

    private static final int HEAD_SLOT = 4;
    private static final int BASE_SLOT = 12;
    private static final int CRIT_SLOT = 13;
    private static final int ELEMENTAL_SLOT = 14;

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

    public StatusGuiScreen(StatusService statusService, GuiConfig guiConfig) {
        this.statusService = statusService;
        this.guiConfig = guiConfig;
    }

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("status", "&%8ステータス"), 27);
        gui.set(HEAD_SLOT, GuiButton.display(headIcon(player)));
        gui.set(BASE_SLOT, GuiButton.display(categoryIcon(player, Material.IRON_INGOT, "&%f基礎ステータス", BASE_STATS)));
        gui.set(CRIT_SLOT, GuiButton.display(categoryIcon(player, Material.BLAZE_POWDER, "&%c会心", CRIT_STATS)));
        gui.set(ELEMENTAL_SLOT, GuiButton.display(categoryIcon(player, Material.FIRE_CHARGE, "&%6属性ダメージ増加", ELEMENTAL_STATS)));
        return gui;
    }

    /** Re-renders every icon into {@code inventory} without rebuilding the whole {@link Gui}. */
    public void refresh(Player player, org.bukkit.inventory.Inventory inventory) {
        inventory.setItem(HEAD_SLOT, headIcon(player));
        inventory.setItem(BASE_SLOT, categoryIcon(player, Material.IRON_INGOT, "&%f基礎ステータス", BASE_STATS));
        inventory.setItem(CRIT_SLOT, categoryIcon(player, Material.BLAZE_POWDER, "&%c会心", CRIT_STATS));
        inventory.setItem(ELEMENTAL_SLOT, categoryIcon(player, Material.FIRE_CHARGE, "&%6属性ダメージ増加", ELEMENTAL_STATS));
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

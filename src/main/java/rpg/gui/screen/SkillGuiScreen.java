package rpg.gui.screen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import rpg.core.message.MessageManager;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.item.model.WeaponData;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.listener.SkillActivationListener;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillProgressService;
import rpg.skill.service.SkillProgressService.UpgradeResult;
import rpg.skill.service.SkillSocketService;
import rpg.util.ColorUtil;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Weapon skill screen (SOW section 17 "武器スキル"): shows every skill for a weapon sitting in
 * one of the player's leftmost 3 hotbar slots ({@link #WEAPON_HOTBAR_SLOTS}), its learned
 * level, and lets the player spend a skill point (left click) or socket it into that weapon
 * (right click).
 *
 * <p>This screen is reachable from the "プレイヤー情報" Nether Star menu
 * (orelia-world's {@code PlayerInfoSkillGuiScreen}), which the player must be holding in
 * their main hand to right-click - so the weapon can't be identified by main hand at that
 * point, it's the Nether Star. The leftmost 3 hotbar slots are used instead as fixed,
 * always-inspectable "weapon slots": nothing pins a weapon there, but the player needs to keep
 * one there for this screen (and the socketing it does) to recognize it. Only one weapon's
 * skills are shown at a time - {@link #WEAPON_PREVIEW_SLOT}/{@link #PREV_WEAPON_SLOT}/
 * {@link #NEXT_WEAPON_SLOT} let the player flip between the 3 slots.
 */
public final class SkillGuiScreen {

    /** The 3 candidate "weapon slots" this screen can show skills for - see the class doc above. */
    private static final int[] WEAPON_HOTBAR_SLOTS = {0, 1, 2};

    private static final int WEAPON_PREVIEW_SLOT = 0;
    private static final int PREV_WEAPON_SLOT = 3;
    private static final int POINTS_HEADER_SLOT = 4;
    private static final int NEXT_WEAPON_SLOT = 5;

    private final SkillRepository skillRepository;
    private final SkillProgressService progressService;
    private final SkillSocketService socketService;
    private final WeaponIdentityService weaponIdentityService;
    private final GuiConfig guiConfig;
    private final MessageManager messages;
    private final GuiManager guiManager;

    public SkillGuiScreen(SkillRepository skillRepository, SkillProgressService progressService,
                           SkillSocketService socketService, WeaponIdentityService weaponIdentityService, GuiConfig guiConfig,
                           MessageManager messages, GuiManager guiManager) {
        this.skillRepository = skillRepository;
        this.progressService = progressService;
        this.socketService = socketService;
        this.weaponIdentityService = weaponIdentityService;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.guiManager = guiManager;
    }

    public Gui build(Player player) {
        return build(player, initialWeaponIndex(player));
    }

    /** The first hotbar slot (of the 3 candidates) that actually holds a recognized weapon, else slot 0 (empty). */
    private int initialWeaponIndex(Player player) {
        for (int i = 0; i < WEAPON_HOTBAR_SLOTS.length; i++) {
            if (weaponIdentityService.dataOf(weaponSlotItem(player, i)).isPresent()) {
                return i;
            }
        }
        return 0;
    }

    private Gui build(Player player, int weaponIndex) {
        Gui gui = new Gui(guiConfig.title("skill", "&%8武器スキル"), 27);
        ItemStack weaponItem = weaponSlotItem(player, weaponIndex);
        WeaponType weaponType = weaponIdentityService.dataOf(weaponItem)
                .map(w -> w.getWeaponType())
                .orElse(null);

        gui.set(WEAPON_PREVIEW_SLOT, GuiButton.display(weaponPreviewIcon(weaponItem, weaponIndex)));
        gui.set(POINTS_HEADER_SLOT, GuiButton.display(pointsHeaderIcon(player, weaponType)));
        placeWeaponNav(gui, player, weaponIndex);

        if (weaponType == null) {
            gui.set(13, GuiButton.display(new ItemBuilder(Material.BARRIER)
                    .name("&%c武器を持っていません")
                    .lore("&%7ホットバーの1〜3番目のいずれかに武器を入れてください")
                    .build()));
            return gui;
        }

        Map<String, SkillData> skills = skillRepository.getByWeaponType(weaponType);
        int slot = 10;
        for (SkillData skill : skills.values()) {
            if (slot >= 27) {
                break; // more skills configured for this weapon type than the screen has room for
            }
            int buttonSlot = slot++;
            gui.set(buttonSlot, new GuiButton(skillIcon(player, skill, weaponIndex), (clicker, clickType) -> {
                if (clickType.contains("RIGHT")) {
                    // Both socket actions need the weapon to still be there. A Gui leaves the
                    // player's own inventory freely usable (see GuiListener), so the weapon this
                    // screen was built around can be moved out of the slot while it's open -
                    // socketing into an empty slot would otherwise NPE in SkillSocketService.
                    ItemStack weapon = weaponSlotItem(clicker, weaponIndex);
                    Optional<WeaponData> weaponData = weaponIdentityService.dataOf(weapon);
                    if (weaponData.isEmpty()) {
                        messages.send(clicker, "skill.no-weapon");
                        return;
                    }
                    if (clickType.equals("SHIFT_RIGHT")) {
                        boolean unsocketed = socketService.unsocket(weapon, skill.getId());
                        messages.send(clicker, unsocketed ? "skill.unsocketed" : "skill.unsocket-failed");
                        if (unsocketed) {
                            // Removing a skill shifts every socketed skill after it down one slot
                            // index (List#remove closes the gap) - every button's "装着中: N番目"
                            // lore needs re-checking, not just the one that was just unsocketed.
                            refreshAllSkillIcons(clicker, skills, weaponIndex);
                        } else {
                            clicker.getOpenInventory().getTopInventory().setItem(buttonSlot, skillIcon(clicker, skill, weaponIndex));
                        }
                    } else {
                        boolean socketed = socketService.socket(weapon, skill.getId(), weaponData.get().getSkillSlotCount());
                        messages.send(clicker, socketed ? "skill.socketed" : "skill.socket-failed");
                        clicker.getOpenInventory().getTopInventory().setItem(buttonSlot, skillIcon(clicker, skill, weaponIndex));
                    }
                } else {
                    UpgradeResult result = progressService.upgradeSkill(clicker.getUniqueId(), skill.getId());
                    String key = switch (result) {
                        case OK -> "skill.upgraded";
                        case MAX_LEVEL -> "skill.upgrade-failed-max-level";
                        case INSUFFICIENT_POINTS -> "skill.upgrade-failed-points";
                        case UNKNOWN_SKILL -> "skill.unknown";
                    };
                    messages.send(clicker, key, "points", progressService.getSkillPoints(clicker.getUniqueId()));
                    if (result == UpgradeResult.OK) {
                        // Without this, the book's "Lv. x / max" lore and the remaining-points
                        // header only reflect the new state once the player closes and reopens
                        // the GUI - items built in this loop are otherwise never re-rendered
                        // after a click.
                        var topInventory = clicker.getOpenInventory().getTopInventory();
                        topInventory.setItem(buttonSlot, skillIcon(clicker, skill, weaponIndex));
                        topInventory.setItem(POINTS_HEADER_SLOT, pointsHeaderIcon(clicker, weaponType));
                    }
                }
            }));
        }
        return gui;
    }

    /** Prev/next-weapon nav around the fixed 3-slot set - not GuiPaginator (that's for paging a variable-length list, not flipping between 3 fixed hotbar slots). */
    private void placeWeaponNav(Gui gui, Player player, int weaponIndex) {
        if (weaponIndex > 0) {
            gui.set(PREV_WEAPON_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%7« 前の武器").build(),
                    (clicker, clickType) -> guiManager.open(clicker, build(clicker, weaponIndex - 1))));
        }
        if (weaponIndex < WEAPON_HOTBAR_SLOTS.length - 1) {
            gui.set(NEXT_WEAPON_SLOT, new GuiButton(new ItemBuilder(Material.ARROW).name("&%7次の武器 »").build(),
                    (clicker, clickType) -> guiManager.open(clicker, build(clicker, weaponIndex + 1))));
        }
    }

    /** The weapon in hotbar slot {@code WEAPON_HOTBAR_SLOTS[weaponIndex]} - see the class doc above. */
    private ItemStack weaponSlotItem(Player player, int weaponIndex) {
        return player.getInventory().getItem(WEAPON_HOTBAR_SLOTS[weaponIndex]);
    }

    /** Re-renders every skill button's icon in place - see the unsocket branch above for why. */
    private void refreshAllSkillIcons(Player player, Map<String, SkillData> skills, int weaponIndex) {
        var topInventory = player.getOpenInventory().getTopInventory();
        int slot = 10;
        for (SkillData skill : skills.values()) {
            if (slot >= 27) {
                break;
            }
            topInventory.setItem(slot++, skillIcon(player, skill, weaponIndex));
        }
    }

    /** A clone of the currently-inspected weapon (or a BARRIER placeholder if that hotbar slot is empty), with a lore note marking it as the active one. */
    private ItemStack weaponPreviewIcon(ItemStack weaponItem, int weaponIndex) {
        int hotbarSlotNumber = WEAPON_HOTBAR_SLOTS[weaponIndex] + 1;
        if (weaponItem == null || weaponItem.getType().isAir()) {
            return new ItemBuilder(Material.BARRIER)
                    .name("&%7ホットバー" + hotbarSlotNumber + "番目: 武器なし")
                    .lore("&%8« / » ボタンで他のホットバー枠を確認できます")
                    .build();
        }
        ItemStack preview = weaponItem.clone();
        preview.setAmount(1);
        ItemMeta meta = preview.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(ColorUtil.component("&%bこの武器のスキルを表示中(ホットバー" + hotbarSlotNumber + "番目)"));
        meta.lore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    private ItemStack pointsHeaderIcon(Player player, WeaponType weaponType) {
        int points = progressService.getSkillPoints(player.getUniqueId());
        // BOW/SPEAR/HOE keep their own vanilla right-click action (draw-and-shoot, throw,
        // till) - see SkillActivationListener - so both their sockets live on the swap-hands
        // key instead, freeing right-click entirely.
        List<String> castKeyLines = weaponType != null && SkillActivationListener.RIGHT_CLICK_RESERVED.contains(weaponType)
                ? List.of(
                        "&%7持ち替え(Fキー) &%f→ 1番目のスキル発動",
                        "&%7Shift+持ち替え(Fキー) &%f→ 2番目のスキル発動")
                : List.of(
                        "&%7右クリック &%f→ 1番目のスキル発動",
                        "&%7持ち替え(Fキー) &%f→ 2番目のスキル発動");
        List<String> lore = new ArrayList<>(List.of(
                "&%7スキルの習得・レベルアップに使います（消費: 1ポイント/回）。",
                "",
                "&%e装着したスキルの発動方法:"));
        lore.addAll(castKeyLines);
        return new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("&%e&lスキル習得ポイント&%7: &%f" + points)
                .lore(lore)
                .build();
    }

    private ItemStack skillIcon(Player player, SkillData skill, int weaponIndex) {
        int level = progressService.getSkillLevel(player.getUniqueId(), skill.getId());
        boolean learned = level > 0;
        List<String> socketed = socketService.getSocketedSkills(weaponSlotItem(player, weaponIndex));
        int socketIndex = socketed.indexOf(skill.getId());

        List<String> lore = new ArrayList<>(List.of(
                "&%7Lv. " + level + " / " + skill.getMaxLevel(),
                "&%7SP消費: " + skill.getSpCost(),
                "&%7クールタイム: " + skill.getCooldownSeconds() + "s",
                ""));
        if (socketIndex >= 0) {
            lore.add("&%b装着中: &%f" + (socketIndex + 1) + "番目のスロット");
        }
        lore.add("&%a左クリック &%7- 習得/レベルアップ");
        lore.add(socketIndex >= 0 ? "&%cShift+右クリック &%7- 装着を解除" : "&%b右クリック &%7- 武器に装着");

        return new ItemBuilder(learned ? Material.ENCHANTED_BOOK : Material.BOOK)
                .name((learned ? "&%e" : "&%7") + skill.getName())
                .lore(lore)
                .build();
    }
}

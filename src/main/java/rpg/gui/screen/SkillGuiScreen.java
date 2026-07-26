package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.listener.SkillActivationListener;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillProgressService;
import rpg.skill.service.SkillProgressService.UpgradeResult;
import rpg.skill.service.SkillSocketService;
import rpg.util.ItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weapon skill screen (SOW section 17 "武器スキル"): shows every skill for the currently
 * held weapon's type, its learned level, and lets the player spend a skill point
 * (left click) or socket it into the held weapon (right click).
 */
public final class SkillGuiScreen {

    private final SkillRepository skillRepository;
    private final SkillProgressService progressService;
    private final SkillSocketService socketService;
    private final WeaponIdentityService weaponIdentityService;
    private final GuiConfig guiConfig;
    private final MessageManager messages;

    public SkillGuiScreen(SkillRepository skillRepository, SkillProgressService progressService,
                           SkillSocketService socketService, WeaponIdentityService weaponIdentityService, GuiConfig guiConfig,
                           MessageManager messages) {
        this.skillRepository = skillRepository;
        this.progressService = progressService;
        this.socketService = socketService;
        this.weaponIdentityService = weaponIdentityService;
        this.guiConfig = guiConfig;
        this.messages = messages;
    }

    private static final int POINTS_HEADER_SLOT = 4;

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("skill", "&%8武器スキル"), 27);

        WeaponType weaponType = weaponIdentityService.dataOf(player.getInventory().getItemInMainHand())
                .map(w -> w.getWeaponType())
                .orElse(null);
        gui.set(POINTS_HEADER_SLOT, GuiButton.display(pointsHeaderIcon(player, weaponType)));
        if (weaponType == null) {
            gui.set(13, GuiButton.display(new ItemBuilder(Material.BARRIER).name("&%c武器を持っていません").build()));
            return gui;
        }

        Map<String, SkillData> skills = skillRepository.getByWeaponType(weaponType);
        int slot = 10;
        for (SkillData skill : skills.values()) {
            if (slot >= 27) {
                break; // more skills configured for this weapon type than the screen has room for
            }
            int buttonSlot = slot++;
            gui.set(buttonSlot, new GuiButton(skillIcon(player, skill), (clicker, clickType) -> {
                if (clickType.equals("SHIFT_RIGHT")) {
                    boolean unsocketed = socketService.unsocket(clicker.getInventory().getItemInMainHand(), skill.getId());
                    messages.send(clicker, unsocketed ? "skill.unsocketed" : "skill.unsocket-failed");
                    clicker.getOpenInventory().getTopInventory().setItem(buttonSlot, skillIcon(clicker, skill));
                } else if (clickType.contains("RIGHT")) {
                    boolean socketed = socketService.socket(clicker.getInventory().getItemInMainHand(), skill.getId(),
                            weaponIdentityService.dataOf(clicker.getInventory().getItemInMainHand())
                                    .map(w -> w.getSkillSlotCount()).orElse(1));
                    messages.send(clicker, socketed ? "skill.socketed" : "skill.socket-failed");
                    clicker.getOpenInventory().getTopInventory().setItem(buttonSlot, skillIcon(clicker, skill));
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
                        topInventory.setItem(buttonSlot, skillIcon(clicker, skill));
                        topInventory.setItem(POINTS_HEADER_SLOT, pointsHeaderIcon(clicker, weaponType));
                    }
                }
            }));
        }
        return gui;
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

    private ItemStack skillIcon(Player player, SkillData skill) {
        int level = progressService.getSkillLevel(player.getUniqueId(), skill.getId());
        boolean learned = level > 0;
        List<String> socketed = socketService.getSocketedSkills(player.getInventory().getItemInMainHand());
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

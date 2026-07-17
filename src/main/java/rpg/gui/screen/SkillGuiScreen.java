package rpg.gui.screen;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.item.model.WeaponType;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillProgressService;
import rpg.skill.service.SkillSocketService;
import rpg.util.ItemBuilder;

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

    public Gui build(Player player) {
        Gui gui = new Gui(guiConfig.title("skill", "&8武器スキル"), 27);

        WeaponType weaponType = weaponIdentityService.dataOf(player.getInventory().getItemInMainHand())
                .map(w -> w.getWeaponType())
                .orElse(null);
        if (weaponType == null) {
            gui.set(13, GuiButton.display(new ItemBuilder(Material.BARRIER).name("&c武器を持っていません").build()));
            return gui;
        }

        Map<String, SkillData> skills = skillRepository.getByWeaponType(weaponType);
        int slot = 10;
        for (SkillData skill : skills.values()) {
            int level = progressService.getSkillLevel(player.getUniqueId(), skill.getId());
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name("&e" + skill.getName())
                    .lore(List.of(
                            "&7Lv. " + level + " / " + skill.getMaxLevel(),
                            "&7SP消費: " + skill.getSpCost(),
                            "&7クールタイム: " + skill.getCooldownSeconds() + "s",
                            "",
                            "&a左クリック &7- 習得/レベルアップ",
                            "&b右クリック &7- 武器に装着"))
                    .build(), (clicker, clickType) -> {
                if (clickType.contains("RIGHT")) {
                    boolean socketed = socketService.socket(clicker.getInventory().getItemInMainHand(), skill.getId(),
                            weaponIdentityService.dataOf(clicker.getInventory().getItemInMainHand())
                                    .map(w -> w.getSkillSlotCount()).orElse(1));
                    messages.send(clicker, socketed ? "skill.socketed" : "skill.socket-failed");
                } else {
                    boolean upgraded = progressService.upgradeSkill(clicker.getUniqueId(), skill.getId());
                    messages.send(clicker, upgraded ? "skill.upgraded" : "skill.upgrade-failed");
                }
            }));
        }
        return gui;
    }
}

package rpg.extra.mount.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.mount.manager.MountManager;
import rpg.extra.mount.model.MountDefinition;
import rpg.extra.mount.service.MountService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists every mount from {@code mounts.yml}; clicking an unowned one buys it, clicking an
 * owned one toggles summon/dismiss. Same shape as {@code rpg.extra.pet.gui.PetGuiScreen} -
 * mounts and pets are structurally identical ("buy/summon/dismiss a companion entity"), so this
 * mirrors that class rather than sharing code across the two independent modules.
 */
public final class MountGuiScreen {

    private final MountService mountService;
    private final MountManager mountManager;
    private final MessageManager messages;

    public MountGuiScreen(MountService mountService, MountManager mountManager, MessageManager messages) {
        this.mountService = mountService;
        this.mountManager = mountManager;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8乗り物", 27);
        Map<String, MountDefinition> all = mountService.getAllMounts();
        Set<String> unlocked = mountService.getUnlockedMounts(player.getUniqueId());

        int slot = 0;
        for (MountDefinition mount : all.values()) {
            if (slot >= 27) {
                break;
            }
            boolean owned = unlocked.contains(mount.getId());
            boolean active = owned && mount.getId().equals(mountService.getSelectedMountId(player.getUniqueId()))
                    && mountManager.hasActiveMount(player.getUniqueId());
            List<String> lore = owned
                    ? (active ? List.of("&%a召喚中", "&%7クリックして送り返す") : List.of("&%a所持済み", "&%7クリックして召喚"))
                    : List.of("&%7価格: &%f" + MoneyFormat.format(mount.getPrice()), "&%7クリックして購入");
            gui.set(slot++, new GuiButton(new ItemBuilder(spawnEggFor(mount))
                    .name("&%e" + mount.getName())
                    .lore(lore)
                    .build(), (clicker, clickType) -> handleClick(clicker, mount.getId(), owned)));
        }
        return gui;
    }

    private void handleClick(Player player, String mountId, boolean owned) {
        if (owned) {
            player.closeInventory();
            boolean active = mountId.equals(mountService.getSelectedMountId(player.getUniqueId()))
                    && mountManager.hasActiveMount(player.getUniqueId());
            if (active) {
                report(player, mountService.dismiss(player), "mount.dismissed");
            } else {
                report(player, mountService.summon(player, mountId), "mount.summoned");
            }
        } else {
            report(player, mountService.unlock(player, mountId), "mount.unlocked");
        }
    }

    private void report(Player player, MountService.ActionResult result, String successKey) {
        if (result == MountService.ActionResult.OK) {
            messages.send(player, successKey);
            return;
        }
        String key = switch (result) {
            case OK -> "command.ok";
            case MOUNT_NOT_FOUND -> "mount.not-found";
            case ALREADY_UNLOCKED -> "mount.already-unlocked";
            case NOT_UNLOCKED -> "mount.not-unlocked";
            case INSUFFICIENT_FUNDS -> "mount.insufficient-funds";
            case NO_ACTIVE_MOUNT -> "mount.no-active-mount";
        };
        messages.send(player, key);
    }

    private Material spawnEggFor(MountDefinition mount) {
        try {
            return Material.valueOf(mount.getEntityType().name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.SADDLE;
        }
    }
}

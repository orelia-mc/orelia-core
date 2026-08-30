package rpg.extra.mount.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.mount.gui.MountGuiScreen;
import rpg.extra.mount.model.MountDefinition;
import rpg.extra.mount.service.MountService;
import rpg.gui.framework.GuiManager;
import rpg.util.MoneyFormat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /ol mount [gui|list|buy <id>|summon [id]|dismiss]} (SOW MountModule). Bare
 * {@code /ol mount} opens the GUI (same as explicit {@code gui}) rather than the plain-text
 * {@code list} - same reasoning {@code rpg.extra.pet.command.PetCommand} gives for its own
 * identical default, which this mirrors ({@code PetGuiScreen} was this screen's template too).
 */
public final class MountCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("gui", "list", "buy", "summon", "dismiss");
    private static final List<String> MOUNT_ID_ACTIONS = List.of("buy", "summon");

    private final MountService mountService;
    private final MountGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public MountCommand(MountService mountService, MountGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.mountService = mountService;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            listMounts(sender, player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "buy" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.mount-buy");
                    return true;
                }
                report(sender, mountService.unlock(player, args[1]), "mount.unlocked");
            }
            case "summon" -> {
                String mountId = args.length >= 2 ? args[1] : mountService.getSelectedMountId(player.getUniqueId());
                if (mountId == null) {
                    messages.send(sender, "usage.mount-summon");
                    return true;
                }
                report(sender, mountService.summon(player, mountId), "mount.summoned");
            }
            case "dismiss" -> report(sender, mountService.dismiss(player), "mount.dismissed");
            default -> messages.send(sender, "usage.mount");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length != 2 || !MOUNT_ID_ACTIONS.contains(args[0].toLowerCase())) {
            return List.of();
        }
        return TabCompletions.matching(mountService.getAllMounts().keySet(), args[1]);
    }

    private void listMounts(CommandSender sender, Player player) {
        Map<String, MountDefinition> all = mountService.getAllMounts();
        Set<String> unlocked = mountService.getUnlockedMounts(player.getUniqueId());
        if (all.isEmpty()) {
            messages.send(sender, "mount.no-mounts-available");
            return;
        }
        messages.send(sender, "mount.list-header");
        all.values().forEach(mount -> {
            boolean owned = unlocked.contains(mount.getId());
            String status = owned ? messages.format("mount.owned-tag") : messages.format("mount.price-tag", "price", MoneyFormat.format(mount.getPrice()));
            messages.sendRaw(sender, "mount.list-entry", "id", mount.getId(), "name", mount.getName(), "status", status);
        });
    }

    private void report(CommandSender sender, MountService.ActionResult result, String successKey) {
        if (result == MountService.ActionResult.OK) {
            messages.send(sender, successKey);
            return;
        }
        String key = switch (result) {
            case MOUNT_NOT_FOUND -> "mount.not-found";
            case ALREADY_UNLOCKED -> "mount.already-unlocked";
            case NOT_UNLOCKED -> "mount.not-unlocked";
            case INSUFFICIENT_FUNDS -> "mount.insufficient-funds";
            case NO_ACTIVE_MOUNT -> "mount.no-active-mount";
            case OK -> "command.ok";
        };
        messages.send(sender, key);
    }
}

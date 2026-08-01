package rpg.dungeon.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.dungeon.gui.DungeonGuiScreen;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.service.DungeonEncounterService;
import rpg.gui.framework.GuiManager;

import java.util.List;

/**
 * {@code /ol dungeon [list]|start <id>|retire} - {@code list} (or no argument) opens the
 * dungeon-select GUI, {@code start} attempts a run directly without opening it.
 */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private final DungeonEncounterService encounterService;
    private final DungeonGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final PlayerDataManager playerDataManager;
    private final MessageManager messages;

    public DungeonCommand(DungeonEncounterService encounterService, DungeonGuiScreen guiScreen, GuiManager guiManager,
                           PlayerDataManager playerDataManager, MessageManager messages) {
        this.encounterService = encounterService;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.playerDataManager = playerDataManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (args.length < 2) {
                    messages.send(player, "dungeon.usage");
                    return true;
                }
                Integer difficulty = null;
                if (args.length >= 3) {
                    try {
                        difficulty = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        messages.send(player, "dungeon.invalid-difficulty");
                        return true;
                    }
                }
                encounterService.challenge(player, args[1], difficulty)
                        .ifPresent(failure -> messages.send(player, "dungeon.challenge-failed." + failure.name().toLowerCase()));
            }
            case "retire" -> {
                boolean retired = encounterService.retire(player);
                messages.send(player, retired ? "dungeon.retired" : "dungeon.not-in-dungeon");
            }
            default -> messages.send(player, "dungeon.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(List.of("list", "start", "retire"), args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender instanceof Player player) {
            PlayerDungeonComponent component = playerDataManager.get(player.getUniqueId())
                    .flatMap(d -> d.component(PlayerDungeonComponent.class))
                    .orElse(null);
            if (component != null) {
                return TabCompletions.matching(component.getUnlockedDungeonIds(), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return TabCompletions.matching(
                    DungeonEncounterService.DIFFICULTY_TIERS.stream().map(String::valueOf).toList(), args[2]);
        }
        return List.of();
    }
}

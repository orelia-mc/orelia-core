package rpg.extra.pet.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.pet.gui.PetGuiScreen;
import rpg.extra.pet.model.PetDefinition;
import rpg.extra.pet.service.PetService;
import rpg.gui.framework.GuiManager;
import rpg.util.MoneyFormat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /ol pet [gui|list|buy <id>|summon [id]|dismiss]} (SOW PetModule). Bare {@code /ol pet}
 * opens the GUI (same as explicit {@code gui}) rather than the plain-text {@code list} - general
 * players reach for the icon-and-click screen far more often than the chat listing, which stays
 * available for anyone who wants it without opening an inventory.
 */
public final class PetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("list", "gui", "buy", "summon", "dismiss");
    private static final List<String> PET_ID_ACTIONS = List.of("buy", "summon");

    private final PetService petService;
    private final PetGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public PetCommand(PetService petService, PetGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.petService = petService;
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
            listPets(sender, player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "buy" -> {
                if (args.length < 2) {
                    messages.send(sender, "usage.pet-buy");
                    return true;
                }
                report(sender, petService.unlock(player, args[1]), "pet.unlocked");
            }
            case "summon" -> {
                String petId = args.length >= 2 ? args[1] : petService.getSelectedPetId(player.getUniqueId());
                if (petId == null) {
                    messages.send(sender, "usage.pet-summon");
                    return true;
                }
                report(sender, petService.summon(player, petId), "pet.summoned");
            }
            case "dismiss" -> report(sender, petService.dismiss(player), "pet.dismissed");
            default -> messages.send(sender, "usage.pet");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length != 2 || !PET_ID_ACTIONS.contains(args[0].toLowerCase())) {
            return List.of();
        }
        return TabCompletions.matching(petService.getAllPets().keySet(), args[1]);
    }

    private void listPets(CommandSender sender, Player player) {
        Map<String, PetDefinition> all = petService.getAllPets();
        Set<String> unlocked = petService.getUnlockedPets(player.getUniqueId());
        if (all.isEmpty()) {
            messages.send(sender, "pet.no-pets-available");
            return;
        }
        messages.send(sender, "pet.list-header");
        all.values().forEach(pet -> {
            boolean owned = unlocked.contains(pet.getId());
            String status = owned ? messages.format("pet.owned-tag") : messages.format("pet.price-tag", "price", MoneyFormat.format(pet.getPrice()));
            messages.sendRaw(sender, "pet.list-entry", "id", pet.getId(), "name", pet.getName(), "status", status);
        });
    }

    private void report(CommandSender sender, PetService.ActionResult result, String successKey) {
        if (result == PetService.ActionResult.OK) {
            messages.send(sender, successKey);
            return;
        }
        String key = switch (result) {
            case PET_NOT_FOUND -> "pet.not-found";
            case ALREADY_UNLOCKED -> "pet.already-unlocked";
            case NOT_UNLOCKED -> "pet.not-unlocked";
            case INSUFFICIENT_FUNDS -> "pet.insufficient-funds";
            case NO_ACTIVE_PET -> "pet.no-active-pet";
            case OK -> "command.ok";
        };
        messages.send(sender, key);
    }
}

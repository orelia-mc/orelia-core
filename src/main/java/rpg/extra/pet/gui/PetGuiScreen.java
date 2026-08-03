package rpg.extra.pet.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.pet.manager.PetManager;
import rpg.extra.pet.model.PetDefinition;
import rpg.extra.pet.service.PetService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists every pet from {@code pets.yml}; clicking an unowned one buys it, clicking an
 * owned one toggles summon/dismiss ("休憩"). Same shape as {@code RankingGuiScreen} - a plain
 * {@code build(Player) -> Gui} method reusing orelia-core's generic Gui framework.
 */
public final class PetGuiScreen {

    private final PetService petService;
    private final PetManager petManager;
    private final MessageManager messages;

    public PetGuiScreen(PetService petService, PetManager petManager, MessageManager messages) {
        this.petService = petService;
        this.petManager = petManager;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8ペット", 27);
        Map<String, PetDefinition> all = petService.getAllPets();
        Set<String> unlocked = petService.getUnlockedPets(player.getUniqueId());

        int slot = 0;
        for (PetDefinition pet : all.values()) {
            if (slot >= 27) {
                break;
            }
            boolean owned = unlocked.contains(pet.getId());
            boolean active = owned && pet.getId().equals(petService.getSelectedPetId(player.getUniqueId()))
                    && petManager.hasActivePet(player.getUniqueId());
            List<String> lore = owned
                    ? (active ? List.of("&%a召喚中", "&%7クリックして休憩させる") : List.of("&%a所持済み", "&%7クリックして召喚"))
                    : List.of("&%7価格: &%f" + MoneyFormat.format(pet.getPrice()), "&%7クリックして購入");
            gui.set(slot++, new GuiButton(new ItemBuilder(spawnEggFor(pet))
                    .name("&%e" + pet.getName())
                    .lore(lore)
                    .build(), (clicker, clickType) -> handleClick(clicker, pet.getId(), owned)));
        }
        return gui;
    }

    private void handleClick(Player player, String petId, boolean owned) {
        if (owned) {
            player.closeInventory();
            boolean active = petId.equals(petService.getSelectedPetId(player.getUniqueId()))
                    && petManager.hasActivePet(player.getUniqueId());
            report(player, active ? petService.dismiss(player) : petService.summon(player, petId));
        } else {
            report(player, petService.unlock(player, petId));
        }
    }

    private void report(Player player, PetService.ActionResult result) {
        String key = switch (result) {
            case OK -> "command.ok";
            case PET_NOT_FOUND -> "pet.not-found";
            case ALREADY_UNLOCKED -> "pet.already-unlocked";
            case NOT_UNLOCKED -> "pet.not-unlocked";
            case INSUFFICIENT_FUNDS -> "pet.insufficient-funds";
            case NO_ACTIVE_PET -> "pet.no-active-pet";
        };
        messages.send(player, key);
    }

    private Material spawnEggFor(PetDefinition pet) {
        try {
            return Material.valueOf(pet.getEntityType().name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.PLAYER_HEAD;
        }
    }
}

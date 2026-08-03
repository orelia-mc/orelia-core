package rpg.extra.pet.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import rpg.extra.pet.manager.PetManager;
import rpg.extra.pet.model.PetDefinition;
import rpg.extra.pet.repository.PetConfigRepository;
import rpg.extra.pet.repository.PetOwnershipRepository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unlock/summon/dismiss flow for pets (SOW PetModule). Money moves through Vault's
 * {@link Economy}.
 */
public final class PetService {

    public enum ActionResult {
        OK, PET_NOT_FOUND, ALREADY_UNLOCKED, NOT_UNLOCKED, INSUFFICIENT_FUNDS, NO_ACTIVE_PET
    }

    private final PetConfigRepository configRepository;
    private final PetOwnershipRepository ownershipRepository;
    private final PetManager petManager;
    private final Economy economy;
    private final Map<UUID, Set<String>> unlockedByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedByOwner = new ConcurrentHashMap<>();

    public PetService(PetConfigRepository configRepository, PetOwnershipRepository ownershipRepository,
                       PetManager petManager, Economy economy) {
        this.configRepository = configRepository;
        this.ownershipRepository = ownershipRepository;
        this.petManager = petManager;
        this.economy = economy;
    }

    public void loadAll() {
        unlockedByOwner.clear();
        unlockedByOwner.putAll(ownershipRepository.loadUnlocks());
        selectedByOwner.clear();
        selectedByOwner.putAll(ownershipRepository.loadSelections());
    }

    public Map<String, PetDefinition> getAllPets() {
        return configRepository.getAll();
    }

    public Set<String> getUnlockedPets(UUID ownerId) {
        return Set.copyOf(unlockedByOwner.getOrDefault(ownerId, Set.of()));
    }

    public ActionResult unlock(Player player, String petId) {
        PetDefinition definition = configRepository.findById(petId).orElse(null);
        if (definition == null) {
            return ActionResult.PET_NOT_FOUND;
        }
        Set<String> owned = unlockedByOwner.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (owned.contains(petId)) {
            return ActionResult.ALREADY_UNLOCKED;
        }
        if (!economy.has(player, definition.getPrice())) {
            return ActionResult.INSUFFICIENT_FUNDS;
        }
        economy.withdrawPlayer(player, definition.getPrice());
        owned.add(petId);
        ownershipRepository.saveUnlock(player.getUniqueId(), petId);
        return ActionResult.OK;
    }

    /** Debug helper: unlocks a pet for {@code ownerId} without an economy check. */
    public ActionResult forceUnlock(UUID ownerId, String petId) {
        PetDefinition definition = configRepository.findById(petId).orElse(null);
        if (definition == null) {
            return ActionResult.PET_NOT_FOUND;
        }
        Set<String> owned = unlockedByOwner.computeIfAbsent(ownerId, k -> new HashSet<>());
        if (owned.contains(petId)) {
            return ActionResult.ALREADY_UNLOCKED;
        }
        owned.add(petId);
        ownershipRepository.saveUnlock(ownerId, petId);
        return ActionResult.OK;
    }

    public ActionResult summon(Player player, String petId) {
        PetDefinition definition = configRepository.findById(petId).orElse(null);
        if (definition == null) {
            return ActionResult.PET_NOT_FOUND;
        }
        if (!unlockedByOwner.getOrDefault(player.getUniqueId(), Set.of()).contains(petId)) {
            return ActionResult.NOT_UNLOCKED;
        }
        LivingEntity entity = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), definition.getEntityType());
        entity.setCustomName(definition.getName());
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
        }
        // Tameable species (wolves, cats, ...) get vanilla's own follow-owner AI on top of
        // PetManager#tickFollow's teleport-back safety net, so they track the owner smoothly
        // instead of only snapping back once they drift past the follow-distance threshold.
        if (entity instanceof Tameable tameable) {
            tameable.setOwner(player);
            tameable.setTamed(true);
        }
        petManager.register(player.getUniqueId(), entity);
        selectedByOwner.put(player.getUniqueId(), petId);
        ownershipRepository.saveSelection(player.getUniqueId(), petId);
        return ActionResult.OK;
    }

    public ActionResult dismiss(Player player) {
        if (!petManager.hasActivePet(player.getUniqueId())) {
            return ActionResult.NO_ACTIVE_PET;
        }
        petManager.despawn(player.getUniqueId());
        return ActionResult.OK;
    }

    public String getSelectedPetId(UUID ownerId) {
        return selectedByOwner.get(ownerId);
    }
}

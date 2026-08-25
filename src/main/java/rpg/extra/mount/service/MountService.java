package rpg.extra.mount.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.inventory.ItemStack;
import rpg.extra.mount.manager.MountManager;
import rpg.extra.mount.model.MountDefinition;
import rpg.extra.mount.repository.MountConfigRepository;
import rpg.extra.mount.repository.MountOwnershipRepository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unlock/summon/dismiss flow for rideable mounts (SOW MountModule). Money moves through
 * Vault's {@link Economy}.
 */
public final class MountService {

    public enum ActionResult {
        OK, MOUNT_NOT_FOUND, ALREADY_UNLOCKED, NOT_UNLOCKED, INSUFFICIENT_FUNDS, NO_ACTIVE_MOUNT
    }

    private final MountConfigRepository configRepository;
    private final MountOwnershipRepository ownershipRepository;
    private final MountManager mountManager;
    private final Economy economy;
    private final MountGrowthService growthService;
    private final Map<UUID, Set<String>> unlockedByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedByOwner = new ConcurrentHashMap<>();

    public MountService(MountConfigRepository configRepository, MountOwnershipRepository ownershipRepository,
                         MountManager mountManager, Economy economy, MountGrowthService growthService) {
        this.configRepository = configRepository;
        this.ownershipRepository = ownershipRepository;
        this.mountManager = mountManager;
        this.economy = economy;
        this.growthService = growthService;
    }

    public void loadAll() {
        unlockedByOwner.clear();
        unlockedByOwner.putAll(ownershipRepository.loadUnlocks());
        selectedByOwner.clear();
        selectedByOwner.putAll(ownershipRepository.loadSelections());
    }

    public Map<String, MountDefinition> getAllMounts() {
        return configRepository.getAll();
    }

    public Set<String> getUnlockedMounts(UUID ownerId) {
        return Set.copyOf(unlockedByOwner.getOrDefault(ownerId, Set.of()));
    }

    public ActionResult unlock(Player player, String mountId) {
        MountDefinition definition = configRepository.findById(mountId).orElse(null);
        if (definition == null) {
            return ActionResult.MOUNT_NOT_FOUND;
        }
        Set<String> owned = unlockedByOwner.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (owned.contains(mountId)) {
            return ActionResult.ALREADY_UNLOCKED;
        }
        if (!economy.has(player, definition.getPrice())) {
            return ActionResult.INSUFFICIENT_FUNDS;
        }
        economy.withdrawPlayer(player, definition.getPrice());
        owned.add(mountId);
        ownershipRepository.saveUnlock(player.getUniqueId(), mountId);
        return ActionResult.OK;
    }

    /** Debug helper: unlocks a mount for {@code ownerId} without an economy check. */
    public ActionResult forceUnlock(UUID ownerId, String mountId) {
        MountDefinition definition = configRepository.findById(mountId).orElse(null);
        if (definition == null) {
            return ActionResult.MOUNT_NOT_FOUND;
        }
        Set<String> owned = unlockedByOwner.computeIfAbsent(ownerId, k -> new HashSet<>());
        if (owned.contains(mountId)) {
            return ActionResult.ALREADY_UNLOCKED;
        }
        owned.add(mountId);
        ownershipRepository.saveUnlock(ownerId, mountId);
        return ActionResult.OK;
    }

    public ActionResult summon(Player player, String mountId) {
        MountDefinition definition = configRepository.findById(mountId).orElse(null);
        if (definition == null) {
            return ActionResult.MOUNT_NOT_FOUND;
        }
        if (!unlockedByOwner.getOrDefault(player.getUniqueId(), Set.of()).contains(mountId)) {
            return ActionResult.NOT_UNLOCKED;
        }
        Entity entity = player.getWorld().spawnEntity(player.getLocation(), definition.getEntityType());
        entity.setCustomName(definition.getName());
        entity.setCustomNameVisible(true);
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setRemoveWhenFarAway(false);
            livingEntity.setInvulnerable(true);
            var speedAttribute = livingEntity.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.setBaseValue(definition.getSpeed());
            }
        }
        // Without a saddle, a mount ignores the player's steering input entirely. Steerable
        // species (Pig/Strider) track it as a boolean flag; AbstractHorse species (Horse/
        // Donkey/Mule/Camel/...) instead store it as an ItemStack in their own inventory.
        if (entity instanceof Steerable steerable) {
            steerable.setSaddle(true);
        } else if (entity instanceof AbstractHorse horse) {
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
        mountManager.register(player.getUniqueId(), entity);
        entity.addPassenger(player);
        selectedByOwner.put(player.getUniqueId(), mountId);
        ownershipRepository.saveSelection(player.getUniqueId(), mountId);
        growthService.applyGrowthBonus(player.getUniqueId(), mountId);
        return ActionResult.OK;
    }

    public ActionResult dismiss(Player player) {
        if (!mountManager.hasActiveMount(player.getUniqueId())) {
            return ActionResult.NO_ACTIVE_MOUNT;
        }
        mountManager.despawn(player.getUniqueId());
        return ActionResult.OK;
    }

    public String getSelectedMountId(UUID ownerId) {
        return selectedByOwner.get(ownerId);
    }
}

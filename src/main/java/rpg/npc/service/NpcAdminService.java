package rpg.npc.service;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.npc.model.NpcData;
import rpg.npc.model.NpcType;
import rpg.npc.repository.NpcRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Backs {@code /oladmin npc create|move|remove|list}: mutates the in-memory
 * {@link NpcRepository}, spawns/despawns the backing entity via {@link NpcSpawnService}, and
 * writes the basic placement fields (name/type/entity-type/world/x/y/z/yaw) back to
 * {@code npc.yml} so the change survives a restart. Deliberately never touches
 * {@code shop-stock}/{@code dialogue}/{@code quest-ids} or any other nested section - those
 * stay hand-authored content, this service only owns "where is this NPC and what kind is it".
 */
public final class NpcAdminService {

    private static final String NPC_YML = "npc.yml";

    private final NpcRepository repository;
    private final NpcSpawnService spawnService;
    private final ConfigManager configManager;

    public NpcAdminService(NpcRepository repository, NpcSpawnService spawnService, ConfigManager configManager) {
        this.repository = repository;
        this.spawnService = spawnService;
        this.configManager = configManager;
    }

    /** Creates a brand-new NPC at {@code location}. Empty if {@code id} is already taken. */
    public Optional<NpcData> create(String id, NpcType type, EntityType entityType, Location location, String name) {
        if (repository.findById(id).isPresent()) {
            return Optional.empty();
        }
        NpcData data = new NpcData(id, name, type, entityType, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(),
                List.of(), null, List.of(), List.of(), List.of(), 100, 50, "EMERALD", 5, 100, 50);
        repository.add(id, data);
        writePlacement(id, data);
        spawnService.spawn(id, location);
        return Optional.of(data);
    }

    /** Moves an existing NPC (definition + live entity) to {@code newLocation}. */
    public boolean move(String id, Location newLocation) {
        NpcData existing = repository.findById(id).orElse(null);
        if (existing == null) {
            return false;
        }
        NpcData moved = new NpcData(id, existing.getName(), existing.getType(), existing.getEntityType(),
                newLocation.getWorld().getName(), newLocation.getX(), newLocation.getY(), newLocation.getZ(),
                newLocation.getYaw(), existing.getDialogueLines(), existing.getConditionalItemId(),
                existing.getConditionalDialogueLines(), existing.getShopStock(), existing.getQuestIds(),
                existing.getEnhancementCostBase(), existing.getEnhancementCostPerLevel(),
                existing.getWeaponLevelupItemMaterial(), existing.getWeaponLevelupItemAmount(),
                existing.getWeaponLevelupCostBase(), existing.getWeaponLevelupCostPerLevel());
        repository.replace(id, moved);
        writePlacement(id, moved);
        spawnService.despawn(id);
        spawnService.spawn(id, newLocation);
        return true;
    }

    /** Deletes an NPC definition, its live entity, and its {@code npc.yml} section entirely. */
    public boolean remove(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        repository.remove(id);
        spawnService.despawn(id);
        ConfigFile file = configManager.get(NPC_YML);
        file.get().set("npcs." + id, null);
        file.save();
        return true;
    }

    public Collection<NpcData> list() {
        return repository.getAll().values();
    }

    private void writePlacement(String id, NpcData data) {
        ConfigFile file = configManager.get(NPC_YML);
        YamlConfiguration config = file.get();
        String base = "npcs." + id + ".";
        config.set(base + "name", data.getName());
        config.set(base + "type", data.getType().name());
        config.set(base + "entity-type", data.getEntityType().name());
        config.set(base + "world", data.getWorld());
        config.set(base + "x", data.getX());
        config.set(base + "y", data.getY());
        config.set(base + "z", data.getZ());
        config.set(base + "yaw", (double) data.getYaw());
        file.save();
    }
}

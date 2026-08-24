package rpg.npc.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import rpg.npc.model.NpcData;
import rpg.npc.model.NpcType;
import rpg.npc.repository.NpcRepository;

/**
 * Ensures every configured NPC has exactly one entity alive in the world: checks whether an
 * entity tagged with that *specific* NPC's id already exists near its configured location (it
 * would, after a normal server restart, since Bukkit persists entities in chunk data) and only
 * spawns a fresh one if it is missing. Admin-triggered only (see {@code NpcAdminCommand}'s
 * {@code spawnall} subcommand) - nothing calls this automatically on startup, so re-running it
 * is always safe to do on demand (e.g. after adding new entries to {@code npc.yml}).
 *
 * <p>The target chunk is force-loaded ({@link World#getChunkAt(Location)}) before searching for
 * a nearby entity - {@link World#getNearbyEntities} only sees already-loaded chunks, and an
 * npc.yml coordinate isn't guaranteed to be near spawn or any online player, so skipping this
 * would make the dedup check silently fail (and spawn a duplicate on top of the persisted one)
 * whenever that chunk happened not to be loaded yet.
 *
 * <p>{@link NpcType#JOB_CHANGE} is excluded from this sync - that NPC is only ever placed via
 * {@code /oladmin spawnnpc <npc-id>} (see {@code NpcSpawnCommand}).
 */
public final class NpcSpawnSyncService {

    private static final double MATCH_RADIUS = 3.0;

    private final NpcKeys keys;
    private final NpcRepository repository;
    private final NpcSpawnService spawnService;

    public NpcSpawnSyncService(NpcKeys keys, NpcRepository repository, NpcSpawnService spawnService) {
        this.keys = keys;
        this.repository = repository;
        this.spawnService = spawnService;
    }

    public void syncAll() {
        for (NpcData data : repository.getAll().values()) {
            if (data.getType() == NpcType.JOB_CHANGE) {
                continue;
            }
            World world = Bukkit.getWorld(data.getWorld());
            if (world == null) {
                continue;
            }
            Location location = new Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), 0);
            world.getChunkAt(location); // force-load so a persisted entity here becomes visible to getNearbyEntities
            boolean alreadyPresent = world.getNearbyEntities(location, MATCH_RADIUS, MATCH_RADIUS, MATCH_RADIUS).stream()
                    .anyMatch(entity -> isTaggedAs(entity, data.getId()));
            if (!alreadyPresent) {
                spawnService.spawn(data.getId(), location);
            }
        }
    }

    private boolean isTaggedAs(Entity entity, String npcId) {
        return npcId.equals(entity.getPersistentDataContainer().get(keys.npcId(), PersistentDataType.STRING));
    }
}

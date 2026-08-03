package rpg.npc.service;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class NpcKeys {

    /**
     * The namespace this key carried before the orelia-core/orelia-world/orelia-extra merge,
     * when NPCs were spawned by the separate OreliaWorld plugin.
     *
     * <p>A {@link NamespacedKey}'s namespace comes from the owning plugin's name, so the merge
     * moved this key from {@code oreliaworld:npc_id} to {@code oreliacore:npc_id} - every NPC
     * entity already standing in an existing world still carries the old one. Without reading
     * it, those NPCs stop being recognized entirely: they no longer respond to interaction, and
     * {@code /oladmin npc spawnall} can't see them, so it spawns a duplicate on top of each.
     * {@link NpcSpawnService#idOf} reads this as a fallback and re-stamps the entity under the
     * current namespace, so each NPC is healed once, on sight.
     *
     * <p>Removable once no pre-merge world is in use.
     */
    private static final NamespacedKey LEGACY_NPC_ID =
            Objects.requireNonNull(NamespacedKey.fromString("oreliaworld:npc_id"));

    private final NamespacedKey npcId;

    public NpcKeys(Plugin plugin) {
        this.npcId = new NamespacedKey(plugin, "npc_id");
    }

    public NamespacedKey npcId() {
        return npcId;
    }

    /** @see #LEGACY_NPC_ID */
    public NamespacedKey legacyNpcId() {
        return LEGACY_NPC_ID;
    }
}

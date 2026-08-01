package rpg.npc.service;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class NpcKeys {

    private final NamespacedKey npcId;

    public NpcKeys(Plugin plugin) {
        this.npcId = new NamespacedKey(plugin, "npc_id");
    }

    public NamespacedKey npcId() {
        return npcId;
    }
}

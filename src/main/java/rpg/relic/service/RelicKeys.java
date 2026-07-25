package rpg.relic.service;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys used to stamp a rolled relic's state onto its ItemStack. */
public final class RelicKeys {

    private final NamespacedKey instanceId;
    private final NamespacedKey part;
    private final NamespacedKey mainStatType;
    private final NamespacedKey mainStatElement;
    private final NamespacedKey mainStatValue;
    private final NamespacedKey level;
    private final NamespacedKey sourceDungeonId;
    private final NamespacedKey substats;

    public RelicKeys(Plugin plugin) {
        this.instanceId = new NamespacedKey(plugin, "relic_instance_id");
        this.part = new NamespacedKey(plugin, "relic_part");
        this.mainStatType = new NamespacedKey(plugin, "relic_main_stat_type");
        this.mainStatElement = new NamespacedKey(plugin, "relic_main_stat_element");
        this.mainStatValue = new NamespacedKey(plugin, "relic_main_stat_value");
        this.level = new NamespacedKey(plugin, "relic_level");
        this.sourceDungeonId = new NamespacedKey(plugin, "relic_source_dungeon_id");
        this.substats = new NamespacedKey(plugin, "relic_substats");
    }

    public NamespacedKey instanceId() {
        return instanceId;
    }

    public NamespacedKey part() {
        return part;
    }

    public NamespacedKey mainStatType() {
        return mainStatType;
    }

    public NamespacedKey mainStatElement() {
        return mainStatElement;
    }

    public NamespacedKey mainStatValue() {
        return mainStatValue;
    }

    public NamespacedKey level() {
        return level;
    }

    public NamespacedKey sourceDungeonId() {
        return sourceDungeonId;
    }

    /** Serialized substat lines - see {@code RelicIdentityService} for the encoding. */
    public NamespacedKey substats() {
        return substats;
    }
}

package rpg.relic.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import rpg.accessory.model.AccessoryType;
import rpg.item.model.ElementType;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicLine;
import rpg.relic.model.RelicStatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads/writes a {@link RelicInstance}'s full state to an {@link ItemStack}'s
 * PersistentDataContainer. Unlike weapons (identity looked up from a config-driven
 * repository by a stamped id), a relic's roll is per-instance and unique - the ItemStack's
 * PDC *is* the source of truth, so this class is read/write both ways.
 */
public final class RelicIdentityService {

    private static final String LINE_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ",";

    private final RelicKeys keys;

    public RelicIdentityService(RelicKeys keys) {
        this.keys = keys;
    }

    public boolean isRelic(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(keys.instanceId(), PersistentDataType.STRING);
    }

    public Optional<RelicInstance> read(ItemStack stack) {
        if (!isRelic(stack)) {
            return Optional.empty();
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        UUID instanceId = UUID.fromString(pdc.get(keys.instanceId(), PersistentDataType.STRING));
        AccessoryType part = AccessoryType.valueOf(pdc.get(keys.part(), PersistentDataType.STRING));
        RelicLine mainStat = new RelicLine(
                RelicStatType.valueOf(pdc.get(keys.mainStatType(), PersistentDataType.STRING)),
                ElementType.valueOf(pdc.get(keys.mainStatElement(), PersistentDataType.STRING)),
                pdc.getOrDefault(keys.mainStatValue(), PersistentDataType.DOUBLE, 0.0));
        int level = pdc.getOrDefault(keys.level(), PersistentDataType.INTEGER, 0);
        String sourceDungeonId = pdc.get(keys.sourceDungeonId(), PersistentDataType.STRING);
        List<RelicLine> substats = deserializeSubstats(pdc.getOrDefault(keys.substats(), PersistentDataType.STRING, ""));
        return Optional.of(new RelicInstance(instanceId, part, mainStat, substats, level, sourceDungeonId));
    }

    public void write(ItemStack stack, RelicInstance instance) {
        ItemMeta meta = stack.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(keys.instanceId(), PersistentDataType.STRING, instance.instanceId().toString());
        pdc.set(keys.part(), PersistentDataType.STRING, instance.part().name());
        pdc.set(keys.mainStatType(), PersistentDataType.STRING, instance.mainStat().type().name());
        pdc.set(keys.mainStatElement(), PersistentDataType.STRING, instance.mainStat().element().name());
        pdc.set(keys.mainStatValue(), PersistentDataType.DOUBLE, instance.mainStat().value());
        pdc.set(keys.level(), PersistentDataType.INTEGER, instance.level());
        pdc.set(keys.sourceDungeonId(), PersistentDataType.STRING, instance.sourceDungeonId());
        pdc.set(keys.substats(), PersistentDataType.STRING, serializeSubstats(instance.substats()));
        stack.setItemMeta(meta);
    }

    private String serializeSubstats(List<RelicLine> substats) {
        return substats.stream()
                .map(line -> line.type().name() + FIELD_SEPARATOR + line.element().name() + FIELD_SEPARATOR + line.value())
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private List<RelicLine> deserializeSubstats(String raw) {
        List<RelicLine> lines = new ArrayList<>();
        if (raw.isBlank()) {
            return lines;
        }
        for (String encoded : raw.split(LINE_SEPARATOR)) {
            String[] fields = encoded.split(FIELD_SEPARATOR);
            lines.add(new RelicLine(RelicStatType.valueOf(fields[0]), ElementType.valueOf(fields[1]), Double.parseDouble(fields[2])));
        }
        return lines;
    }
}

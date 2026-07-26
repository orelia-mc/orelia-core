package rpg.town.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads {@code config.yml: town-detection.*} - whether town detection is enabled, and which
 * WorldGuard region IDs count as "inside a town" (see
 * {@link rpg.town.service.TownDetectionService}). Multiple region IDs can be listed to
 * represent one logical town spread across disjoint areas - a WorldGuard region ID is unique
 * per world, so a town made of separate physical areas needs one region per area, each listed
 * here. This is a flat allow-list; there is no separate "town name" beyond the region ID
 * itself.
 */
public final class TownConfig {

    private boolean enabled;
    private Set<String> townRegions = Set.of();

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("town-detection.enabled", true);
        List<String> raw = config.getStringList("town-detection.town-regions");
        Set<String> normalized = new LinkedHashSet<>();
        for (String regionId : raw) {
            normalized.add(regionId.toLowerCase(Locale.ROOT));
        }
        townRegions = Set.copyOf(normalized);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getTownRegions() {
        return townRegions;
    }
}

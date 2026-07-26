package rpg.gathering.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads {@code gathering.yml: regen-exclusion.*} - which WorldGuard region IDs are exempt from
 * the gathering regen system (see {@link rpg.gathering.service.RegenExclusionService}). Same
 * flat, case-insensitive allow-list shape as {@code config.yml: town-detection.town-regions}:
 * a build area spread across disjoint shapes needs one WorldGuard region per shape, with every
 * ID listed here, since region IDs are unique per world.
 *
 * <p>Deliberately separate from town detection rather than reusing its list - a decorative
 * forest that should not regenerate is not necessarily a town, and a town is not necessarily
 * somewhere gathering should be disabled.
 */
public final class RegenExclusionConfig {

    private boolean enabled;
    private Set<String> excludedRegions = Set.of();

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("regen-exclusion.enabled", true);
        List<String> raw = config.getStringList("regen-exclusion.regions");
        Set<String> normalized = new LinkedHashSet<>();
        for (String regionId : raw) {
            normalized.add(regionId.toLowerCase(Locale.ROOT));
        }
        excludedRegions = Set.copyOf(normalized);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getExcludedRegions() {
        return excludedRegions;
    }
}

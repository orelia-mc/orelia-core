package rpg.gathering.config;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.gathering.model.LevelRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Level-band to bulk-action radius mapping ({@code gathering.yml: level-ranges}, SOW 3.3).
 * A level above every configured band keeps the highest band's radius, so raising the
 * level cap later doesn't also require editing this list at the same time.
 */
public final class LevelRadiusConfig {

    private List<LevelRange> ranges = new ArrayList<>();

    public LevelRadiusConfig() {
    }

    public LevelRadiusConfig(List<LevelRange> ranges) {
        this.ranges = ranges;
    }

    public void load(YamlConfiguration config) {
        List<LevelRange> loaded = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("level-ranges")) {
            int min = toInt(entry.get("min-level"), 1);
            int max = toInt(entry.get("max-level"), min);
            int radius = toInt(entry.get("radius"), 0);
            loaded.add(new LevelRange(min, max, radius));
        }
        loaded.sort((a, b) -> Integer.compare(a.minLevel(), b.minLevel()));
        this.ranges = loaded;
    }

    private int toInt(Object raw, int fallback) {
        return raw instanceof Number number ? number.intValue() : fallback;
    }

    /** Bulk-action radius for {@code level}; 0 means single-block only (vanilla). */
    public int radiusForLevel(int level) {
        if (ranges.isEmpty()) {
            return 0;
        }
        LevelRange highest = ranges.get(0);
        for (LevelRange range : ranges) {
            if (level >= range.minLevel() && level <= range.maxLevel()) {
                return range.radius();
            }
            if (range.maxLevel() > highest.maxLevel()) {
                highest = range;
            }
        }
        return level > highest.maxLevel() ? highest.radius() : 0;
    }
}

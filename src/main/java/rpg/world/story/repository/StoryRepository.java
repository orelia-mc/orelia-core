package rpg.world.story.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.world.story.model.StoryChapter;
import rpg.world.story.model.StoryEnding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry of every {@link StoryChapter}/{@link StoryEnding}, rebuilt from
 * {@code story.yml}.
 */
public final class StoryRepository {

    private Map<String, StoryChapter> chapters = new LinkedHashMap<>();
    private Map<String, StoryEnding> endings = new LinkedHashMap<>();

    public void load(YamlConfiguration config) {
        Map<String, StoryChapter> loadedChapters = new LinkedHashMap<>();
        ConfigurationSection chaptersSection = config.getConfigurationSection("chapters");
        if (chaptersSection != null) {
            for (String id : chaptersSection.getKeys(false)) {
                ConfigurationSection section = chaptersSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                loadedChapters.put(id, new StoryChapter(
                        id,
                        section.getInt("order", 0),
                        section.getString("title", id),
                        section.getStringList("description"),
                        section.getStringList("required-flags"),
                        section.getString("unlock-message")));
            }
        }
        this.chapters = loadedChapters;

        Map<String, StoryEnding> loadedEndings = new LinkedHashMap<>();
        ConfigurationSection endingsSection = config.getConfigurationSection("endings");
        if (endingsSection != null) {
            for (String id : endingsSection.getKeys(false)) {
                ConfigurationSection section = endingsSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                loadedEndings.put(id, new StoryEnding(
                        id,
                        section.getString("title", id),
                        section.getStringList("message"),
                        section.getStringList("required-flags")));
            }
        }
        this.endings = loadedEndings;
    }

    public Optional<StoryChapter> findChapterById(String id) {
        return Optional.ofNullable(chapters.get(id));
    }

    public List<StoryChapter> getChaptersInOrder() {
        List<StoryChapter> ordered = new ArrayList<>(chapters.values());
        ordered.sort(Comparator.comparingInt(StoryChapter::getOrder));
        return ordered;
    }

    public Map<String, StoryEnding> getEndings() {
        return Map.copyOf(endings);
    }
}

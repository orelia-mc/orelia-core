package rpg.quest.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Toggle for the title/action-bar feedback {@link rpg.quest.service.QuestObjectiveFeedbackService}
 * shows when a quest's objectives are all met - lets an operator disable it (e.g. it doesn't
 * suit their server's pacing) without touching the quest state machine itself.
 */
public final class QuestFeedbackConfig {

    private boolean enabled = true;

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("quest.completion-feedback.enabled", true);
    }

    public boolean isEnabled() {
        return enabled;
    }
}

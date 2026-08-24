package rpg.quest.service;

import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.quest.config.QuestFeedbackConfig;
import rpg.quest.model.QuestData;
import rpg.util.ColorUtil;

import java.time.Duration;

/**
 * Title/action-bar feedback for the moment a quest's objectives all become met (state
 * transitions to {@code AWAITING_REPORT}) - orelia-world can't reuse orelia-core's internal
 * {@code LevelUpFeedbackService} directly (cross-plugin internals are off-limits, see this
 * repo's CLAUDE.md), so it plays the same "don't bury it in chat" idea with plain Bukkit/
 * Adventure API calls instead.
 */
public final class QuestObjectiveFeedbackService {

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500));

    private final MessageManager messages;
    private final QuestFeedbackConfig config;

    public QuestObjectiveFeedbackService(MessageManager messages, QuestFeedbackConfig config) {
        this.messages = messages;
        this.config = config;
    }

    public void notifyObjectivesComplete(Player player, QuestData quest) {
        if (!config.isEnabled()) {
            return;
        }
        player.showTitle(Title.title(
                ColorUtil.component(messages.raw("quest.objective-complete-title")),
                ColorUtil.component(messages.format("quest.objective-complete-subtitle", "quest", quest.getName())),
                TITLE_TIMES));
        player.sendActionBar(ColorUtil.component(
                messages.format("quest.objective-complete-actionbar", "quest", quest.getName())));
    }
}

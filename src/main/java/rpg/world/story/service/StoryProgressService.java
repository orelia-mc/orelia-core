package rpg.world.story.service;

import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.util.ColorUtil;
import rpg.world.story.model.PlayerStoryComponent;
import rpg.world.story.model.StoryChapter;
import rpg.world.story.model.StoryEnding;
import rpg.world.story.repository.StoryRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Chapter/flag/ending progression (SOW StoryModule).
 */
public final class StoryProgressService {

    private final StoryRepository repository;
    private final PlayerDataManager playerDataManager;

    public StoryProgressService(StoryRepository repository, PlayerDataManager playerDataManager) {
        this.repository = repository;
        this.playerDataManager = playerDataManager;
    }

    public void setFlag(UUID playerId, String flag) {
        component(playerId).ifPresent(c -> c.setFlag(flag));
    }

    public boolean hasFlag(UUID playerId, String flag) {
        return component(playerId).map(c -> c.hasFlag(flag)).orElse(false);
    }

    /**
     * Marks the given chapter completed and advances to the next unlockable chapter
     * (the earliest by {@link StoryChapter#getOrder()} whose required flags are all held
     * and which isn't already completed). Then checks for newly-unlocked endings.
     */
    public void advanceChapter(Player player, String completedChapterId) {
        PlayerStoryComponent component = component(player.getUniqueId()).orElse(null);
        if (component == null) {
            return;
        }
        component.completeChapter(completedChapterId);

        for (StoryChapter chapter : repository.getChaptersInOrder()) {
            if (component.hasCompletedChapter(chapter.getId())) {
                continue;
            }
            boolean unlocked = chapter.getRequiredFlags().stream().allMatch(component::hasFlag);
            if (unlocked) {
                component.setCurrentChapterId(chapter.getId());
                if (chapter.getUnlockMessage() != null && !chapter.getUnlockMessage().isBlank()) {
                    player.sendMessage(ColorUtil.colorize(chapter.getUnlockMessage()));
                }
                break;
            }
        }

        checkEndings(player, component);
    }

    private void checkEndings(Player player, PlayerStoryComponent component) {
        for (StoryEnding ending : repository.getEndings().values()) {
            if (component.getUnlockedEndingIds().contains(ending.getId())) {
                continue;
            }
            boolean unlocked = ending.getRequiredFlags().stream().allMatch(component::hasFlag);
            if (unlocked && component.unlockEnding(ending.getId())) {
                for (String line : ending.getMessage()) {
                    player.sendMessage(ColorUtil.colorize(line));
                }
            }
        }
    }

    public Optional<StoryChapter> getCurrentChapter(UUID playerId) {
        return component(playerId)
                .map(PlayerStoryComponent::getCurrentChapterId)
                .flatMap(repository::findChapterById);
    }

    private Optional<PlayerStoryComponent> component(UUID playerId) {
        return playerDataManager.get(playerId).flatMap(d -> d.component(PlayerStoryComponent.class));
    }
}

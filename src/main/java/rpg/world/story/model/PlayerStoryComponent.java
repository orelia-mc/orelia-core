package rpg.world.story.model;

import rpg.core.player.PlayerDataComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlayerStoryComponent implements PlayerDataComponent {

    private final UUID owner;
    private String currentChapterId;
    private final Set<String> completedChapterIds;
    private final Set<String> flags;
    private final Set<String> unlockedEndingIds;

    public PlayerStoryComponent(UUID owner, String currentChapterId, Set<String> completedChapterIds,
                                 Set<String> flags, Set<String> unlockedEndingIds) {
        this.owner = owner;
        this.currentChapterId = currentChapterId;
        this.completedChapterIds = new HashSet<>(completedChapterIds);
        this.flags = new HashSet<>(flags);
        this.unlockedEndingIds = new HashSet<>(unlockedEndingIds);
    }

    @Override
    public UUID getOwner() {
        return owner;
    }

    public String getCurrentChapterId() {
        return currentChapterId;
    }

    public void setCurrentChapterId(String currentChapterId) {
        this.currentChapterId = currentChapterId;
    }

    public void completeChapter(String chapterId) {
        completedChapterIds.add(chapterId);
    }

    public boolean hasCompletedChapter(String chapterId) {
        return completedChapterIds.contains(chapterId);
    }

    public Set<String> getCompletedChapterIds() {
        return Set.copyOf(completedChapterIds);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void setFlag(String flag) {
        flags.add(flag);
    }

    public Set<String> getFlags() {
        return Set.copyOf(flags);
    }

    public boolean unlockEnding(String endingId) {
        return unlockedEndingIds.add(endingId);
    }

    public Set<String> getUnlockedEndingIds() {
        return Set.copyOf(unlockedEndingIds);
    }
}

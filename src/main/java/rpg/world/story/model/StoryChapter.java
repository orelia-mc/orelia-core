package rpg.world.story.model;

import java.util.List;

/**
 * One chapter in the main scenario (SOW StoryModule "Chapter管理"/"シナリオ管理").
 */
public final class StoryChapter {

    private final String id;
    private final int order;
    private final String title;
    private final List<String> description;
    private final List<String> requiredFlags;
    private final String unlockMessage;

    public StoryChapter(String id, int order, String title, List<String> description, List<String> requiredFlags, String unlockMessage) {
        this.id = id;
        this.order = order;
        this.title = title;
        this.description = description;
        this.requiredFlags = requiredFlags;
        this.unlockMessage = unlockMessage;
    }

    public String getId() {
        return id;
    }

    public int getOrder() {
        return order;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getDescription() {
        return description;
    }

    public List<String> getRequiredFlags() {
        return requiredFlags;
    }

    public String getUnlockMessage() {
        return unlockMessage;
    }
}

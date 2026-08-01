package rpg.world.story.model;

import java.util.List;

/**
 * One possible ending, unlocked when a player holds every flag in {@link #getRequiredFlags()}
 * (SOW StoryModule "エンディング管理").
 */
public final class StoryEnding {

    private final String id;
    private final String title;
    private final List<String> message;
    private final List<String> requiredFlags;

    public StoryEnding(String id, String title, List<String> message, List<String> requiredFlags) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.requiredFlags = requiredFlags;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getMessage() {
        return message;
    }

    public List<String> getRequiredFlags() {
        return requiredFlags;
    }
}

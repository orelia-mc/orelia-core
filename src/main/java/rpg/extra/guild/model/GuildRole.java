package rpg.extra.guild.model;

public enum GuildRole {
    LEADER("リーダー"),
    OFFICER("幹部"),
    MEMBER("メンバー");

    private final String displayName;

    GuildRole(String displayName) {
        this.displayName = displayName;
    }

    /** Japanese label for UI display - not used for config/DB identity. */
    public String getDisplayName() {
        return displayName;
    }
}

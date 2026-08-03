package rpg.extra.chat.model;

/** The chat channels a player can select via {@code /ol chat <channel>}. */
public enum ChatChannel {

    PUBLIC("パブリック"),
    PARTY("パーティー"),
    GUILD("ギルド"),
    ADMIN("アドミン");

    private final String displayName;

    ChatChannel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

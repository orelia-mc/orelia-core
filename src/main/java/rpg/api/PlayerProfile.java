package rpg.api;

/**
 * A snapshot of cross-plugin display info for one player, returned by
 * {@link PlayerProfileApi#getProfile}. Any field the player doesn't currently have (unemployed,
 * no guild, no party, no equipped title) is an empty string / {@code false}, never {@code null}.
 */
public record PlayerProfile(int level, String job, String guildName, String guildTag, boolean inParty, String title) {
}

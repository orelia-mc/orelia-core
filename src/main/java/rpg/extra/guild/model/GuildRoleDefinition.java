package rpg.extra.guild.model;

/**
 * A guild-defined, freely named role a non-leader member can be assigned (SOW GuildModule role
 * redesign - replaces the previous fixed OFFICER/MEMBER two-tier ladder). Purely a display
 * label: it carries no permissions of its own. Only the guild leader (tracked separately via
 * {@link Guild#getLeaderId()}, never a row here) can manage members, roles, or guild settings -
 * see {@code GuildService}. {@code id} is a short, internally-generated, guild-scoped identifier
 * (never shown to players); {@code sortOrder} is the display/assignment order, lowest first.
 */
public record GuildRoleDefinition(String id, String name, int sortOrder) {
}

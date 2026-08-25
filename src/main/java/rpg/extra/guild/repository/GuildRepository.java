package rpg.extra.guild.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRoleDefinition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists guilds, their member/role assignments, and each guild's own custom role definitions
 * via orelia-core's shared {@link DatabaseManager}. Development-phase DB reset is acceptable per
 * project instruction, so this only ever creates fresh tables - no {@code ALTER TABLE} migration
 * path from the previous fixed-enum {@code guild_member.role} values.
 */
public final class GuildRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public GuildRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(64) NOT NULL,
                        tag VARCHAR(16) NOT NULL,
                        leader_id VARCHAR(36) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_member (
                        guild_id VARCHAR(36) NOT NULL,
                        uuid VARCHAR(36) NOT NULL,
                        role VARCHAR(32) NOT NULL,
                        PRIMARY KEY (guild_id, uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS guild_role (
                        guild_id VARCHAR(36) NOT NULL,
                        role_id VARCHAR(32) NOT NULL,
                        name VARCHAR(32) NOT NULL,
                        sort_order INT NOT NULL,
                        PRIMARY KEY (guild_id, role_id)
                    )
                    """);
        }
    }

    public List<Guild> loadAll() {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, String> tags = new LinkedHashMap<>();
        Map<UUID, UUID> leaders = new LinkedHashMap<>();
        Map<UUID, Map<UUID, String>> membersByGuild = new LinkedHashMap<>();
        Map<UUID, List<GuildRoleDefinition>> rolesByGuild = new LinkedHashMap<>();

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, name, tag, leader_id FROM guild")) {
            while (resultSet.next()) {
                UUID id = UUID.fromString(resultSet.getString("id"));
                names.put(id, resultSet.getString("name"));
                tags.put(id, resultSet.getString("tag"));
                leaders.put(id, UUID.fromString(resultSet.getString("leader_id")));
                membersByGuild.put(id, new LinkedHashMap<>());
                rolesByGuild.put(id, new ArrayList<>());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load guilds", e);
        }

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT guild_id, uuid, role FROM guild_member")) {
            while (resultSet.next()) {
                Map<UUID, String> members = membersByGuild.get(UUID.fromString(resultSet.getString("guild_id")));
                if (members != null) {
                    members.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getString("role"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load guild members", e);
        }

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT guild_id, role_id, name, sort_order FROM guild_role")) {
            while (resultSet.next()) {
                List<GuildRoleDefinition> roles = rolesByGuild.get(UUID.fromString(resultSet.getString("guild_id")));
                if (roles != null) {
                    roles.add(new GuildRoleDefinition(resultSet.getString("role_id"), resultSet.getString("name"), resultSet.getInt("sort_order")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load guild roles", e);
        }

        List<Guild> guilds = new ArrayList<>();
        for (UUID id : names.keySet()) {
            guilds.add(new Guild(id, names.get(id), tags.get(id), leaders.get(id), membersByGuild.get(id), rolesByGuild.get(id)));
        }
        return guilds;
    }

    public void save(Guild guild) {
        String guildSql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO guild (id, name, tag, leader_id) VALUES (?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET name = excluded.name, tag = excluded.tag, leader_id = excluded.leader_id
                    """;
            case MYSQL -> """
                    INSERT INTO guild (id, name, tag, leader_id) VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE name = VALUES(name), tag = VALUES(tag), leader_id = VALUES(leader_id)
                    """;
        };
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(guildSql)) {
                statement.setString(1, guild.getId().toString());
                statement.setString(2, guild.getName());
                statement.setString(3, guild.getTag());
                statement.setString(4, guild.getLeaderId().toString());
                statement.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM guild_member WHERE guild_id = ?")) {
                delete.setString(1, guild.getId().toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insertMember = connection.prepareStatement(
                    "INSERT INTO guild_member (guild_id, uuid, role) VALUES (?, ?, ?)")) {
                for (var entry : guild.getMembers().entrySet()) {
                    insertMember.setString(1, guild.getId().toString());
                    insertMember.setString(2, entry.getKey().toString());
                    insertMember.setString(3, entry.getValue());
                    insertMember.executeUpdate();
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM guild_role WHERE guild_id = ?")) {
                delete.setString(1, guild.getId().toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insertRole = connection.prepareStatement(
                    "INSERT INTO guild_role (guild_id, role_id, name, sort_order) VALUES (?, ?, ?, ?)")) {
                for (GuildRoleDefinition role : guild.getRoles()) {
                    insertRole.setString(1, guild.getId().toString());
                    insertRole.setString(2, role.id());
                    insertRole.setString(3, role.name());
                    insertRole.setInt(4, role.sortOrder());
                    insertRole.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save guild " + guild.getId(), e);
        }
    }

    public void delete(UUID guildId) {
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guild WHERE id = ?")) {
                statement.setString(1, guildId.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guild_member WHERE guild_id = ?")) {
                statement.setString(1, guildId.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guild_role WHERE guild_id = ?")) {
                statement.setString(1, guildId.toString());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete guild " + guildId, e);
        }
    }
}

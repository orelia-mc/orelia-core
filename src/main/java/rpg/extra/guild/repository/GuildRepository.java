package rpg.extra.guild.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.guild.model.Guild;
import rpg.extra.guild.model.GuildRole;

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
 * Persists guilds and their member/role rows via orelia-core's shared {@link DatabaseManager}.
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
                        role VARCHAR(16) NOT NULL,
                        PRIMARY KEY (guild_id, uuid)
                    )
                    """);
        }
    }

    public List<Guild> loadAll() {
        Map<UUID, Guild> guilds = new LinkedHashMap<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, name, tag, leader_id FROM guild")) {
            while (resultSet.next()) {
                UUID id = UUID.fromString(resultSet.getString("id"));
                guilds.put(id, new Guild(id, resultSet.getString("name"), resultSet.getString("tag"),
                        UUID.fromString(resultSet.getString("leader_id")), new LinkedHashMap<>()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load guilds", e);
        }

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT guild_id, uuid, role FROM guild_member")) {
            while (resultSet.next()) {
                Guild guild = guilds.get(UUID.fromString(resultSet.getString("guild_id")));
                if (guild != null) {
                    guild.addMember(UUID.fromString(resultSet.getString("uuid")), GuildRole.valueOf(resultSet.getString("role")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load guild members", e);
        }

        return new ArrayList<>(guilds.values());
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
                    insertMember.setString(3, entry.getValue().name());
                    insertMember.executeUpdate();
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
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete guild " + guildId, e);
        }
    }
}

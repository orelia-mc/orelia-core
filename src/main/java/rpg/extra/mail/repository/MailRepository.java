package rpg.extra.mail.repository;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.mail.model.MailMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Persists mail (with serialized item attachments, same approach as orelia-core's
 * warehouse) via orelia-core's shared {@link DatabaseManager}.
 */
public final class MailRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public MailRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS mail_message (
                        id VARCHAR(36) PRIMARY KEY,
                        recipient_uuid VARCHAR(36) NOT NULL,
                        sender_name VARCHAR(32),
                        subject VARCHAR(64) NOT NULL,
                        body VARCHAR(512),
                        attachments TEXT,
                        sent_at BIGINT NOT NULL,
                        is_read BOOLEAN NOT NULL DEFAULT FALSE,
                        claimed BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
        }
    }

    public MailMessage send(UUID recipientId, String senderName, String subject, String body, ItemStack[] attachments) {
        MailMessage message = new MailMessage(UUID.randomUUID(), recipientId, senderName, subject, body,
                attachments, System.currentTimeMillis(), false, false);
        save(message);
        return message;
    }

    public List<MailMessage> findByRecipient(UUID recipientId) {
        List<MailMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM mail_message WHERE recipient_uuid = ? ORDER BY sent_at DESC";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipientId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(fromRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mail for " + recipientId, e);
        }
        return messages;
    }

    private MailMessage fromRow(ResultSet resultSet) throws SQLException {
        String attachmentsRaw = resultSet.getString("attachments");
        ItemStack[] attachments;
        try {
            attachments = attachmentsRaw == null || attachmentsRaw.isBlank() ? new ItemStack[0] : deserialize(attachmentsRaw);
        } catch (IOException | ClassNotFoundException e) {
            attachments = new ItemStack[0];
        }
        return new MailMessage(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("recipient_uuid")),
                resultSet.getString("sender_name"),
                resultSet.getString("subject"),
                resultSet.getString("body"),
                attachments,
                resultSet.getLong("sent_at"),
                resultSet.getBoolean("is_read"),
                resultSet.getBoolean("claimed"));
    }

    public void save(MailMessage message) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO mail_message (id, recipient_uuid, sender_name, subject, body, attachments, sent_at, is_read, claimed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET is_read = excluded.is_read, claimed = excluded.claimed, attachments = excluded.attachments
                    """;
            case MYSQL -> """
                    INSERT INTO mail_message (id, recipient_uuid, sender_name, subject, body, attachments, sent_at, is_read, claimed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE is_read = VALUES(is_read), claimed = VALUES(claimed), attachments = VALUES(attachments)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, message.getId().toString());
            statement.setString(2, message.getRecipientId().toString());
            statement.setString(3, message.getSenderName());
            statement.setString(4, message.getSubject());
            statement.setString(5, message.getBody());
            statement.setString(6, serialize(message.getAttachments()));
            statement.setLong(7, message.getSentAtMillis());
            statement.setBoolean(8, message.isRead());
            statement.setBoolean(9, message.isClaimed());
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to save mail " + message.getId(), e);
        }
    }

    public void delete(UUID id) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM mail_message WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete mail " + id, e);
        }
    }

    public int countByRecipient(UUID recipientId) {
        String sql = "SELECT COUNT(*) FROM mail_message WHERE recipient_uuid = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipientId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count mail for " + recipientId, e);
        }
    }

    /** Every message sent before {@code cutoffMillis}, across all recipients - feeds {@link rpg.extra.mail.service.MailService#purgeExpired}. */
    public List<MailMessage> findOlderThan(long cutoffMillis) {
        List<MailMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM mail_message WHERE sent_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cutoffMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(fromRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load mail older than " + cutoffMillis, e);
        }
        return messages;
    }

    private String serialize(ItemStack[] items) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(byteStream)) {
            dataStream.writeInt(items.length);
            for (ItemStack item : items) {
                dataStream.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(byteStream.toByteArray());
    }

    private ItemStack[] deserialize(String encoded) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
        try (BukkitObjectInputStream dataStream = new BukkitObjectInputStream(byteStream)) {
            int length = dataStream.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataStream.readObject();
            }
            return items;
        }
    }
}

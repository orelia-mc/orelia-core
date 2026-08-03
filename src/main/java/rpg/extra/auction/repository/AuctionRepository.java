package rpg.extra.auction.repository;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;
import rpg.extra.auction.model.AuctionListing;

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
 * Persists auction listings (with a serialized item, same approach as orelia-core's
 * warehouse) via orelia-core's shared {@link DatabaseManager}.
 */
public final class AuctionRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public AuctionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS auction_listing (
                        id VARCHAR(36) PRIMARY KEY,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(32),
                        item TEXT NOT NULL,
                        price DOUBLE NOT NULL,
                        listed_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        buyer_uuid VARCHAR(36)
                    )
                    """);
        }
    }

    public List<AuctionListing> findAllActiveOrPending() {
        List<AuctionListing> listings = new ArrayList<>();
        String sql = "SELECT * FROM auction_listing WHERE status != 'COLLECTED'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                listings.add(fromRow(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load auction listings", e);
        }
        return listings;
    }

    private AuctionListing fromRow(ResultSet resultSet) throws SQLException {
        ItemStack item;
        try {
            item = deserialize(resultSet.getString("item"));
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Corrupt auction item for listing " + resultSet.getString("id"), e);
        }
        String buyerRaw = resultSet.getString("buyer_uuid");
        return new AuctionListing(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("seller_uuid")),
                resultSet.getString("seller_name"),
                item,
                resultSet.getDouble("price"),
                resultSet.getLong("listed_at"),
                resultSet.getLong("expires_at"),
                AuctionListing.Status.valueOf(resultSet.getString("status")),
                buyerRaw == null ? null : UUID.fromString(buyerRaw));
    }

    public void save(AuctionListing listing) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO auction_listing (id, seller_uuid, seller_name, item, price, listed_at, expires_at, status, buyer_uuid)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET status = excluded.status, buyer_uuid = excluded.buyer_uuid
                    """;
            case MYSQL -> """
                    INSERT INTO auction_listing (id, seller_uuid, seller_name, item, price, listed_at, expires_at, status, buyer_uuid)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), buyer_uuid = VALUES(buyer_uuid)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, listing.getId().toString());
            statement.setString(2, listing.getSellerId().toString());
            statement.setString(3, listing.getSellerName());
            statement.setString(4, serialize(listing.getItem()));
            statement.setDouble(5, listing.getPrice());
            statement.setLong(6, listing.getListedAtMillis());
            statement.setLong(7, listing.getExpiresAtMillis());
            statement.setString(8, listing.getStatus().name());
            statement.setString(9, listing.getBuyerId() == null ? null : listing.getBuyerId().toString());
            statement.executeUpdate();
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to save auction listing " + listing.getId(), e);
        }
    }

    private String serialize(ItemStack item) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(byteStream)) {
            dataStream.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(byteStream.toByteArray());
    }

    private ItemStack deserialize(String encoded) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
        try (BukkitObjectInputStream dataStream = new BukkitObjectInputStream(byteStream)) {
            return (ItemStack) dataStream.readObject();
        }
    }
}

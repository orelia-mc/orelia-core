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
                        listing_type VARCHAR(16) NOT NULL DEFAULT 'BUY_NOW',
                        price DOUBLE NOT NULL,
                        listed_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        buyer_uuid VARCHAR(36),
                        current_bidder_uuid VARCHAR(36),
                        current_bidder_name VARCHAR(32),
                        current_bid_amount DOUBLE
                    )
                    """);
            migrateBiddingColumns(connection, statement);
        }
    }

    /** One-time migration for installs created before bidding was added - adds the 4 new columns, defaulting existing rows to BUY_NOW/no-bid. */
    private void migrateBiddingColumns(Connection connection, Statement statement) throws SQLException {
        addColumnIfMissing(connection, statement, "listing_type", "VARCHAR(16) NOT NULL DEFAULT 'BUY_NOW'");
        addColumnIfMissing(connection, statement, "current_bidder_uuid", "VARCHAR(36)");
        addColumnIfMissing(connection, statement, "current_bidder_name", "VARCHAR(32)");
        addColumnIfMissing(connection, statement, "current_bid_amount", "DOUBLE");
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String column, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "auction_listing", column)) {
            if (!columns.next()) {
                statement.execute("ALTER TABLE auction_listing ADD COLUMN " + column + " " + definition);
            }
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
        String bidderRaw = resultSet.getString("current_bidder_uuid");
        // current_bidder_uuid and current_bid_amount are always written together (see #save) -
        // no bid yet means both are NULL, so bidderRaw == null is a reliable proxy without
        // relying on ResultSet#wasNull's "last column read" ordering footgun.
        Double bidAmount = bidderRaw == null ? null : resultSet.getDouble("current_bid_amount");
        return new AuctionListing(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("seller_uuid")),
                resultSet.getString("seller_name"),
                item,
                AuctionListing.ListingType.valueOf(resultSet.getString("listing_type")),
                resultSet.getDouble("price"),
                resultSet.getLong("listed_at"),
                resultSet.getLong("expires_at"),
                AuctionListing.Status.valueOf(resultSet.getString("status")),
                buyerRaw == null ? null : UUID.fromString(buyerRaw),
                bidderRaw == null ? null : UUID.fromString(bidderRaw),
                resultSet.getString("current_bidder_name"),
                bidAmount);
    }

    public void save(AuctionListing listing) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO auction_listing (id, seller_uuid, seller_name, item, listing_type, price, listed_at, expires_at, status, buyer_uuid, current_bidder_uuid, current_bidder_name, current_bid_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET status = excluded.status, buyer_uuid = excluded.buyer_uuid,
                        current_bidder_uuid = excluded.current_bidder_uuid, current_bidder_name = excluded.current_bidder_name,
                        current_bid_amount = excluded.current_bid_amount
                    """;
            case MYSQL -> """
                    INSERT INTO auction_listing (id, seller_uuid, seller_name, item, listing_type, price, listed_at, expires_at, status, buyer_uuid, current_bidder_uuid, current_bidder_name, current_bid_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE status = VALUES(status), buyer_uuid = VALUES(buyer_uuid),
                        current_bidder_uuid = VALUES(current_bidder_uuid), current_bidder_name = VALUES(current_bidder_name),
                        current_bid_amount = VALUES(current_bid_amount)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, listing.getId().toString());
            statement.setString(2, listing.getSellerId().toString());
            statement.setString(3, listing.getSellerName());
            statement.setString(4, serialize(listing.getItem()));
            statement.setString(5, listing.getType().name());
            statement.setDouble(6, listing.getPrice());
            statement.setLong(7, listing.getListedAtMillis());
            statement.setLong(8, listing.getExpiresAtMillis());
            statement.setString(9, listing.getStatus().name());
            statement.setString(10, listing.getBuyerId() == null ? null : listing.getBuyerId().toString());
            statement.setString(11, listing.getCurrentBidderId() == null ? null : listing.getCurrentBidderId().toString());
            statement.setString(12, listing.getCurrentBidderName());
            if (listing.getCurrentBidAmount() == null) {
                statement.setNull(13, java.sql.Types.DOUBLE);
            } else {
                statement.setDouble(13, listing.getCurrentBidAmount());
            }
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

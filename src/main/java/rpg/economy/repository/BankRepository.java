package rpg.economy.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Named-bank storage for Vault's {@code Economy#createBank}/{@code bankDeposit}/... family
 * ({@link rpg.economy.vault.OreliaVaultEconomy}). A bank name is matched exactly (case-sensitive)
 * as its own primary key - Vault's interface doesn't mandate case-insensitive names, and treating
 * "Guild" and "guild" as the same bank would surprise a caller that created both expecting two
 * accounts. Ownership is a single UUID per bank; Vault's interface itself exposes no "add member"
 * method, so a bank member is, in practice, always exactly its owner - see
 * {@code BankService#isMember} for where that equivalence is made explicit.
 */
public final class BankRepository implements SchemaOwner {

    private final DatabaseManager databaseManager;

    public BankRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS vault_bank (
                        name VARCHAR(64) PRIMARY KEY,
                        owner_uuid VARCHAR(36) NOT NULL,
                        balance DOUBLE NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public boolean exists(String name) {
        String sql = "SELECT 1 FROM vault_bank WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check bank existence for " + name, e);
        }
    }

    /** {@code false} if a bank with this exact name already exists - the caller doesn't create over it. */
    public boolean create(String name, UUID owner) {
        if (exists(name)) {
            return false;
        }
        String sql = "INSERT INTO vault_bank (name, owner_uuid, balance) VALUES (?, ?, 0)";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, owner.toString());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create bank " + name, e);
        }
    }

    /** {@code false} if no bank with this name exists. */
    public boolean delete(String name) {
        String sql = "DELETE FROM vault_bank WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete bank " + name, e);
        }
    }

    public Optional<UUID> ownerOf(String name) {
        String sql = "SELECT owner_uuid FROM vault_bank WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(UUID.fromString(resultSet.getString("owner_uuid")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read owner of bank " + name, e);
        }
        return Optional.empty();
    }

    public Optional<Double> getBalance(String name) {
        String sql = "SELECT balance FROM vault_bank WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getDouble("balance"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read balance of bank " + name, e);
        }
        return Optional.empty();
    }

    /** No-op if {@code name} doesn't exist - callers are expected to have already checked via {@link #getBalance}. */
    public void setBalance(String name, double balance) {
        String sql = "UPDATE vault_bank SET balance = ? WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, Math.max(0, balance));
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to set balance of bank " + name, e);
        }
    }

    public List<String> getAllNames() {
        String sql = "SELECT name FROM vault_bank";
        List<String> names = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list banks", e);
        }
        return names;
    }
}

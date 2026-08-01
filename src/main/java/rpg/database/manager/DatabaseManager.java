package rpg.database.manager;

import rpg.database.DatabaseType;
import rpg.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Facade every module repository depends on to obtain a {@link Connection}. Owns no
 * table schemas itself - each module's repository creates and migrates its own tables
 * on top of this connection, keeping data-access ownership with the module that needs it.
 */
public final class DatabaseManager {

    private final DatabaseType type;
    private final ConnectionProvider connectionProvider;
    private volatile boolean shuttingDown;

    public DatabaseManager(DatabaseType type, ConnectionProvider connectionProvider) {
        this.type = type;
        this.connectionProvider = connectionProvider;
    }

    public Connection getConnection() throws SQLException {
        return connectionProvider.getConnection();
    }

    public DatabaseType getType() {
        return type;
    }

    /**
     * Whether {@link #shutdown()} has been called. A background async task (e.g.
     * {@code BlockRegenService}'s regen tick) that raced the plugin's disable sequence and
     * lost can check this to tell "the connection closed out from under me because the
     * server is stopping" apart from a genuine database error.
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public void shutdown() {
        shuttingDown = true;
        connectionProvider.close();
    }
}

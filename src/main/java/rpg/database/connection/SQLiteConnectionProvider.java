package rpg.database.connection;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite is a single-file, single-writer database, so this provider keeps one long-lived
 * connection open for the plugin's lifetime rather than pooling.
 *
 * <p>Every repository brackets its work as
 * {@code try (Connection c = databaseManager.getConnection(); Statement s = ...) { ... }},
 * which is correct for a per-call connection (see {@link MySQLConnectionProvider}) but would
 * close this provider's single shared connection out from under any other thread still using
 * it if {@link #getConnection()} returned it directly. Instead, {@link #getConnection()} hands
 * out a proxy that holds {@link #lock} for the duration of the try-with-resources block and
 * releases it (without closing the real connection) on {@code close()}, serializing every
 * caller - sync and async, across every module - onto the one real connection.
 */
public final class SQLiteConnectionProvider implements ConnectionProvider {

    private final File databaseFile;
    private final ReentrantLock lock = new ReentrantLock();
    private Connection connection;

    public SQLiteConnectionProvider(File dataFolder, String fileName) {
        this.databaseFile = new File(dataFolder, fileName);
    }

    @Override
    public Connection getConnection() throws SQLException {
        lock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                if (!databaseFile.getParentFile().exists()) {
                    databaseFile.getParentFile().mkdirs();
                }
                connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                    statement.execute("PRAGMA busy_timeout = 5000");
                }
            }
            return wrap(connection);
        } catch (SQLException | RuntimeException e) {
            lock.unlock();
            throw e;
        }
    }

    private Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new NonClosingHandler(real));
    }

    private final class NonClosingHandler implements InvocationHandler {

        private final Connection real;

        private NonClosingHandler(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close") && method.getParameterCount() == 0) {
                lock.unlock();
                return null;
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        } finally {
            lock.unlock();
        }
    }
}

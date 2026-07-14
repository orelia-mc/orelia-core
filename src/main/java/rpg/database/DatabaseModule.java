package rpg.database;

import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.database.connection.ConnectionProvider;
import rpg.database.connection.MySQLConnectionProvider;
import rpg.database.connection.SQLiteConnectionProvider;
import rpg.database.manager.DatabaseManager;

/**
 * Bootstraps the JDBC connection for whichever backend {@code config.yml: database} selects
 * and exposes {@link DatabaseManager} to every other module via {@link rpg.core.module.ModuleManager#get}.
 */
public final class DatabaseModule implements RpgModule {

    private DatabaseManager databaseManager;

    @Override
    public String getName() {
        return "database";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        DatabaseType type = DatabaseType.parse(config.getString("database.type", "SQLITE"), DatabaseType.SQLITE);

        ConnectionProvider provider = switch (type) {
            case SQLITE -> new SQLiteConnectionProvider(plugin.getDataFolder(), config.getString("database.sqlite.file", "orelia.db"));
            case MYSQL -> new MySQLConnectionProvider(
                    config.getString("database.mysql.host", "localhost"),
                    config.getInt("database.mysql.port", 3306),
                    config.getString("database.mysql.database", "orelia"),
                    config.getString("database.mysql.username", "orelia"),
                    config.getString("database.mysql.password", ""),
                    config.getBoolean("database.mysql.use-ssl", false));
        };

        this.databaseManager = new DatabaseManager(type, provider);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

package id.cyayo.gathering.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import id.cyayo.gathering.CyayoGathering;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {

    private final CyayoGathering plugin;
    private HikariDataSource dataSource;
    private ExecutorService writerExecutor;
    private boolean isMySQL = false;

    public DatabaseManager(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "SQLITE").trim().toUpperCase();
        this.isMySQL = "MYSQL".equals(type);

        HikariConfig hikariConfig = new HikariConfig();

        if (isMySQL) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "cyayogathering");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            hikariConfig.setMaximumPoolSize(config.getInt("database.mysql.pool.maximum-pool-size", 10));
            hikariConfig.setMinimumIdle(config.getInt("database.mysql.pool.minimum-idle", 2));
            hikariConfig.setConnectionTimeout(config.getLong("database.mysql.pool.connection-timeout", 30000));
            hikariConfig.setIdleTimeout(config.getLong("database.mysql.pool.idle-timeout", 600000));
            hikariConfig.setMaxLifetime(config.getLong("database.mysql.pool.max-lifetime", 1800000));
            hikariConfig.setPoolName("CyayoGathering-MySQL");
        } else {
            String fileName = config.getString("database.sqlite.file", "data/gathering.db");
            File dbFile = new File(plugin.getDataFolder(), fileName);
            if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setPoolName("CyayoGathering-SQLite");
            hikariConfig.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(hikariConfig);
        this.writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CyayoGathering-DB-Writer");
            t.setDaemon(true);
            return t;
        });

        createTables();
    }

    private void createTables() {
        String levelsSql = "CREATE TABLE IF NOT EXISTS cyayo_gathering_levels (" +
                "data_uuid VARCHAR(36) NOT NULL, " +
                "category VARCHAR(64) NOT NULL, " +
                "level INT NOT NULL DEFAULT 1, " +
                "xp DOUBLE NOT NULL DEFAULT 0.0, " +
                "PRIMARY KEY (data_uuid, category)" +
                ");";

        String statsSql = "CREATE TABLE IF NOT EXISTS cyayo_gathering_stats (" +
                "data_uuid VARCHAR(36) NOT NULL, " +
                "stat_type VARCHAR(16) NOT NULL, " +
                "stat_key VARCHAR(64) NOT NULL, " +
                "amount INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (data_uuid, stat_type, stat_key)" +
                ");";

        String modifiersSql = "CREATE TABLE IF NOT EXISTS cyayo_gathering_modifiers (" +
                "data_uuid VARCHAR(36) PRIMARY KEY, " +
                "luck DOUBLE NOT NULL DEFAULT 0.0, " +
                "double_gather DOUBLE NOT NULL DEFAULT 0.0, " +
                "speed DOUBLE NOT NULL DEFAULT 0.0" +
                ");";

        String nodesSql = "CREATE TABLE IF NOT EXISTS cyayo_gathering_nodes (" +
                "node_id VARCHAR(128) PRIMARY KEY, " +
                "type_id VARCHAR(64) NOT NULL, " +
                "world VARCHAR(64) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL DEFAULT 0, " +
                "pitch FLOAT NOT NULL DEFAULT 0, " +
                "current_charges INT NOT NULL DEFAULT 10, " +
                "max_charges INT NOT NULL DEFAULT 10, " +
                "status VARCHAR(16) NOT NULL DEFAULT 'READY', " +
                "last_interaction_millis BIGINT NOT NULL DEFAULT 0, " +
                "cooldown_remaining_seconds INT NOT NULL DEFAULT 0" +
                ");";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            if (!isMySQL) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
            }
            stmt.execute(levelsSql);
            stmt.execute(statsSql);
            stmt.execute(modifiersSql);
            stmt.execute(nodesSql);

            plugin.getLogger().info("Database tables initialized successfully (" + (isMySQL ? "MySQL" : "SQLite") + ")!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource is closed or not initialized!");
        }
        return dataSource.getConnection();
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public void executeAsync(Runnable task) {
        if (task == null) return;
        if (writerExecutor == null || writerExecutor.isShutdown()) {
            task.run();
            return;
        }
        writerExecutor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("Async DB write failed: " + t.getMessage());
            }
        });
    }

    public boolean isTableEmpty(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1")) {
            return !rs.next();
        }
    }

    public void markMigratedFile(File yamlFile) {
        if (yamlFile == null || !yamlFile.exists()) return;
        File target = new File(yamlFile.getParentFile(), yamlFile.getName() + ".migrated");
        if (target.exists()) {
            target = new File(yamlFile.getParentFile(), yamlFile.getName() + ".migrated." + System.currentTimeMillis());
        }
        if (yamlFile.renameTo(target)) {
            plugin.getLogger().info("Migrated legacy YAML file '" + yamlFile.getName() + "' -> '" + target.getName() + "'");
        }
    }

    public void flushPendingWritesSync() {
        if (writerExecutor == null || writerExecutor.isShutdown()) return;
        try {
            writerExecutor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public void shutdown() {
        if (writerExecutor != null && !writerExecutor.isShutdown()) {
            writerExecutor.shutdown();
            try {
                writerExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

package id.cyayo.gathering.stats;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.hook.CyayoProfilesHook;
import id.cyayo.gathering.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private final CyayoGathering plugin;

    // UUID -> Category -> Count
    private final Map<UUID, Map<String, Integer>> categoryStats = new ConcurrentHashMap<>();
    // UUID -> NodeTypeId -> Count
    private final Map<UUID, Map<String, Integer>> nodeStats = new ConcurrentHashMap<>();
    // UUID -> Total Count
    private final Map<UUID, Integer> totalStats = new ConcurrentHashMap<>();
    // Dirty profile UUIDs pending batched SQL flush
    private final Set<UUID> dirtyProfiles = ConcurrentHashMap.newKeySet();

    private record ProfileStatsSnapshot(UUID dataUuid, int total, Map<String, Integer> categories, Map<String, Integer> nodes) {}

    public StatsManager(CyayoGathering plugin) {
        this.plugin = plugin;
        load();

        // Flush dirty stats periodically every 60 seconds onto async DB writer
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 1200L, 1200L);
    }

    public void increment(UUID playerUuid, String category, String nodeTypeId) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        // Total
        totalStats.merge(dataUuid, 1, Integer::sum);

        // Category
        categoryStats.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .merge(category.toLowerCase(), 1, Integer::sum);

        // Node Type
        nodeStats.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .merge(nodeTypeId.toLowerCase(), 1, Integer::sum);

        dirtyProfiles.add(dataUuid);
    }

    public int getTotal(UUID playerUuid) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        return totalStats.getOrDefault(dataUuid, 0);
    }

    public int getCategory(UUID playerUuid, String category) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        Map<String, Integer> catMap = categoryStats.get(dataUuid);
        if (catMap == null) return 0;
        return catMap.getOrDefault(category.toLowerCase(), 0);
    }

    public int getNodeType(UUID playerUuid, String nodeTypeId) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        Map<String, Integer> nMap = nodeStats.get(dataUuid);
        if (nMap == null) return 0;
        return nMap.getOrDefault(nodeTypeId.toLowerCase(), 0);
    }

    public Map<String, Integer> getCategoryMap(UUID playerUuid) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        Map<String, Integer> catMap = categoryStats.get(dataUuid);
        return catMap != null ? java.util.Collections.unmodifiableMap(catMap) : java.util.Collections.emptyMap();
    }

    public Map<String, Integer> getNodeTypeMap(UUID playerUuid) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        Map<String, Integer> nMap = nodeStats.get(dataUuid);
        return nMap != null ? java.util.Collections.unmodifiableMap(nMap) : java.util.Collections.emptyMap();
    }

    public void setCategory(UUID playerUuid, String category, int amount) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        categoryStats.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .put(category.toLowerCase(), Math.max(0, amount));
        dirtyProfiles.add(dataUuid);
        save();
    }

    public void setNodeType(UUID playerUuid, String nodeTypeId, int amount) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        nodeStats.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .put(nodeTypeId.toLowerCase(), Math.max(0, amount));
        dirtyProfiles.add(dataUuid);
        save();
    }

    public void setTotal(UUID playerUuid, int amount) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        totalStats.put(dataUuid, Math.max(0, amount));
        dirtyProfiles.add(dataUuid);
        save();
    }

    public int addCategory(UUID playerUuid, String category, int amount) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        int newAmount = categoryStats.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .merge(category.toLowerCase(), amount, (oldVal, newVal) -> Math.max(0, oldVal + newVal));
        totalStats.merge(dataUuid, amount, (oldVal, newVal) -> Math.max(0, oldVal + newVal));
        dirtyProfiles.add(dataUuid);
        save();
        return newAmount;
    }

    public int addNodeType(UUID playerUuid, String nodeTypeId, int amount) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        int newAmount = nodeStats.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .merge(nodeTypeId.toLowerCase(), amount, (oldVal, newVal) -> Math.max(0, oldVal + newVal));
        totalStats.merge(dataUuid, amount, (oldVal, newVal) -> Math.max(0, oldVal + newVal));
        dirtyProfiles.add(dataUuid);
        save();
        return newAmount;
    }

    public int addTotal(UUID playerUuid, int amount) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        int newAmount = totalStats.merge(dataUuid, amount, (oldVal, newVal) -> Math.max(0, oldVal + newVal));
        dirtyProfiles.add(dataUuid);
        save();
        return newAmount;
    }

    public int removeCategory(UUID playerUuid, String category, int amount) {
        int current = getCategory(playerUuid, category);
        int newAmount = Math.max(0, current - amount);
        setCategory(playerUuid, category, newAmount);
        return newAmount;
    }

    public int removeNodeType(UUID playerUuid, String nodeTypeId, int amount) {
        int current = getNodeType(playerUuid, nodeTypeId);
        int newAmount = Math.max(0, current - amount);
        setNodeType(playerUuid, nodeTypeId, newAmount);
        return newAmount;
    }

    public int removeTotal(UUID playerUuid, int amount) {
        int current = getTotal(playerUuid);
        int newAmount = Math.max(0, current - amount);
        setTotal(playerUuid, newAmount);
        return newAmount;
    }

    public void resetCategory(UUID playerUuid, String category) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        String cat = category.toLowerCase();
        Map<String, Integer> map = categoryStats.get(dataUuid);
        if (map != null) {
            map.remove(cat);
        }
        deleteSingleStatKey(dataUuid, "CATEGORY", cat);
    }

    public void resetNodeType(UUID playerUuid, String nodeTypeId) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        String node = nodeTypeId.toLowerCase();
        Map<String, Integer> map = nodeStats.get(dataUuid);
        if (map != null) {
            map.remove(node);
        }
        deleteSingleStatKey(dataUuid, "NODE", node);
    }

    public void resetAll(UUID playerUuid) {
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(playerUuid);
        deleteData(dataUuid);
    }

    public void deleteData(UUID profileUuid) {
        if (profileUuid == null) return;
        totalStats.remove(profileUuid);
        categoryStats.remove(profileUuid);
        nodeStats.remove(profileUuid);
        dirtyProfiles.remove(profileUuid);

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> {
                try (Connection conn = db.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM cyayo_gathering_stats WHERE data_uuid = ?")) {
                    stmt.setString(1, profileUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to delete gathering stats for " + profileUuid + ": " + e.getMessage());
                }
            });
        }
    }

    private void deleteSingleStatKey(UUID dataUuid, String statType, String statKey) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;
        db.executeAsync(() -> {
            try (Connection conn = db.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM cyayo_gathering_stats WHERE data_uuid = ? AND stat_type = ? AND stat_key = ?")) {
                stmt.setString(1, dataUuid.toString());
                stmt.setString(2, statType);
                stmt.setString(3, statKey);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete gathering stat row: " + e.getMessage());
            }
        });
    }

    public void load() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT data_uuid, stat_type, stat_key, amount FROM cyayo_gathering_stats");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("data_uuid"));
                        String statType = rs.getString("stat_type");
                        String statKey = rs.getString("stat_key");
                        int amount = rs.getInt("amount");

                        if ("TOTAL".equals(statType)) {
                            totalStats.put(uuid, amount);
                        } else if ("CATEGORY".equals(statType)) {
                            categoryStats.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(statKey, amount);
                        } else if ("NODE".equals(statType)) {
                            nodeStats.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(statKey, amount);
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Auto-migrate legacy YAML data/player_stats.yml if table is empty
            File legacyFile = new File(plugin.getDataFolder(), "data/player_stats.yml");
            if (legacyFile.exists() && db.isTableEmpty(conn, "cyayo_gathering_stats")) {
                YamlConfiguration legacyCfg = YamlConfiguration.loadConfiguration(legacyFile);
                ConfigurationSection statsSec = legacyCfg.getConfigurationSection("stats");
                List<ProfileStatsSnapshot> snapshots = new ArrayList<>();
                if (statsSec != null) {
                    for (String uuidStr : statsSec.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            int total = statsSec.getInt(uuidStr + ".total", 0);
                            totalStats.put(uuid, total);

                            Map<String, Integer> cats = new ConcurrentHashMap<>();
                            ConfigurationSection cSec = statsSec.getConfigurationSection(uuidStr + ".categories");
                            if (cSec != null) {
                                for (String cat : cSec.getKeys(false)) {
                                    cats.put(cat.toLowerCase(), cSec.getInt(cat));
                                }
                                categoryStats.put(uuid, cats);
                            }

                            Map<String, Integer> nodes = new ConcurrentHashMap<>();
                            ConfigurationSection nSec = statsSec.getConfigurationSection(uuidStr + ".nodes");
                            if (nSec != null) {
                                for (String n : nSec.getKeys(false)) {
                                    nodes.put(n.toLowerCase(), nSec.getInt(n));
                                }
                                nodeStats.put(uuid, nodes);
                            }

                            snapshots.add(new ProfileStatsSnapshot(uuid, total, new HashMap<>(cats), new HashMap<>(nodes)));
                        } catch (Exception ignored) {}
                    }
                }
                boolean migratedOk = snapshots.isEmpty() || writeStatsBatch(conn, db, snapshots);
                if (migratedOk) {
                    db.markMigratedFile(legacyFile);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load gathering stats from database: " + e.getMessage());
        }
    }

    public void save() {
        List<ProfileStatsSnapshot> snapshots = drainDirtySnapshots();
        if (snapshots.isEmpty()) return;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> writeStatsBatch(snapshots));
        }
    }

    public void saveSync() {
        List<ProfileStatsSnapshot> snapshots = drainDirtySnapshots();
        if (!snapshots.isEmpty()) {
            writeStatsBatch(snapshots);
        }
    }

    public void saveIfDirty() {
        if (!dirtyProfiles.isEmpty()) {
            save();
        }
    }

    private List<ProfileStatsSnapshot> drainDirtySnapshots() {
        if (dirtyProfiles.isEmpty()) return List.of();
        List<UUID> uuids = new ArrayList<>();
        for (UUID uuid : dirtyProfiles) {
            if (dirtyProfiles.remove(uuid)) {
                uuids.add(uuid);
            }
        }
        List<ProfileStatsSnapshot> snapshots = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Map<String, Integer> cMap = categoryStats.get(uuid);
            Map<String, Integer> nMap = nodeStats.get(uuid);
            snapshots.add(new ProfileStatsSnapshot(
                    uuid,
                    totalStats.getOrDefault(uuid, 0),
                    cMap != null ? new HashMap<>(cMap) : Collections.emptyMap(),
                    nMap != null ? new HashMap<>(nMap) : Collections.emptyMap()
            ));
        }
        return snapshots;
    }

    private boolean writeStatsBatch(List<ProfileStatsSnapshot> snapshots) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || snapshots.isEmpty()) return true;
        try (Connection conn = db.getConnection()) {
            return writeStatsBatch(conn, db, snapshots);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save gathering stats to database: " + e.getMessage());
            return false;
        }
    }

    private boolean writeStatsBatch(Connection conn, DatabaseManager db, List<ProfileStatsSnapshot> snapshots) {
        if (conn == null || db == null || snapshots.isEmpty()) return true;

        String upsertSql = db.isMySQL()
                ? "INSERT INTO cyayo_gathering_stats (data_uuid, stat_type, stat_key, amount) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE amount = VALUES(amount)"
                : "INSERT INTO cyayo_gathering_stats (data_uuid, stat_type, stat_key, amount) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(data_uuid, stat_type, stat_key) DO UPDATE SET amount = excluded.amount";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                for (ProfileStatsSnapshot snap : snapshots) {
                    String u = snap.dataUuid.toString();
                    stmt.setString(1, u);
                    stmt.setString(2, "TOTAL");
                    stmt.setString(3, "");
                    stmt.setInt(4, snap.total);
                    stmt.addBatch();

                    for (Map.Entry<String, Integer> entry : snap.categories.entrySet()) {
                        stmt.setString(1, u);
                        stmt.setString(2, "CATEGORY");
                        stmt.setString(3, entry.getKey());
                        stmt.setInt(4, entry.getValue());
                        stmt.addBatch();
                    }

                    for (Map.Entry<String, Integer> entry : snap.nodes.entrySet()) {
                        stmt.setString(1, u);
                        stmt.setString(2, "NODE");
                        stmt.setString(3, entry.getKey());
                        stmt.setInt(4, entry.getValue());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save gathering stats to database: " + e.getMessage());
            return false;
        }
    }
}

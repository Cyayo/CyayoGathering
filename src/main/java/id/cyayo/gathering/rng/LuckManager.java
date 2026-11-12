package id.cyayo.gathering.rng;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.hook.CyayoProfilesHook;
import id.cyayo.gathering.storage.DatabaseManager;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LuckManager {

    private final CyayoGathering plugin;

    // UUID -> Custom Gathering Luck Bonus
    private final Map<UUID, Double> customLuck = new ConcurrentHashMap<>();
    // UUID -> Custom Double Gather % Bonus
    private final Map<UUID, Double> customDoubleGather = new ConcurrentHashMap<>();
    // UUID -> Custom Gathering Speed % Bonus
    private final Map<UUID, Double> customSpeed = new ConcurrentHashMap<>();
    // Dirty UUIDs pending SQL flush
    private final Set<UUID> dirtyProfiles = ConcurrentHashMap.newKeySet();

    private record ModifierSnapshot(UUID dataUuid, double luck, double doubleGather, double speed) {}

    public LuckManager(CyayoGathering plugin) {
        this.plugin = plugin;
        loadData();

        // Flush dirty modifiers periodically every 60 seconds onto async DB writer
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 1200L, 1200L);
        plugin.getLogger().info("CyayoGathering Modifiers engine (Luck, Speed, Double Gather) initialized!");
    }

    private void loadData() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT data_uuid, luck, double_gather, speed FROM cyayo_gathering_modifiers");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("data_uuid"));
                        double luckVal = rs.getDouble("luck");
                        double doubleVal = rs.getDouble("double_gather");
                        double speedVal = rs.getDouble("speed");
                        if (luckVal != 0.0) customLuck.put(uuid, luckVal);
                        if (doubleVal != 0.0) customDoubleGather.put(uuid, doubleVal);
                        if (speedVal != 0.0) customSpeed.put(uuid, speedVal);
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // Auto-migrate legacy YAML data/player_modifiers.yml if table is empty
            File legacyFile = new File(plugin.getDataFolder(), "data/player_modifiers.yml");
            if (legacyFile.exists() && db.isTableEmpty(conn, "cyayo_gathering_modifiers")) {
                YamlConfiguration legacyCfg = YamlConfiguration.loadConfiguration(legacyFile);
                ConfigurationSection modSec = legacyCfg.getConfigurationSection("modifiers");
                List<ModifierSnapshot> snapshots = new ArrayList<>();
                if (modSec != null) {
                    for (String key : modSec.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(key);
                            double luckVal = modSec.getDouble(key + ".luck", 0.0);
                            double doubleVal = modSec.getDouble(key + ".double_gather", 0.0);
                            double speedVal = modSec.getDouble(key + ".speed", 0.0);
                            if (luckVal != 0.0) customLuck.put(uuid, luckVal);
                            if (doubleVal != 0.0) customDoubleGather.put(uuid, doubleVal);
                            if (speedVal != 0.0) customSpeed.put(uuid, speedVal);
                            if (luckVal != 0.0 || doubleVal != 0.0 || speedVal != 0.0) {
                                snapshots.add(new ModifierSnapshot(uuid, luckVal, doubleVal, speedVal));
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                boolean migratedOk = snapshots.isEmpty() || writeModifiersBatch(conn, db, snapshots);
                if (migratedOk) {
                    db.markMigratedFile(legacyFile);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load gathering modifiers from database: " + e.getMessage());
        }
    }

    public void saveData() {
        List<ModifierSnapshot> snapshots = drainDirtySnapshots();
        if (snapshots.isEmpty()) return;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> writeModifiersBatch(snapshots));
        }
    }

    public void saveDataSync() {
        List<ModifierSnapshot> snapshots = drainDirtySnapshots();
        if (!snapshots.isEmpty()) {
            writeModifiersBatch(snapshots);
        }
    }

    private void saveIfDirty() {
        if (!dirtyProfiles.isEmpty()) {
            saveData();
        }
    }

    private List<ModifierSnapshot> drainDirtySnapshots() {
        if (dirtyProfiles.isEmpty()) return List.of();
        List<UUID> uuids = new ArrayList<>();
        for (UUID uuid : dirtyProfiles) {
            if (dirtyProfiles.remove(uuid)) {
                uuids.add(uuid);
            }
        }
        List<ModifierSnapshot> snapshots = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            snapshots.add(new ModifierSnapshot(
                    uuid,
                    customLuck.getOrDefault(uuid, 0.0),
                    customDoubleGather.getOrDefault(uuid, 0.0),
                    customSpeed.getOrDefault(uuid, 0.0)
            ));
        }
        return snapshots;
    }

    private boolean writeModifiersBatch(List<ModifierSnapshot> snapshots) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || snapshots.isEmpty()) return true;
        try (Connection conn = db.getConnection()) {
            return writeModifiersBatch(conn, db, snapshots);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save gathering modifiers to database: " + e.getMessage());
            return false;
        }
    }

    private boolean writeModifiersBatch(Connection conn, DatabaseManager db, List<ModifierSnapshot> snapshots) {
        if (conn == null || db == null || snapshots.isEmpty()) return true;

        String upsertSql = db.isMySQL()
                ? "INSERT INTO cyayo_gathering_modifiers (data_uuid, luck, double_gather, speed) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE luck = VALUES(luck), double_gather = VALUES(double_gather), speed = VALUES(speed)"
                : "INSERT INTO cyayo_gathering_modifiers (data_uuid, luck, double_gather, speed) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(data_uuid) DO UPDATE SET luck = excluded.luck, double_gather = excluded.double_gather, speed = excluded.speed";
        String deleteSql = "DELETE FROM cyayo_gathering_modifiers WHERE data_uuid = ?";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSql);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                boolean hasUpserts = false;
                boolean hasDeletes = false;
                for (ModifierSnapshot snap : snapshots) {
                    if (snap.luck == 0.0 && snap.doubleGather == 0.0 && snap.speed == 0.0) {
                        deleteStmt.setString(1, snap.dataUuid.toString());
                        deleteStmt.addBatch();
                        hasDeletes = true;
                    } else {
                        upsertStmt.setString(1, snap.dataUuid.toString());
                        upsertStmt.setDouble(2, snap.luck);
                        upsertStmt.setDouble(3, snap.doubleGather);
                        upsertStmt.setDouble(4, snap.speed);
                        upsertStmt.addBatch();
                        hasUpserts = true;
                    }
                }
                if (hasUpserts) upsertStmt.executeBatch();
                if (hasDeletes) deleteStmt.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save gathering modifiers to database: " + e.getMessage());
            return false;
        }
    }

    // --- Custom Luck Management ---

    public double getCustomLuck(UUID uuid) {
        if (uuid == null) return 0.0;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        return customLuck.getOrDefault(dataUuid, 0.0);
    }

    public void setCustomLuck(UUID uuid, double amount) {
        if (uuid == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        if (amount == 0.0) {
            customLuck.remove(dataUuid);
        } else {
            customLuck.put(dataUuid, amount);
        }
        dirtyProfiles.add(dataUuid);
        saveData();
    }

    public double addCustomLuck(UUID uuid, double amount) {
        if (uuid == null) return 0.0;
        double current = getCustomLuck(uuid);
        double updated = current + amount;
        setCustomLuck(uuid, updated);
        return updated;
    }

    public double removeCustomLuck(UUID uuid, double amount) {
        if (uuid == null) return 0.0;
        double current = getCustomLuck(uuid);
        double updated = Math.max(0.0, current - amount);
        setCustomLuck(uuid, updated);
        return updated;
    }

    public void resetCustomLuck(UUID uuid) {
        if (uuid == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        customLuck.remove(dataUuid);
        dirtyProfiles.add(dataUuid);
        saveData();
    }

    // --- Custom Double Gather Management ---

    public double getCustomDoubleGather(UUID uuid) {
        if (uuid == null) return 0.0;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        return customDoubleGather.getOrDefault(dataUuid, 0.0);
    }

    public void setCustomDoubleGather(UUID uuid, double amount) {
        if (uuid == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        if (amount == 0.0) {
            customDoubleGather.remove(dataUuid);
        } else {
            customDoubleGather.put(dataUuid, amount);
        }
        dirtyProfiles.add(dataUuid);
        saveData();
    }

    public double addCustomDoubleGather(UUID uuid, double amount) {
        if (uuid == null) return 0.0;
        double current = getCustomDoubleGather(uuid);
        double updated = current + amount;
        setCustomDoubleGather(uuid, updated);
        return updated;
    }

    public double removeCustomDoubleGather(UUID uuid, double amount) {
        if (uuid == null) return 0.0;
        double current = getCustomDoubleGather(uuid);
        double updated = Math.max(0.0, current - amount);
        setCustomDoubleGather(uuid, updated);
        return updated;
    }

    public void resetCustomDoubleGather(UUID uuid) {
        if (uuid == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        customDoubleGather.remove(dataUuid);
        dirtyProfiles.add(dataUuid);
        saveData();
    }

    // --- Custom Speed Management ---

    public double getCustomSpeed(UUID uuid) {
        if (uuid == null) return 0.0;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        return customSpeed.getOrDefault(dataUuid, 0.0);
    }

    public void setCustomSpeed(UUID uuid, double amount) {
        if (uuid == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        if (amount == 0.0) {
            customSpeed.remove(dataUuid);
        } else {
            customSpeed.put(dataUuid, amount);
        }
        dirtyProfiles.add(dataUuid);
        saveData();
    }

    public double addCustomSpeed(UUID uuid, double amount) {
        if (uuid == null) return 0.0;
        double current = getCustomSpeed(uuid);
        double updated = current + amount;
        setCustomSpeed(uuid, updated);
        return updated;
    }

    public double removeCustomSpeed(UUID uuid, double amount) {
        if (uuid == null) return 0.0;
        double current = getCustomSpeed(uuid);
        double updated = Math.max(0.0, current - amount);
        setCustomSpeed(uuid, updated);
        return updated;
    }

    public void resetCustomSpeed(UUID uuid) {
        if (uuid == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        customSpeed.remove(dataUuid);
        dirtyProfiles.add(dataUuid);
        saveData();
    }

    public void deleteData(UUID profileUuid) {
        if (profileUuid == null) return;
        customLuck.remove(profileUuid);
        customDoubleGather.remove(profileUuid);
        customSpeed.remove(profileUuid);
        dirtyProfiles.remove(profileUuid);
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> {
                try (Connection conn = db.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM cyayo_gathering_modifiers WHERE data_uuid = ?")) {
                    stmt.setString(1, profileUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to delete gathering modifiers for " + profileUuid + ": " + e.getMessage());
                }
            });
        }
    }

    // --- Base & Total Stat Queries ---

    public double getBaseGatheringLuck(Player player) {
        if (player == null) return 0.0;
        double base = 0.0;

        // 1. Ambil dari MMOItems stat: GATHERING_LUCK
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems") && Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            try {
                MMOPlayerData data = MMOPlayerData.get(player);
                if (data != null && data.getStatMap() != null) {
                    base += data.getStatMap().getStat("GATHERING_LUCK");
                }
            } catch (Throwable ignored) {}
        }

        // 2. Opsi Vanilla GENERIC_LUCK
        if (plugin.getConfig().getBoolean("gathering_luck.include_vanilla_luck", false)) {
            try {
                Attribute attrType = null;
                try {
                    attrType = Attribute.valueOf("GENERIC_LUCK");
                } catch (IllegalArgumentException e) {
                    try {
                        attrType = Attribute.valueOf("LUCK");
                    } catch (IllegalArgumentException ignored) {}
                }
                if (attrType != null) {
                    AttributeInstance attr = player.getAttribute(attrType);
                    if (attr != null) {
                        base += attr.getValue();
                    }
                }
            } catch (Throwable ignored) {}
        }

        return Math.max(0.0, base);
    }

    /**
     * Total Gathering Luck efektif pemain (Base MMOItems + Vanilla + Custom Bonus CyayoGathering).
     */
    public double getPlayerGatheringLuck(Player player) {
        if (player == null) return 0.0;
        double base = getBaseGatheringLuck(player);
        double custom = getCustomLuck(player.getUniqueId());
        double modeBonus = CyayoProfilesHook.getGatheringLuckBonus(player.getUniqueId());
        return Math.max(0.0, base + custom + modeBonus);
    }

    public double getBaseDoubleGather(Player player) {
        if (player == null) return 0.0;
        double base = 0.0;
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems") && Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            try {
                MMOPlayerData data = MMOPlayerData.get(player);
                if (data != null && data.getStatMap() != null) {
                    base += data.getStatMap().getStat("GATHERING_DOUBLE_DROP");
                }
            } catch (Throwable ignored) {}
        }
        return Math.max(0.0, base);
    }

    /**
     * Total Double Gather Chance efektif pemain (Base MMOItems + Custom Bonus CyayoGathering).
     */
    public double getPlayerDoubleGather(Player player) {
        if (player == null) return 0.0;
        double base = getBaseDoubleGather(player);
        double custom = getCustomDoubleGather(player.getUniqueId());
        double modeBonus = CyayoProfilesHook.getGatheringDoubleBonus(player.getUniqueId());
        return Math.max(0.0, base + custom + modeBonus);
    }

    public double getBaseGatheringSpeed(Player player) {
        if (player == null) return 0.0;
        double base = 0.0;
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems") && Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            try {
                MMOPlayerData data = MMOPlayerData.get(player);
                if (data != null && data.getStatMap() != null) {
                    base += data.getStatMap().getStat("GATHERING_SPEED");
                }
            } catch (Throwable ignored) {}
        }
        return Math.max(0.0, base);
    }

    /**
     * Total Gathering Speed efektif pemain (Base MMOItems + Custom Bonus CyayoGathering).
     */
    public double getPlayerGatheringSpeed(Player player) {
        if (player == null) return 0.0;
        double base = getBaseGatheringSpeed(player);
        double custom = getCustomSpeed(player.getUniqueId());
        double modeBonus = CyayoProfilesHook.getGatheringSpeedBonus(player.getUniqueId());
        return Math.max(0.0, base + custom + modeBonus);
    }

    /**
     * Menghitung total pengali peluang efektif (Effective Multiplier).
     * Rumus: (1.0 + PlayerLuck / 100.0) * (1.0 + NodeBonusLuck / 100.0)
     */
    public double getEffectiveMultiplier(Player player, double nodeBonusLuckPercent) {
        if (player == null) {
            double nodeMult = 1.0 + (Math.max(0.0, nodeBonusLuckPercent) / 100.0);
            return Math.max(1.0, nodeMult);
        }

        double playerLuck = getPlayerGatheringLuck(player);
        double playerMult = 1.0 + (Math.max(0.0, playerLuck) / 100.0);
        double nodeMult = 1.0 + (Math.max(0.0, nodeBonusLuckPercent) / 100.0);

        return Math.max(1.0, playerMult * nodeMult);
    }

    public double getPlayerLuckBonus(Player player, double nodeBonusLuckPercent) {
        return getEffectiveMultiplier(player, nodeBonusLuckPercent);
    }
}

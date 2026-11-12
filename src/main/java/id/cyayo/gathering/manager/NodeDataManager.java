package id.cyayo.gathering.manager;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.api.event.NodeGatherCancelEvent.CancelReason;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeStatus;
import id.cyayo.gathering.model.NodeType;
import id.cyayo.gathering.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeDataManager {

    private final CyayoGathering plugin;
    private final Map<String, ActiveNode> activeNodes = new ConcurrentHashMap<>();

    private record NodeSnapshot(
            String id, String typeId, String world,
            double x, double y, double z, float yaw, float pitch,
            int currentCharges, int maxCharges, String status,
            long lastInteractionMillis, int cooldownRemainingSeconds
    ) {
        static NodeSnapshot from(ActiveNode node) {
            Location loc = node.getLocation();
            String worldName = (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : "world";
            return new NodeSnapshot(
                    node.getId(),
                    node.getTypeId(),
                    worldName,
                    loc != null ? loc.getX() : 0.0,
                    loc != null ? loc.getY() : 64.0,
                    loc != null ? loc.getZ() : 0.0,
                    loc != null ? loc.getYaw() : 0.0f,
                    loc != null ? loc.getPitch() : 0.0f,
                    node.getCurrentCharges(),
                    node.getMaxCharges(),
                    node.getStatus().name(),
                    node.getLastInteractionMillis(),
                    node.getCooldownRemainingSeconds()
            );
        }
    }

    public NodeDataManager(CyayoGathering plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        Set<String> keysInDb = new HashSet<>();
        try (Connection conn = db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT node_id, type_id, world, x, y, z, yaw, pitch, current_charges, max_charges, status, last_interaction_millis, cooldown_remaining_seconds FROM cyayo_gathering_nodes");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("node_id");
                    String typeId = rs.getString("type_id");
                    String worldName = rs.getString("world");
                    World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                    if (world == null) continue;

                    keysInDb.add(key);
                    Location loc = new Location(
                            world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );
                    int currentCharges = rs.getInt("current_charges");
                    int maxCharges = rs.getInt("max_charges");
                    String statusStr = rs.getString("status");
                    NodeStatus status = NodeStatus.READY;
                    if (statusStr != null) {
                        try {
                            status = NodeStatus.valueOf(statusStr.toUpperCase());
                        } catch (Exception ignored) {}
                    }
                    long lastInteraction = rs.getLong("last_interaction_millis");
                    int cdRemaining = rs.getInt("cooldown_remaining_seconds");

                    registerOrUpdateLoadedNode(key, typeId, loc, currentCharges, maxCharges, status, lastInteraction, cdRemaining);
                }
            }

            // Auto-migrate legacy data/nodes.yml if database table is empty
            File legacyFile = new File(plugin.getDataFolder(), "data/nodes.yml");
            if (legacyFile.exists() && db.isTableEmpty(conn, "cyayo_gathering_nodes")) {
                YamlConfiguration legacyCfg = YamlConfiguration.loadConfiguration(legacyFile);
                ConfigurationSection sec = legacyCfg.getConfigurationSection("nodes");
                List<NodeSnapshot> migratedSnapshots = new ArrayList<>();
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        String typeId = sec.getString(key + ".type_id");
                        String worldName = sec.getString(key + ".world");
                        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                        if (typeId == null || world == null) continue;

                        keysInDb.add(key);
                        Location loc = new Location(
                                world,
                                sec.getDouble(key + ".x"),
                                sec.getDouble(key + ".y"),
                                sec.getDouble(key + ".z"),
                                (float) sec.getDouble(key + ".yaw", 0.0),
                                (float) sec.getDouble(key + ".pitch", 0.0)
                        );
                        int currentCharges = sec.getInt(key + ".current_charges", 10);
                        int maxCharges = sec.getInt(key + ".max_charges", 10);
                        String statusStr = sec.getString(key + ".status", "READY").toUpperCase();
                        NodeStatus status = NodeStatus.READY;
                        try {
                            status = NodeStatus.valueOf(statusStr);
                        } catch (Exception ignored) {}
                        long lastInteraction = sec.getLong(key + ".last_interaction_millis", System.currentTimeMillis());
                        int cdRemaining = sec.getInt(key + ".cooldown_remaining_seconds", 0);

                        ActiveNode node = registerOrUpdateLoadedNode(key, typeId, loc, currentCharges, maxCharges, status, lastInteraction, cdRemaining);
                        migratedSnapshots.add(NodeSnapshot.from(node));
                    }
                }
                boolean migratedOk = migratedSnapshots.isEmpty() || writeNodesBatch(conn, db, migratedSnapshots);
                if (migratedOk) {
                    db.markMigratedFile(legacyFile);
                }
            }

            activeNodes.keySet().removeIf(id -> {
                if (!keysInDb.contains(id)) {
                    plugin.getInteractionController().removeInteraction(id);
                    plugin.getHologramManager().removeHologram(id);
                    return true;
                }
                return false;
            });

            plugin.getLogger().info("Loaded " + activeNodes.size() + " active gathering nodes from database.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load active nodes from database: " + e.getMessage());
        }
    }

    private ActiveNode registerOrUpdateLoadedNode(String key, String typeId, Location loc, int currentCharges, int maxCharges,
                                                  NodeStatus status, long lastInteraction, int cdRemaining) {
        ActiveNode existing = activeNodes.get(key);
        if (existing != null) {
            existing.setLocation(loc);
            existing.setCurrentCharges(currentCharges);
            existing.setMaxCharges(maxCharges);
            existing.setStatus(status);
            existing.setLastInteractionMillis(lastInteraction);
            existing.setCooldownRemainingSeconds(cdRemaining);
            return existing;
        }
        ActiveNode node = new ActiveNode(key, typeId, loc, currentCharges, maxCharges, status, lastInteraction, cdRemaining);
        activeNodes.put(key, node);
        return node;
    }

    public void respawnAllNodes() {
        for (ActiveNode node : activeNodes.values()) {
            if (node.getLocation() != null && node.getLocation().getWorld() != null) {
                if (!node.getLocation().getChunk().isLoaded()) {
                    node.getLocation().getChunk().load();
                }
            }
            plugin.getInteractionController().spawnInteraction(node);
            plugin.getHologramManager().updateHologram(node, 0.0, null);
        }
    }

    public ActiveNode createNode(String typeId, Location location, String customId) {
        NodeType type = plugin.getNodeConfigManager().getType(typeId);
        if (type == null) return null;

        String id = customId != null && !customId.isEmpty() ? customId : typeId + "_" + UUID.randomUUID().toString().substring(0, 8);
        int charges = type.rollRandomCharges();

        ActiveNode node = new ActiveNode(id, typeId, location, charges);
        activeNodes.put(id, node);

        // Spawn visual & hitbox
        plugin.getInteractionController().spawnInteraction(node);
        plugin.getHologramManager().updateHologram(node, 0.0, null);

        saveNode(node);
        return node;
    }

    public boolean moveNode(String id, Location newLocation) {
        ActiveNode node = activeNodes.get(id);
        if (node == null || newLocation == null || newLocation.getWorld() == null) return false;

        // Cancel any active gathering session on this node
        if (node.getActiveGatherer() != null) {
            Player p = Bukkit.getPlayer(node.getActiveGatherer());
            if (p != null) {
                plugin.getGatherManager().cancelGathering(p, CancelReason.PLUGIN);
            }
        }

        // Update location in memory
        node.setLocation(newLocation);

        // Move or respawn interaction entity
        plugin.getInteractionController().moveInteraction(id, newLocation);

        // Update / teleport hologram to new location
        plugin.getHologramManager().updateHologram(node, 0.0, null);

        saveNode(node);
        return true;
    }

    public boolean removeNode(String id) {
        ActiveNode node = activeNodes.remove(id);
        if (node != null) {
            plugin.getInteractionController().removeInteraction(id);
            plugin.getHologramManager().removeHologram(id);
            if (node.getActiveGatherer() != null) {
                Player p = Bukkit.getPlayer(node.getActiveGatherer());
                if (p != null) {
                    plugin.getGatherManager().cancelGathering(p, CancelReason.PLUGIN);
                }
            }
            DatabaseManager db = plugin.getDatabaseManager();
            if (db != null) {
                db.executeAsync(() -> {
                    try (Connection conn = db.getConnection();
                         PreparedStatement stmt = conn.prepareStatement("DELETE FROM cyayo_gathering_nodes WHERE node_id = ?")) {
                        stmt.setString(1, id);
                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Failed to delete gathering node '" + id + "' from database: " + e.getMessage());
                    }
                });
            }
            return true;
        }
        return false;
    }

    public boolean resetNode(String id) {
        ActiveNode node = activeNodes.get(id);
        if (node != null) {
            NodeType type = plugin.getNodeConfigManager().getType(node.getTypeId());
            int newCharges = type != null ? type.rollRandomCharges() : 10;
            node.resetCharges(newCharges);
            plugin.getGatherManager().releaseLock(node);
            plugin.getHologramManager().updateHologram(node, 0.0, null);
            saveNode(node);
            return true;
        }
        return false;
    }

    public Optional<ActiveNode> getNearestNode(Location loc, double maxDistance) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();
        ActiveNode nearest = null;
        double bestDistSq = maxDistance * maxDistance;

        for (ActiveNode node : activeNodes.values()) {
            if (node.getLocation() != null && node.getLocation().getWorld().equals(loc.getWorld())) {
                double distSq = node.getLocation().distanceSquared(loc);
                if (distSq <= bestDistSq) {
                    bestDistSq = distSq;
                    nearest = node;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    public void saveNode(ActiveNode node) {
        if (node == null) return;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;
        NodeSnapshot snap = NodeSnapshot.from(node);
        db.executeAsync(() -> writeNodesBatch(List.of(snap)));
    }

    public void save() {
        List<NodeSnapshot> snapshots = captureAllSnapshots();
        if (snapshots.isEmpty()) return;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> writeNodesBatch(snapshots));
        }
    }

    public void saveSync() {
        List<NodeSnapshot> snapshots = captureAllSnapshots();
        if (!snapshots.isEmpty()) {
            writeNodesBatch(snapshots);
        }
    }

    private List<NodeSnapshot> captureAllSnapshots() {
        if (activeNodes.isEmpty()) return List.of();
        List<NodeSnapshot> snapshots = new ArrayList<>(activeNodes.size());
        for (ActiveNode node : activeNodes.values()) {
            snapshots.add(NodeSnapshot.from(node));
        }
        return snapshots;
    }

    private boolean writeNodesBatch(List<NodeSnapshot> snapshots) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || snapshots.isEmpty()) return true;
        try (Connection conn = db.getConnection()) {
            return writeNodesBatch(conn, db, snapshots);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save gathering nodes to database: " + e.getMessage());
            return false;
        }
    }

    private boolean writeNodesBatch(Connection conn, DatabaseManager db, List<NodeSnapshot> snapshots) {
        if (conn == null || db == null || snapshots.isEmpty()) return true;

        String upsertSql = db.isMySQL()
                ? "INSERT INTO cyayo_gathering_nodes (node_id, type_id, world, x, y, z, yaw, pitch, current_charges, max_charges, status, last_interaction_millis, cooldown_remaining_seconds) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                  "type_id = VALUES(type_id), world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z), yaw = VALUES(yaw), pitch = VALUES(pitch), " +
                  "current_charges = VALUES(current_charges), max_charges = VALUES(max_charges), status = VALUES(status), " +
                  "last_interaction_millis = VALUES(last_interaction_millis), cooldown_remaining_seconds = VALUES(cooldown_remaining_seconds)"
                : "INSERT INTO cyayo_gathering_nodes (node_id, type_id, world, x, y, z, yaw, pitch, current_charges, max_charges, status, last_interaction_millis, cooldown_remaining_seconds) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(node_id) DO UPDATE SET " +
                  "type_id = excluded.type_id, world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch, " +
                  "current_charges = excluded.current_charges, max_charges = excluded.max_charges, status = excluded.status, " +
                  "last_interaction_millis = excluded.last_interaction_millis, cooldown_remaining_seconds = excluded.cooldown_remaining_seconds";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                for (NodeSnapshot snap : snapshots) {
                    stmt.setString(1, snap.id);
                    stmt.setString(2, snap.typeId);
                    stmt.setString(3, snap.world);
                    stmt.setDouble(4, snap.x);
                    stmt.setDouble(5, snap.y);
                    stmt.setDouble(6, snap.z);
                    stmt.setFloat(7, snap.yaw);
                    stmt.setFloat(8, snap.pitch);
                    stmt.setInt(9, snap.currentCharges);
                    stmt.setInt(10, snap.maxCharges);
                    stmt.setString(11, snap.status);
                    stmt.setLong(12, snap.lastInteractionMillis);
                    stmt.setInt(13, snap.cooldownRemainingSeconds);
                    stmt.addBatch();
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
            plugin.getLogger().warning("Failed to save gathering nodes to database: " + e.getMessage());
            return false;
        }
    }

    public ActiveNode getNode(String id) {
        return activeNodes.get(id);
    }

    public Collection<ActiveNode> getAllNodes() {
        return Collections.unmodifiableCollection(activeNodes.values());
    }
}

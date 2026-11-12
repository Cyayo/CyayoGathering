package id.cyayo.gathering.entity;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NodeInteractionController {

    private final CyayoGathering plugin;
    private final NamespacedKey nodeKey;
    private final Map<String, UUID> activeEntities = new ConcurrentHashMap<>();

    public NodeInteractionController(CyayoGathering plugin) {
        this.plugin = plugin;
        this.nodeKey = new NamespacedKey(plugin, "node_id");
    }

    public Interaction spawnInteraction(ActiveNode node) {
        if (node == null || node.getLocation() == null || node.getLocation().getWorld() == null) return null;

        World world = node.getLocation().getWorld();
        Location loc = node.getLocation();

        // Check if already spawned
        UUID existingUUID = activeEntities.get(node.getId());
        if (existingUUID != null) {
            Entity ent = Bukkit.getEntity(existingUUID);
            if (ent instanceof Interaction interaction && ent.isValid()) {
                NodeType type = plugin.getNodeConfigManager().getType(node.getTypeId());
                if (type != null) {
                    interaction.setInteractionWidth((float) type.getHitboxWidth());
                    interaction.setInteractionHeight((float) type.getHitboxHeight());
                }
                return interaction;
            }
        }

        NodeType type = plugin.getNodeConfigManager().getType(node.getTypeId());
        float width = type != null ? (float) type.getHitboxWidth() : 1.2f;
        float height = type != null ? (float) type.getHitboxHeight() : 1.8f;

        Interaction interaction = (Interaction) world.spawnEntity(loc, EntityType.INTERACTION);
        interaction.setInteractionWidth(width);
        interaction.setInteractionHeight(height);
        interaction.setResponsive(true);
        interaction.getPersistentDataContainer().set(nodeKey, PersistentDataType.STRING, node.getId());

        activeEntities.put(node.getId(), interaction.getUniqueId());
        return interaction;
    }

    public void moveInteraction(String nodeId, Location newLoc) {
        if (nodeId == null || newLoc == null || newLoc.getWorld() == null) return;
        if (!newLoc.getChunk().isLoaded()) {
            newLoc.getChunk().load();
        }

        UUID uuid = activeEntities.get(nodeId);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && e.isValid()) {
                e.teleport(newLoc);
                return;
            }
        }

        // If not found or invalid, spawn fresh at new location
        ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
        if (node != null) {
            spawnInteraction(node);
        }
    }

    public void removeInteraction(String nodeId) {
        UUID uuid = activeEntities.remove(nodeId);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.remove();
            }
        }
    }

    public String getNodeId(Entity entity) {
        if (!(entity instanceof Interaction interaction)) return null;
        return interaction.getPersistentDataContainer().get(nodeKey, PersistentDataType.STRING);
    }

    public void pruneOrphanEntitiesInChunk(Chunk chunk) {
        if (chunk == null || !chunk.isLoaded()) return;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Interaction interaction) {
                String id = interaction.getPersistentDataContainer().get(nodeKey, PersistentDataType.STRING);
                if (id != null) {
                    ActiveNode node = plugin.getNodeDataManager().getNode(id);
                    if (node == null) {
                        interaction.remove();
                    }
                }
            }
        }
    }

    public void cleanup() {
        for (UUID uuid : activeEntities.values()) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.remove();
            }
        }
        activeEntities.clear();
    }
}

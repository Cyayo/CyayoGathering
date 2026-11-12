package id.cyayo.gathering.manager;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.NodeType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NodeConfigManager {

    private final CyayoGathering plugin;
    private final Map<String, NodeType> nodeTypes = new HashMap<>();

    public NodeConfigManager(CyayoGathering plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        nodeTypes.clear();
        File folder = new File(plugin.getDataFolder(), "nodes");
        if (!folder.exists()) {
            folder.mkdirs();
            plugin.saveResource("nodes/mining_agate.yml", false);
            plugin.saveResource("nodes/woodcutting_oak.yml", false);
            plugin.saveResource("nodes/fishing_mystic.yml", false);
            plugin.saveResource("nodes/farming_wheat.yml", false);
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String id = config.getString("id", file.getName().replace(".yml", "")).toLowerCase();
                    NodeType type = new NodeType(id, config);
                    nodeTypes.put(id, type);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load node config '" + file.getName() + "': " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + nodeTypes.size() + " gathering node templates.");
    }

    public NodeType getType(String id) {
        if (id == null) return null;
        return nodeTypes.get(id.toLowerCase());
    }

    public Collection<NodeType> getAllTypes() {
        return Collections.unmodifiableCollection(nodeTypes.values());
    }

    public boolean hasType(String id) {
        return id != null && nodeTypes.containsKey(id.toLowerCase());
    }
}

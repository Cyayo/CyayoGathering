package id.cyayo.gathering.manager;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.api.event.NodeRespawnEvent;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeStatus;
import id.cyayo.gathering.model.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class NodeLifecycleManager {

    private final CyayoGathering plugin;
    private BukkitTask tickerTask;

    private long lastAutoSaveMillis = System.currentTimeMillis();

    public NodeLifecycleManager(CyayoGathering plugin) {
        this.plugin = plugin;
        startTicker();
    }

    private void startTicker() {
        // Runs once every second (20 ticks)
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (ActiveNode node : plugin.getNodeDataManager().getAllNodes()) {
                NodeType type = plugin.getNodeConfigManager().getType(node.getTypeId());
                if (type == null) continue;

                // 1. Handle Cooldown
                if (node.getStatus() == NodeStatus.COOLDOWN) {
                    int remaining = node.getCooldownRemainingSeconds() - 1;
                    if (remaining <= 0) {
                        // Respawn
                        int newCharges = type.rollRandomCharges();
                        node.resetCharges(newCharges);
                        plugin.getHologramManager().updateHologram(node, 0.0, null);

                        Bukkit.getPluginManager().callEvent(new NodeRespawnEvent(node, newCharges));
                        plugin.getNodeDataManager().saveNode(node);
                    } else {
                        node.setCooldownRemainingSeconds(remaining);
                        plugin.getHologramManager().updateHologram(node, 0.0, null);
                    }
                    continue;
                }

                // 2. Handle Inactivity Reset
                if (node.getStatus() == NodeStatus.READY && node.getCurrentCharges() < node.getMaxCharges()) {
                    long elapsedSeconds = (now - node.getLastInteractionMillis()) / 1000L;
                    if (elapsedSeconds >= type.getInactivityResetSeconds()) {
                        // Reset charges to fresh random
                        int freshCharges = type.rollRandomCharges();
                        node.resetCharges(freshCharges);
                        plugin.getHologramManager().updateHologram(node, 0.0, null);
                        plugin.getNodeDataManager().saveNode(node);
                    }
                }
            }

            // 3. Periodic autosave every 5 minutes (300 seconds)
            if (now - lastAutoSaveMillis >= 300_000L) {
                lastAutoSaveMillis = now;
                plugin.getNodeDataManager().save();
            }
        }, 20L, 20L);
    }

    public void cleanup() {
        if (tickerTask != null) {
            tickerTask.cancel();
        }
    }
}

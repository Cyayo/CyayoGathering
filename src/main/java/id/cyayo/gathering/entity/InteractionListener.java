package id.cyayo.gathering.entity;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.api.event.NodeGatherCancelEvent;
import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public class InteractionListener implements Listener {

    private final CyayoGathering plugin;

    public InteractionListener(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 1. Attack Interaction Entity to Start Gathering (Left-Click)
        if (event.getEntity() instanceof Interaction interaction) {
            String nodeId = plugin.getInteractionController().getNodeId(interaction);
            if (nodeId != null) {
                event.setCancelled(true);
                if (event.getDamager() instanceof Player player) {
                    ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
                    if (node != null) {
                        plugin.getGatherManager().startGathering(player, node);
                    }
                }
                return;
            }
        }

        // 2. Combat Damage Interruption: If player attacks or gets attacked
        if (plugin.getConfig().getBoolean("settings.cancel_on_damage", true)) {
            if (event.getEntity() instanceof Player victim && plugin.getGatherManager().isGathering(victim)) {
                plugin.getGatherManager().cancelGathering(victim, NodeGatherCancelEvent.CancelReason.DAMAGE);
            }
            if (event.getDamager() instanceof Player attacker && plugin.getGatherManager().isGathering(attacker)) {
                plugin.getGatherManager().cancelGathering(attacker, NodeGatherCancelEvent.CancelReason.ACTION);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("settings.cancel_on_damage", true)) return;
        if (event.getEntity() instanceof Player player && plugin.getGatherManager().isGathering(player)) {
            plugin.getGatherManager().cancelGathering(player, NodeGatherCancelEvent.CancelReason.DAMAGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Right-Click on Interaction Entity
        if (event.getRightClicked() instanceof Interaction interaction) {
            String nodeId = plugin.getInteractionController().getNodeId(interaction);
            if (nodeId != null) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
                if (node != null) {
                    plugin.getGatherManager().startGathering(player, node);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGatherManager().isGathering(player)) {
            plugin.getGatherManager().cancelGathering(player, NodeGatherCancelEvent.CancelReason.ITEM_SWITCH);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        plugin.getHologramManager().showHologramsToPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGatherManager().isGathering(player)) {
            plugin.getGatherManager().cancelGathering(player, NodeGatherCancelEvent.CancelReason.LOGOUT);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getInteractionController().pruneOrphanEntitiesInChunk(event.getChunk());
    }
}

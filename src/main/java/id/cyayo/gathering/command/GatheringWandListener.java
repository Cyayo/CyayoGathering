package id.cyayo.gathering.command;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.util.ColorUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GatheringWandListener implements Listener {

    private final CyayoGathering plugin;
    private final NamespacedKey wandKey;

    public GatheringWandListener(CyayoGathering plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, GatheringCommand.WAND_KEY);
    }

    private boolean isHoldingWand(Player player) {
        if (!player.hasPermission("cyayogathering.admin")) return false;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.hasItemMeta()) return false;
        return hand.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWandHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof Interaction interaction) {
            if (isHoldingWand(player)) {
                event.setCancelled(true);
                String nodeId = plugin.getInteractionController().getNodeId(interaction);
                if (nodeId == null) return;

                ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
                if (node == null) return;

                if (player.isSneaking()) {
                    // Shift + Left Click: Instant Remove
                    plugin.getNodeDataManager().removeNode(nodeId);
                    String msg = plugin.getMessage("admin.node_removed").replace("{id}", nodeId);
                    plugin.sendPrefixedMessage(player, msg);
                } else {
                    // Left Click: Inspect Info
                    String msg = plugin.getMessage("admin.wand_info")
                            .replace("{id}", node.getId())
                            .replace("{type}", node.getTypeId())
                            .replace("{charges}", String.valueOf(node.getCurrentCharges()))
                            .replace("{max_charges}", String.valueOf(node.getMaxCharges()))
                            .replace("{status}", node.getStatus().name());
                    plugin.sendPrefixedMessage(player, msg);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWandRightClick(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getRightClicked() instanceof Interaction interaction) {
            if (isHoldingWand(player)) {
                event.setCancelled(true);
                String nodeId = plugin.getInteractionController().getNodeId(interaction);
                if (nodeId == null) return;

                ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
                if (node == null) return;

                // Right click: Inspect Info
                String msg = plugin.getMessage("admin.wand_info")
                        .replace("{id}", node.getId())
                        .replace("{type}", node.getTypeId())
                        .replace("{charges}", String.valueOf(node.getCurrentCharges()))
                        .replace("{max_charges}", String.valueOf(node.getMaxCharges()))
                        .replace("{status}", node.getStatus().name());
                plugin.sendPrefixedMessage(player, msg);
            }
        }
    }
}

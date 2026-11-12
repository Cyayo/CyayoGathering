package id.cyayo.gathering.api.event;

import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class NodeGatherCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ActiveNode node;
    private final String nodeTypeId;
    private final String category;
    private final ItemStack droppedItem;
    private final int remainingCharges;

    public NodeGatherCompleteEvent(Player player, ActiveNode node, String nodeTypeId, String category, ItemStack droppedItem, int remainingCharges) {
        this.player = player;
        this.node = node;
        this.nodeTypeId = nodeTypeId;
        this.category = category;
        this.droppedItem = droppedItem != null ? droppedItem.clone() : null;
        this.remainingCharges = remainingCharges;
    }

    public Player getPlayer() { return player; }
    public ActiveNode getNode() { return node; }
    public String getNodeTypeId() { return nodeTypeId; }
    public String getCategory() { return category; }
    public ItemStack getDroppedItem() { return droppedItem; }
    public int getRemainingCharges() { return remainingCharges; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

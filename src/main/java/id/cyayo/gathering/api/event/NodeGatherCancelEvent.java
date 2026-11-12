package id.cyayo.gathering.api.event;

import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class NodeGatherCancelEvent extends Event {

    public enum CancelReason {
        DISTANCE,
        DAMAGE,
        ACTION,
        ITEM_SWITCH,
        LOGOUT,
        PLUGIN
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ActiveNode node;
    private final CancelReason reason;

    public NodeGatherCancelEvent(Player player, ActiveNode node, CancelReason reason) {
        this.player = player;
        this.node = node;
        this.reason = reason;
    }

    public Player getPlayer() { return player; }
    public ActiveNode getNode() { return node; }
    public CancelReason getReason() { return reason; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

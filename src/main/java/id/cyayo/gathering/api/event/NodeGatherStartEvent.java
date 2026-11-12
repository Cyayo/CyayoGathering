package id.cyayo.gathering.api.event;

import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class NodeGatherStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final ActiveNode node;

    public NodeGatherStartEvent(Player player, ActiveNode node) {
        this.player = player;
        this.node = node;
    }

    public Player getPlayer() { return player; }
    public ActiveNode getNode() { return node; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

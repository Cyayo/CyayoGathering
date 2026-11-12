package id.cyayo.gathering.api.event;

import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class NodeRespawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveNode node;
    private final int newCharges;

    public NodeRespawnEvent(ActiveNode node, int newCharges) {
        this.node = node;
        this.newCharges = newCharges;
    }

    public ActiveNode getNode() { return node; }
    public int getNewCharges() { return newCharges; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

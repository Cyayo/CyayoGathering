package id.cyayo.gathering.api.event;

import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class NodeDepletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveNode node;
    private final Player lastGatherer;
    private final int cooldownSeconds;

    public NodeDepletedEvent(ActiveNode node, Player lastGatherer, int cooldownSeconds) {
        this.node = node;
        this.lastGatherer = lastGatherer;
        this.cooldownSeconds = cooldownSeconds;
    }

    public ActiveNode getNode() { return node; }
    public Player getLastGatherer() { return lastGatherer; }
    public int getCooldownSeconds() { return cooldownSeconds; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

package id.cyayo.gathering.api.event;

import id.cyayo.gathering.model.ActiveNode;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class NodeGatherTickEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ActiveNode node;
    private final double progress; // 0.0 to 1.0

    public NodeGatherTickEvent(Player player, ActiveNode node, double progress) {
        this.player = player;
        this.node = node;
        this.progress = progress;
    }

    public Player getPlayer() { return player; }
    public ActiveNode getNode() { return node; }
    public double getProgress() { return progress; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

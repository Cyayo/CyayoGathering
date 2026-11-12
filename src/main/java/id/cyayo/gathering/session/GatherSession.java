package id.cyayo.gathering.session;

import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import org.bukkit.entity.Player;

public class GatherSession {

    private final Player player;
    private final ActiveNode node;
    private final NodeType type;
    private final double totalTicks;
    private double currentTicks;
    private boolean completed = false;

    private long elapsedTicks = 0;
    private long lastSwingTick = -999;
    private long lastParticleTick = -999;
    private long lastSoundTick = -999;

    public GatherSession(Player player, ActiveNode node, NodeType type, double gatheringSpeed) {
        this.player = player;
        this.node = node;
        this.type = type;

        double baseSeconds = type.getBaseGatherTimeSeconds();
        double speedMult = 1.0 + Math.max(0.0, gatheringSpeed) / 100.0;
        double actualSeconds = Math.max(0.4, baseSeconds / speedMult);
        this.totalTicks = actualSeconds * 20.0;
        this.currentTicks = 0.0;
        this.elapsedTicks = 0;
    }

    public void tick(double deltaTicks) {
        this.currentTicks += deltaTicks;
        this.elapsedTicks += (long) Math.max(1, Math.round(deltaTicks));
        if (currentTicks >= totalTicks) {
            this.completed = true;
        }
    }

    public boolean shouldSwing(int intervalTicks) {
        if (intervalTicks <= 0) return false;
        if (lastSwingTick < 0 || (elapsedTicks - lastSwingTick) >= intervalTicks) {
            lastSwingTick = elapsedTicks;
            return true;
        }
        return false;
    }

    public boolean shouldSpawnParticle(int intervalTicks) {
        if (intervalTicks <= 0) return false;
        if (lastParticleTick < 0 || (elapsedTicks - lastParticleTick) >= intervalTicks) {
            lastParticleTick = elapsedTicks;
            return true;
        }
        return false;
    }

    public boolean shouldPlaySound(int intervalTicks) {
        if (intervalTicks <= 0) return false;
        if (lastSoundTick < 0 || (elapsedTicks - lastSoundTick) >= intervalTicks) {
            lastSoundTick = elapsedTicks;
            return true;
        }
        return false;
    }

    public void resetCycle() {
        this.currentTicks = 0.0;
        this.completed = false;
        this.elapsedTicks = 0;
        this.lastSwingTick = -999;
        this.lastParticleTick = -999;
        this.lastSoundTick = -999;
    }

    public double getProgress() {
        if (totalTicks <= 0) return 1.0;
        return Math.min(1.0, currentTicks / totalTicks);
    }

    public Player getPlayer() { return player; }
    public ActiveNode getNode() { return node; }
    public NodeType getType() { return type; }
    public boolean isCompleted() { return completed; }
}

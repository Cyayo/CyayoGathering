package id.cyayo.gathering.model;

import org.bukkit.Location;

import java.util.UUID;

public class ActiveNode {

    private final String id;
    private final String typeId;
    private Location location;

    private int currentCharges;
    private int maxCharges;
    private NodeStatus status;
    private long lastInteractionMillis;
    private int cooldownRemainingSeconds;
    private UUID activeGatherer;

    public ActiveNode(String id, String typeId, Location location, int maxCharges) {
        this.id = id;
        this.typeId = typeId;
        this.location = location.clone();
        this.maxCharges = maxCharges;
        this.currentCharges = maxCharges;
        this.status = NodeStatus.READY;
        this.lastInteractionMillis = System.currentTimeMillis();
        this.cooldownRemainingSeconds = 0;
        this.activeGatherer = null;
    }

    public ActiveNode(String id, String typeId, Location location, int currentCharges, int maxCharges, NodeStatus status, long lastInteractionMillis, int cooldownRemainingSeconds) {
        this.id = id;
        this.typeId = typeId;
        this.location = location.clone();
        this.currentCharges = currentCharges;
        this.maxCharges = maxCharges;
        this.status = status;
        this.lastInteractionMillis = lastInteractionMillis;
        this.cooldownRemainingSeconds = cooldownRemainingSeconds;
        this.activeGatherer = null;
    }

    public void touch() {
        this.lastInteractionMillis = System.currentTimeMillis();
    }

    public void decrementCharge() {
        if (currentCharges > 0) {
            currentCharges--;
        }
    }

    public boolean isDepleted() {
        return currentCharges <= 0;
    }

    public void startCooldown(int seconds) {
        this.status = NodeStatus.COOLDOWN;
        this.cooldownRemainingSeconds = seconds;
        this.activeGatherer = null;
        touch();
    }

    public void resetCharges(int newCharges) {
        this.maxCharges = newCharges;
        this.currentCharges = newCharges;
        this.status = NodeStatus.READY;
        this.cooldownRemainingSeconds = 0;
        this.activeGatherer = null;
        touch();
    }

    public String getId() { return id; }
    public String getTypeId() { return typeId; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location != null ? location.clone() : null; }
    public int getCurrentCharges() { return currentCharges; }
    public void setCurrentCharges(int charges) { this.currentCharges = charges; }
    public int getMaxCharges() { return maxCharges; }
    public void setMaxCharges(int maxCharges) { this.maxCharges = maxCharges; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public long getLastInteractionMillis() { return lastInteractionMillis; }
    public void setLastInteractionMillis(long millis) { this.lastInteractionMillis = millis; }
    public int getCooldownRemainingSeconds() { return cooldownRemainingSeconds; }
    public void setCooldownRemainingSeconds(int seconds) { this.cooldownRemainingSeconds = seconds; }
    public UUID getActiveGatherer() { return activeGatherer; }
    public void setActiveGatherer(UUID activeGatherer) { this.activeGatherer = activeGatherer; }
}

package id.cyayo.gathering.model;

import org.bukkit.Material;

public class DropItem {

    public enum DropType {
        VANILLA,
        MMOITEMS,
        COMMAND
    }

    private final DropType type;
    private final Material material;
    private final String mmoType;
    private final String mmoId;
    private final int base;
    private final int amount;
    private final String broadcast;
    private final String command;

    public DropItem(DropType type, Material material, String mmoType, String mmoId, int base, int amount, String broadcast, String command) {
        this.type = type;
        this.material = material;
        this.mmoType = mmoType;
        this.mmoId = mmoId;
        this.base = Math.max(1, base);
        this.amount = Math.max(1, amount);
        this.broadcast = broadcast;
        this.command = command;
    }

    public DropType getType() { return type; }
    public Material getMaterial() { return material; }
    public String getMmoType() { return mmoType; }
    public String getMmoId() { return mmoId; }
    public int getBase() { return base; }
    public int getAmount() { return amount; }
    public String getBroadcast() { return broadcast; }
    public String getCommand() { return command; }
}

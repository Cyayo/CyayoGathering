package id.cyayo.gathering.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCollectItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualItem {

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(2000000);

    private final int entityId;
    private final UUID entityUUID;
    private final UUID ownerUUID;
    private final org.bukkit.inventory.ItemStack bukkitItemStack;
    private final Location location;
    private final long spawnTime;
    private final long despawnTime;
    private final long canPickupTime;
    private boolean alive = true;

    public VirtualItem(UUID ownerUUID, org.bukkit.inventory.ItemStack itemStack, Location location, int despawnSeconds, long pickupDelayMillis) {
        this.entityId = ENTITY_ID_COUNTER.incrementAndGet();
        this.entityUUID = UUID.randomUUID();
        this.ownerUUID = ownerUUID;
        this.bukkitItemStack = itemStack;
        this.location = location.clone();
        this.spawnTime = System.currentTimeMillis();
        this.despawnTime = spawnTime + (despawnSeconds * 1000L);
        this.canPickupTime = spawnTime + pickupDelayMillis;
    }

    public boolean canBePickedUp() {
        return System.currentTimeMillis() >= canPickupTime;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= despawnTime;
    }

    public void spawn(Player player) {
        if (!player.getUniqueId().equals(ownerUUID)) return;
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        // 1. Spawn Packet
        WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(entityUUID),
                EntityTypes.ITEM,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0f, 0f, 0f, 1,
                Optional.empty()
        );
        user.sendPacket(spawnPacket);

        // 2. Metadata Packet
        ItemStack peItem = SpigotConversionUtil.fromBukkitItemStack(bukkitItemStack);
        List<EntityData> dataList = new ArrayList<>();
        // Index 8 for Item Entity ItemStack in 1.20+
        dataList.add(new EntityData(8, EntityDataTypes.ITEMSTACK, peItem));
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(entityId, dataList);
        user.sendPacket(metaPacket);

        // 3. Gentle pop velocity packet towards player
        org.bukkit.util.Vector dir = player.getLocation().toVector().subtract(location.toVector()).setY(0);
        if (dir.lengthSquared() > 0.001) {
            dir.normalize();
        } else {
            dir = new org.bukkit.util.Vector(0.5, 0, 0);
        }

        double speed = ThreadLocalRandom.current().nextDouble(0.12, 0.22);
        double vx = dir.getX() * speed + ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
        double vy = ThreadLocalRandom.current().nextDouble(0.2, 0.3);
        double vz = dir.getZ() * speed + ThreadLocalRandom.current().nextDouble(-0.04, 0.04);

        WrapperPlayServerEntityVelocity velPacket = new WrapperPlayServerEntityVelocity(
                entityId,
                new Vector3d(vx, vy, vz)
        );
        user.sendPacket(velPacket);
    }

    public void collectAnimation(Player player) {
        if (!player.getUniqueId().equals(ownerUUID)) return;
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        WrapperPlayServerCollectItem collect = new WrapperPlayServerCollectItem(entityId, player.getEntityId(), bukkitItemStack.getAmount());
        user.sendPacket(collect);
    }

    public void destroy(Player player) {
        alive = false;
        if (player == null || !player.isOnline()) return;
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(new int[]{entityId});
        user.sendPacket(destroy);
    }

    public int getEntityId() { return entityId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public org.bukkit.inventory.ItemStack getBukkitItemStack() { return bukkitItemStack; }
    public Location getLocation() { return location; }
    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
}

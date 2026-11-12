package id.cyayo.gathering.packet;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InstancedDropManager {

    private final CyayoGathering plugin;
    private final List<VirtualItem> activeItems = new CopyOnWriteArrayList<>();
    private final Map<UUID, Long> fullInvMsgCooldown = new ConcurrentHashMap<>();
    private BukkitTask pickupTask;

    public InstancedDropManager(CyayoGathering plugin) {
        this.plugin = plugin;
        startPickupTask();
    }

    public void spawnDrop(Player player, ItemStack item, Location location) {
        if (player == null || item == null || item.getType().isAir()) return;

        int despawnSeconds = plugin.getConfig().getInt("settings.drop_despawn_seconds", 60);
        long pickupDelay = plugin.getConfig().getLong("settings.drop_pickup_delay_millis", 600);

        VirtualItem vItem = new VirtualItem(player.getUniqueId(), item, location, despawnSeconds, pickupDelay);
        activeItems.add(vItem);
        vItem.spawn(player);
    }

    private void startPickupTask() {
        // Run every 2 ticks (100ms) for high responsiveness
        pickupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeItems.isEmpty()) return;

            double radius = plugin.getConfig().getDouble("settings.drop_pickup_radius", 2.5);
            double radiusSquared = radius * radius;

            Iterator<VirtualItem> iterator = activeItems.iterator();
            while (iterator.hasNext()) {
                VirtualItem vItem = iterator.next();
                if (!vItem.isAlive()) {
                    activeItems.remove(vItem);
                    continue;
                }

                if (vItem.isExpired()) {
                    Player owner = Bukkit.getPlayer(vItem.getOwnerUUID());
                    vItem.destroy(owner);
                    activeItems.remove(vItem);
                    continue;
                }

                if (!vItem.canBePickedUp()) {
                    continue;
                }

                Player player = Bukkit.getPlayer(vItem.getOwnerUUID());
                if (player == null || !player.isOnline()) continue;

                Location pLoc = player.getLocation();
                Location iLoc = vItem.getLocation();

                if (!pLoc.getWorld().equals(iLoc.getWorld())) continue;

                double dx = pLoc.getX() - iLoc.getX();
                double dy = pLoc.getY() - iLoc.getY();
                double dz = pLoc.getZ() - iLoc.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq <= radiusSquared || (Math.abs(dy) <= 2.2 && (dx * dx + dz * dz) <= radiusSquared)) {
                    ItemStack itemToAdd = vItem.getBukkitItemStack().clone();
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemToAdd);

                    if (leftover.isEmpty()) {
                        // Completely picked up
                        vItem.setAlive(false);
                        vItem.collectAnimation(player);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            vItem.destroy(player);
                        }, 3L);
                        activeItems.remove(vItem);

                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    } else {
                        // Inventory full or partially full
                        int originalAmount = itemToAdd.getAmount();
                        int remainingAmount = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                        int added = originalAmount - remainingAmount;

                        if (added > 0) {
                            vItem.collectAnimation(player);
                            vItem.getBukkitItemStack().setAmount(remainingAmount);
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                        }

                        // Notify player of full inventory with cooldown of 3 seconds
                        long now = System.currentTimeMillis();
                        long lastMsg = fullInvMsgCooldown.getOrDefault(player.getUniqueId(), 0L);
                        if (now - lastMsg > 3000L) {
                            fullInvMsgCooldown.put(player.getUniqueId(), now);
                            String msg = plugin.getMessage("messages.inventory_full_drop");
                            plugin.sendPrefixedMessage(player, msg);
                        }
                    }
                }
            }
        }, 2L, 2L);
    }

    public void cleanup() {
        if (pickupTask != null) {
            pickupTask.cancel();
        }
        for (VirtualItem vItem : activeItems) {
            Player p = Bukkit.getPlayer(vItem.getOwnerUUID());
            vItem.destroy(p);
        }
        activeItems.clear();
    }
}

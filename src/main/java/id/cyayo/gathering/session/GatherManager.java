package id.cyayo.gathering.session;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.api.event.*;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeStatus;
import id.cyayo.gathering.model.NodeType;
import id.cyayo.gathering.rng.GatheringRngEngine;
import id.cyayo.gathering.util.ColorUtils;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GatherManager {

    private final CyayoGathering plugin;
    private final Map<UUID, GatherSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> nodeLocks = new ConcurrentHashMap<>();
    private BukkitTask gatheringTask;

    public GatherManager(CyayoGathering plugin) {
        this.plugin = plugin;
        startGatheringLoop();
    }

    public boolean isGathering(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public Optional<GatherSession> getSession(Player player) {
        return Optional.ofNullable(activeSessions.get(player.getUniqueId()));
    }

    public void startGathering(Player player, ActiveNode node) {
        if (player == null || node == null) return;

        // 0. Distance check: Reject if player interacts from too far away
        double maxDist = plugin.getConfig().getDouble("settings.max_gather_distance", 2.8);
        Location pLoc = player.getLocation();
        Location nLoc = node.getLocation();
        if (pLoc.getWorld() == null || nLoc.getWorld() == null || !pLoc.getWorld().equals(nLoc.getWorld()) || pLoc.distance(nLoc) > maxDist) {
            String msg = plugin.getMessage("messages.too_far", "<red>You are too far away from this gathering node!");
            plugin.sendPrefixedMessage(player, msg);
            return;
        }

        // 1. Single node gathering check: Player can only gather 1 node at a time
        if (isGathering(player)) {
            plugin.sendPrefixedMessage(player, plugin.getMessage("messages.already_gathering"));
            return;
        }

        // 2. Node lock check: Is node currently locked by another player?
        UUID lockHolder = nodeLocks.get(node.getId());
        if (lockHolder != null && !lockHolder.equals(player.getUniqueId())) {
            Player holderPlayer = Bukkit.getPlayer(lockHolder);
            String holderName = holderPlayer != null ? holderPlayer.getName() : "Someone";
            String msg = plugin.getMessage("messages.node_locked").replace("{player}", holderName);
            plugin.sendPrefixedMessage(player, msg);
            return;
        }

        // 3. Node status check
        if (node.getStatus() == NodeStatus.COOLDOWN) {
            String msg = plugin.getMessage("messages.node_cooldown").replace("{time}", String.valueOf(node.getCooldownRemainingSeconds()));
            plugin.sendPrefixedMessage(player, msg);
            return;
        }

        NodeType type = plugin.getNodeConfigManager().getType(node.getTypeId());
        if (type == null) return;

        // 4. Level requirement check
        if (plugin.getLevelManager() != null && plugin.getLevelManager().isEnabled()) {
            int playerLevel = plugin.getLevelManager().getLevel(player.getUniqueId(), type.getCategory());
            if (playerLevel < type.getRequiredLevel()) {
                String rawMsg = plugin.getMessage("messages.level_too_low", "<color:#FF5555>Kamu membutuhkan {category} Level {req_level} untuk memanen {node_name}!</color>");
                if (!ColorUtils.isNone(rawMsg)) {
                    String msg = rawMsg
                            .replace("{category}", type.getCategory())
                            .replace("{level}", String.valueOf(playerLevel))
                            .replace("{req_level}", String.valueOf(type.getRequiredLevel()))
                            .replace("{node_name}", type.getName());
                    plugin.sendPrefixedMessage(player, msg);
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    } catch (Throwable ignored) {}
                }
                return;
            }
        }

        // 5. Tool check
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!type.getToolRequirement().isToolValid(handItem, player)) {
            plugin.sendPrefixedMessage(player, plugin.getMessage("messages.invalid_tool"));
            return;
        }

        // 6. Fire start event
        NodeGatherStartEvent startEvent = new NodeGatherStartEvent(player, node);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) return;

        // 7. Lock node and establish session
        double gatheringSpeed = getPlayerGatheringSpeed(player);
        GatherSession session = new GatherSession(player, node, type, gatheringSpeed);

        activeSessions.put(player.getUniqueId(), session);
        nodeLocks.put(node.getId(), player.getUniqueId());
        node.setStatus(NodeStatus.GATHERING);
        node.setActiveGatherer(player.getUniqueId());
        node.touch();

        // Immediate visual update
        plugin.getHologramManager().updateHologram(node, 0.0, player.getName());
    }

    private void startGatheringLoop() {
        int rate = Math.max(1, plugin.getConfig().getInt("settings.gathering_tick_rate", 1));
        gatheringTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeSessions.isEmpty()) return;

            double maxDist = plugin.getConfig().getDouble("settings.max_gather_distance", 2.8);
            boolean autoContinue = plugin.getConfig().getBoolean("settings.auto_continue_gathering", true);

            Iterator<Map.Entry<UUID, GatherSession>> iterator = activeSessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, GatherSession> entry = iterator.next();
                GatherSession session = entry.getValue();
                Player player = session.getPlayer();
                ActiveNode node = session.getNode();
                NodeType type = session.getType();

                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    releaseLock(node);
                    continue;
                }

                // Distance check
                Location pLoc = player.getLocation();
                Location nLoc = node.getLocation();
                if (!pLoc.getWorld().equals(nLoc.getWorld()) || pLoc.distance(nLoc) > maxDist) {
                    cancelGathering(player, NodeGatherCancelEvent.CancelReason.DISTANCE);
                    continue;
                }

                // Main hand tool check
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (!type.getToolRequirement().isToolValid(mainHand, player)) {
                    cancelGathering(player, NodeGatherCancelEvent.CancelReason.ITEM_SWITCH);
                    continue;
                }

                // Advance progress
                session.tick(rate);
                double progress = session.getProgress();

                // Arm swing animation
                boolean swingEnabled = plugin.getConfig().getBoolean("gathering_effects.animation.swing_enabled", true);
                int swingInterval = type.getSwingIntervalTicks() != null ? type.getSwingIntervalTicks()
                        : plugin.getConfig().getInt("gathering_effects.animation.swing_interval_ticks", 6);
                if (swingEnabled && session.shouldSwing(swingInterval)) {
                    player.swingMainHand();
                }

                // Visual effects & particles
                boolean particleEnabled = plugin.getConfig().getBoolean("gathering_effects.particle.enabled", true);
                if (particleEnabled && type.getParticle() != null) {
                    int particleInterval = type.getParticleIntervalTicks() != null ? type.getParticleIntervalTicks()
                            : plugin.getConfig().getInt("gathering_effects.particle.interval_ticks", 4);
                    if (session.shouldSpawnParticle(particleInterval)) {
                        Location fxLoc = nLoc.clone().add(0, 0.5, 0);
                        int pCount = type.getParticleCount() > 0 ? type.getParticleCount()
                                : plugin.getConfig().getInt("gathering_effects.particle.count", 5);
                        double offX = type.getParticleOffsetX() != null ? type.getParticleOffsetX()
                                : plugin.getConfig().getDouble("gathering_effects.particle.offset_x", 0.3);
                        double offY = type.getParticleOffsetY() != null ? type.getParticleOffsetY()
                                : plugin.getConfig().getDouble("gathering_effects.particle.offset_y", 0.3);
                        double offZ = type.getParticleOffsetZ() != null ? type.getParticleOffsetZ()
                                : plugin.getConfig().getDouble("gathering_effects.particle.offset_z", 0.3);
                        double pSpeed = type.getParticleSpeed() != null ? type.getParticleSpeed()
                                : plugin.getConfig().getDouble("gathering_effects.particle.speed", 0.05);

                        if (type.getParticleBlock() != null) {
                            nLoc.getWorld().spawnParticle(type.getParticle(), fxLoc, pCount, offX, offY, offZ, pSpeed, type.getParticleBlock().createBlockData());
                        } else {
                            nLoc.getWorld().spawnParticle(type.getParticle(), fxLoc, pCount, offX, offY, offZ, pSpeed);
                        }
                    }
                }

                // Sound tick
                boolean soundEnabled = plugin.getConfig().getBoolean("gathering_effects.sound.enabled", true);
                if (soundEnabled && type.getSoundTick() != null) {
                    int soundInterval = type.getSoundTickIntervalTicks() != null ? type.getSoundTickIntervalTicks()
                            : plugin.getConfig().getInt("gathering_effects.sound.interval_ticks", 6);
                    if (session.shouldPlaySound(soundInterval)) {
                        float soundVol = type.getSoundTickVolume() != null ? type.getSoundTickVolume()
                                : (float) plugin.getConfig().getDouble("gathering_effects.sound.volume", 0.35);
                        player.playSound(pLoc, type.getSoundTick(), soundVol, type.getSoundTickPitch());
                    }
                }

                // Call Tick Event
                Bukkit.getPluginManager().callEvent(new NodeGatherTickEvent(player, node, progress));

                // Update Hologram Realtime
                plugin.getHologramManager().updateHologram(node, progress, player.getName());

                // Check completion of 1 cycle
                if (session.isCompleted()) {
                    handleGatherCycleComplete(player, node, type, mainHand, session, autoContinue);
                }
            }
        }, 1L, rate);
    }

    private void handleGatherCycleComplete(Player player, ActiveNode node, NodeType type, ItemStack tool, GatherSession session, boolean autoContinue) {
        // 1. Decrement Charge
        node.decrementCharge();
        node.touch();

        // 2. Tool Durability Damage
        if (tool != null && type.getToolRequirement().getDurabilityDamage() > 0) {
            if (tool.getItemMeta() instanceof Damageable dm) {
                dm.setDamage(dm.getDamage() + type.getToolRequirement().getDurabilityDamage());
                tool.setItemMeta(dm);
                if (dm.getDamage() >= tool.getType().getMaxDurability() && tool.getType().getMaxDurability() > 0) {
                    tool.setAmount(0);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
            }
        }

        // 3. Roll Reward
        GatheringRngEngine.RollResult result = plugin.getRngEngine().roll(player, type.getDropTable());
        ItemStack dropItem = result != null ? result.getItemStack() : null;

        if (result == null || (result.getItemEntry() == null && result.getItemStack() == null)) {
            // Player did NOT get any item (no base 1 item, and no RNG roll succeeded)
            // Play pop sound with vanish/poof particle, NO text message per user request!
            Location popLoc = node.getLocation().clone().add(0, 0.5, 0);
            playNoDropEffect(player, popLoc);
        } else {
            // Player won a reward
            // Showcase ItemDisplay above hologram for onlookers!
            if (dropItem != null) {
                plugin.getHologramManager().showRewardShowcase(node, type, dropItem);
            }

            // Spawn Instanced Drop
            if (dropItem != null) {
                boolean directToInv = plugin.getConfig().getBoolean("settings.direct_to_inventory", false);
                if (directToInv) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(dropItem);
                    if (!leftover.isEmpty()) {
                        for (ItemStack rem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rem);
                        }
                        String msg = plugin.getMessage("messages.inventory_full_drop");
                        plugin.sendPrefixedMessage(player, msg);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                } else {
                    Location spawnLoc = node.getLocation().clone().add(0, 0.5, 0);
                    org.bukkit.util.Vector toPlayer = player.getLocation().toVector().subtract(node.getLocation().toVector()).setY(0);
                    if (toPlayer.lengthSquared() > 0.001) {
                        toPlayer.normalize().multiply(0.4);
                        spawnLoc.add(toPlayer);
                    }
                    plugin.getDropManager().spawnDrop(player, dropItem, spawnLoc);
                }
            }

            // Play completion sound
            if (type.getSoundComplete() != null) {
                float completeVol = type.getSoundCompleteVolume() != null ? type.getSoundCompleteVolume() : 0.8f;
                player.playSound(player.getLocation(), type.getSoundComplete(), completeVol, type.getSoundCompletePitch());
            }

            // Notify
            String itemName = dropItem != null ? (dropItem.hasItemMeta() && dropItem.getItemMeta().hasDisplayName() ? dropItem.getItemMeta().getDisplayName() : dropItem.getType().name()) : "Reward";
            String succMsg = plugin.getMessage("messages.gather_success").replace("{item_name}", itemName);
            plugin.sendPrefixedMessage(player, succMsg);
        }

        // 4. Record Player Stats
        plugin.getStatsManager().increment(player.getUniqueId(), type.getCategory(), type.getId());

        // 4b. Add Category XP to Player
        if (plugin.getLevelManager() != null && plugin.getLevelManager().isEnabled() && type.getExp() > 0) {
            plugin.getLevelManager().addXp(player, type.getCategory(), type.getExp());
        }

        // 5. Fire Event
        NodeGatherCompleteEvent completeEvent = new NodeGatherCompleteEvent(player, node, type.getId(), type.getCategory(), dropItem, node.getCurrentCharges());
        Bukkit.getPluginManager().callEvent(completeEvent);

        // 6. Check node depletion
        if (node.isDepleted()) {
            activeSessions.remove(player.getUniqueId());
            releaseLock(node);

            node.startCooldown(type.getCooldownSeconds());
            plugin.getHologramManager().updateHologram(node, 0.0, null);

            String depMsg = plugin.getMessage("messages.node_depleted").replace("{node_name}", type.getName());
            plugin.sendPrefixedMessage(player, depMsg);

            Bukkit.getPluginManager().callEvent(new NodeDepletedEvent(node, player, type.getCooldownSeconds()));
            plugin.getNodeDataManager().saveNode(node);
        } else {
            // Sisa charges masih ada
            if (autoContinue) {
                // Continue gathering cycle seamlessly
                session.resetCycle();
                plugin.getHologramManager().updateHologram(node, 0.0, player.getName());
            } else {
                // Stop session and return node to ready
                activeSessions.remove(player.getUniqueId());
                releaseLock(node);
                node.setStatus(NodeStatus.READY);
                plugin.getHologramManager().updateHologram(node, 0.0, null);
            }
        }
    }

    public void cancelGathering(Player player, NodeGatherCancelEvent.CancelReason reason) {
        GatherSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            ActiveNode node = session.getNode();
            releaseLock(node);

            if (node.getStatus() == NodeStatus.GATHERING) {
                node.setStatus(NodeStatus.READY);
                plugin.getHologramManager().updateHologram(node, 0.0, null);
            }

            Bukkit.getPluginManager().callEvent(new NodeGatherCancelEvent(player, node, reason));

            String msgKey = switch (reason) {
                case DISTANCE -> "messages.gather_cancelled_distance";
                case DAMAGE -> "messages.gather_cancelled_damage";
                default -> "messages.gather_cancelled_action";
            };
            plugin.sendPrefixedMessage(player, plugin.getMessage(msgKey));
        }
    }

    public void releaseLock(ActiveNode node) {
        if (node != null) {
            nodeLocks.remove(node.getId());
            node.setActiveGatherer(null);
        }
    }

    public double getPlayerGatheringSpeed(Player player) {
        if (player == null) return 0.0;
        return plugin.getLuckManager().getPlayerGatheringSpeed(player);
    }

    public void restartGatheringLoop() {
        if (gatheringTask != null) {
            gatheringTask.cancel();
        }
        startGatheringLoop();
    }

    public void cleanup() {
        if (gatheringTask != null) {
            gatheringTask.cancel();
        }
        for (GatherSession session : activeSessions.values()) {
            releaseLock(session.getNode());
            if (session.getNode().getStatus() == NodeStatus.GATHERING) {
                session.getNode().setStatus(NodeStatus.READY);
            }
        }
        activeSessions.clear();
        nodeLocks.clear();
    }

    private void playNoDropEffect(Player player, Location loc) {
        // Pop sound
        String soundName = plugin.getConfig().getString("no_drop_effect.sound", "ENTITY_CHICKEN_EGG");
        float pitch = (float) plugin.getConfig().getDouble("no_drop_effect.sound_pitch", 1.2);
        float vol = (float) plugin.getConfig().getDouble("no_drop_effect.sound_volume", 1.0);
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, sound, vol, pitch);
            }
        } catch (Exception e) {
            try {
                if (loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.2f);
                }
            } catch (Exception ignored) {}
        }

        // Vanish particle (e.g. POOF / SMOKE)
        String partName = plugin.getConfig().getString("no_drop_effect.particle", "POOF");
        int count = plugin.getConfig().getInt("no_drop_effect.particle_count", 8);
        try {
            Particle particle = Particle.valueOf(partName.toUpperCase());
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(particle, loc, count, 0.25, 0.25, 0.25, 0.05);
            }
        } catch (Exception e) {
            try {
                Particle fallback = Particle.valueOf("SMOKE");
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(fallback, loc, count, 0.25, 0.25, 0.25, 0.05);
                }
            } catch (Exception ignored) {}
        }
    }
}

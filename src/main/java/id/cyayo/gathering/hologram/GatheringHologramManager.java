package id.cyayo.gathering.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GatheringHologramManager {

    public static final String PREFIX = "cyayogath_node_";

    private final CyayoGathering plugin;
    private final HologramFormatter formatter;
    private final NamespacedKey nativeHoloKey;
    private final NamespacedKey showcaseHoloKey;

    // FancyHolograms cache
    private final Map<String, Hologram> fancyHolograms = new ConcurrentHashMap<>();
    // Native TextDisplay cache
    private final Map<String, UUID> nativeDisplays = new ConcurrentHashMap<>();
    // Showcase ItemDisplay cache
    private final Map<String, UUID> activeShowcaseDisplays = new ConcurrentHashMap<>();

    public GatheringHologramManager(CyayoGathering plugin) {
        this.plugin = plugin;
        this.formatter = new HologramFormatter(plugin);
        this.nativeHoloKey = new NamespacedKey(plugin, "hologram_node_id");
        this.showcaseHoloKey = new NamespacedKey(plugin, "showcase_node_id");

        sweepOrphanHolograms();
    }

    public void updateHologram(ActiveNode node, double progress, String gathererName) {
        if (node == null || node.getLocation() == null || node.getLocation().getWorld() == null) return;

        NodeType type = plugin.getNodeConfigManager().getType(node.getTypeId());
        if (type == null) return;

        List<String> rawLines = formatter.formatRaw(node, type, progress, gathererName);
        List<String> legacyLines = formatter.formatLegacy(node, type, progress, gathererName);

        String provider = plugin.getConfig().getString("hologram.provider", "AUTO").toUpperCase();

        if ("DISPLAY_TEXT".equals(provider)) {
            updateNativeTextDisplay(node, type, legacyLines);
        } else {
            // AUTO or FANCYHOLOGRAMS
            if (isFancyHologramsEnabled()) {
                updateFancyHologram(node, type, rawLines, legacyLines);
            } else {
                updateNativeTextDisplay(node, type, legacyLines);
            }
        }
    }

    /**
     * FancyHolograms Plugin (menerapkan atribut dari config)
     */
    private void updateFancyHologram(ActiveNode node, NodeType type, List<String> rawLines, List<String> legacyLines) {
        try {
            String holoName = PREFIX + node.getId();
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            Hologram hologram = fancyHolograms.get(node.getId());

            Location holoLoc = node.getLocation().clone().add(0, type.getHologramOffsetY(), 0);

            boolean locationChanged = false;
            if (hologram != null && hologram.getData().getLocation() != null) {
                Location currentHoloLoc = hologram.getData().getLocation();
                if (!currentHoloLoc.getWorld().equals(holoLoc.getWorld()) || currentHoloLoc.distanceSquared(holoLoc) > 0.0001) {
                    locationChanged = true;
                }
            }

            if (hologram == null || !manager.getHologram(holoName).isPresent() || locationChanged) {
                if (hologram != null) {
                    try {
                        hologram.hideHologram(Bukkit.getOnlinePlayers());
                        manager.removeHologram(hologram);
                    } catch (Throwable ignored) {}
                }
                manager.getHologram(holoName).ifPresent(h -> {
                    try {
                        h.hideHologram(Bukkit.getOnlinePlayers());
                        manager.removeHologram(h);
                    } catch (Throwable ignored) {}
                });

                TextHologramData data = new TextHologramData(holoName, holoLoc);
                data.setPersistent(false);
                data.setText(legacyLines);
                applyFancyAttributes(data);

                hologram = manager.create(data);
                manager.addHologram(hologram);
                hologram.showHologram(Bukkit.getOnlinePlayers());

                fancyHolograms.put(node.getId(), hologram);
            } else {
                if (hologram.getData() instanceof TextHologramData data) {
                    data.setText(legacyLines);
                    applyFancyAttributes(data);
                    hologram.forceUpdate();
                }
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("FancyHolograms failed for node " + node.getId() + ": " + e.getMessage() + ". Using native TextDisplay.");
            updateNativeTextDisplay(node, type, legacyLines);
        }
    }

    /**
     * Vanilla Minecraft 1.20+ TextDisplay Entity (menerapkan atribut dari config)
     */
    private void updateNativeTextDisplay(ActiveNode node, NodeType type, List<String> legacyLines) {
        Location holoLoc = node.getLocation().clone().add(0, type.getHologramOffsetY(), 0);
        World world = holoLoc.getWorld();
        if (world == null) return;

        UUID existingUUID = nativeDisplays.get(node.getId());
        TextDisplay display = null;

        if (existingUUID != null) {
            Entity ent = Bukkit.getEntity(existingUUID);
            if (ent instanceof TextDisplay td && ent.isValid()) {
                display = td;
            }
        }

        if (display == null) {
            Location searchCenter = node.getLocation().clone().add(0, 1.5, 0);
            for (Entity ent : world.getNearbyEntities(searchCenter, 3.0, 5.0, 3.0)) {
                if (ent instanceof TextDisplay td && td.isValid()) {
                    String tag = td.getPersistentDataContainer().get(nativeHoloKey, PersistentDataType.STRING);
                    if (node.getId().equals(tag)) {
                        display = td;
                        nativeDisplays.put(node.getId(), display.getUniqueId());
                        break;
                    }
                }
            }
        }

        String joined = String.join("\n", legacyLines);

        if (display == null) {
            display = (TextDisplay) world.spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
            display.getPersistentDataContainer().set(nativeHoloKey, PersistentDataType.STRING, node.getId());
            nativeDisplays.put(node.getId(), display.getUniqueId());
        } else {
            if (!display.getWorld().equals(holoLoc.getWorld()) || display.getLocation().distanceSquared(holoLoc) > 0.0001) {
                display.teleport(holoLoc);
            }
        }

        display.setText(joined);
        applyNativeAttributes(display);
    }

    private void applyFancyAttributes(TextHologramData data) {
        try {
            String bbStr = plugin.getConfig().getString("hologram.billboard", "CENTER");
            data.setBillboard(parseBillboard(bbStr));

            String alignStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
            data.setTextAlignment(parseAlignment(alignStr));

            boolean seeThrough = plugin.getConfig().getBoolean("hologram.see_through", false);
            data.setSeeThrough(seeThrough);

            boolean shadow = plugin.getConfig().getBoolean("hologram.text_shadow", true);
            data.setTextShadow(shadow);

            String bgStr = plugin.getConfig().getString("hologram.background_color", "DEFAULT");
            if ("NONE".equalsIgnoreCase(bgStr)) {
                data.setBackground(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            } else if (!"DEFAULT".equalsIgnoreCase(bgStr)) {
                int alpha = plugin.getConfig().getInt("hologram.background_alpha", 64);
                org.bukkit.Color c = parseHexColor(bgStr, alpha);
                if (c != null) data.setBackground(c);
            }
        } catch (Throwable ignored) {}
    }

    private void applyNativeAttributes(TextDisplay display) {
        try {
            String bbStr = plugin.getConfig().getString("hologram.billboard", "CENTER");
            display.setBillboard(parseBillboard(bbStr));

            String alignStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
            display.setAlignment(parseAlignment(alignStr));

            boolean seeThrough = plugin.getConfig().getBoolean("hologram.see_through", false);
            display.setSeeThrough(seeThrough);

            boolean shadow = plugin.getConfig().getBoolean("hologram.text_shadow", true);
            display.setShadowed(shadow);

            float viewRange = (float) plugin.getConfig().getDouble("hologram.view_range", 1.0);
            if (viewRange > 0.0f) {
                display.setViewRange(viewRange);
            }

            String bgStr = plugin.getConfig().getString("hologram.background_color", "DEFAULT");
            if ("NONE".equalsIgnoreCase(bgStr)) {
                display.setDefaultBackground(false);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            } else if ("DEFAULT".equalsIgnoreCase(bgStr)) {
                display.setDefaultBackground(true);
            } else {
                display.setDefaultBackground(false);
                int alpha = plugin.getConfig().getInt("hologram.background_alpha", 64);
                org.bukkit.Color c = parseHexColor(bgStr, alpha);
                if (c != null) display.setBackgroundColor(c);
            }
        } catch (Throwable ignored) {}
    }

    private Display.Billboard parseBillboard(String str) {
        if (str != null) {
            try {
                return Display.Billboard.valueOf(str.toUpperCase());
            } catch (Exception ignored) {}
        }
        return Display.Billboard.CENTER;
    }

    private TextDisplay.TextAlignment parseAlignment(String str) {
        if (str != null) {
            try {
                return TextDisplay.TextAlignment.valueOf(str.toUpperCase());
            } catch (Exception ignored) {}
        }
        return TextDisplay.TextAlignment.CENTER;
    }

    private org.bukkit.Color parseHexColor(String hex, int alpha) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return org.bukkit.Color.fromARGB(Math.max(0, Math.min(255, alpha)), r, g, b);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void showHologramsToPlayer(Player player) {
        if (isFancyHologramsEnabled()) {
            for (Hologram h : fancyHolograms.values()) {
                try {
                    h.showHologram(Collections.singletonList(player));
                } catch (Throwable ignored) {
                    try {
                        h.showHologram(player);
                    } catch (Throwable ignored2) {}
                }
            }
        }
    }

    public void showRewardShowcase(ActiveNode node, NodeType type, ItemStack item) {
        if (!plugin.getConfig().getBoolean("reward_showcase.enabled", true)) return;
        if (node == null || node.getLocation() == null || type == null || item == null || item.getType().isAir()) return;

        World world = node.getLocation().getWorld();
        if (world == null) return;

        // Remove any existing showcase for this node
        removeRewardShowcase(node.getId());

        double offsetY = plugin.getConfig().getDouble("reward_showcase.offset_y", 0.6);
        Location showcaseLoc = node.getLocation().clone().add(0, type.getHologramOffsetY() + offsetY, 0);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(showcaseLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item.clone());

        String bbStr = plugin.getConfig().getString("reward_showcase.billboard", "CENTER");
        display.setBillboard(parseBillboard(bbStr));

        try {
            String transStr = plugin.getConfig().getString("reward_showcase.item_transform", "FIXED");
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(transStr.toUpperCase()));
        } catch (Throwable ignored) {}

        float viewRange = (float) plugin.getConfig().getDouble("hologram.view_range", 1.0);
        if (viewRange > 0.0f) {
            display.setViewRange(viewRange);
        }

        display.getPersistentDataContainer().set(showcaseHoloKey, PersistentDataType.STRING, node.getId());

        double durationSec = plugin.getConfig().getDouble("reward_showcase.duration_seconds", 2.0);
        long durationTicks = Math.max(10L, (long) (durationSec * 20L));

        boolean floatAnim = plugin.getConfig().getBoolean("reward_showcase.float_animation", true);
        float s = (float) plugin.getConfig().getDouble("reward_showcase.scale", 0.75);

        try {
            org.joml.Vector3f scale = new org.joml.Vector3f(s, s, s);
            org.joml.Vector3f translation = new org.joml.Vector3f(0f, 0f, 0f);
            org.joml.Quaternionf leftRot = new org.joml.Quaternionf();
            org.joml.Quaternionf rightRot = new org.joml.Quaternionf();
            display.setTransformation(new org.bukkit.util.Transformation(translation, leftRot, scale, rightRot));

            if (floatAnim) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration((int) durationTicks);
                org.joml.Vector3f floatTranslation = new org.joml.Vector3f(0f, 0.25f, 0f);
                display.setTransformation(new org.bukkit.util.Transformation(floatTranslation, leftRot, scale, rightRot));
            }
        } catch (Throwable ignored) {}

        String partName = plugin.getConfig().getString("reward_showcase.particle", "HAPPY_VILLAGER");
        if (partName != null && !partName.isEmpty() && !"NONE".equalsIgnoreCase(partName)) {
            try {
                Particle p = Particle.valueOf(partName.toUpperCase());
                int count = plugin.getConfig().getInt("reward_showcase.particle_count", 5);
                double pSpeed = plugin.getConfig().getDouble("reward_showcase.particle_speed", 0.05);
                world.spawnParticle(p, showcaseLoc, count, 0.15, 0.15, 0.15, pSpeed);
            } catch (Throwable ignored) {}
        }

        activeShowcaseDisplays.put(node.getId(), display.getUniqueId());

        final UUID displayUUID = display.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID current = activeShowcaseDisplays.get(node.getId());
            if (current != null && current.equals(displayUUID)) {
                removeRewardShowcase(node.getId());
            }
        }, durationTicks);
    }

    public void removeRewardShowcase(String nodeId) {
        if (nodeId == null) return;
        UUID uuid = activeShowcaseDisplays.remove(nodeId);
        if (uuid != null) {
            Entity ent = Bukkit.getEntity(uuid);
            if (ent != null) {
                ent.remove();
            }
        }
    }

    public void removeHologram(String nodeId) {
        removeRewardShowcase(nodeId);
        if (isFancyHologramsEnabled()) {
            Hologram hologram = fancyHolograms.remove(nodeId);
            if (hologram != null) {
                try {
                    hologram.hideHologram(Bukkit.getOnlinePlayers());
                    FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
                } catch (Throwable ignored) {}
            }
        }
        UUID uuid = nativeDisplays.remove(nodeId);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.remove();
            }
        }
    }

    public int purgeAllHolograms() {
        int count = 0;

        // 1. Purge FancyHolograms
        if (isFancyHologramsEnabled()) {
            try {
                HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
                List<Hologram> toRemove = new ArrayList<>();
                for (Hologram h : manager.getHolograms()) {
                    if (h.getData().getName().startsWith(PREFIX)) {
                        toRemove.add(h);
                    }
                }
                for (Hologram h : toRemove) {
                    try {
                        h.hideHologram(Bukkit.getOnlinePlayers());
                        manager.removeHologram(h);
                        count++;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            fancyHolograms.clear();
        }

        // 2. Purge Native TextDisplays across all loaded worlds
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay td) {
                    if (td.getPersistentDataContainer().has(nativeHoloKey, PersistentDataType.STRING)) {
                        td.remove();
                        count++;
                    }
                } else if (entity instanceof ItemDisplay id) {
                    if (id.getPersistentDataContainer().has(showcaseHoloKey, PersistentDataType.STRING)) {
                        id.remove();
                        count++;
                    }
                }
            }
        }
        nativeDisplays.clear();
        activeShowcaseDisplays.clear();

        return count;
    }

    public void sweepOrphanHolograms() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                purgeAllHolograms();
            } catch (Throwable ignored) {}
        }, 10L);
    }

    public void cleanup() {
        for (UUID uuid : activeShowcaseDisplays.values()) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.remove();
            }
        }
        activeShowcaseDisplays.clear();
        purgeAllHolograms();
    }

    public boolean isFancyHologramsEnabled() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
        } catch (Throwable e) {
            return false;
        }
    }

    public HologramFormatter getFormatter() {
        return formatter;
    }
}

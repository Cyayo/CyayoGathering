package id.cyayo.gathering.model;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ToolRequirement {

    private final boolean enabled;
    private final boolean allowAnyTool;
    private final int durabilityDamage;
    private final List<ToolEntry> allowedTools = new ArrayList<>();

    public ToolRequirement(ConfigurationSection section) {
        if (section == null) {
            this.enabled = false;
            this.allowAnyTool = true;
            this.durabilityDamage = 1;
            return;
        }

        this.enabled = section.getBoolean("enabled", true);
        this.allowAnyTool = section.getBoolean("allow_any_tool", false);
        this.durabilityDamage = section.getInt("durability_damage_per_gather", 1);

        List<?> list = section.getList("allowed_tools");
        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    Object typeObj = map.get("type");
                    String type = (typeObj != null ? typeObj.toString() : "VANILLA").toUpperCase();
                    if ("MMOITEMS".equalsIgnoreCase(type)) {
                        Object mmoTypeObj = map.get("mmo_type");
                        String mmoType = mmoTypeObj != null ? mmoTypeObj.toString() : "";
                        Object mmoIdObj = map.get("mmo_id");
                        String mmoId = mmoIdObj != null ? mmoIdObj.toString() : "";
                        int minLevel = map.containsKey("min_level") && map.get("min_level") != null ? Integer.parseInt(map.get("min_level").toString()) : 0;
                        allowedTools.add(new ToolEntry(type, null, mmoType, mmoId, minLevel));
                    } else {
                        Object matObj = map.get("material");
                        String matStr = matObj != null ? matObj.toString() : "AIR";
                        Material mat = Material.matchMaterial(matStr);
                        if (mat != null) {
                            allowedTools.add(new ToolEntry("VANILLA", mat, null, null, 0));
                        }
                    }
                }
            }
        }
    }

    public boolean isToolValid(ItemStack item, Player player) {
        if (!enabled || allowAnyTool) return true;
        if (item == null || item.getType() == Material.AIR) return false;

        for (ToolEntry entry : allowedTools) {
            if ("VANILLA".equalsIgnoreCase(entry.type)) {
                if (item.getType() == entry.material) {
                    return true;
                }
            } else if ("MMOITEMS".equalsIgnoreCase(entry.type)) {
                try {
                    NBTItem nbt = NBTItem.get(item);
                    if (nbt != null && nbt.hasType()) {
                        String itemType = nbt.getType();
                        String itemId = nbt.getString("MMOITEMS_ITEM_ID");
                        if (entry.mmoType.equalsIgnoreCase(itemType)) {
                            if (entry.mmoId == null || entry.mmoId.isEmpty() || "*".equals(entry.mmoId) || "ANY".equalsIgnoreCase(entry.mmoId)) {
                                return true;
                            }
                            if (entry.mmoId.contains("*")) {
                                String regex = java.util.regex.Pattern.quote(entry.mmoId).replace("*", ".*");
                                if (itemId != null && itemId.matches("(?i)" + regex)) {
                                    return true;
                                }
                            }
                            if (itemId != null && entry.mmoId.equalsIgnoreCase(itemId)) {
                                return true;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    public int getDurabilityDamage() {
        return durabilityDamage;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static class ToolEntry {
        public final String type;
        public final Material material;
        public final String mmoType;
        public final String mmoId;
        public final int minLevel;

        public ToolEntry(String type, Material material, String mmoType, String mmoId, int minLevel) {
            this.type = type;
            this.material = material;
            this.mmoType = mmoType;
            this.mmoId = mmoId;
            this.minLevel = minLevel;
        }
    }
}

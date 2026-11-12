package id.cyayo.gathering.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DropTable {

    private final boolean useLuck;
    private final boolean useDoubleDrop;
    private final double bonusLuckPercent;
    private final List<DropItem> items = new ArrayList<>();
    private final DropItem defaultItem;

    public DropTable(ConfigurationSection section) {
        if (section == null) {
            this.useLuck = true;
            this.useDoubleDrop = true;
            this.bonusLuckPercent = 0.0;
            this.defaultItem = new DropItem(DropItem.DropType.VANILLA, Material.COBBLESTONE, null, null, 1, 1, null, null);
            this.items.add(defaultItem);
            return;
        }

        this.useLuck = section.getBoolean("use-luck", true);
        this.useDoubleDrop = section.getBoolean("use-double-drop", true);
        this.bonusLuckPercent = section.getDouble("bonus-luck-percent", 0.0);

        List<?> list = section.getList("items");
        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> map) {
                    Object typeObj = map.get("type");
                    String typeStr = (typeObj != null ? typeObj.toString() : "vanilla").toUpperCase();
                    DropItem.DropType type = DropItem.DropType.VANILLA;
                    try {
                        type = DropItem.DropType.valueOf(typeStr);
                    } catch (Exception ignored) {}

                    Material mat = null;
                    if (map.containsKey("material")) {
                        mat = Material.matchMaterial(String.valueOf(map.get("material")));
                    }
                    if (mat == null && type == DropItem.DropType.VANILLA) {
                        mat = Material.COBBLESTONE;
                    }

                    String mmoType = map.containsKey("mmo-type") ? String.valueOf(map.get("mmo-type")) : null;
                    String mmoId = map.containsKey("id") ? String.valueOf(map.get("id")) : null;
                    int base = map.containsKey("base") ? Integer.parseInt(String.valueOf(map.get("base"))) : 1;
                    int amount = map.containsKey("amount") ? Integer.parseInt(String.valueOf(map.get("amount"))) : 1;
                    String broadcast = map.containsKey("broadcast") ? String.valueOf(map.get("broadcast")) : null;
                    String command = map.containsKey("command") ? String.valueOf(map.get("command")) : null;

                    items.add(new DropItem(type, mat, mmoType, mmoId, base, amount, broadcast, command));
                }
            }
        }

        // Find guaranteed default item (base <= 1)
        DropItem def = null;
        for (DropItem it : items) {
            if (it.getBase() <= 1) {
                def = it;
                break;
            }
        }
        this.defaultItem = def;
    }

    public boolean usesLuck() { return useLuck; }
    public boolean usesDoubleDrop() { return useDoubleDrop; }
    public double getBonusLuckPercent() { return bonusLuckPercent; }
    public List<DropItem> getItems() { return Collections.unmodifiableList(items); }
    public DropItem getDefaultItem() { return defaultItem; }
    public boolean hasGuaranteedDefault() { return defaultItem != null; }
}

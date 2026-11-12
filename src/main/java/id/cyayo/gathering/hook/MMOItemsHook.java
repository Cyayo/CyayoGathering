package id.cyayo.gathering.hook;

import id.cyayo.gathering.CyayoGathering;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.stat.type.DoubleStat;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.Material;

public class MMOItemsHook {

    private final CyayoGathering plugin;

    public MMOItemsHook(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    public void registerCustomStats() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems"))
            return;

        try {
            registerSingleStat(new DoubleStat(
                    "GATHERING_SPEED",
                    Material.GOLDEN_PICKAXE,
                    "Gathering Speed",
                    new String[] { "Increases the speed of harvesting gathering nodes." },
                    new String[] { "all" }));

            registerSingleStat(new DoubleStat(
                    "GATHERING_DOUBLE_DROP",
                    Material.EMERALD,
                    "Double Gather Chance",
                    new String[] { "Chance in percent to receive double loot from gathering nodes." },
                    new String[] { "all" }));

            registerSingleStat(new DoubleStat(
                    "GATHERING_LUCK",
                    Material.SUNFLOWER,
                    "Gathering Luck",
                    new String[] { "Increases the chance of obtaining rare materials from gathering nodes." },
                    new String[] { "all" }));

            plugin.getLogger()
                    .info("Successfully registered MMOItems custom stats (GATHERING_SPEED, GATHERING_DOUBLE_DROP, GATHERING_LUCK)!");
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to register MMOItems stats: " + e.getMessage());
        }
    }

    private void registerSingleStat(ItemStat<?, ?> stat) {
        try {
            if (MMOItems.plugin.getStats().get(stat.getId()) == null) {
                MMOItems.plugin.getStats().register(stat);
            }
        } catch (Throwable ignored) {
        }
    }
}

package id.cyayo.gathering.rng;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.DropItem;
import id.cyayo.gathering.model.DropTable;
import id.cyayo.gathering.util.ColorUtils;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import net.Indyuce.mmoitems.MMOItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GatheringRngEngine {

    private final CyayoGathering plugin;
    private final Random random = new Random();

    public GatheringRngEngine(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    public RollResult roll(Player player, DropTable table) {
        if (table == null || table.getItems().isEmpty()) {
            return new RollResult(null, null);
        }

        double luckMultiplier = table.usesLuck() 
                ? plugin.getLuckManager().getEffectiveMultiplier(player, table.getBonusLuckPercent()) 
                : 1.0;

        // Double drop chance check
        int multiplier = 1;
        if (table.usesDoubleDrop()) {
            double doubleDropChance = getDoubleDropChance(player);
            if (doubleDropChance > 0 && random.nextDouble() * 100.0 < doubleDropChance) {
                multiplier = 2;
            }
        }

        List<DropItem> allItems = table.getItems();
        DropItem wonItem = null;

        for (DropItem entry : allItems) {
            if (entry.getBase() <= 1) {
                // Guaranteed fallback item (base 1)
                wonItem = entry;
                break;
            }
            int bound = Math.max(1, (int) Math.round(entry.getBase() / luckMultiplier));
            if (random.nextInt(bound) == 0) {
                wonItem = entry;
                break;
            }
        }

        // If no item was won (no base 1 item and no RNG roll succeeded)
        if (wonItem == null) {
            return new RollResult(null, null);
        }

        int finalAmount = wonItem.getAmount() * multiplier;

        // Broadcast if applicable
        if (wonItem.getBroadcast() != null && !wonItem.getBroadcast().isEmpty()) {
            String bMsg = wonItem.getBroadcast()
                    .replace("{player}", player.getName())
                    .replace("{amount}", String.valueOf(finalAmount));
            Bukkit.broadcastMessage(ColorUtils.color(player, bMsg));
        }

        // Process rewards
        if (wonItem.getType() == DropItem.DropType.COMMAND) {
            if (wonItem.getCommand() != null && !wonItem.getCommand().isEmpty()) {
                String cmd = wonItem.getCommand()
                        .replace("{player}", player.getName())
                        .replace("{amount}", String.valueOf(finalAmount));
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            }
            return new RollResult(wonItem, null);
        }

        ItemStack itemStack = createItemStack(wonItem, finalAmount);
        return new RollResult(wonItem, itemStack);
    }

    private double getDoubleDropChance(Player player) {
        if (player == null) return 0.0;
        return plugin.getLuckManager().getPlayerDoubleGather(player);
    }

    private ItemStack createItemStack(DropItem entry, int amount) {
        if (entry.getType() == DropItem.DropType.MMOITEMS && entry.getMmoType() != null && entry.getMmoId() != null) {
            if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                try {
                    ItemStack mmoItem = MMOItems.plugin.getItem(entry.getMmoType(), entry.getMmoId());
                    if (mmoItem != null) {
                        mmoItem.setAmount(amount);
                        return mmoItem;
                    }
                } catch (Throwable e) {
                    plugin.getLogger().warning("Failed to generate MMOItem: " + entry.getMmoType() + ":" + entry.getMmoId() + " (" + e.getMessage() + ")");
                }
            }
        }

        Material mat = entry.getMaterial() != null ? entry.getMaterial() : Material.COBBLESTONE;
        return new ItemStack(mat, amount);
    }

    public static class RollResult {
        private final DropItem itemEntry;
        private final ItemStack itemStack;

        public RollResult(DropItem itemEntry, ItemStack itemStack) {
            this.itemEntry = itemEntry;
            this.itemStack = itemStack;
        }

        public DropItem getItemEntry() { return itemEntry; }
        public DropItem getItem() { return itemEntry; }
        public ItemStack getItemStack() { return itemStack; }
    }
}

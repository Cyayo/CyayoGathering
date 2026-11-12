package id.cyayo.gathering.gui;

import id.cyayo.gathering.CyayoGathering;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class ProfileGUIListener implements Listener {

    private final CyayoGathering plugin;

    public ProfileGUIListener(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ProfileHolder holder)) {
            return;
        }

        // 1. Kunci inventory agar item tidak bisa diambil / digeser
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Cek jika pemain mengklik di luar batas inventory atas
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getSlot();

        // 2. Handling Menu Utama Profile
        if (holder.getViewType() == ProfileHolder.ViewType.MAIN_PROFILE) {
            ConfigurationSection pConfig = plugin.getProfileGUIManager().getProfileConfig();

            // Klik kepala profil -> Tutup menu
            int headSlot = pConfig.getInt("profile_head.slot", 4);
            if (slot == headSlot) {
                player.closeInventory();
                return;
            }

            // Klik salah satu kategori gathering -> Buka Level Road kategori tersebut
            ConfigurationSection catSec = pConfig.getConfigurationSection("categories");
            if (catSec != null) {
                for (String catKey : catSec.getKeys(false)) {
                    int catSlot = catSec.getInt(catKey + ".slot", -1);
                    if (slot == catSlot) {
                        plugin.getProfileGUIManager().openCategoryDetail(player, holder.getTargetPlayer(), catKey, 1);
                        return;
                    }
                }
            }
            return;
        }

        // 3. Handling Menu Detail Level Road
        if (holder.getViewType() == ProfileHolder.ViewType.CATEGORY_DETAIL) {
            ConfigurationSection dConfig = plugin.getProfileGUIManager().getDetailConfig();

            int backSlot = dConfig.getInt("navigation.back_button.slot", 31);
            int prevSlot = dConfig.getInt("navigation.previous_page.slot", 29);
            int nextSlot = dConfig.getInt("navigation.next_page.slot", 33);

            // Tombol Kembali ke Profil Utama
            if (slot == backSlot) {
                String soundBack = dConfig.getString("sound_back");
                plugin.getLevelManager().playSound(player, soundBack);
                plugin.getProfileGUIManager().openProfile(player, holder.getTargetPlayer());
                return;
            }

            // Tombol Halaman Sebelumnya
            if (slot == prevSlot && holder.getPage() > 1) {
                String soundPage = dConfig.getString("sound_page");
                plugin.getLevelManager().playSound(player, soundPage);
                plugin.getProfileGUIManager().openCategoryDetail(player, holder.getTargetPlayer(), holder.getCategory(), holder.getPage() - 1);
                return;
            }

            // Tombol Halaman Berikutnya
            int maxLvl = plugin.getLevelManager().getMaxLevel();
            java.util.List<Integer> configuredSlots = ProfileGUIManager.parseSlots(dConfig);
            int levelsPerPage = !configuredSlots.isEmpty() ? configuredSlots.size() : Math.max(1, dConfig.getInt("level_slots_per_page", 27));
            int maxPage = Math.max(1, (int) Math.ceil((double) maxLvl / (double) levelsPerPage));

            if (slot == nextSlot && holder.getPage() < maxPage) {
                String soundPage = dConfig.getString("sound_page");
                plugin.getLevelManager().playSound(player, soundPage);
                plugin.getProfileGUIManager().openCategoryDetail(player, holder.getTargetPlayer(), holder.getCategory(), holder.getPage() + 1);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ProfileHolder) {
            event.setCancelled(true);
        }
    }
}


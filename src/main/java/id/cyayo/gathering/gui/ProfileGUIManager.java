package id.cyayo.gathering.gui;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.util.ColorUtils;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProfileGUIManager {

    private final CyayoGathering plugin;

    private final File profileConfigFile;
    private YamlConfiguration profileConfig;

    private final File detailConfigFile;
    private YamlConfiguration detailConfig;

    public ProfileGUIManager(CyayoGathering plugin) {
        this.plugin = plugin;

        // 1. gui/profile.yml
        this.profileConfigFile = new File(plugin.getDataFolder(), "gui/profile.yml");
        if (!profileConfigFile.exists()) {
            plugin.saveResource("gui/profile.yml", false);
        }
        this.profileConfig = YamlConfiguration.loadConfiguration(profileConfigFile);

        // 2. gui/category_detail.yml
        this.detailConfigFile = new File(plugin.getDataFolder(), "gui/category_detail.yml");
        if (!detailConfigFile.exists()) {
            plugin.saveResource("gui/category_detail.yml", false);
        }
        this.detailConfig = YamlConfiguration.loadConfiguration(detailConfigFile);
    }

    public void reloadConfig() {
        this.profileConfig = YamlConfiguration.loadConfiguration(profileConfigFile);
        this.detailConfig = YamlConfiguration.loadConfiguration(detailConfigFile);
    }

    public YamlConfiguration getProfileConfig() {
        return profileConfig;
    }

    public YamlConfiguration getDetailConfig() {
        return detailConfig;
    }

    /**
     * Membuka menu utama profil gathering pemain.
     */
    public void openProfile(Player viewer, OfflinePlayer target) {
        if (viewer == null || target == null) return;

        int rows = Math.max(1, Math.min(6, profileConfig.getInt("rows", 3)));
        int size = rows * 9;

        String rawTitle = profileConfig.getString("title", "Profil Gathering - {player}");
        String title = ColorUtils.color(target, rawTitle.replace("{player}", target.getName() != null ? target.getName() : "Unknown"));

        ProfileHolder holder = new ProfileHolder(ProfileHolder.ViewType.MAIN_PROFILE, target);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // 1. Isi background filler (mendukung 'filler' dengan 'slots' & multi-filler 'filler_items')
        applyFillerConfig(
                inv,
                target,
                profileConfig.getConfigurationSection("filler"),
                profileConfig.getConfigurationSection("filler_items"),
                size
        );

        Player onlineTarget = target.getPlayer();

        // 2. Pasang item kepala pemain (Profile Head)
        if (profileConfig.isConfigurationSection("profile_head")) {
            ConfigurationSection headSec = profileConfig.getConfigurationSection("profile_head");
            int slot = headSec.getInt("slot", 4);
            if (slot >= 0 && slot < size) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(target);

                    String nameRaw = headSec.getString("name", "&e&lPROFIL: {player}");
                    meta.setDisplayName(ColorUtils.color(target, nameRaw.replace("{player}", target.getName() != null ? target.getName() : "Unknown")));

                    double speed = onlineTarget != null ? plugin.getGatherManager().getPlayerGatheringSpeed(onlineTarget) : plugin.getLuckManager().getCustomSpeed(target.getUniqueId());
                    double luck = onlineTarget != null ? plugin.getLuckManager().getPlayerGatheringLuck(onlineTarget) : plugin.getLuckManager().getCustomLuck(target.getUniqueId());
                    double dDrop = onlineTarget != null ? plugin.getLuckManager().getPlayerDoubleGather(onlineTarget) : plugin.getLuckManager().getCustomDoubleGather(target.getUniqueId());

                    List<String> rawLore = headSec.getStringList("lore");
                    List<String> lore = new ArrayList<>();
                    for (String line : rawLore) {
                        String formatted = line
                                .replace("{player}", target.getName() != null ? target.getName() : "Unknown")
                                .replace("{total_gathered}", String.format(Locale.US, "%,d", plugin.getStatsManager().getTotal(target.getUniqueId())))
                                .replace("{gathering_speed}", String.format(Locale.US, "+%.1f%%", speed))
                                .replace("{gathering_luck}", String.format(Locale.US, "+%.1f", luck))
                                .replace("{double_drop}", String.format(Locale.US, "%.1f", dDrop));
                        lore.add(ColorUtils.color(target, formatted));
                    }
                    meta.setLore(lore);
                    head.setItemMeta(meta);
                }
                inv.setItem(slot, head);
            }
        }

        // 3. Pasang ikon tiap kategori gathering
        ConfigurationSection catSec = profileConfig.getConfigurationSection("categories");
        if (catSec != null) {
            int barLen = profileConfig.getInt("progress_bar.length", 10);
            String symbol = profileConfig.getString("progress_bar.symbol", "■");
            String compCol = profileConfig.getString("progress_bar.completed_color", "&a");
            String remCol = profileConfig.getString("progress_bar.remaining_color", "&7");

            for (String catKey : catSec.getKeys(false)) {
                ConfigurationSection itemSec = catSec.getConfigurationSection(catKey);
                if (itemSec == null) continue;

                int slot = itemSec.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;

                String matStr = itemSec.getString("material", "STONE");
                Material mat = Material.matchMaterial(matStr);
                if (mat == null) mat = Material.STONE;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    int lvl = plugin.getLevelManager().getLevel(target.getUniqueId(), catKey);
                    boolean isMax = lvl >= plugin.getLevelManager().getMaxLevel();
                    double curXp = plugin.getLevelManager().getXp(target.getUniqueId(), catKey);
                    long reqXp = plugin.getLevelManager().getRequiredXpForNextLevel(target.getUniqueId(), catKey);
                    double pct = plugin.getLevelManager().getProgressPercent(target.getUniqueId(), catKey);
                    int gathered = plugin.getStatsManager().getCategory(target.getUniqueId(), catKey);
                    String pBar = plugin.getLevelManager().getProgressBar(target.getUniqueId(), catKey, barLen, symbol, compCol, remCol);

                    String nameRaw = itemSec.getString("name", "&6&l" + catKey.toUpperCase());
                    meta.setDisplayName(ColorUtils.color(target, nameRaw));

                    String maxXpText = profileConfig.getString("max_xp_text", "MAX");
                    List<String> rawLore = (isMax && itemSec.isList("lore_max")) ? itemSec.getStringList("lore_max") : itemSec.getStringList("lore");
                    List<String> lore = new ArrayList<>();
                    for (String line : rawLore) {
                        String formatted = line
                                .replace("{category}", capitalize(catKey))
                                .replace("{level}", String.valueOf(lvl))
                                .replace("{max_level}", String.valueOf(plugin.getLevelManager().getMaxLevel()))
                                .replace("{xp}", isMax ? maxXpText : String.format(Locale.US, "%,.0f", curXp))
                                .replace("{req_xp}", isMax ? maxXpText : String.format(Locale.US, "%,d", reqXp))
                                .replace("{percent}", String.format(Locale.US, "%.1f", pct))
                                .replace("{progress_bar}", pBar)
                                .replace("{gathered}", String.format(Locale.US, "%,d", gathered));
                        lore.add(ColorUtils.color(target, formatted));
                    }
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            }
        }

        viewer.openInventory(inv);

        // Sound effect open
        String soundOpen = profileConfig.getString("sound_open");
        plugin.getLevelManager().playSound(viewer, soundOpen);
    }

    /**
     * Membuka menu detail Level Road untuk kategori tertentu dengan pagination.
     */
    public void openCategoryDetail(Player viewer, OfflinePlayer target, String category, int page) {
        if (viewer == null || target == null || category == null) return;

        int rows = Math.max(1, Math.min(6, detailConfig.getInt("rows", 4)));
        int size = rows * 9;

        int maxLvl = plugin.getLevelManager().getMaxLevel();

        List<Integer> configuredSlots = parseSlots(detailConfig);
        if (configuredSlots.isEmpty()) {
            int fallbackCount = Math.max(1, detailConfig.getInt("level_slots_per_page", 27));
            for (int i = 0; i < fallbackCount; i++) {
                configuredSlots.add(i);
            }
        }

        int levelsPerPage = configuredSlots.size();
        int maxPage = Math.max(1, (int) Math.ceil((double) maxLvl / (double) levelsPerPage));
        int clampedPage = Math.max(1, Math.min(maxPage, page));

        String catCap = capitalize(category);
        String rawTitle = detailConfig.getString("title", "&8{category_name} - Hal. {page}/{max_page}");
        String title = ColorUtils.color(target, rawTitle
                .replace("{category_name}", catCap)
                .replace("{page}", String.valueOf(clampedPage))
                .replace("{max_page}", String.valueOf(maxPage)));

        ProfileHolder holder = new ProfileHolder(ProfileHolder.ViewType.CATEGORY_DETAIL, target, category, clampedPage);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // 1. Isi background filler terlebih dahulu (mendukung 'slots' pada navigation.filler & multi-filler 'filler_items')
        applyFillerConfig(
                inv,
                target,
                detailConfig.getConfigurationSection("navigation.filler"),
                detailConfig.getConfigurationSection("filler_items"),
                size
        );

        int playerLvl = plugin.getLevelManager().getLevel(target.getUniqueId(), category);
        double playerXpVal = plugin.getLevelManager().getXp(target.getUniqueId(), category);

        // 2. Render Level Road pada daftar slot yang dikonfigurasi
        for (int i = 0; i < levelsPerPage; i++) {
            int slot = configuredSlots.get(i);
            if (slot < 0 || slot >= size) continue;

            int currentLevelNum = (clampedPage - 1) * levelsPerPage + i + 1;
            if (currentLevelNum > maxLvl) {
                continue;
            }

            long reqXp = plugin.getLevelManager().getRequiredXp(currentLevelNum);
            double remainingXp = Math.max(0.0, reqXp - playerXpVal);
            double pct = Math.min(100.0, (playerXpVal / (double) reqXp) * 100.0);

            // Cek milestone reward pada level ini
            ConfigurationSection milestoneSec = plugin.getLevelManager().getMilestoneConfig(category, currentLevelNum);
            boolean isMilestone = milestoneSec != null;

            String stateKey;
            if (currentLevelNum < playerLvl || (currentLevelNum == playerLvl && playerLvl >= maxLvl)) {
                stateKey = "completed";
            } else if (currentLevelNum == playerLvl) {
                stateKey = "current";
            } else {
                stateKey = "locked";
            }

            ConfigurationSection stateSec = detailConfig.getConfigurationSection("items." + stateKey);

            // Tentukan Material: jika milestone memiliki icon khusus (selain DEFAULT/NONE/SAME/GLASS), gunakan icon tersebut.
            // Jika icon tidak diisi atau diset DEFAULT/NONE, gunakan material status glass yang sama seperti level lainnya.
            Material mat = null;
            if (isMilestone && milestoneSec.contains("icon")) {
                String iconStr = milestoneSec.getString("icon");
                if (iconStr != null && !iconStr.trim().isEmpty()
                        && !iconStr.equalsIgnoreCase("DEFAULT")
                        && !iconStr.equalsIgnoreCase("NONE")
                        && !iconStr.equalsIgnoreCase("GLASS")
                        && !iconStr.equalsIgnoreCase("SAME")) {
                    mat = Material.matchMaterial(iconStr.trim());
                }
            }
            if (mat == null) {
                String matStr = stateSec != null ? stateSec.getString("material", "LIME_STAINED_GLASS_PANE") : "LIME_STAINED_GLASS_PANE";
                mat = Material.matchMaterial(matStr);
            }
            if (mat == null) mat = Material.LIME_STAINED_GLASS_PANE;

            // Stack amount diatur sesuai angka level (maks 64 per stack agar angka terlihat natural)
            int amount = Math.min(64, Math.max(1, currentLevelNum));
            ItemStack levelItem = new ItemStack(mat, amount);
            ItemMeta meta = levelItem.getItemMeta();

            if (meta != null) {
                String nameRaw = stateSec != null ? stateSec.getString("name", "Level {level}") : "Level {level}";
                if (isMilestone && milestoneSec.contains("display_name")) {
                    String dName = milestoneSec.getString("display_name");
                    if (dName != null && !dName.trim().isEmpty() && !dName.equalsIgnoreCase("DEFAULT")) {
                        nameRaw = dName;
                    }
                }
                meta.setDisplayName(ColorUtils.color(target, nameRaw.replace("{level}", String.valueOf(currentLevelNum))));

                // Siapkan reward lore
                List<String> rewardLines = plugin.getLevelManager().getLevelRewardLore(category, currentLevelNum);
                String pBar = plugin.getLevelManager().getProgressBar(target.getUniqueId(), category, 10, "■", "&a", "&7");

                List<String> rawLore = stateSec != null ? stateSec.getStringList("lore") : List.of();
                List<String> lore = new ArrayList<>();

                for (String line : rawLore) {
                    if (line.contains("{reward_lore}")) {
                        if (rewardLines != null && !rewardLines.isEmpty()) {
                            for (String rLine : rewardLines) {
                                lore.add(ColorUtils.color(target, rLine));
                            }
                        } else {
                            lore.add(ColorUtils.color("&7-"));
                        }
                        continue;
                    }

                    double displayCurXp = stateKey.equals("completed") ? reqXp : (stateKey.equals("locked") ? 0.0 : playerXpVal);
                    double displayRemXp = stateKey.equals("completed") ? 0.0 : (stateKey.equals("locked") ? reqXp : remainingXp);
                    double displayPct = stateKey.equals("completed") ? 100.0 : (stateKey.equals("locked") ? 0.0 : pct);

                    String formatted = line
                            .replace("{level}", String.valueOf(currentLevelNum))
                            .replace("{req_xp}", String.format(Locale.US, "%,d", reqXp))
                            .replace("{current_xp}", String.format(Locale.US, "%,.0f", displayCurXp))
                            .replace("{remaining_xp}", String.format(Locale.US, "%,.0f", displayRemXp))
                            .replace("{percent}", String.format(Locale.US, "%.1f", displayPct))
                            .replace("{progress_bar}", pBar);
                    lore.add(ColorUtils.color(target, formatted));
                }
                meta.setLore(lore);
                levelItem.setItemMeta(meta);
            }

            inv.setItem(slot, levelItem);
        }

        // Tombol Previous Page (hanya muncul jika bukan halaman 1)
        if (clampedPage > 1 && detailConfig.isConfigurationSection("navigation.previous_page")) {
            ConfigurationSection prevSec = detailConfig.getConfigurationSection("navigation.previous_page");
            int prevSlot = prevSec.getInt("slot", 29);
            if (prevSlot >= 0 && prevSlot < size) {
                inv.setItem(prevSlot, buildNavButton(target, prevSec, clampedPage - 1));
            }
        }

        // Tombol Kembali ke Profil Utama (Back Button)
        if (detailConfig.isConfigurationSection("navigation.back_button")) {
            ConfigurationSection backSec = detailConfig.getConfigurationSection("navigation.back_button");
            int backSlot = backSec.getInt("slot", 31);
            if (backSlot >= 0 && backSlot < size) {
                inv.setItem(backSlot, buildSimpleItem(target, backSec, 1));
            }
        }

        // Tombol Next Page (hanya muncul jika masih ada halaman berikutnya)
        if (clampedPage < maxPage && detailConfig.isConfigurationSection("navigation.next_page")) {
            ConfigurationSection nextSec = detailConfig.getConfigurationSection("navigation.next_page");
            int nextSlot = nextSec.getInt("slot", 33);
            if (nextSlot >= 0 && nextSlot < size) {
                inv.setItem(nextSlot, buildNavButton(target, nextSec, clampedPage + 1));
            }
        }

        viewer.openInventory(inv);

        // Sound effect open
        String soundOpen = detailConfig.getString("sound_open");
        plugin.getLevelManager().playSound(viewer, soundOpen);
    }

    private void applyFillerConfig(Inventory inv, OfflinePlayer player, ConfigurationSection singleFiller,
                                   ConfigurationSection fillerItemsSec, int size) {
        applySingleFiller(inv, player, singleFiller, size, true);

        if (fillerItemsSec != null) {
            for (String key : fillerItemsSec.getKeys(false)) {
                applySingleFiller(inv, player, fillerItemsSec.getConfigurationSection(key), size, false);
            }
        }
    }

    private void applySingleFiller(Inventory inv, OfflinePlayer player, ConfigurationSection sec, int size, boolean fillAllIfOmitted) {
        if (sec == null || !sec.getBoolean("enabled", true)) return;

        ItemStack fillerItem = buildSimpleItem(player, sec, 1);
        if (fillerItem.getType().isAir()) return;

        boolean hasExplicitSlots = sec.contains("slots") || sec.contains("slot");
        if (!hasExplicitSlots) {
            if (!fillAllIfOmitted) return;
            for (int i = 0; i < size; i++) {
                inv.setItem(i, fillerItem);
            }
            return;
        }

        for (int slot : parseSlots(sec)) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, fillerItem);
            }
        }
    }

    private ItemStack buildSimpleItem(ConfigurationSection sec, int amount) {
        return buildSimpleItem(null, sec, amount);
    }

    private ItemStack buildSimpleItem(OfflinePlayer player, ConfigurationSection sec, int amount) {
        if (sec == null) return new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        String matStr = sec.getString("material", "GRAY_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int cmd = sec.getInt("custom_model_data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);

            String nameRaw = sec.getString("name", " ");
            meta.setDisplayName(ColorUtils.color(player, nameRaw));

            List<String> rawLore = sec.getStringList("lore");
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : rawLore) {
                    lore.add(ColorUtils.color(player, line));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildNavButton(OfflinePlayer player, ConfigurationSection sec, int targetPage) {
        String matStr = sec.getString("material", "ARROW");
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.ARROW;

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String nameRaw = sec.getString("name", "Halaman " + targetPage);
            meta.setDisplayName(ColorUtils.color(player, nameRaw.replace("{page}", String.valueOf(targetPage)).replace("{prev_page}", String.valueOf(targetPage)).replace("{next_page}", String.valueOf(targetPage))));

            List<String> rawLore = sec.getStringList("lore");
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : rawLore) {
                    lore.add(ColorUtils.color(player, line.replace("{page}", String.valueOf(targetPage)).replace("{prev_page}", String.valueOf(targetPage)).replace("{next_page}", String.valueOf(targetPage))));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public static List<Integer> parseSlots(ConfigurationSection config) {
        List<Integer> result = new ArrayList<>();
        if (config == null) return result;

        if (config.contains("slots")) {
            if (config.isList("slots")) {
                List<?> list = config.getList("slots");
                if (list != null) {
                    for (Object item : list) {
                        if (item instanceof Number num) {
                            result.add(num.intValue());
                        } else if (item instanceof String str) {
                            parseSlotEntry(str, result);
                        }
                    }
                }
            } else if (config.isInt("slots")) {
                result.add(config.getInt("slots"));
            } else if (config.isString("slots")) {
                parseSlotEntry(config.getString("slots"), result);
            }
        } else if (config.contains("slot")) {
            if (config.isInt("slot")) {
                result.add(config.getInt("slot"));
            } else if (config.isString("slot")) {
                parseSlotEntry(config.getString("slot"), result);
            }
        }

        return result;
    }

    private static void parseSlotEntry(String str, List<Integer> result) {
        if (str == null || str.trim().isEmpty()) return;
        String[] parts = str.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length == 2) {
                    try {
                        int start = Integer.parseInt(range[0].trim());
                        int end = Integer.parseInt(range[1].trim());
                        if (start <= end) {
                            for (int i = start; i <= end; i++) result.add(i);
                        } else {
                            for (int i = start; i >= end; i--) result.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                try {
                    result.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {}
            }
        }
    }
}


package id.cyayo.gathering.level;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.hook.CyayoProfilesHook;
import id.cyayo.gathering.storage.DatabaseManager;
import id.cyayo.gathering.util.ColorUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LevelManager {

    private final CyayoGathering plugin;
    private final File levelsConfigFile;
    private YamlConfiguration levelsConfig;

    // UUID -> Category -> Level
    private final Map<UUID, Map<String, Integer>> playerLevels = new ConcurrentHashMap<>();
    // UUID -> Category -> Current XP
    private final Map<UUID, Map<String, Double>> playerXp = new ConcurrentHashMap<>();
    // Dirty keys formatted as "uuid|category" pending SQL flush
    private final Set<String> dirtyEntries = ConcurrentHashMap.newKeySet();

    private record LevelEntrySnapshot(String dataUuid, String category, int level, double xp) {}

    public LevelManager(CyayoGathering plugin) {
        this.plugin = plugin;

        // 1. Load levels.yml configuration
        this.levelsConfigFile = new File(plugin.getDataFolder(), "levels.yml");
        if (!levelsConfigFile.exists()) {
            plugin.saveResource("levels.yml", false);
        }
        this.levelsConfig = YamlConfiguration.loadConfiguration(levelsConfigFile);

        // 2. Load player levels from database (auto-migrating legacy YAML if needed)
        loadData();

        // 3. Periodic dirty flush every 60 seconds (main-thread snapshot -> async single-writer DB)
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, 1200L, 1200L);
    }

    public void reloadConfig() {
        this.levelsConfig = YamlConfiguration.loadConfiguration(levelsConfigFile);
    }

    public boolean isEnabled() {
        return levelsConfig.getBoolean("enabled", true);
    }

    public int getMaxLevel() {
        return levelsConfig.getInt("max_level", 100);
    }

    public double getBaseXp() {
        return levelsConfig.getDouble("formula.base_xp", 100.0);
    }

    public double getCurveMultiplier() {
        return levelsConfig.getDouble("formula.curve_multiplier", 1.5);
    }

    /**
     * Menghitung kebutuhan XP untuk naik dari level tertentu ke level berikutnya.
     * Formula: round(base_xp * (level ^ curve_multiplier))
     */
    public long getRequiredXp(int level) {
        if (level <= 0) level = 1;
        double raw = getBaseXp() * Math.pow(level, getCurveMultiplier());
        return Math.max(1L, Math.round(raw));
    }

    public int getLevel(UUID uuid, String category) {
        if (uuid == null || category == null) return 1;
        return getLevelDirect(CyayoProfilesHook.resolveDataUuid(uuid), category.toLowerCase());
    }

    private int getLevelDirect(UUID dataUuid, String catLower) {
        Map<String, Integer> map = playerLevels.get(dataUuid);
        return map != null ? map.getOrDefault(catLower, 1) : 1;
    }

    public double getXp(UUID uuid, String category) {
        if (uuid == null || category == null) return 0.0;
        return getXpDirect(CyayoProfilesHook.resolveDataUuid(uuid), category.toLowerCase());
    }

    private double getXpDirect(UUID dataUuid, String catLower) {
        if (getLevelDirect(dataUuid, catLower) >= getMaxLevel()) return 0.0;
        Map<String, Double> map = playerXp.get(dataUuid);
        return map != null ? map.getOrDefault(catLower, 0.0) : 0.0;
    }

    public long getRequiredXpForNextLevel(UUID uuid, String category) {
        if (getLevel(uuid, category) >= getMaxLevel()) return 0L;
        return getRequiredXp(getLevel(uuid, category));
    }

    public double getProgressPercent(UUID uuid, String category) {
        int lvl = getLevel(uuid, category);
        if (lvl >= getMaxLevel()) return 100.0;
        double cur = getXp(uuid, category);
        long req = getRequiredXp(lvl);
        return Math.min(100.0, Math.max(0.0, (cur / (double) req) * 100.0));
    }

    public String getProgressBar(UUID uuid, String category, int length, String symbol, String compColor, String remColor) {
        double pct = getProgressPercent(uuid, category) / 100.0;
        int completed = (int) Math.round(pct * length);
        if (completed > length) completed = length;
        int remaining = Math.max(0, length - completed);

        StringBuilder sb = new StringBuilder();
        sb.append(compColor);
        for (int i = 0; i < completed; i++) {
            sb.append(symbol);
        }
        sb.append(remColor);
        for (int i = 0; i < remaining; i++) {
            sb.append(symbol);
        }
        return ColorUtils.color(sb.toString());
    }

    /**
     * Menambahkan XP ke pemain untuk kategori tertentu.
     */
    public void addXp(Player player, String category, double amount) {
        if (player == null || category == null || amount <= 0 || !isEnabled()) return;

        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(player.getUniqueId());
        String cat = category.toLowerCase();
        int currentLvl = getLevelDirect(dataUuid, cat);
        int maxLvl = getMaxLevel();

        // 1. Jika pemain SUDAH mencapai max level:
        // Tidak menambah XP lagi dan tidak memunculkan Action Bar!
        if (currentLvl >= maxLvl) {
            if (playerXp.containsKey(dataUuid) && playerXp.get(dataUuid).getOrDefault(cat, 0.0) > 0) {
                playerXp.get(dataUuid).put(cat, 0.0);
                dirtyEntries.add(dataUuid.toString() + "|" + cat);
            }
            return;
        }

        double finalAmount = amount * CyayoProfilesHook.getGatheringXpMultiplier(player.getUniqueId());
        double currentXpVal = getXpDirect(dataUuid, cat);

        // Tambah XP
        currentXpVal += finalAmount;

        // Loop Level Up (mendukung lonjakan XP banyak sekaligus)
        while (currentLvl < maxLvl) {
            long req = getRequiredXp(currentLvl);
            if (currentXpVal >= req) {
                currentXpVal -= req;
                int oldLvl = currentLvl;
                currentLvl++;
                handleLevelUp(player, category, oldLvl, currentLvl);
            } else {
                break;
            }
        }

        // Jika setelah proses level up pemain mencapai max level:
        if (currentLvl >= maxLvl) {
            currentXpVal = 0.0;
        }

        // Cek Action Bar notification: HANYA kirim jika belum max level (atau jika diizinkan di config)
        if (levelsConfig.getBoolean("action_bar.enabled", true)) {
            if (currentLvl < maxLvl) {
                sendActionBarNotification(player, category, finalAmount, currentXpVal, currentLvl);
            } else if (levelsConfig.getBoolean("action_bar.show_on_max_level", false)) {
                sendMaxLevelActionBar(player, category);
            }
        }

        // Simpan ke memory berdasarkan UUID Profil aktif & tandai dirty untuk SQL flush
        playerLevels.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>()).put(cat, currentLvl);
        playerXp.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>()).put(cat, currentXpVal);
        dirtyEntries.add(dataUuid.toString() + "|" + cat);
    }

    private void sendMaxLevelActionBar(Player player, String category) {
        String format = levelsConfig.getString("action_bar.max_level_format", "none");
        if (ColorUtils.isNone(format)) return;

        String msg = format
                .replace("{category}", capitalize(category))
                .replace("{max_level}", String.valueOf(getMaxLevel()));
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtils.color(player, msg)));
        } catch (Throwable ignored) {}
    }

    private void sendActionBarNotification(Player player, String category, double gainedXp, double currentXp, int currentLvl) {
        String format = levelsConfig.getString("action_bar.format", "<color:#55FF55>+{xp} {category} XP</color> &8(&e{current_xp}&7/&a{req_xp}&8)");
        if (ColorUtils.isNone(format)) return;

        long req = getRequiredXp(currentLvl);
        double pct = Math.min(100.0, (currentXp / (double) req) * 100.0);

        int barLen = levelsConfig.getInt("action_bar.progress_bar.length", 10);
        String barSymbol = levelsConfig.getString("action_bar.progress_bar.symbol", "■");
        String compCol = levelsConfig.getString("action_bar.progress_bar.completed_color", "&a");
        String remCol = levelsConfig.getString("action_bar.progress_bar.remaining_color", "&7");

        int completed = (int) Math.round((pct / 100.0) * barLen);
        if (completed > barLen) completed = barLen;
        int remaining = Math.max(0, barLen - completed);
        StringBuilder bar = new StringBuilder();
        bar.append(compCol);
        for (int i = 0; i < completed; i++) bar.append(barSymbol);
        bar.append(remCol);
        for (int i = 0; i < remaining; i++) bar.append(barSymbol);

        String msg = format
                .replace("{xp}", String.format(Locale.US, "%.0f", gainedXp))
                .replace("{category}", capitalize(category))
                .replace("{current_xp}", String.format(Locale.US, "%,.0f", currentXp))
                .replace("{req_xp}", String.format(Locale.US, "%,d", req))
                .replace("{percent}", String.format(Locale.US, "%.1f", pct))
                .replace("{progress_bar}", bar.toString());

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ColorUtils.color(player, msg)));
        } catch (Throwable ignored) {}
    }

    private void handleLevelUp(Player player, String category, int oldLevel, int newLevel) {
        // 1. Panggil Bukkit Event
        PlayerGatherLevelUpEvent event = new PlayerGatherLevelUpEvent(player, category, oldLevel, newLevel);
        Bukkit.getPluginManager().callEvent(event);

        String catCap = capitalize(category);

        // 2. Cek apakah level ini memiliki konfigurasi hadiah khusus (Specific Level / Milestone)
        ConfigurationSection milestoneSec = levelsConfig.getConfigurationSection("rewards.milestones." + category.toLowerCase() + "." + newLevel);
        boolean isSpecificLevel = (milestoneSec != null);
        ConfigurationSection perLevelSec = levelsConfig.getConfigurationSection("rewards.per_level");

        // 3. Mainkan Suara (Prioritas suara khusus level tertentu -> fallback ke per_level)
        String soundKey = null;
        if (isSpecificLevel && milestoneSec.contains("sound")) {
            soundKey = milestoneSec.getString("sound");
        } else if (perLevelSec != null) {
            soundKey = perLevelSec.getString("sound", "ENTITY_PLAYER_LEVELUP:1.0:1.2");
        }
        if (soundKey != null && !ColorUtils.isNone(soundKey)) {
            playSound(player, soundKey);
        }

        // 4. Tampilkan Title di Layar (Prioritas title khusus level tertentu -> fallback ke per_level)
        String titleRaw = null;
        String subRaw = null;
        if (isSpecificLevel && (milestoneSec.contains("title") || milestoneSec.contains("subtitle"))) {
            titleRaw = milestoneSec.getString("title");
            subRaw = milestoneSec.getString("subtitle", "");
        } else if (perLevelSec != null) {
            titleRaw = perLevelSec.getString("title", "<gradient:#FFD700:#FFA500><bold>LEVEL UP!</bold></gradient>");
            subRaw = perLevelSec.getString("subtitle", "&7Kemampuan &e{category}&7 naik ke &aLevel {level}&7!");
        }
        if (titleRaw != null && !ColorUtils.isNone(titleRaw)) {
            String title = titleRaw.replace("{player}", player.getName()).replace("{category}", catCap).replace("{level}", String.valueOf(newLevel));
            String sub = (subRaw != null && !ColorUtils.isNone(subRaw)) ? subRaw.replace("{player}", player.getName()).replace("{category}", catCap).replace("{level}", String.valueOf(newLevel)) : "";
            player.sendTitle(ColorUtils.color(player, title), ColorUtils.color(player, sub), 10, 40, 15);
        }

        // 5. Kirim Pesan Chat (Mendukung String tunggal maupun List of Strings)
        // Jika level ini memiliki chat_message di milestone/specific level, kirim itu.
        // Jika tidak diset di milestone, otomatis fallback ke chat_message per_level.
        sendLevelChatMessage(player, milestoneSec, perLevelSec, category, oldLevel, newLevel);

        // 6. Broadcast jika dikonfigurasi (Mendukung teks tunggal maupun List of Strings)
        // Jika level ini memiliki broadcast di milestone, gunakan itu.
        // Jika tidak diset di milestone, fallback ke broadcast per_level (jika ada).
        // CATATAN: Broadcast ini HANYA dikirimkan ke pemain lain di server (dan console),
        // pemain yang naik level TIDAK akan melihat broadcast ini agar tidak double dengan pesan chat pribadinya.
        List<String> bcastLines = getMessageLines(milestoneSec, "broadcast");
        if (bcastLines == null && perLevelSec != null) {
            bcastLines = getMessageLines(perLevelSec, "broadcast");
        }
        if (bcastLines != null && !bcastLines.isEmpty()) {
            for (String line : bcastLines) {
                if (line == null || ColorUtils.isNone(line)) continue;
                String bcast = line.replace("{player}", player.getName())
                        .replace("{category}", catCap)
                        .replace("{level}", String.valueOf(newLevel));
                String colored = ColorUtils.color(player, bcast);
                String finalBcast = colored.trim().isEmpty() ? " " : colored;

                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getUniqueId().equals(player.getUniqueId())) continue;
                    online.sendMessage(finalBcast);
                }
                Bukkit.getConsoleSender().sendMessage(finalBcast);
            }
        }

        // 7. Jalankan Hadiah Commands
        // PENTING: Level yang terdaftar sebagai hadiah khusus (milestone)
        // HANYA menjalankan hadiah khusus ini dan TIDAK menjalankan hadiah per_level agar tidak double!
        if (isSpecificLevel) {
            // Jalankan commands khusus level ini
            List<String> mCmds = milestoneSec.getStringList("commands");
            for (String rawCmd : mCmds) {
                if (rawCmd == null || rawCmd.trim().isEmpty() || ColorUtils.isNone(rawCmd)) continue;
                String cmd = rawCmd.replace("{player}", player.getName())
                        .replace("{category}", catCap)
                        .replace("{level}", String.valueOf(newLevel));
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            }
        } else if (perLevelSec != null) {
            // Jalankan commands per-level HANYA jika bukan level khusus
            List<String> perLevelCmds = perLevelSec.getStringList("commands");
            for (String rawCmd : perLevelCmds) {
                if (rawCmd == null || rawCmd.trim().isEmpty() || ColorUtils.isNone(rawCmd)) continue;
                String cmd = rawCmd.replace("{player}", player.getName())
                        .replace("{category}", catCap)
                        .replace("{level}", String.valueOf(newLevel));
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            }
        }
    }

    private void sendLevelChatMessage(Player player, ConfigurationSection primarySec, ConfigurationSection fallbackSec, String category, int oldLevel, int newLevel) {
        if (player == null) return;

        List<String> messages = getMessageLines(primarySec, "chat_message");
        if (messages == null && fallbackSec != null) {
            messages = getMessageLines(fallbackSec, "chat_message");
        }

        if (messages == null || messages.isEmpty()) return;

        String catCap = capitalize(category);
        for (String line : messages) {
            if (line == null || ColorUtils.isNone(line)) continue;
            String formatted = line
                    .replace("{player}", player.getName())
                    .replace("{category}", catCap)
                    .replace("{level}", String.valueOf(newLevel))
                    .replace("{old_level}", String.valueOf(oldLevel));
            String colored = ColorUtils.color(player, formatted);
            player.sendMessage(colored.trim().isEmpty() ? " " : colored);
        }
    }

    private List<String> getMessageLines(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) return null;

        if (section.isList(path)) {
            List<String> raw = section.getStringList(path);
            List<String> filtered = new ArrayList<>();
            for (String s : raw) {
                if (s != null && !ColorUtils.isNone(s)) {
                    filtered.add(s);
                }
            }
            return filtered;
        } else if (section.isString(path)) {
            String str = section.getString(path);
            if (str == null || ColorUtils.isNone(str)) return Collections.emptyList();
            return List.of(str);
        }
        return null;
    }

    public ConfigurationSection getMilestoneConfig(String category, int level) {
        if (levelsConfig == null) return null;
        return levelsConfig.getConfigurationSection("rewards.milestones." + category.toLowerCase() + "." + level);
    }

    public List<String> getLevelRewardLore(String category, int level) {
        ConfigurationSection mSec = getMilestoneConfig(category, level);
        List<String> lore = null;
        if (mSec != null && mSec.contains("display_lore")) {
            lore = mSec.getStringList("display_lore");
        } else if (levelsConfig != null) {
            lore = levelsConfig.getStringList("rewards.per_level.display_lore");
        }
        if (lore == null || lore.isEmpty()) return Collections.emptyList();
        List<String> filtered = new ArrayList<>();
        for (String s : lore) {
            if (s != null && !ColorUtils.isNone(s)) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public void playSound(Player player, String soundStr) {
        if (player == null || soundStr == null || soundStr.trim().isEmpty() || ColorUtils.isNone(soundStr)) return;

        Location loc = player.getLocation();
        float volume = 1.0f;
        float pitch = 1.0f;
        String soundName = soundStr.trim();

        if (soundName.contains(";")) {
            String[] parts = soundName.split(";");
            soundName = parts[0].trim();
            if (parts.length > 1) {
                try { volume = Float.parseFloat(parts[1].trim()); } catch (NumberFormatException ignored) {}
            }
            if (parts.length > 2) {
                try { pitch = Float.parseFloat(parts[2].trim()); } catch (NumberFormatException ignored) {}
            }
        } else if (soundName.contains(":")) {
            String[] parts = soundName.split(":");
            if (parts.length == 3 && isNumber(parts[1]) && isNumber(parts[2])) {
                soundName = parts[0].trim();
                try { volume = Float.parseFloat(parts[1].trim()); } catch (NumberFormatException ignored) {}
                try { pitch = Float.parseFloat(parts[2].trim()); } catch (NumberFormatException ignored) {}
            } else if (parts.length == 4 && isNumber(parts[2]) && isNumber(parts[3])) {
                soundName = parts[0].trim() + ":" + parts[1].trim();
                try { volume = Float.parseFloat(parts[2].trim()); } catch (NumberFormatException ignored) {}
                try { pitch = Float.parseFloat(parts[3].trim()); } catch (NumberFormatException ignored) {}
            }
        }

        // Custom string sound (contains colon or dot)
        if (soundName.contains(":") || soundName.contains(".")) {
            try {
                player.playSound(loc, soundName.toLowerCase(), volume, pitch);
                return;
            } catch (Throwable ignored) {}
        }

        // Bukkit enum sound
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(loc, sound, volume, pitch);
            return;
        } catch (IllegalArgumentException ignored) {} catch (Throwable ignored) {}

        // Fallback string
        try {
            player.playSound(loc, soundName.toLowerCase(), volume, pitch);
        } catch (Throwable ignored) {}
    }

    private boolean isNumber(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        try {
            Float.parseFloat(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void setLevel(UUID uuid, String category, int level) {
        if (uuid == null || category == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        String cat = category.toLowerCase();
        int targetLvl = Math.max(1, Math.min(getMaxLevel(), level));
        playerLevels.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .put(cat, targetLvl);
        if (targetLvl >= getMaxLevel()) {
            playerXp.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>()).put(cat, 0.0);
        }
        dirtyEntries.add(dataUuid.toString() + "|" + cat);
        saveData();
    }

    public void setXp(UUID uuid, String category, double xp) {
        if (uuid == null || category == null) return;
        UUID dataUuid = CyayoProfilesHook.resolveDataUuid(uuid);
        String cat = category.toLowerCase();
        playerXp.computeIfAbsent(dataUuid, k -> new ConcurrentHashMap<>())
                .put(cat, Math.max(0.0, xp));
        dirtyEntries.add(dataUuid.toString() + "|" + cat);
        saveData();
    }

    public void deleteData(UUID profileUuid) {
        if (profileUuid == null) return;
        playerLevels.remove(profileUuid);
        playerXp.remove(profileUuid);
        String prefix = profileUuid.toString() + "|";
        dirtyEntries.removeIf(k -> k.startsWith(prefix));

        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> {
                try (Connection conn = db.getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM cyayo_gathering_levels WHERE data_uuid = ?")) {
                    stmt.setString(1, profileUuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to delete gathering levels for " + profileUuid + ": " + e.getMessage());
                }
            });
        }
    }

    private void loadData() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) return;

        try (Connection conn = db.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT data_uuid, category, level, xp FROM cyayo_gathering_levels");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("data_uuid"));
                        String cat = rs.getString("category").toLowerCase();
                        int lvl = rs.getInt("level");
                        double xpVal = rs.getDouble("xp");
                        playerLevels.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(cat, lvl);
                        playerXp.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(cat, xpVal);
                    } catch (Exception ignored) {}
                }
            }

            // Auto-migrate legacy YAML data/player_levels.yml if table is empty
            File legacyFile = new File(plugin.getDataFolder(), "data/player_levels.yml");
            if (legacyFile.exists() && db.isTableEmpty(conn, "cyayo_gathering_levels")) {
                YamlConfiguration legacyCfg = YamlConfiguration.loadConfiguration(legacyFile);
                ConfigurationSection sec = legacyCfg.getConfigurationSection("players");
                List<LevelEntrySnapshot> snapshots = new ArrayList<>();
                if (sec != null) {
                    for (String uuidStr : sec.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            ConfigurationSection pSec = sec.getConfigurationSection(uuidStr);
                            if (pSec == null) continue;

                            for (String cat : pSec.getKeys(false)) {
                                String catLower = cat.toLowerCase();
                                int lvl = pSec.getInt(cat + ".level", 1);
                                double xpVal = pSec.getDouble(cat + ".xp", 0.0);
                                playerLevels.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(catLower, lvl);
                                playerXp.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(catLower, xpVal);
                                snapshots.add(new LevelEntrySnapshot(uuid.toString(), catLower, lvl, xpVal));
                            }
                        } catch (Exception ignored) {}
                    }
                }
                boolean migratedOk = snapshots.isEmpty() || writeLevelsBatch(conn, db, snapshots);
                if (migratedOk) {
                    db.markMigratedFile(legacyFile);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load gathering levels from database: " + e.getMessage());
        }
    }

    public void saveData() {
        List<LevelEntrySnapshot> snapshots = drainDirtySnapshots();
        if (snapshots.isEmpty()) return;
        DatabaseManager db = plugin.getDatabaseManager();
        if (db != null) {
            db.executeAsync(() -> writeLevelsBatch(snapshots));
        }
    }

    public void saveDataSync() {
        List<LevelEntrySnapshot> snapshots = drainDirtySnapshots();
        if (!snapshots.isEmpty()) {
            writeLevelsBatch(snapshots);
        }
    }

    public void saveIfDirty() {
        if (!dirtyEntries.isEmpty()) {
            saveData();
        }
    }

    private List<LevelEntrySnapshot> drainDirtySnapshots() {
        if (dirtyEntries.isEmpty()) return List.of();
        List<String> keys = new ArrayList<>();
        for (String key : dirtyEntries) {
            if (dirtyEntries.remove(key)) {
                keys.add(key);
            }
        }
        List<LevelEntrySnapshot> snapshots = new ArrayList<>(keys.size());
        for (String key : keys) {
            String[] parts = key.split("\\|", 2);
            if (parts.length != 2) continue;
            try {
                UUID uuid = UUID.fromString(parts[0]);
                String cat = parts[1];
                snapshots.add(new LevelEntrySnapshot(
                        parts[0],
                        cat,
                        getLevelDirect(uuid, cat),
                        getXpDirect(uuid, cat)
                ));
            } catch (IllegalArgumentException ignored) {}
        }
        return snapshots;
    }

    private boolean writeLevelsBatch(List<LevelEntrySnapshot> snapshots) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || snapshots.isEmpty()) return true;
        try (Connection conn = db.getConnection()) {
            return writeLevelsBatch(conn, db, snapshots);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save gathering levels to database: " + e.getMessage());
            return false;
        }
    }

    private boolean writeLevelsBatch(Connection conn, DatabaseManager db, List<LevelEntrySnapshot> snapshots) {
        if (conn == null || db == null || snapshots.isEmpty()) return true;

        String upsertSql = db.isMySQL()
                ? "INSERT INTO cyayo_gathering_levels (data_uuid, category, level, xp) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp)"
                : "INSERT INTO cyayo_gathering_levels (data_uuid, category, level, xp) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(data_uuid, category) DO UPDATE SET level = excluded.level, xp = excluded.xp";

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                for (LevelEntrySnapshot snap : snapshots) {
                    stmt.setString(1, snap.dataUuid);
                    stmt.setString(2, snap.category);
                    stmt.setInt(3, snap.level);
                    stmt.setDouble(4, snap.xp);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save gathering levels to database: " + e.getMessage());
            return false;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}


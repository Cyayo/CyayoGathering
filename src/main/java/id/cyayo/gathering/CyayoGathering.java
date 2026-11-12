package id.cyayo.gathering;

import id.cyayo.gathering.command.GatheringCommand;
import id.cyayo.gathering.command.GatheringTabCompleter;
import id.cyayo.gathering.command.GatheringWandListener;
import id.cyayo.gathering.entity.InteractionListener;
import id.cyayo.gathering.entity.NodeInteractionController;
import id.cyayo.gathering.gui.ProfileGUIListener;
import id.cyayo.gathering.gui.ProfileGUIManager;
import id.cyayo.gathering.hologram.GatheringHologramManager;
import id.cyayo.gathering.hook.CyayoProfilesHook;
import id.cyayo.gathering.hook.GatheringExpansion;
import id.cyayo.gathering.hook.MMOItemsHook;
import id.cyayo.gathering.level.LevelManager;
import id.cyayo.gathering.manager.NodeConfigManager;
import id.cyayo.gathering.manager.NodeDataManager;
import id.cyayo.gathering.manager.NodeLifecycleManager;
import id.cyayo.gathering.packet.InstancedDropManager;
import id.cyayo.gathering.rng.GatheringRngEngine;
import id.cyayo.gathering.rng.LuckManager;
import id.cyayo.gathering.session.GatherManager;
import id.cyayo.gathering.stats.StatsManager;
import id.cyayo.gathering.storage.DatabaseManager;
import id.cyayo.gathering.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class CyayoGathering extends JavaPlugin {

    private static CyayoGathering instance;

    private YamlConfiguration messagesConfig;
    private File messagesFile;

    private DatabaseManager databaseManager;
    private LuckManager luckManager;
    private GatheringRngEngine rngEngine;
    private NodeConfigManager nodeConfigManager;
    private NodeInteractionController interactionController;
    private GatheringHologramManager hologramManager;
    private InstancedDropManager dropManager;
    private GatherManager gatherManager;
    private StatsManager statsManager;
    private LevelManager levelManager;
    private ProfileGUIManager profileGUIManager;
    private NodeDataManager nodeDataManager;
    private NodeLifecycleManager lifecycleManager;
    private MMOItemsHook mmoItemsHook;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Resources
        saveDefaultConfig();
        saveDefaultResource("messages.yml");
        saveDefaultResource("levels.yml");
        saveDefaultResource("gui/profile.yml");
        saveDefaultResource("gui/category_detail.yml");
        saveDefaultResource("nodes/mining_agate.yml");
        saveDefaultResource("nodes/woodcutting_oak.yml");
        saveDefaultResource("nodes/fishing_mystic.yml");
        saveDefaultResource("nodes/farming_wheat.yml");

        loadMessages();

        // 2. Database Pool & Tables (must initialize before data managers)
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // 3. Core Engines & Managers
        this.luckManager = new LuckManager(this);
        this.rngEngine = new GatheringRngEngine(this);
        this.nodeConfigManager = new NodeConfigManager(this);
        this.interactionController = new NodeInteractionController(this);
        this.hologramManager = new GatheringHologramManager(this);
        this.dropManager = new InstancedDropManager(this);
        this.statsManager = new StatsManager(this);
        this.levelManager = new LevelManager(this);
        this.profileGUIManager = new ProfileGUIManager(this);
        this.gatherManager = new GatherManager(this);
        this.nodeDataManager = new NodeDataManager(this);
        this.lifecycleManager = new NodeLifecycleManager(this);

        // 3. Hooks
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            this.mmoItemsHook = new MMOItemsHook(this);
            mmoItemsHook.registerCustomStats();
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new GatheringExpansion(this).register();
        }

        CyayoProfilesHook.registerListener(this);

        // 4. Commands & TabCompleters
        GatheringCommand cmd = new GatheringCommand(this);
        if (getCommand("cyayogathering") != null) {
            getCommand("cyayogathering").setExecutor(cmd);
            getCommand("cyayogathering").setTabCompleter(new GatheringTabCompleter(this));
        }

        // 5. Event Listeners
        Bukkit.getPluginManager().registerEvents(new InteractionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GatheringWandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProfileGUIListener(this), this);

        // 6. Spawn visual nodes with 1-second delay so worlds and FancyHolograms are fully ready
        Bukkit.getScheduler().runTaskLater(this, () -> {
            nodeDataManager.respawnAllNodes();
        }, 20L);

        getLogger().info("CyayoGathering v" + getDescription().getVersion() + " has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (gatherManager != null) gatherManager.cleanup();
        if (dropManager != null) dropManager.cleanup();
        if (lifecycleManager != null) lifecycleManager.cleanup();
        if (hologramManager != null) hologramManager.cleanup();
        if (interactionController != null) interactionController.cleanup();
        if (databaseManager != null) databaseManager.flushPendingWritesSync();
        if (luckManager != null) luckManager.saveDataSync();
        if (levelManager != null) levelManager.saveDataSync();
        if (nodeDataManager != null) nodeDataManager.saveSync();
        if (statsManager != null) statsManager.saveSync();
        if (databaseManager != null) databaseManager.shutdown();

        getLogger().info("CyayoGathering has been disabled.");
    }

    public void reload() {
        if (databaseManager != null) {
            databaseManager.flushPendingWritesSync();
        }
        if (nodeDataManager != null) {
            nodeDataManager.saveSync();
        }
        if (levelManager != null) {
            levelManager.saveDataSync();
        }
        if (luckManager != null) {
            luckManager.saveDataSync();
        }
        if (statsManager != null) {
            statsManager.saveSync();
        }
        reloadConfig();
        loadMessages();
        nodeConfigManager.load();
        if (levelManager != null) {
            levelManager.reloadConfig();
        }
        if (profileGUIManager != null) {
            profileGUIManager.reloadConfig();
        }
        if (gatherManager != null) {
            gatherManager.restartGatheringLoop();
        }
        if (nodeDataManager != null) {
            nodeDataManager.load();
            nodeDataManager.respawnAllNodes();
        }
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String path) {
        return getMessage(path, "");
    }

    public String getMessage(String path, String def) {
        if (messagesConfig == null) return def;
        return messagesConfig.getString(path, def);
    }

    public boolean isNone(String text) {
        return ColorUtils.isNone(text);
    }

    public void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || ColorUtils.isNone(message)) return;
        if (message.trim().isEmpty()) return;
        org.bukkit.OfflinePlayer player = sender instanceof org.bukkit.OfflinePlayer p ? p : null;
        String colored = ColorUtils.color(player, message);
        if (!colored.isEmpty()) {
            sender.sendMessage(colored);
        }
    }

    public void sendPrefixedMessage(CommandSender sender, String message) {
        if (sender == null || message == null || ColorUtils.isNone(message)) return;
        if (message.trim().isEmpty()) return;
        String prefix = getMessage("prefix");
        if (ColorUtils.isNone(prefix)) prefix = "";
        org.bukkit.OfflinePlayer player = sender instanceof org.bukkit.OfflinePlayer p ? p : null;
        String colored = ColorUtils.color(player, prefix + message);
        if (!colored.isEmpty()) {
            sender.sendMessage(colored);
        }
    }

    private void saveDefaultResource(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    public static CyayoGathering getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LuckManager getLuckManager() { return luckManager; }
    public GatheringRngEngine getRngEngine() { return rngEngine; }
    public NodeConfigManager getNodeConfigManager() { return nodeConfigManager; }
    public NodeInteractionController getInteractionController() { return interactionController; }
    public GatheringHologramManager getHologramManager() { return hologramManager; }
    public InstancedDropManager getDropManager() { return dropManager; }
    public GatherManager getGatherManager() { return gatherManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public ProfileGUIManager getProfileGUIManager() { return profileGUIManager; }
    public NodeDataManager getNodeDataManager() { return nodeDataManager; }
}

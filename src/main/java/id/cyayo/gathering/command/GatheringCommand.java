package id.cyayo.gathering.command;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import id.cyayo.gathering.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GatheringCommand implements CommandExecutor {

    private final CyayoGathering plugin;
    public static final String WAND_KEY = "gathering_wand";

    public GatheringCommand(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    private void sendPrefixed(CommandSender sender, String message) {
        plugin.sendPrefixedMessage(sender, message);
    }

    private void send(CommandSender sender, String message) {
        plugin.sendMessage(sender, message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Subcommand profile / menu can be accessed by players with cyayogathering.profile permission
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (sub.equals("profile") || sub.equals("menu")) {
                handleProfile(sender, args);
                return true;
            }
        }

        // STRICT ADMIN & CONSOLE CHECK for all administrative subcommands
        if (!sender.hasPermission("cyayogathering.admin") && !sender.isOp() && !(sender instanceof ConsoleCommandSender)) {
            sendPrefixed(sender, plugin.getMessage("messages.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "move" -> handleMove(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "wand" -> handleWand(sender);
            case "near" -> handleNear(sender, args);
            case "tp" -> handleTp(sender, args);
            case "reset" -> handleReset(sender, args);
            case "stats", "count" -> handleStats(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "addxp" -> handleAddXp(sender, args);
            case "luck" -> handleLuck(sender, args);
            case "doublegather", "double", "doubledrop" -> handleDoubleGather(sender, args);
            case "speed", "gatherspeed", "gatheringspeed" -> handleSpeed(sender, args);
            case "list" -> handleList(sender);
            case "purgeholograms" -> handlePurge(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, ChatColor.RED + "Perintah ini hanya dapat dijalankan oleh player in-game.");
            return;
        }

        // 1. Buka profil diri sendiri
        if (args.length < 2) {
            if (!player.hasPermission("cyayogathering.profile") && !player.hasPermission("cyayogathering.admin") && !player.isOp()) {
                sendPrefixed(player, plugin.getMessage("messages.no_permission"));
                return;
            }
            plugin.getProfileGUIManager().openProfile(player, player);
            return;
        }

        // 2. Buka profil pemain lain
        if (!player.hasPermission("cyayogathering.profile.others") && !player.hasPermission("cyayogathering.admin") && !player.isOp()) {
            sendPrefixed(player, plugin.getMessage("messages.no_permission"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found", "<red>Player '<yellow>{player}</yellow>' has never played on this server!").replace("{player}", args[1]);
            sendPrefixed(player, msg);
            return;
        }

        plugin.getProfileGUIManager().openProfile(player, target);
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        // /cgath setlevel <player> <category> <level>
        if (args.length < 4) {
            send(sender, "&cPenggunaan: /cgath setlevel <player> <category> <level>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[1]);
            sendPrefixed(sender, msg);
            return;
        }

        String category = args[2].toLowerCase();
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sendPrefixed(sender, plugin.getMessage("admin.invalid_number", "&cNilai level tidak valid!"));
            return;
        }

        plugin.getLevelManager().setLevel(target.getUniqueId(), category, level);
        String msg = plugin.getMessage("admin.level_set", "&aBerhasil mengatur level {player} ({category}) ke Level {level}.")
                .replace("{player}", target.getName() != null ? target.getName() : args[1])
                .replace("{category}", category)
                .replace("{level}", String.valueOf(level));
        sendPrefixed(sender, msg);
    }

    private void handleAddXp(CommandSender sender, String[] args) {
        // /cgath addxp <player> <category> <amount>
        if (args.length < 4) {
            send(sender, "&cPenggunaan: /cgath addxp <player> <category> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sendPrefixed(sender, plugin.getMessage("admin.player_not_found").replace("{player}", args[1]));
            return;
        }

        String category = args[2].toLowerCase();
        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendPrefixed(sender, plugin.getMessage("admin.invalid_number", "&cNilai XP tidak valid!"));
            return;
        }

        plugin.getLevelManager().addXp(target, category, amount);
        String msg = plugin.getMessage("admin.xp_added", "&aBerhasil menambahkan {amount} XP {category} ke {player}.")
                .replace("{player}", target.getName())
                .replace("{category}", category)
                .replace("{amount}", String.format(Locale.US, "%.0f", amount));
        sendPrefixed(sender, msg);
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, ChatColor.RED + "This command can only be executed by in-game players.");
            return;
        }

        if (args.length < 2) {
            send(player, ChatColor.RED + "Usage: /cgath create <node_type> [custom_id]");
            return;
        }

        String typeId = args[1].toLowerCase();
        if (!plugin.getNodeConfigManager().hasType(typeId)) {
            send(player, ChatColor.RED + "Node type '" + typeId + "' not found in nodes/ folder!");
            return;
        }

        String customId = args.length >= 3 ? args[2] : null;
        if (customId != null && plugin.getNodeDataManager().getNode(customId) != null) {
            send(player, ChatColor.RED + "A node with ID '" + customId + "' already exists!");
            return;
        }

        ActiveNode node = plugin.getNodeDataManager().createNode(typeId, player.getLocation(), customId);
        if (node != null) {
            String msg = plugin.getMessage("admin.node_created")
                    .replace("{type}", typeId)
                    .replace("{id}", node.getId());
            sendPrefixed(player, msg);
        }
    }

    private void handleMove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, ChatColor.RED + "This command can only be executed by in-game players.");
            return;
        }

        String idToMove = null;
        if (args.length >= 2) {
            idToMove = args[1];
        } else {
            // Find nearest node within 4.0 blocks
            Optional<ActiveNode> nearest = plugin.getNodeDataManager().getNearestNode(player.getLocation(), 4.0);
            if (nearest.isPresent()) {
                idToMove = nearest.get().getId();
            }
        }

        if (idToMove == null) {
            sendPrefixed(player, "<red>Usage: /cgath move <id> (or stand near a node to move it)</red>");
            return;
        }

        ActiveNode node = plugin.getNodeDataManager().getNode(idToMove);
        if (node == null) {
            sendPrefixed(player, plugin.getMessage("admin.node_not_found"));
            return;
        }

        boolean success = plugin.getNodeDataManager().moveNode(idToMove, player.getLocation());
        if (success) {
            String msg = plugin.getMessage("admin.node_moved", "<green>Node <aqua>{id}</aqua> has been moved to your current location!")
                    .replace("{id}", idToMove);
            sendPrefixed(player, msg);
        } else {
            sendPrefixed(player, "<red>Failed to move node <aqua>" + idToMove + "</aqua>!</red>");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        String idToRemove = null;

        if (args.length >= 2) {
            idToRemove = args[1];
        } else if (sender instanceof Player player) {
            // Find nearest node within 3.5 blocks
            Optional<ActiveNode> nearest = plugin.getNodeDataManager().getNearestNode(player.getLocation(), 3.5);
            if (nearest.isPresent()) {
                idToRemove = nearest.get().getId();
            }
        }

        if (idToRemove == null) {
            sendPrefixed(sender, plugin.getMessage("admin.node_not_found"));
            return;
        }

        boolean success = plugin.getNodeDataManager().removeNode(idToRemove);
        if (success) {
            String msg = plugin.getMessage("admin.node_removed").replace("{id}", idToRemove);
            sendPrefixed(sender, msg);
        } else {
            sendPrefixed(sender, plugin.getMessage("admin.node_not_found"));
        }
    }

    private void handleWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, ChatColor.RED + "This command can only be executed by in-game players.");
            return;
        }

        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.color(player, "<gradient:#FFAA00:#FF5500><bold>Gathering Admin Wand</bold></gradient>"));
            meta.setLore(List.of(
                    ColorUtils.color(player, "<gray>Left-Click on a node to inspect details."),
                    ColorUtils.color(player, "<red>Shift + Left-Click on a node to instantly remove.")
            ));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, WAND_KEY), PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        sendPrefixed(player, plugin.getMessage("admin.wand_given"));
    }

    private void handleNear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, ChatColor.RED + "This command can only be executed by in-game players.");
            return;
        }

        double radius = args.length >= 2 ? Double.parseDouble(args[1]) : 25.0;
        Location pLoc = player.getLocation();

        send(player, "<gold>=== Nearby Gathering Nodes (Radius " + radius + "m) ===");
        int count = 0;
        for (ActiveNode node : plugin.getNodeDataManager().getAllNodes()) {
            if (node.getLocation().getWorld().equals(pLoc.getWorld())) {
                double dist = pLoc.distance(node.getLocation());
                if (dist <= radius) {
                    count++;
                    send(player, String.format("<gray>• <aqua>%s</aqua> [<yellow>%s</yellow>] - Distance: <green>%.1fm</green> (Charges: %d/%d, Status: %s)",
                            node.getId(), node.getTypeId(), dist, node.getCurrentCharges(), node.getMaxCharges(), node.getStatus().name()));
                }
            }
        }
        if (count == 0) {
            send(player, "<gray>No gathering nodes found within radius.");
        }
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, ChatColor.RED + "This command can only be executed by in-game players.");
            return;
        }

        if (args.length < 2) {
            send(player, ChatColor.RED + "Usage: /cgath tp <node_id>");
            return;
        }

        ActiveNode node = plugin.getNodeDataManager().getNode(args[1]);
        if (node == null) {
            sendPrefixed(player, plugin.getMessage("admin.node_not_found"));
            return;
        }

        player.teleport(node.getLocation());
        String msg = plugin.getMessage("admin.teleported").replace("{id}", node.getId());
        sendPrefixed(player, msg);
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, ChatColor.RED + "Usage: /cgath reset <node_id>");
            return;
        }

        boolean success = plugin.getNodeDataManager().resetNode(args[1]);
        if (success) {
            String msg = plugin.getMessage("admin.node_reset").replace("{id}", args[1]);
            sendPrefixed(sender, msg);
        } else {
            sendPrefixed(sender, plugin.getMessage("admin.node_not_found"));
        }
    }

    private void handleList(CommandSender sender) {
        send(sender, "<gold>=== Active Gathering Nodes List ===");
        for (ActiveNode node : plugin.getNodeDataManager().getAllNodes()) {
            send(sender, String.format("<gray>• <aqua>%s</aqua> [<yellow>%s</yellow>] @ %s %.1f, %.1f, %.1f (%d/%d %s)",
                    node.getId(), node.getTypeId(),
                    node.getLocation().getWorld().getName(),
                    node.getLocation().getX(), node.getLocation().getY(), node.getLocation().getZ(),
                    node.getCurrentCharges(), node.getMaxCharges(), node.getStatus().name()));
        }
    }

    private void handlePurge(CommandSender sender) {
        int count = plugin.getHologramManager().purgeAllHolograms();
        // Respawn needed ones
        plugin.getNodeDataManager().respawnAllNodes();
        String msg = plugin.getMessage("admin.holograms_purged").replace("{count}", String.valueOf(count));
        sendPrefixed(sender, msg);
    }

    private void handleReload(CommandSender sender) {
        plugin.reload();
        sendPrefixed(sender, plugin.getMessage("admin.reload_success"));
    }

    private static final java.util.Set<String> STAT_ACTIONS = java.util.Set.of("get", "view", "info", "set", "add", "remove", "reset");

    private void handleStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendStatsHelp(sender);
            return;
        }

        // Format A: /cgath stats/count <action> <player> [target] [amount]
        // Contoh: /cgath stats set steve mining 100
        if (STAT_ACTIONS.contains(args[1].toLowerCase())) {
            String action = args[1].toLowerCase();
            switch (action) {
                case "set" -> handleStatsSet(sender, args);
                case "add" -> handleStatsAdd(sender, args);
                case "remove" -> handleStatsRemove(sender, args);
                case "reset" -> handleStatsReset(sender, args);
                case "get", "view", "info" -> handleStatsGet(sender, args);
                default -> sendStatsHelp(sender);
            }
            return;
        }

        // Format B: /cgath count/stats <player> <target> <action> [amount]
        // Contoh:
        // /cgath count steve mining reset
        // /cgath count steve mining set 100
        // /cgath count steve mining add 50
        // /cgath count steve mining remove 20
        // /cgath count steve mining get
        // /cgath count steve reset [target]
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[1]);
        if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[1]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : args[1];
        UUID uuid = targetPlayer.getUniqueId();

        if (args.length == 2) {
            showFullStatsOverview(sender, playerName, uuid);
            return;
        }

        String arg2 = args[2].toLowerCase();

        // Cek jika arg2 adalah aksi (misal: /cgath count steve reset [target])
        if (STAT_ACTIONS.contains(arg2)) {
            String target = args.length >= 4 ? args[3].toLowerCase() : "all";
            String amount = args.length >= 5 ? args[4] : null;
            dispatchCountAction(sender, playerName, uuid, arg2, target, amount);
            return;
        }

        // Jika arg2 adalah target (misal: /cgath count steve mining [action] [amount])
        String target = arg2;
        String action = args.length >= 4 ? args[3].toLowerCase() : "get";
        String amount = args.length >= 5 ? args[4] : null;
        dispatchCountAction(sender, playerName, uuid, action, target, amount);
    }

    private void handleStatsGet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendPrefixed(sender, "<red>Usage: /cgath stats get <player> [category|node|total]</red>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        String specificTarget = args.length >= 4 ? args[3].toLowerCase() : "all";
        dispatchCountAction(sender, playerName, uuid, "get", specificTarget, null);
    }

    private void handleStatsSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sendPrefixed(sender, "<red>Usage: /cgath stats set <player> <target> <amount></red>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        String specificTarget = args[3].toLowerCase();
        String amountStr = args[4];

        dispatchCountAction(sender, playerName, uuid, "set", specificTarget, amountStr);
    }

    private void handleStatsAdd(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sendPrefixed(sender, "<red>Usage: /cgath stats add <player> <target> <amount></red>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        String specificTarget = args[3].toLowerCase();
        String amountStr = args[4];

        dispatchCountAction(sender, playerName, uuid, "add", specificTarget, amountStr);
    }

    private void handleStatsRemove(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sendPrefixed(sender, "<red>Usage: /cgath stats remove <player> <target> <amount></red>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        String specificTarget = args[3].toLowerCase();
        String amountStr = args[4];

        dispatchCountAction(sender, playerName, uuid, "remove", specificTarget, amountStr);
    }

    private void handleStatsReset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendPrefixed(sender, "<red>Usage: /cgath stats reset <player> [all|category|node]</red>");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        String specificTarget = args.length >= 4 ? args[3].toLowerCase() : "all";

        dispatchCountAction(sender, playerName, uuid, "reset", specificTarget, null);
    }

    private void dispatchCountAction(CommandSender sender, String playerName, UUID uuid, String action, String target, String amountStr) {
        switch (action) {
            case "get", "view", "info" -> {
                if (target == null || target.equals("all")) {
                    showFullStatsOverview(sender, playerName, uuid);
                } else if (target.equals("total")) {
                    int count = plugin.getStatsManager().getTotal(uuid);
                    sendPrefixed(sender, "<gray>" + playerName + "'s Total Gathers: <aqua>" + count + "</aqua></gray>");
                } else {
                    int catVal = plugin.getStatsManager().getCategory(uuid, target);
                    int nodeVal = plugin.getStatsManager().getNodeType(uuid, target);
                    if (isKnownCategory(target) || catVal > 0) {
                        sendPrefixed(sender, "<gray>" + playerName + "'s Category [<yellow>" + target.toUpperCase() + "</yellow>]: <green>" + catVal + "</green></gray>");
                    } else {
                        sendPrefixed(sender, "<gray>" + playerName + "'s Node [<aqua>" + target + "</aqua>]: <green>" + nodeVal + "</green></gray>");
                    }
                }
            }
            case "reset" -> {
                if (target == null || target.equalsIgnoreCase("all")) {
                    plugin.getStatsManager().resetAll(uuid);
                    sendStatsSuccess(sender, "admin.stats_reset", playerName, "ALL", 0);
                } else if (target.equalsIgnoreCase("total")) {
                    plugin.getStatsManager().setTotal(uuid, 0);
                    sendStatsSuccess(sender, "admin.stats_reset", playerName, "Total", 0);
                } else if (isKnownCategory(target)) {
                    plugin.getStatsManager().resetCategory(uuid, target);
                    sendStatsSuccess(sender, "admin.stats_reset", playerName, "Category " + target.toUpperCase(), 0);
                } else {
                    plugin.getStatsManager().resetNodeType(uuid, target);
                    sendStatsSuccess(sender, "admin.stats_reset", playerName, "Node " + target, 0);
                }
            }
            case "set", "add", "remove" -> {
                if (amountStr == null) {
                    sendPrefixed(sender, "<red>Harap masukkan jumlah angka!</red>");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(amountStr);
                } catch (NumberFormatException e) {
                    sendPrefixed(sender, plugin.getMessage("admin.invalid_number", "<red>Nilai angka tidak valid!"));
                    return;
                }

                if (action.equals("set")) {
                    if ("total".equals(target)) {
                        plugin.getStatsManager().setTotal(uuid, amount);
                        sendStatsSuccess(sender, "admin.stats_set", playerName, "Total", amount);
                    } else if (isKnownCategory(target)) {
                        plugin.getStatsManager().setCategory(uuid, target, amount);
                        sendStatsSuccess(sender, "admin.stats_set", playerName, "Category " + target.toUpperCase(), amount);
                    } else {
                        plugin.getStatsManager().setNodeType(uuid, target, amount);
                        sendStatsSuccess(sender, "admin.stats_set", playerName, "Node " + target, amount);
                    }
                } else if (action.equals("add")) {
                    int newAmount;
                    String targetName;
                    if ("total".equals(target)) {
                        newAmount = plugin.getStatsManager().addTotal(uuid, amount);
                        targetName = "Total";
                    } else if (isKnownCategory(target)) {
                        newAmount = plugin.getStatsManager().addCategory(uuid, target, amount);
                        targetName = "Category " + target.toUpperCase();
                    } else {
                        newAmount = plugin.getStatsManager().addNodeType(uuid, target, amount);
                        targetName = "Node " + target;
                    }
                    String msg = plugin.getMessage("admin.stats_added")
                            .replace("{player}", playerName)
                            .replace("{target}", targetName)
                            .replace("{amount}", String.valueOf(amount))
                            .replace("{new_amount}", String.valueOf(newAmount));
                    sendPrefixed(sender, msg);
                } else if (action.equals("remove")) {
                    int newAmount;
                    String targetName;
                    if ("total".equals(target)) {
                        newAmount = plugin.getStatsManager().removeTotal(uuid, amount);
                        targetName = "Total";
                    } else if (isKnownCategory(target)) {
                        newAmount = plugin.getStatsManager().removeCategory(uuid, target, amount);
                        targetName = "Category " + target.toUpperCase();
                    } else {
                        newAmount = plugin.getStatsManager().removeNodeType(uuid, target, amount);
                        targetName = "Node " + target;
                    }
                    String msg = plugin.getMessage("admin.stats_removed", "<green>Successfully removed <white>{amount}</white> from <yellow>{player}</yellow>'s <aqua>{target}</aqua> stats. (New: <white>{new_amount}</white>)")
                            .replace("{player}", playerName)
                            .replace("{target}", targetName)
                            .replace("{amount}", String.valueOf(amount))
                            .replace("{new_amount}", String.valueOf(newAmount));
                    sendPrefixed(sender, msg);
                }
            }
            default -> sendStatsHelp(sender);
        }
    }

    private void showFullStatsOverview(CommandSender sender, String playerName, UUID uuid) {
        String header = plugin.getMessage("admin.stats_header").replace("{player}", playerName);
        send(sender, header);
        send(sender, "<gray>• <yellow>Total Gathered</yellow>: <green>" + plugin.getStatsManager().getTotal(uuid) + "</green></gray>");

        Map<String, Integer> catMap = plugin.getStatsManager().getCategoryMap(uuid);
        if (!catMap.isEmpty()) {
            send(sender, "<gold>Categories:</gold>");
            for (Map.Entry<String, Integer> e : catMap.entrySet()) {
                send(sender, String.format("  <gray>• <aqua>%s</aqua>: <white>%d</white></gray>", e.getKey().toUpperCase(), e.getValue()));
            }
        }

        Map<String, Integer> nodeMap = plugin.getStatsManager().getNodeTypeMap(uuid);
        if (!nodeMap.isEmpty()) {
            send(sender, "<gold>Specific Nodes:</gold>");
            for (Map.Entry<String, Integer> e : nodeMap.entrySet()) {
                send(sender, String.format("  <gray>• <yellow>%s</yellow>: <white>%d</white></gray>", e.getKey(), e.getValue()));
            }
        }
    }

    private void sendStatsSuccess(CommandSender sender, String path, String player, String target, int amount) {
        String msg = plugin.getMessage(path)
                .replace("{player}", player)
                .replace("{target}", target)
                .replace("{amount}", String.valueOf(amount));
        sendPrefixed(sender, msg);
    }

    private void sendStatsHelp(CommandSender sender) {
        send(sender, "<gradient:#00FFAA:#00A8FF><bold>=== Gathering Count & Stats Commands ===</bold></gradient>");
        send(sender, "<aqua>/cgath count <player> <category> reset</aqua> <gray>- Reset category count (e.g. steve mining reset)</gray>");
        send(sender, "<aqua>/cgath count <player> <category> set <amount></aqua> <gray>- Set category count</gray>");
        send(sender, "<aqua>/cgath count <player> <category> add <amount></aqua> <gray>- Add to category count</gray>");
        send(sender, "<aqua>/cgath count <player> <category> remove <amount></aqua> <gray>- Remove from category count</gray>");
        send(sender, "<aqua>/cgath count <player> [category]</aqua> <gray>- View player gathering count</gray>");
        send(sender, "<gray>Format alternatif: /cgath stats <get|set|add|remove|reset> <player> <target> [amount]</gray>");
    }

    private boolean isKnownCategory(String category) {
        if (category == null) return false;
        for (NodeType type : plugin.getNodeConfigManager().getAllTypes()) {
            if (type.getCategory() != null && type.getCategory().equalsIgnoreCase(category)) {
                return true;
            }
        }
        return false;
    }

    private void handleLuck(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendPrefixed(sender, "<red>Usage: /cgath luck <get|set|add|remove|reset> <player> [amount]</red>");
            return;
        }

        String action = args[1].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        Player onlinePlayer = target.getPlayer();

        if (action.equals("get")) {
            double bonus = plugin.getLuckManager().getCustomLuck(uuid);
            double base = onlinePlayer != null ? plugin.getLuckManager().getBaseGatheringLuck(onlinePlayer) : 0.0;
            double total = onlinePlayer != null ? plugin.getLuckManager().getPlayerGatheringLuck(onlinePlayer) : bonus;

            String msg = plugin.getMessage("admin.luck_get", "<gray>Gathering Luck untuk <yellow>{player}</yellow>: <gold>+{amount}</gold> <gray>(Base: <yellow>+{base}</yellow>, Bonus: <green>+{bonus}</green>)</gray>")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(total))
                    .replace("{base}", formatNumber(base))
                    .replace("{bonus}", formatNumber(bonus));
            sendPrefixed(sender, msg);
            return;
        }

        if (action.equals("reset")) {
            plugin.getLuckManager().resetCustomLuck(uuid);
            String msg = plugin.getMessage("admin.luck_reset", "<green>Berhasil mereset Gathering Luck bonus untuk <yellow>{player}</yellow>.")
                    .replace("{player}", playerName);
            sendPrefixed(sender, msg);
            return;
        }

        if (args.length < 4) {
            sendPrefixed(sender, "<red>Usage: /cgath luck " + action + " <player> <amount></red>");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendPrefixed(sender, plugin.getMessage("admin.invalid_number", "<red>Nilai angka tidak valid!"));
            return;
        }

        if (action.equals("set")) {
            plugin.getLuckManager().setCustomLuck(uuid, amount);
            String msg = plugin.getMessage("admin.luck_set", "<green>Berhasil mengatur Gathering Luck bonus <yellow>{player}</yellow> ke <gold>+{amount}</gold>.")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount));
            sendPrefixed(sender, msg);
        } else if (action.equals("add")) {
            double newAmount = plugin.getLuckManager().addCustomLuck(uuid, amount);
            String msg = plugin.getMessage("admin.luck_added", "<green>Berhasil menambahkan <gold>+{amount}</gold> Gathering Luck bonus ke <yellow>{player}</yellow>. (Total Bonus: <gold>+{new_amount}</gold>)")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount))
                    .replace("{new_amount}", formatNumber(newAmount));
            sendPrefixed(sender, msg);
        } else if (action.equals("remove")) {
            double newAmount = plugin.getLuckManager().removeCustomLuck(uuid, amount);
            String msg = plugin.getMessage("admin.luck_removed", "<green>Berhasil mengurangi <gold>-{amount}</gold> Gathering Luck bonus dari <yellow>{player}</yellow>. (Total Bonus: <gold>+{new_amount}</gold>)")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount))
                    .replace("{new_amount}", formatNumber(newAmount));
            sendPrefixed(sender, msg);
        } else {
            sendPrefixed(sender, "<red>Unknown action: " + action + ". Use get, set, add, remove, or reset.</red>");
        }
    }

    private void handleDoubleGather(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendPrefixed(sender, "<red>Usage: /cgath doublegather <get|set|add|remove|reset> <player> [amount]</red>");
            return;
        }

        String action = args[1].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        Player onlinePlayer = target.getPlayer();

        if (action.equals("get")) {
            double bonus = plugin.getLuckManager().getCustomDoubleGather(uuid);
            double base = onlinePlayer != null ? plugin.getLuckManager().getBaseDoubleGather(onlinePlayer) : 0.0;
            double total = onlinePlayer != null ? plugin.getLuckManager().getPlayerDoubleGather(onlinePlayer) : bonus;

            String msg = plugin.getMessage("admin.double_gather_get", "<gray>Double Gather Chance untuk <yellow>{player}</yellow>: <yellow>{amount}%</yellow> <gray>(Base: <yellow>{base}%</yellow>, Bonus: <green>+{bonus}%</green>)</gray>")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(total))
                    .replace("{base}", formatNumber(base))
                    .replace("{bonus}", formatNumber(bonus));
            sendPrefixed(sender, msg);
            return;
        }

        if (action.equals("reset")) {
            plugin.getLuckManager().resetCustomDoubleGather(uuid);
            String msg = plugin.getMessage("admin.double_gather_reset", "<green>Berhasil mereset Double Gather bonus untuk <yellow>{player}</yellow>.")
                    .replace("{player}", playerName);
            sendPrefixed(sender, msg);
            return;
        }

        if (args.length < 4) {
            sendPrefixed(sender, "<red>Usage: /cgath doublegather " + action + " <player> <amount></red>");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendPrefixed(sender, plugin.getMessage("admin.invalid_number", "<red>Nilai angka tidak valid!"));
            return;
        }

        if (action.equals("set")) {
            plugin.getLuckManager().setCustomDoubleGather(uuid, amount);
            String msg = plugin.getMessage("admin.double_gather_set", "<green>Berhasil mengatur Double Gather bonus <yellow>{player}</yellow> ke <yellow>{amount}%</yellow>.")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount));
            sendPrefixed(sender, msg);
        } else if (action.equals("add")) {
            double newAmount = plugin.getLuckManager().addCustomDoubleGather(uuid, amount);
            String msg = plugin.getMessage("admin.double_gather_added", "<green>Berhasil menambahkan <yellow>+{amount}%</yellow> Double Gather bonus ke <yellow>{player}</yellow>. (Total Bonus: <yellow>{new_amount}%</yellow>)")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount))
                    .replace("{new_amount}", formatNumber(newAmount));
            sendPrefixed(sender, msg);
        } else if (action.equals("remove")) {
            double newAmount = plugin.getLuckManager().removeCustomDoubleGather(uuid, amount);
            String msg = plugin.getMessage("admin.double_gather_removed", "<green>Berhasil mengurangi <yellow>-{amount}%</yellow> Double Gather bonus dari <yellow>{player}</yellow>. (Total Bonus: <yellow>{new_amount}%</yellow>)")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount))
                    .replace("{new_amount}", formatNumber(newAmount));
            sendPrefixed(sender, msg);
        } else {
            sendPrefixed(sender, "<red>Unknown action: " + action + ". Use get, set, add, remove, or reset.</red>");
        }
    }

    private void handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendPrefixed(sender, "<red>Usage: /cgath speed <get|set|add|remove|reset> <player> [amount]</red>");
            return;
        }

        String action = args[1].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            String msg = plugin.getMessage("admin.player_not_found").replace("{player}", args[2]);
            sendPrefixed(sender, msg);
            return;
        }

        String playerName = target.getName() != null ? target.getName() : args[2];
        UUID uuid = target.getUniqueId();
        Player onlinePlayer = target.getPlayer();

        if (action.equals("get")) {
            double bonus = plugin.getLuckManager().getCustomSpeed(uuid);
            double base = onlinePlayer != null ? plugin.getLuckManager().getBaseGatheringSpeed(onlinePlayer) : 0.0;
            double total = onlinePlayer != null ? plugin.getLuckManager().getPlayerGatheringSpeed(onlinePlayer) : bonus;

            String msg = plugin.getMessage("admin.speed_get", "<gray>Gathering Speed untuk <yellow>{player}</yellow>: <green>+{amount}%</green> <gray>(Base: <yellow>+{base}%</yellow>, Bonus: <green>+{bonus}%</green>)</gray>")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(total))
                    .replace("{base}", formatNumber(base))
                    .replace("{bonus}", formatNumber(bonus));
            sendPrefixed(sender, msg);
            return;
        }

        if (action.equals("reset")) {
            plugin.getLuckManager().resetCustomSpeed(uuid);
            String msg = plugin.getMessage("admin.speed_reset", "<green>Berhasil mereset Gathering Speed bonus untuk <yellow>{player}</yellow>.")
                    .replace("{player}", playerName);
            sendPrefixed(sender, msg);
            return;
        }

        if (args.length < 4) {
            sendPrefixed(sender, "<red>Usage: /cgath speed " + action + " <player> <amount></red>");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendPrefixed(sender, plugin.getMessage("admin.invalid_number", "<red>Nilai angka tidak valid!"));
            return;
        }

        if (action.equals("set")) {
            plugin.getLuckManager().setCustomSpeed(uuid, amount);
            String msg = plugin.getMessage("admin.speed_set", "<green>Berhasil mengatur Gathering Speed bonus <yellow>{player}</yellow> ke <green>+{amount}%</green>.")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount));
            sendPrefixed(sender, msg);
        } else if (action.equals("add")) {
            double newAmount = plugin.getLuckManager().addCustomSpeed(uuid, amount);
            String msg = plugin.getMessage("admin.speed_added", "<green>Berhasil menambahkan <green>+{amount}%</green> Gathering Speed bonus ke <yellow>{player}</yellow>. (Total Bonus: <green>+{new_amount}%</green>)")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount))
                    .replace("{new_amount}", formatNumber(newAmount));
            sendPrefixed(sender, msg);
        } else if (action.equals("remove")) {
            double newAmount = plugin.getLuckManager().removeCustomSpeed(uuid, amount);
            String msg = plugin.getMessage("admin.speed_removed", "<green>Berhasil mengurangi <green>-{amount}%</green> Gathering Speed bonus dari <yellow>{player}</yellow>. (Total Bonus: <green>+{new_amount}%</green>)")
                    .replace("{player}", playerName)
                    .replace("{amount}", formatNumber(amount))
                    .replace("{new_amount}", formatNumber(newAmount));
            sendPrefixed(sender, msg);
        } else {
            sendPrefixed(sender, "<red>Unknown action: " + action + ". Use get, set, add, remove, or reset.</red>");
        }
    }

    private String formatNumber(double val) {
        if (val == (long) val) {
            return String.format(Locale.US, "%d", (long) val);
        }
        return String.format(Locale.US, "%.1f", val);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.color("<gradient:#00FFAA:#00A8FF><bold>=== CyayoGathering Admin Commands ===</bold></gradient>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " profile [player]</aqua> <gray>- View gathering profile GUI</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " create <type> [id]</aqua> <gray>- Create node at current location</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " move [id]</aqua> <gray>- Move node to your current location</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " remove [id]</aqua> <gray>- Remove nearest node or by ID</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " wand</aqua> <gray>- Get admin inspector/removal wand</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " near [radius]</aqua> <gray>- View nodes around you</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " tp <id></aqua> <gray>- Teleport to a node</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " reset <id></aqua> <gray>- Reset node charges & cooldown</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " setlevel <player> <category> <level></aqua> <gray>- Set player gathering level</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " addxp <player> <category> <amount></aqua> <gray>- Add XP to player category</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " luck <get|set|add|remove|reset> <player> [amount]</aqua> <gray>- Manage Gathering Luck bonus</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " speed <get|set|add|remove|reset> <player> [amount]</aqua> <gray>- Manage Gathering Speed bonus</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " doublegather <get|set|add|remove|reset> <player> [amount]</aqua> <gray>- Manage Double Gather bonus</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " count <player> <category> <set|add|remove|reset> [amount]</aqua> <gray>- Manage player harvest count</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " list</aqua> <gray>- List all active nodes</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " purgeholograms</aqua> <gray>- Purge all orphan holograms</gray>"));
        sender.sendMessage(ColorUtils.color("<aqua>/" + label + " reload</aqua> <gray>- Reload configs and templates</gray>"));
    }
}

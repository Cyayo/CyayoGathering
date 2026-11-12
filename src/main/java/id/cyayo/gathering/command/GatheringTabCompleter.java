package id.cyayo.gathering.command;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GatheringTabCompleter implements TabCompleter {

    private final CyayoGathering plugin;
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
            "create", "move", "remove", "wand", "near", "tp", "reset", "stats", "count", "setlevel", "addxp", "luck", "speed", "doublegather", "profile", "menu", "list", "purgeholograms", "reload"
    );
    private static final List<String> PLAYER_SUBCOMMANDS = List.of(
            "profile", "menu"
    );

    public GatheringTabCompleter(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        boolean isAdmin = sender.hasPermission("cyayogathering.admin") || sender.isOp() || (sender instanceof ConsoleCommandSender);
        boolean hasProfile = sender.hasPermission("cyayogathering.profile") || isAdmin;

        // If no permission for anything, return empty
        if (!isAdmin && !hasProfile) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> available = isAdmin ? ADMIN_SUBCOMMANDS : PLAYER_SUBCOMMANDS;
            return StringUtil.copyPartialMatches(args[0], available, new ArrayList<>());
        }

        String sub = args[0].toLowerCase();

        // 1. Profile / Menu Tab Completion
        if (sub.equals("profile") || sub.equals("menu")) {
            if (args.length == 2 && (isAdmin || sender.hasPermission("cyayogathering.profile.others"))) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
            }
            return Collections.emptyList();
        }

        // Below this point: only admin commands
        if (!isAdmin) {
            return Collections.emptyList();
        }

        // 2. setlevel & addxp Tab Completion
        if ("setlevel".equals(sub) || "addxp".equals(sub)) {
            if (args.length == 2) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
            }
            if (args.length == 3) {
                Set<String> categories = getKnownCategories();
                return StringUtil.copyPartialMatches(args[2], new ArrayList<>(categories), new ArrayList<>());
            }
            if (args.length == 4) {
                if ("setlevel".equals(sub)) {
                    return StringUtil.copyPartialMatches(args[3], List.of("1", "5", "10", "25", "50", "100"), new ArrayList<>());
                } else {
                    return StringUtil.copyPartialMatches(args[3], List.of("10", "50", "100", "500", "1000"), new ArrayList<>());
                }
            }
            return Collections.emptyList();
        }

        // 2b. luck, speed & doublegather Tab Completion
        if ("luck".equals(sub) || "speed".equals(sub) || "doublegather".equals(sub) || "double".equals(sub) || "doubledrop".equals(sub)) {
            if (args.length == 2) {
                return StringUtil.copyPartialMatches(args[1], List.of("get", "set", "add", "remove", "reset"), new ArrayList<>());
            }
            if (args.length == 3) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>());
            }
            if (args.length == 4) {
                String action = args[1].toLowerCase();
                if ("set".equals(action) || "add".equals(action) || "remove".equals(action)) {
                    return StringUtil.copyPartialMatches(args[3], List.of("5", "10", "20", "50", "100"), new ArrayList<>());
                }
            }
            return Collections.emptyList();
        }

        // 3. create / remove / move / tp / reset / near / stats
        if (args.length == 2) {
            if ("create".equals(sub)) {
                List<String> types = new ArrayList<>();
                for (NodeType type : plugin.getNodeConfigManager().getAllTypes()) {
                    types.add(type.getId());
                }
                return StringUtil.copyPartialMatches(args[1], types, new ArrayList<>());
            }

            if ("remove".equals(sub) || "move".equals(sub) || "tp".equals(sub) || "reset".equals(sub)) {
                List<String> nodeIds = new ArrayList<>();
                for (ActiveNode node : plugin.getNodeDataManager().getAllNodes()) {
                    nodeIds.add(node.getId());
                }
                return StringUtil.copyPartialMatches(args[1], nodeIds, new ArrayList<>());
            }

            if ("near".equals(sub)) {
                return StringUtil.copyPartialMatches(args[1], List.of("10", "25", "50", "100"), new ArrayList<>());
            }

            if ("stats".equals(sub) || "count".equals(sub)) {
                List<String> suggestions = new ArrayList<>(List.of("get", "set", "add", "remove", "reset"));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    suggestions.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
            }
        }

        if (args.length == 3 && ("stats".equals(sub) || "count".equals(sub))) {
            String arg1 = args[1].toLowerCase();
            if (List.of("get", "set", "add", "remove", "reset").contains(arg1)) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>());
            } else {
                // arg1 adalah nama player -> sarankan target (kategori/node) atau action
                Set<String> options = new LinkedHashSet<>(getKnownTargets(true));
                options.addAll(List.of("get", "set", "add", "remove", "reset"));
                return StringUtil.copyPartialMatches(args[2], new ArrayList<>(options), new ArrayList<>());
            }
        }

        if (args.length == 4 && ("stats".equals(sub) || "count".equals(sub))) {
            String arg1 = args[1].toLowerCase();
            String arg2 = args[2].toLowerCase();

            if (List.of("get", "set", "add", "remove", "reset").contains(arg1)) {
                // Format: /cgath stats <action> <player> [target]
                return StringUtil.copyPartialMatches(args[3], new ArrayList<>(getKnownTargets("reset".equals(arg1))), new ArrayList<>());
            } else if (List.of("get", "set", "add", "remove", "reset").contains(arg2)) {
                // Format: /cgath count <player> <action> [target]
                return StringUtil.copyPartialMatches(args[3], new ArrayList<>(getKnownTargets("reset".equals(arg2))), new ArrayList<>());
            } else {
                // Format: /cgath count <player> <target> [action]
                return StringUtil.copyPartialMatches(args[3], List.of("set", "add", "remove", "reset", "get"), new ArrayList<>());
            }
        }

        if (args.length == 5 && ("stats".equals(sub) || "count".equals(sub))) {
            return StringUtil.copyPartialMatches(args[4], List.of("10", "50", "100", "500", "1000"), new ArrayList<>());
        }

        return Collections.emptyList();
    }

    private Set<String> getKnownCategories() {
        Set<String> categories = new LinkedHashSet<>(List.of("mining", "woodcutting", "farming", "fishing"));
        for (NodeType type : plugin.getNodeConfigManager().getAllTypes()) {
            if (type.getCategory() != null && !type.getCategory().isBlank()) {
                categories.add(type.getCategory().toLowerCase());
            }
        }
        return categories;
    }

    private Set<String> getKnownTargets(boolean includeAll) {
        Set<String> targets = new LinkedHashSet<>();
        if (includeAll) {
            targets.add("all");
        }
        targets.add("total");
        for (NodeType type : plugin.getNodeConfigManager().getAllTypes()) {
            if (type.getCategory() != null && !type.getCategory().isBlank()) {
                targets.add(type.getCategory().toLowerCase());
            }
            targets.add(type.getId().toLowerCase());
        }
        return targets;
    }
}

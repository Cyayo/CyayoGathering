package id.cyayo.gathering.hook;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GatheringExpansion extends PlaceholderExpansion {

    private final CyayoGathering plugin;

    public GatheringExpansion(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cyayogathering";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cyayo";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        params = params.toLowerCase();

        if (params.equals("total_nodes")) {
            return String.valueOf(plugin.getNodeDataManager().getAllNodes().size());
        }

        if (offlinePlayer != null) {
            java.util.UUID uuid = offlinePlayer.getUniqueId();

            if (params.equals("is_gathering")) {
                Player online = offlinePlayer.getPlayer();
                return String.valueOf(online != null && plugin.getGatherManager().isGathering(online));
            }

            if (params.equals("speed") || params.equals("gathering_speed")) {
                Player online = offlinePlayer.getPlayer();
                double speed = online != null ? plugin.getGatherManager().getPlayerGatheringSpeed(online) : plugin.getLuckManager().getCustomSpeed(uuid);
                return String.format("+%.1f%%", speed);
            }

            if (params.equals("speed_raw") || params.equals("gathering_speed_raw")) {
                Player online = offlinePlayer.getPlayer();
                double speed = online != null ? plugin.getGatherManager().getPlayerGatheringSpeed(online) : plugin.getLuckManager().getCustomSpeed(uuid);
                return String.valueOf(speed);
            }

            if (params.equals("custom_speed") || params.equals("bonus_speed")) {
                double cSpeed = plugin.getLuckManager().getCustomSpeed(uuid);
                return String.format("+%.1f%%", cSpeed);
            }

            if (params.equals("gathering_luck") || params.equals("luck")) {
                Player online = offlinePlayer.getPlayer();
                double luck = online != null ? plugin.getLuckManager().getPlayerGatheringLuck(online) : 0.0;
                if (luck == (long) luck) {
                    return String.format(java.util.Locale.US, "%d", (long) luck);
                } else {
                    return String.format(java.util.Locale.US, "%.1f", luck);
                }
            }

            if (params.equals("gathering_luck_formatted") || params.equals("luck_formatted")) {
                Player online = offlinePlayer.getPlayer();
                double luck = online != null ? plugin.getLuckManager().getPlayerGatheringLuck(online) : 0.0;
                if (luck == (long) luck) {
                    return String.format(java.util.Locale.US, "+%d", (long) luck);
                } else {
                    return String.format(java.util.Locale.US, "+%.1f", luck);
                }
            }

            if (params.equals("gathering_luck_raw") || params.equals("luck_raw")) {
                Player online = offlinePlayer.getPlayer();
                double luck = online != null ? plugin.getLuckManager().getPlayerGatheringLuck(online) : 0.0;
                return String.valueOf(luck);
            }

            if (params.equals("gathering_luck_multiplier") || params.equals("luck_multiplier") || params.equals("luck_mult")) {
                Player online = offlinePlayer.getPlayer();
                double luck = online != null ? plugin.getLuckManager().getPlayerGatheringLuck(online) : 0.0;
                double mult = 1.0 + (Math.max(0.0, luck) / 100.0);
                return String.format(java.util.Locale.US, "%.2fx", mult);
            }

            if (params.equals("custom_luck") || params.equals("bonus_luck")) {
                double cLuck = plugin.getLuckManager().getCustomLuck(uuid);
                if (cLuck == (long) cLuck) {
                    return String.format(java.util.Locale.US, "+%d", (long) cLuck);
                } else {
                    return String.format(java.util.Locale.US, "+%.1f", cLuck);
                }
            }

            if (params.equals("double_gather") || params.equals("double_drop")) {
                Player online = offlinePlayer.getPlayer();
                double dDrop = online != null ? plugin.getLuckManager().getPlayerDoubleGather(online) : plugin.getLuckManager().getCustomDoubleGather(uuid);
                return String.format(java.util.Locale.US, "%.1f%%", dDrop);
            }

            if (params.equals("double_gather_raw") || params.equals("double_drop_raw")) {
                Player online = offlinePlayer.getPlayer();
                double dDrop = online != null ? plugin.getLuckManager().getPlayerDoubleGather(online) : plugin.getLuckManager().getCustomDoubleGather(uuid);
                return String.valueOf(dDrop);
            }

            if (params.equals("custom_double_gather") || params.equals("custom_double_drop")) {
                double cDouble = plugin.getLuckManager().getCustomDoubleGather(uuid);
                return String.format(java.util.Locale.US, "+%.1f%%", cDouble);
            }

            // --- Leveling Placeholders ---
            if (params.startsWith("level_")) {
                String cat = params.substring("level_".length());
                if (plugin.getLevelManager() != null) {
                    return String.valueOf(plugin.getLevelManager().getLevel(uuid, cat));
                }
                return "1";
            }

            if (params.startsWith("xp_") || params.startsWith("exp_")) {
                String cat = params.startsWith("xp_") ? params.substring("xp_".length()) : params.substring("exp_".length());
                if (plugin.getLevelManager() != null) {
                    return String.format(java.util.Locale.US, "%.0f", plugin.getLevelManager().getXp(uuid, cat));
                }
                return "0";
            }

            if (params.startsWith("req_xp_") || params.startsWith("req_exp_")) {
                String cat = params.startsWith("req_xp_") ? params.substring("req_xp_".length()) : params.substring("req_exp_".length());
                if (plugin.getLevelManager() != null) {
                    return String.valueOf(plugin.getLevelManager().getRequiredXpForNextLevel(uuid, cat));
                }
                return "100";
            }

            if (params.startsWith("progress_percent_") || params.startsWith("progress_pct_")) {
                String cat = params.startsWith("progress_percent_") ? params.substring("progress_percent_".length()) : params.substring("progress_pct_".length());
                if (plugin.getLevelManager() != null) {
                    return String.format(java.util.Locale.US, "%.1f%%", plugin.getLevelManager().getProgressPercent(uuid, cat));
                }
                return "0.0%";
            }

            if (params.startsWith("progress_bar_")) {
                String cat = params.substring("progress_bar_".length());
                if (plugin.getLevelManager() != null) {
                    return plugin.getLevelManager().getProgressBar(uuid, cat, 10, "■", "&a", "&7");
                }
                return "&7■■■■■■■■■■";
            }

            // --- Stats Placeholders ---
            if (params.equals("gathered_total") || params.equals("total") || params.equals("stats_total")) {
                return String.valueOf(plugin.getStatsManager().getTotal(uuid));
            }

            // Explicit category prefixes: gathered_category_<cat>, category_<cat>, stats_category_<cat>
            if (params.startsWith("gathered_category_")) {
                String cat = params.substring("gathered_category_".length());
                return String.valueOf(plugin.getStatsManager().getCategory(uuid, cat));
            }
            if (params.startsWith("category_")) {
                String cat = params.substring("category_".length());
                return String.valueOf(plugin.getStatsManager().getCategory(uuid, cat));
            }
            if (params.startsWith("stats_category_")) {
                String cat = params.substring("stats_category_".length());
                return String.valueOf(plugin.getStatsManager().getCategory(uuid, cat));
            }

            // Explicit node prefixes: gathered_node_<node>, stats_node_<node>
            if (params.startsWith("gathered_node_")) {
                String nId = params.substring("gathered_node_".length());
                return String.valueOf(plugin.getStatsManager().getNodeType(uuid, nId));
            }
            if (params.startsWith("stats_node_")) {
                String nId = params.substring("stats_node_".length());
                return String.valueOf(plugin.getStatsManager().getNodeType(uuid, nId));
            }

            // Semi-explicit prefix: gathered_<target> (e.g. gathered_woodcutting or gathered_woodcutting_oak)
            if (params.startsWith("gathered_")) {
                String target = params.substring("gathered_".length());
                int catVal = plugin.getStatsManager().getCategory(uuid, target);
                if (catVal > 0 || isKnownCategory(target)) {
                    return String.valueOf(catVal);
                }
                return String.valueOf(plugin.getStatsManager().getNodeType(uuid, target));
            }

            // Direct category check: e.g. %cyayogathering_woodcutting%
            int catVal = plugin.getStatsManager().getCategory(uuid, params);
            if (catVal > 0 || isKnownCategory(params)) {
                return String.valueOf(catVal);
            }

            // Direct node check: e.g. %cyayogathering_woodcutting_oak%
            if (plugin.getNodeConfigManager().hasType(params) || plugin.getStatsManager().getNodeType(uuid, params) > 0) {
                return String.valueOf(plugin.getStatsManager().getNodeType(uuid, params));
            }
        }

        if (params.startsWith("node_status_")) {
            String nodeId = params.substring("node_status_".length());
            ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
            return node != null ? node.getStatus().name() : "NOT_FOUND";
        }

        if (params.startsWith("node_charges_color_")) {
            String nodeId = params.substring("node_charges_color_".length());
            ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
            if (node == null) return "&7";
            return plugin.getHologramManager().getFormatter().getChargesColor(node.getCurrentCharges(), node.getMaxCharges());
        }

        if (params.startsWith("node_charges_colored_")) {
            String nodeId = params.substring("node_charges_colored_".length());
            ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
            if (node == null) return "0";
            String color = plugin.getHologramManager().getFormatter().getChargesColor(node.getCurrentCharges(), node.getMaxCharges());
            return color + node.getCurrentCharges();
        }

        if (params.startsWith("node_charges_ratio_")) {
            String nodeId = params.substring("node_charges_ratio_".length());
            ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
            if (node == null) return "0/0";
            String color = plugin.getHologramManager().getFormatter().getChargesColor(node.getCurrentCharges(), node.getMaxCharges());
            String colorFull = plugin.getConfig().getString("charges_format.color_full", "&a");
            return color + node.getCurrentCharges() + "&7/" + colorFull + node.getMaxCharges();
        }

        if (params.startsWith("node_max_charges_")) {
            String nodeId = params.substring("node_max_charges_".length());
            ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
            return node != null ? String.valueOf(node.getMaxCharges()) : "0";
        }

        if (params.startsWith("node_charges_")) {
            String nodeId = params.substring("node_charges_".length());
            ActiveNode node = plugin.getNodeDataManager().getNode(nodeId);
            return node != null ? String.valueOf(node.getCurrentCharges()) : "0";
        }

        return null;
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
}

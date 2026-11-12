package id.cyayo.gathering.api;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.api.event.NodeGatherCancelEvent;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.session.GatherSession;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CyayoGatheringAPI {

    private static CyayoGathering getPlugin() {
        return CyayoGathering.getInstance();
    }

    public static ActiveNode getNode(String id) {
        return getPlugin().getNodeDataManager().getNode(id);
    }

    public static Collection<ActiveNode> getAllNodes() {
        return getPlugin().getNodeDataManager().getAllNodes();
    }

    public static List<ActiveNode> getNodesByType(String typeId) {
        return getAllNodes().stream()
                .filter(n -> n.getTypeId().equalsIgnoreCase(typeId))
                .collect(Collectors.toList());
    }

    public static Optional<ActiveNode> getNearestNode(Location location, double maxDistance) {
        return getPlugin().getNodeDataManager().getNearestNode(location, maxDistance);
    }

    public static ActiveNode createNode(String typeId, Location location) {
        return getPlugin().getNodeDataManager().createNode(typeId, location, null);
    }

    public static ActiveNode createNode(String typeId, Location location, String customId) {
        return getPlugin().getNodeDataManager().createNode(typeId, location, customId);
    }

    public static boolean removeNode(String id) {
        return getPlugin().getNodeDataManager().removeNode(id);
    }

    public static boolean resetNode(String id) {
        return getPlugin().getNodeDataManager().resetNode(id);
    }

    public static boolean isGathering(Player player) {
        return getPlugin().getGatherManager().isGathering(player);
    }

    public static Optional<GatherSession> getSession(Player player) {
        return getPlugin().getGatherManager().getSession(player);
    }

    public static void cancelGathering(Player player) {
        getPlugin().getGatherManager().cancelGathering(player, NodeGatherCancelEvent.CancelReason.PLUGIN);
    }

    public static double getGatheringSpeed(Player player) {
        return getPlugin().getGatherManager().getPlayerGatheringSpeed(player);
    }

    public static double getPlayerGatheringLuck(Player player) {
        return getPlugin().getLuckManager().getPlayerGatheringLuck(player);
    }

    public static double getPlayerCustomLuck(UUID playerUuid) {
        return getPlugin().getLuckManager().getCustomLuck(playerUuid);
    }

    public static void setPlayerCustomLuck(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().setCustomLuck(playerUuid, amount);
    }

    public static void addPlayerCustomLuck(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().addCustomLuck(playerUuid, amount);
    }

    public static void removePlayerCustomLuck(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().removeCustomLuck(playerUuid, amount);
    }

    public static void resetPlayerCustomLuck(UUID playerUuid) {
        getPlugin().getLuckManager().resetCustomLuck(playerUuid);
    }

    public static double getPlayerDoubleGather(Player player) {
        return getPlugin().getLuckManager().getPlayerDoubleGather(player);
    }

    public static double getPlayerCustomDoubleGather(UUID playerUuid) {
        return getPlugin().getLuckManager().getCustomDoubleGather(playerUuid);
    }

    public static void setPlayerCustomDoubleGather(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().setCustomDoubleGather(playerUuid, amount);
    }

    public static void addPlayerCustomDoubleGather(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().addCustomDoubleGather(playerUuid, amount);
    }

    public static void removePlayerCustomDoubleGather(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().removeCustomDoubleGather(playerUuid, amount);
    }

    public static void resetPlayerCustomDoubleGather(UUID playerUuid) {
        getPlugin().getLuckManager().resetCustomDoubleGather(playerUuid);
    }

    public static double getPlayerCustomSpeed(UUID playerUuid) {
        return getPlugin().getLuckManager().getCustomSpeed(playerUuid);
    }

    public static void setPlayerCustomSpeed(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().setCustomSpeed(playerUuid, amount);
    }

    public static void addPlayerCustomSpeed(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().addCustomSpeed(playerUuid, amount);
    }

    public static void removePlayerCustomSpeed(UUID playerUuid, double amount) {
        getPlugin().getLuckManager().removeCustomSpeed(playerUuid, amount);
    }

    public static void resetPlayerCustomSpeed(UUID playerUuid) {
        getPlugin().getLuckManager().resetCustomSpeed(playerUuid);
    }

    public static double getPlayerLuckMultiplier(Player player) {
        return getPlugin().getLuckManager().getEffectiveMultiplier(player, 0.0);
    }

    public static double getPlayerLuckBonus(Player player) {
        return getPlugin().getLuckManager().getPlayerLuckBonus(player, 0.0);
    }

    public static int getPlayerTotalGathers(UUID playerUuid) {
        return getPlugin().getStatsManager().getTotal(playerUuid);
    }

    public static int getPlayerCategoryGathers(UUID playerUuid, String category) {
        return getPlugin().getStatsManager().getCategory(playerUuid, category);
    }

    public static void setPlayerCategoryGathers(UUID playerUuid, String category, int amount) {
        getPlugin().getStatsManager().setCategory(playerUuid, category, amount);
    }

    public static int addPlayerCategoryGathers(UUID playerUuid, String category, int amount) {
        return getPlugin().getStatsManager().addCategory(playerUuid, category, amount);
    }

    public static int removePlayerCategoryGathers(UUID playerUuid, String category, int amount) {
        return getPlugin().getStatsManager().removeCategory(playerUuid, category, amount);
    }

    public static void resetPlayerCategoryGathers(UUID playerUuid, String category) {
        getPlugin().getStatsManager().resetCategory(playerUuid, category);
    }

    public static int getPlayerNodeGathers(UUID playerUuid, String nodeTypeId) {
        return getPlugin().getStatsManager().getNodeType(playerUuid, nodeTypeId);
    }

    // --- Leveling & GUI API ---

    public static int getPlayerLevel(UUID playerUuid, String category) {
        if (getPlugin().getLevelManager() == null) return 1;
        return getPlugin().getLevelManager().getLevel(playerUuid, category);
    }

    public static double getPlayerXp(UUID playerUuid, String category) {
        if (getPlugin().getLevelManager() == null) return 0.0;
        return getPlugin().getLevelManager().getXp(playerUuid, category);
    }

    public static long getRequiredXp(int level) {
        if (getPlugin().getLevelManager() == null) return 100L;
        return getPlugin().getLevelManager().getRequiredXp(level);
    }

    public static long getRequiredXpForNextLevel(UUID playerUuid, String category) {
        if (getPlugin().getLevelManager() == null) return 100L;
        return getPlugin().getLevelManager().getRequiredXpForNextLevel(playerUuid, category);
    }

    public static double getProgressPercent(UUID playerUuid, String category) {
        if (getPlugin().getLevelManager() == null) return 0.0;
        return getPlugin().getLevelManager().getProgressPercent(playerUuid, category);
    }

    public static void addPlayerXp(Player player, String category, double amount) {
        if (getPlugin().getLevelManager() != null) {
            getPlugin().getLevelManager().addXp(player, category, amount);
        }
    }

    public static void openProfileGUI(Player viewer, OfflinePlayer target) {
        if (getPlugin().getProfileGUIManager() != null) {
            getPlugin().getProfileGUIManager().openProfile(viewer, target);
        }
    }

    public static void openCategoryDetailGUI(Player viewer, OfflinePlayer target, String category, int page) {
        if (getPlugin().getProfileGUIManager() != null) {
            getPlugin().getProfileGUIManager().openCategoryDetail(viewer, target, category, page);
        }
    }
}

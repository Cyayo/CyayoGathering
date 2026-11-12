package id.cyayo.gathering.hook;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.api.event.NodeGatherCancelEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.UUID;

public class CyayoProfilesHook {

    private static boolean checked = false;
    private static boolean available = false;
    private static Method getActiveProfileUuidMethod = null;
    private static Method getGatheringXpMultiplierMethod = null;
    private static Method getGatheringLuckBonusMethod = null;
    private static Method getGatheringSpeedBonusMethod = null;
    private static Method getGatheringDoubleBonusMethod = null;

    public static void registerListener(CyayoGathering plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("CyayoProfiles")) {
            return;
        }

        try {
            Class<?> deleteEventClass = Class.forName("id.cyayo.profiles.api.event.ProfileDeleteEvent");
            Class<? extends Event> eventClass = deleteEventClass.asSubclass(Event.class);

            Bukkit.getPluginManager().registerEvent(eventClass, new Listener() {}, EventPriority.MONITOR, (listener, event) -> {
                if (!deleteEventClass.isInstance(event)) return;
                try {
                    Method getProfileMethod = deleteEventClass.getMethod("getProfile");
                    Object profileObj = getProfileMethod.invoke(event);
                    if (profileObj != null) {
                        Method getProfileUuidMethod = profileObj.getClass().getMethod("getProfileUuid");
                        Object uuidObj = getProfileUuidMethod.invoke(profileObj);
                        if (uuidObj instanceof UUID profileUuid) {
                            plugin.getLevelManager().deleteData(profileUuid);
                            plugin.getStatsManager().deleteData(profileUuid);
                            plugin.getLuckManager().deleteData(profileUuid);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }, plugin);

            Class<?> switchEventClass = Class.forName("id.cyayo.profiles.api.event.ProfileSwitchEvent");
            Class<? extends Event> swClass = switchEventClass.asSubclass(Event.class);

            Bukkit.getPluginManager().registerEvent(swClass, new Listener() {}, EventPriority.LOWEST, (listener, event) -> {
                if (!switchEventClass.isInstance(event)) return;
                try {
                    Method getPlayerMethod = switchEventClass.getMethod("getPlayer");
                    Object pObj = getPlayerMethod.invoke(event);
                    if (pObj instanceof Player p && plugin.getGatherManager() != null) {
                        plugin.getGatherManager().cancelGathering(p, NodeGatherCancelEvent.CancelReason.ITEM_SWITCH);
                    }
                } catch (Throwable ignored) {
                }
            }, plugin);

            plugin.getLogger().info("Successfully hooked into CyayoProfiles for per-profile gathering isolation!");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook CyayoProfiles events: " + t.getMessage());
        }
    }

    private static void ensureInitialized() {
        if (checked) return;
        checked = true;
        try {
            Class<?> apiClass = Class.forName("id.cyayo.profiles.api.CyayoProfilesAPI");
            getActiveProfileUuidMethod = apiClass.getMethod("getActiveProfileUuid", UUID.class);
            getGatheringXpMultiplierMethod = apiClass.getMethod("getGatheringXpMultiplier", UUID.class);
            getGatheringLuckBonusMethod = apiClass.getMethod("getGatheringLuckBonus", UUID.class);
            getGatheringSpeedBonusMethod = apiClass.getMethod("getGatheringSpeedBonus", UUID.class);
            getGatheringDoubleBonusMethod = apiClass.getMethod("getGatheringDoubleBonus", UUID.class);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public static UUID resolveDataUuid(UUID playerUuid) {
        if (playerUuid == null) return null;
        if (!Bukkit.getPluginManager().isPluginEnabled("CyayoProfiles")) {
            return playerUuid;
        }

        ensureInitialized();

        if (!available || getActiveProfileUuidMethod == null) {
            return playerUuid;
        }

        try {
            Object res = getActiveProfileUuidMethod.invoke(null, playerUuid);
            if (res instanceof UUID profileUuid) {
                return profileUuid;
            }
        } catch (Throwable ignored) {
        }

        return playerUuid;
    }

    public static double getGatheringXpMultiplier(UUID playerUuid) {
        return invokeDoubleMethod(getGatheringXpMultiplierMethod, playerUuid, 1.0);
    }

    public static double getGatheringLuckBonus(UUID playerUuid) {
        return invokeDoubleMethod(getGatheringLuckBonusMethod, playerUuid, 0.0);
    }

    public static double getGatheringSpeedBonus(UUID playerUuid) {
        return invokeDoubleMethod(getGatheringSpeedBonusMethod, playerUuid, 0.0);
    }

    public static double getGatheringDoubleBonus(UUID playerUuid) {
        return invokeDoubleMethod(getGatheringDoubleBonusMethod, playerUuid, 0.0);
    }

    private static double invokeDoubleMethod(Method method, UUID playerUuid, double fallback) {
        if (playerUuid == null || !Bukkit.getPluginManager().isPluginEnabled("CyayoProfiles")) {
            return fallback;
        }
        ensureInitialized();
        if (!available || method == null) {
            return fallback;
        }
        try {
            Object res = method.invoke(null, playerUuid);
            if (res instanceof Number num) {
                return num.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}

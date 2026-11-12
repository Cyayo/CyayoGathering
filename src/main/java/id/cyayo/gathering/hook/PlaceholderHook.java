package id.cyayo.gathering.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridge terpusat (softdepend-safe) untuk menyelesaikan placeholder
 * {@code PlaceholderAPI} dan {@code SeriaPrefix} (baik format {@code %...%} maupun tag MiniMessage
 * seperti {@code <sc:...>}, {@code <p:...>}, {@code <sp:...>}, {@code <seriaprefix:...>},
 * {@code <placeholder:...>}, {@code <imc:...>}, {@code <itsmyconfig:...>}, {@code <papi:...>},
 * {@code <smallcaps>}, dll.).
 */
public final class PlaceholderHook {

    private static final int MAX_PAPI_PASSES = 2;

    private static final Pattern AMPERSAND_HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern SERIAPREFIX_TAG_PATTERN = Pattern.compile(
            "<(?:/?(?:p|sc|sp|placeholder|seriaprefix|itsmyconfig|imc|papi):|/?(?:smallcaps|lowercase|uppercase|plain)(?::|>)|(?:quote|delay|title|subtitle|toast|actionbar|bossbar|sound|repeat)(?::|>))",
            Pattern.CASE_INSENSITIVE
    );

    private static volatile ReflectionBridge cachedBridge;

    private PlaceholderHook() {
    }

    /**
     * Menyelesaikan placeholder {@code %...%} menggunakan PlaceholderAPI (jika aktif),
     * mendukung {@code player} bernilai {@code null} untuk placeholder global.
     */
    public static String resolvePlaceholders(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.indexOf('%') == -1 || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        String current = text;
        try {
            for (int pass = 0; pass < MAX_PAPI_PASSES; pass++) {
                String updated = PapiBridge.setPlaceholders(player, current);
                if (updated == null || updated.equals(current)) {
                    break;
                }
                current = updated;
                if (current.indexOf('%') == -1) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Biarkan fallback ke teks terakhir jika plugin ekspansi PAPI bermasalah
        }
        return current;
    }

    /**
     * Mengecek apakah teks mengandung tag kustom {@code SeriaPrefix}.
     */
    public static boolean containsSeriaPrefixTag(String text) {
        if (text == null || text.indexOf('<') == -1) {
            return false;
        }
        return SERIAPREFIX_TAG_PATTERN.matcher(text).find();
    }

    /**
     * Mencoba menerjemahkan teks yang mengandung tag {@code SeriaPrefix} langsung melalui engine
     * {@code SeriaPrefix} dan men-serialize hasilnya ke string legacy (§ + hex §x).
     *
     * @return String terformat (§), atau {@code null} jika SeriaPrefix tidak aktif,
     *         teks tidak mengandung tag SeriaPrefix, atau bridge refleksi gagal.
     */
    public static String tryTranslateSeriaPrefix(OfflinePlayer player, String text) {
        if (!containsSeriaPrefixTag(text)) {
            return null;
        }

        Plugin seriaPlugin = Bukkit.getPluginManager().getPlugin("SeriaPrefix");
        if (seriaPlugin == null || !seriaPlugin.isEnabled()) {
            return null;
        }

        ReflectionBridge bridge = getOrInitBridge(seriaPlugin);
        if (bridge == null) {
            return null;
        }

        try {
            // Normalisasi &#RRGGBB -> <#RRGGBB> sebelum masuk parser MiniMessage SeriaPrefix
            String normalized = normalizeAmpersandHexToMini(text);
            Object component = bridge.translateOfflineMethod.invoke(null, normalized, player, bridge.emptyTagResolvers);
            if (component == null) {
                return null;
            }
            String serialized = (String) bridge.serializeMethod.invoke(bridge.legacySerializer, component);
            if (serialized == null) {
                return null;
            }
            // Jika hasil tag SeriaPrefix masih mengandung PAPI (%...%) atau &#RRGGBB, selesaikan
            if (serialized.indexOf('%') != -1) {
                serialized = resolvePlaceholders(player, serialized);
            }
            return convertAmpersandHexToSection(serialized);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeAmpersandHexToMini(String text) {
        if (text.indexOf("&#") == -1) {
            return text;
        }
        return AMPERSAND_HEX_PATTERN.matcher(text).replaceAll("<#$1>");
    }

    private static String convertAmpersandHexToSection(String text) {
        if (text == null || text.indexOf("&#") == -1) {
            return text;
        }
        Matcher matcher = AMPERSAND_HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) {
                replacement.append('§').append(hex.charAt(i));
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static ReflectionBridge getOrInitBridge(Plugin seriaPlugin) {
        ReflectionBridge current = cachedBridge;
        if (current != null && current.plugin == seriaPlugin) {
            return current;
        }
        synchronized (PlaceholderHook.class) {
            current = cachedBridge;
            if (current != null && current.plugin == seriaPlugin) {
                return current;
            }
            ReflectionBridge created = ReflectionBridge.create(seriaPlugin);
            cachedBridge = created;
            return created;
        }
    }

    private static final class PapiBridge {
        private static String setPlaceholders(OfflinePlayer player, String text) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
    }

    private static final class ReflectionBridge {
        private final Plugin plugin;
        private final Method translateOfflineMethod;
        private final Object emptyTagResolvers;
        private final Object legacySerializer;
        private final Method serializeMethod;

        private ReflectionBridge(
                Plugin plugin,
                Method translateOfflineMethod,
                Object emptyTagResolvers,
                Object legacySerializer,
                Method serializeMethod
        ) {
            this.plugin = plugin;
            this.translateOfflineMethod = translateOfflineMethod;
            this.emptyTagResolvers = emptyTagResolvers;
            this.legacySerializer = legacySerializer;
            this.serializeMethod = serializeMethod;
        }

        private static ReflectionBridge create(Plugin seriaPlugin) {
            try {
                ClassLoader cl = seriaPlugin.getClass().getClassLoader();
                Class<?> utilitiesClass = Class.forName("id.seria.prefix.util.Utilities", true, cl);

                Method translateMethod = null;
                for (Method m : utilitiesClass.getMethods()) {
                    if (!"translate".equals(m.getName())) {
                        continue;
                    }
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 3
                            && params[0] == String.class
                            && params[1] == OfflinePlayer.class
                            && params[2].isArray()) {
                        translateMethod = m;
                        break;
                    }
                }
                if (translateMethod == null) {
                    return null;
                }

                Class<?> tagResolverClass = translateMethod.getParameterTypes()[2].getComponentType();
                Object emptyResolvers = Array.newInstance(tagResolverClass, 0);

                Class<?> componentClass = translateMethod.getReturnType();
                String textPkg = componentClass.getPackageName();

                Object serializer = null;
                Method serialize = null;
                try {
                    Class<?> legacySerializerClass = Class.forName(
                            textPkg + ".serializer.legacy.LegacyComponentSerializer", true, cl);
                    Class<?> builderClass = Class.forName(
                            textPkg + ".serializer.legacy.LegacyComponentSerializer$Builder", true, cl);

                    Object builder = legacySerializerClass.getMethod("builder").invoke(null);
                    builderClass.getMethod("character", char.class).invoke(builder, '§');
                    builderClass.getMethod("hexCharacter", char.class).invoke(builder, '#');
                    builderClass.getMethod("hexColors").invoke(builder);
                    builderClass.getMethod("useUnusualXRepeatedCharacterHexFormat").invoke(builder);
                    serializer = builderClass.getMethod("build").invoke(builder);
                    serialize = legacySerializerClass.getMethod("serialize", componentClass);
                } catch (Throwable ignored) {
                    // Fallback ke Utilities.LEGACY_SERIALIZER jika builder berbeda versi
                    Field legacyField = utilitiesClass.getField("LEGACY_SERIALIZER");
                    serializer = legacyField.get(null);
                    Class<?> legacySerializerClass = Class.forName(
                            textPkg + ".serializer.legacy.LegacyComponentSerializer", true, cl);
                    serialize = legacySerializerClass.getMethod("serialize", componentClass);
                }

                return new ReflectionBridge(seriaPlugin, translateMethod, emptyResolvers, serializer, serialize);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}


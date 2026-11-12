package id.cyayo.gathering.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern HEX_TAG_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("[§&]x[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])[§&]([0-9a-fA-F])");

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        String processed = text;

        // Convert Minecraft legacy hex (§x§r§r§g§g§b§b) -> <color:#rrggbb>
        Matcher legacyHexMatcher = LEGACY_HEX_PATTERN.matcher(processed);
        StringBuffer sbHex = new StringBuffer();
        while (legacyHexMatcher.find()) {
            String hex = "#" + legacyHexMatcher.group(1) + legacyHexMatcher.group(2) + legacyHexMatcher.group(3)
                    + legacyHexMatcher.group(4) + legacyHexMatcher.group(5) + legacyHexMatcher.group(6);
            legacyHexMatcher.appendReplacement(sbHex, "<color:" + hex + ">");
        }
        legacyHexMatcher.appendTail(sbHex);
        processed = sbHex.toString();

        // Convert standard hex &#RRGGBB -> <color:#RRGGBB>
        Matcher hexTagMatcher = HEX_TAG_PATTERN.matcher(processed);
        StringBuffer sbTag = new StringBuffer();
        while (hexTagMatcher.find()) {
            hexTagMatcher.appendReplacement(sbTag, "<color:#" + hexTagMatcher.group(1) + ">");
        }
        hexTagMatcher.appendTail(sbTag);
        processed = sbTag.toString();

        // Convert legacy color codes to MiniMessage tags
        processed = processed
                .replace("&0", "<reset><black>").replace("§0", "<reset><black>")
                .replace("&1", "<reset><dark_blue>").replace("§1", "<reset><dark_blue>")
                .replace("&2", "<reset><dark_green>").replace("§2", "<reset><dark_green>")
                .replace("&3", "<reset><dark_aqua>").replace("§3", "<reset><dark_aqua>")
                .replace("&4", "<reset><dark_red>").replace("§4", "<reset><dark_red>")
                .replace("&5", "<reset><dark_purple>").replace("§5", "<reset><dark_purple>")
                .replace("&6", "<reset><gold>").replace("§6", "<reset><gold>")
                .replace("&7", "<reset><gray>").replace("§7", "<reset><gray>")
                .replace("&8", "<reset><dark_gray>").replace("§8", "<reset><dark_gray>")
                .replace("&9", "<reset><blue>").replace("§9", "<reset><blue>")
                .replace("&a", "<reset><green>").replace("§a", "<reset><green>")
                .replace("&b", "<reset><aqua>").replace("§b", "<reset><aqua>")
                .replace("&c", "<reset><red>").replace("§c", "<reset><red>")
                .replace("&d", "<reset><light_purple>").replace("§d", "<reset><light_purple>")
                .replace("&e", "<reset><yellow>").replace("§e", "<reset><yellow>")
                .replace("&f", "<reset><white>").replace("§f", "<reset><white>")
                .replace("&l", "<bold>").replace("§l", "<bold>")
                .replace("&m", "<strikethrough>").replace("§m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("§n", "<underlined>")
                .replace("&o", "<italic>").replace("§o", "<italic>")
                .replace("&k", "<obfuscated>").replace("§k", "<obfuscated>")
                .replace("&r", "<reset>").replace("§r", "<reset>");

        try {
            return MINI_MESSAGE.deserialize(processed);
        } catch (Exception e) {
            try {
                return SECTION_SERIALIZER.deserialize(text);
            } catch (Exception ex) {
                return AMPERSAND_SERIALIZER.deserialize(text);
            }
        }
    }

    public static String color(String text) {
        return color((org.bukkit.OfflinePlayer) null, text);
    }

    public static String color(org.bukkit.OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) return "";
        String resolved = id.cyayo.gathering.hook.PlaceholderHook.resolvePlaceholders(player, text);
        String seriaTranslated = id.cyayo.gathering.hook.PlaceholderHook.tryTranslateSeriaPrefix(player, resolved);
        if (seriaTranslated != null) {
            return seriaTranslated;
        }
        return colorResolved(resolved);
    }

    private static String colorResolved(String resolved) {
        if (resolved == null || resolved.isEmpty()) return "";
        if (resolved.contains("\n")) {
            String[] lines = resolved.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                lines[i] = colorResolved(lines[i]);
            }
            return String.join("\n", lines);
        }
        try {
            return SECTION_SERIALIZER.serialize(parse(resolved));
        } catch (Exception e) {
            return ChatColor.translateAlternateColorCodes('&', resolved);
        }
    }

    public static List<String> color(List<String> list) {
        return color((org.bukkit.OfflinePlayer) null, list);
    }

    public static List<String> color(org.bukkit.OfflinePlayer player, List<String> list) {
        if (list == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String s : list) {
            result.add(color(player, s));
        }
        return result;
    }

    public static String toLegacy(String text) {
        return color(text);
    }

    public static String toLegacy(org.bukkit.OfflinePlayer player, String text) {
        return color(player, text);
    }

    /**
     * Memeriksa apakah teks bernilai "none", '"none"', atau null (case-insensitive).
     * Jika true, maka pesan tersebut tidak akan dikirimkan ke player/server.
     */
    public static boolean isNone(String text) {
        if (text == null) return true;
        String clean = text.trim();
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            if (clean.length() >= 2) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
        }
        return clean.equalsIgnoreCase("none");
    }

    public static boolean isNoneOrEmpty(String text) {
        if (text == null) return true;
        if (text.trim().isEmpty()) return true;
        return isNone(text);
    }
}

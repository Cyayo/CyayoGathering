package id.cyayo.gathering.hologram;

import id.cyayo.gathering.CyayoGathering;
import id.cyayo.gathering.model.ActiveNode;
import id.cyayo.gathering.model.NodeType;
import id.cyayo.gathering.util.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class HologramFormatter {

    private final CyayoGathering plugin;

    public HologramFormatter(CyayoGathering plugin) {
        this.plugin = plugin;
    }

    public List<String> formatRaw(ActiveNode node, NodeType type, double progress, String gathererName) {
        if (node == null || type == null) return List.of();

        String stateStr = node.getStatus().name().toLowerCase();
        List<String> rawLines = type.getHologramLines(stateStr);
        if (rawLines == null || rawLines.isEmpty()) {
            rawLines = List.of(type.getName() != null ? type.getName() : "&eGathering Node");
        }

        int percent = (int) Math.min(100, Math.max(0, Math.round(progress * 100.0)));
        String progressBar = buildProgressBar(percent);

        int currentCharges = node.getCurrentCharges();
        int maxCharges = Math.max(1, node.getMaxCharges());
        double chargesPercent = ((double) currentCharges / maxCharges) * 100.0;

        String chargesColor = getChargesColor(currentCharges, maxCharges);
        String colorFull = plugin.getConfig().getString("charges_format.color_full", "&a");
        boolean autoColorCharges = plugin.getConfig().getBoolean("charges_format.auto_color_charges_placeholder", false);

        String chargesBar = buildChargesBar(currentCharges, maxCharges, chargesColor);
        String chargesValueStr = autoColorCharges ? (chargesColor + currentCharges) : String.valueOf(currentCharges);

        List<String> formatted = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw
                    .replace("{name}", type.getName())
                    .replace("{charges_color}", chargesColor)
                    .replace("{charges_colored}", chargesColor + currentCharges)
                    .replace("{charges_ratio}", chargesColor + currentCharges + "&7/" + colorFull + maxCharges)
                    .replace("{charges_percent}", String.valueOf((int) Math.round(chargesPercent)))
                    .replace("{charges}", chargesValueStr)
                    .replace("{max_charges}", String.valueOf(maxCharges))
                    .replace("{charges_bar}", chargesBar)
                    .replace("{progress_bar}", progressBar)
                    .replace("{percent}", String.valueOf(percent))
                    .replace("{gatherer}", gathererName != null ? gathererName : "-")
                    .replace("{cooldown}", String.valueOf(node.getCooldownRemainingSeconds()));

            formatted.add(line);
        }

        return formatted;
    }

    public List<String> formatLegacy(ActiveNode node, NodeType type, double progress, String gathererName) {
        List<String> raw = formatRaw(node, type, progress, gathererName);
        org.bukkit.OfflinePlayer gatherer = (gathererName != null && !gathererName.isEmpty() && !"-".equals(gathererName))
                ? org.bukkit.Bukkit.getPlayerExact(gathererName)
                : null;
        List<String> legacy = new ArrayList<>(raw.size());
        for (String s : raw) {
            legacy.add(ColorUtils.color(gatherer, s));
        }
        return legacy;
    }

    public List<String> format(ActiveNode node, NodeType type, double progress, String gathererName) {
        return formatLegacy(node, type, progress, gathererName);
    }

    public String getChargesColor(int currentCharges, int maxCharges) {
        if (maxCharges <= 0) maxCharges = 1;
        double ratio = (double) currentCharges / maxCharges;
        double percent = ratio * 100.0;

        int threshHigh = plugin.getConfig().getInt("charges_format.threshold_high", 75);
        int threshLow = plugin.getConfig().getInt("charges_format.threshold_low", 25);

        String colorFull = plugin.getConfig().getString("charges_format.color_full", "&a");
        String colorNotFull = plugin.getConfig().getString("charges_format.color_not_full",
                plugin.getConfig().getString("charges_format.color_medium", "&e"));
        String colorBelow75 = plugin.getConfig().getString("charges_format.color_below_75", "&6");
        String colorBelow25 = plugin.getConfig().getString("charges_format.color_below_25",
                plugin.getConfig().getString("charges_format.color_low", "&c"));

        if (currentCharges >= maxCharges) {
            return colorFull;
        } else if (percent >= threshHigh) {
            return colorNotFull;
        } else if (percent >= threshLow) {
            return colorBelow75;
        } else {
            return colorBelow25;
        }
    }

    private String buildProgressBar(int percent) {
        String filledSym = plugin.getConfig().getString("progress_bar.symbol_filled", "■");
        String emptySym = plugin.getConfig().getString("progress_bar.symbol_empty", "□");
        int totalBars = plugin.getConfig().getInt("progress_bar.total_bars", 10);
        String filledColor = plugin.getConfig().getString("progress_bar.color_filled", "&a");
        String emptyColor = plugin.getConfig().getString("progress_bar.color_empty", "&7");

        int filledCount = (int) Math.round((percent / 100.0) * totalBars);
        filledCount = Math.max(0, Math.min(totalBars, filledCount));
        int emptyCount = totalBars - filledCount;

        return filledColor + filledSym.repeat(filledCount) + emptyColor + emptySym.repeat(emptyCount);
    }

    private String buildChargesBar(int current, int max, String chargesColor) {
        boolean dynamicColor = plugin.getConfig().getBoolean("charges_bar.dynamic_color", true);
        String filledColor = dynamicColor ? chargesColor
                : plugin.getConfig().getString("charges_bar.color_filled", "&a");

        String filledSym = plugin.getConfig().getString("charges_bar.symbol_filled", plugin.getConfig().getString("progress_bar.symbol_filled", "■"));
        String emptySym = plugin.getConfig().getString("charges_bar.symbol_empty", plugin.getConfig().getString("progress_bar.symbol_empty", "□"));
        int totalBars = plugin.getConfig().getInt("charges_bar.total_bars", 8);
        String emptyColor = plugin.getConfig().getString("charges_bar.color_empty", "&7");

        if (max <= 0) max = 1;
        int filled = (int) Math.round(((double) current / max) * totalBars);
        filled = Math.max(0, Math.min(totalBars, filled));
        int empty = totalBars - filled;

        return filledColor + filledSym.repeat(filled) + emptyColor + emptySym.repeat(empty);
    }
}

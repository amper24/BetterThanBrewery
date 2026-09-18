package dev.moonaticks.betterThanBrewery.util;

import org.bukkit.Color;
import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;

public final class ColorUtil {
    private static final Map<String, Color> NAMES = Map.ofEntries(
            Map.entry("black", Color.BLACK), Map.entry("white", Color.WHITE),
            Map.entry("red", Color.RED), Map.entry("green", Color.GREEN),
            Map.entry("blue", Color.BLUE), Map.entry("yellow", Color.YELLOW),
            Map.entry("orange", Color.ORANGE), Map.entry("purple", Color.PURPLE),
            Map.entry("aqua", Color.AQUA), Map.entry("fuchsia", Color.FUCHSIA),
            Map.entry("lime", Color.LIME), Map.entry("maroon", Color.MAROON),
            Map.entry("navy", Color.NAVY), Map.entry("olive", Color.OLIVE),
            Map.entry("teal", Color.TEAL), Map.entry("silver", Color.SILVER),
            Map.entry("gray", Color.GRAY), Map.entry("brown", Color.fromRGB(139, 69, 19)),
            Map.entry("pink", Color.fromRGB(255, 105, 180)), Map.entry("beige", Color.fromRGB(245, 245, 220)),
            Map.entry("gold", Color.fromRGB(255, 215, 0)));

    private ColorUtil() { }

    public static Color parse(String input, Color fallback) {
        if (input == null || input.isBlank()) return fallback;
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("#")) value = value.substring(1);
        try {
            if (value.matches("[0-9a-f]{6}")) return Color.fromRGB(Integer.parseInt(value, 16));
            if (value.matches("[0-9a-f]{3}")) {
                String expanded = value.substring(0, 1).repeat(2)
                        + value.substring(1, 2).repeat(2) + value.substring(2).repeat(2);
                return Color.fromRGB(Integer.parseInt(expanded, 16));
            }
        } catch (IllegalArgumentException ignored) { }
        return NAMES.getOrDefault(value, fallback);
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public static String replace(String value, Map<String, String> placeholders) {
        String out = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }
}

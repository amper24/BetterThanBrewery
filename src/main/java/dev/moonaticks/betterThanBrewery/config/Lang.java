package dev.moonaticks.betterThanBrewery.config;

import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public final class Lang {
    private final ConfigManager configs;
    public Lang(ConfigManager configs) { this.configs = configs; }
    public String text(String key, Map<String, String> placeholders) {
        FileConfiguration yml = configs.messages();
        String prefix = yml.getString("prefix", "");
        String value = yml.getString(key, key);
        return ColorUtil.color(ColorUtil.replace(prefix + value, placeholders));
    }
    public void send(CommandSender sender, String key) { sender.sendMessage(text(key, Map.of())); }
    public void send(CommandSender sender, String key, Map<String, String> placeholders) { sender.sendMessage(text(key, placeholders)); }
}

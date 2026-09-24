package dev.moonaticks.betterThanBrewery.config;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigManager {
    private final BetterThanBrewery plugin;
    private FileConfiguration messages;
    private File messagesFile;

    public ConfigManager(BetterThanBrewery plugin) { this.plugin = plugin; }

    public void load() {
        plugin.saveDefaultConfig();
        copyIfMissing("messages.yml");
        for (String resource : new String[]{
                "recipes/boiler/apple-cider.yml", "recipes/distiller/brandy.yml",
                "recipes/barrel/oak-cider.yml", "recipes/kettle/herbal-tea.yml",
                "recipes/brewery/wheat-beer.yml", "recipes/brewery/berry-wine.yml",
                "recipes/brewery/honey-mead.yml", "recipes/brewery/grain-vodka.yml",
                "recipes/brewery/berry-spirit.yml", "recipes/brewery/whiskey.yml",
                "recipes/brewery/rum.yml", "recipes/brewery/absinthe.yml",
                "recipes/brewery/oak-beer.yml", "recipes/brewery/oak-wine.yml",
                "recipes/kettle/black-tea.yml", "recipes/kettle/mint-tea.yml",
                "recipes/kettle/berry-tea.yml", "recipes/kettle/coffee.yml"}) copyIfMissing(resource);
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messages = loadMessages();
    }

    private FileConfiguration loadMessages() {
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(messagesFile);
        // Do not overwrite server translations; fall back to new bundled keys
        // for existing installations with an older messages.yml.
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) loaded.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot read default messages: " + ex.getMessage());
        }
        return loaded;
    }

    private void copyIfMissing(String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resource, false);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        messages = loadMessages();
    }

    public FileConfiguration config() { return plugin.getConfig(); }
    public FileConfiguration messages() { return messages; }
    public File recipesFolder() { return new File(plugin.getDataFolder(), "recipes"); }
    public void saveMessages() throws IOException { messages.save(messagesFile); }
}

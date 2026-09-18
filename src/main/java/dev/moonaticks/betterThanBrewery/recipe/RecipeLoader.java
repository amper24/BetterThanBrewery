package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;
import dev.moonaticks.betterThanBrewery.drink.DrinkEffect;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeLoader {
    private final ItemService items;
    public RecipeLoader(ItemService items) { this.items = items; }

    public RecipeRegistry load(File root) {
        RecipeRegistry registry = new RecipeRegistry();
        if (!root.exists()) root.mkdirs();
        loadFolder(root, registry);
        return registry;
    }

    private void loadFolder(File folder, RecipeRegistry registry) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) loadFolder(file, registry);
            else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                try { loadOne(file, registry); }
                catch (RuntimeException ex) { System.err.println("Could not load recipe " + file + ": " + ex.getMessage()); }
            }
        }
    }

    private void loadOne(File file, RecipeRegistry registry) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = yaml.getString("id", file.getName().replaceFirst("\\.(yaml|yml)$", "")).toLowerCase(Locale.ROOT);
        String station = yaml.getString("station", inferStation(file)).toLowerCase(Locale.ROOT);
        ConfigurationSection output = yaml.getConfigurationSection("output");
        DrinkDefinition drink = drink(id, output == null ? yaml : output, yaml.getConfigurationSection("formulas"));
        List<Ingredient> ingredients = ingredients(yaml, station);
        int time = ticks(yaml, "time", 0);
        if (yaml.contains("time-seconds")) time = Math.max(0, yaml.getInt("time-seconds") * 20);
        int ideal = ticks(yaml, "ideal-time", time);
        int max = ticks(yaml, "max-time", ideal + Math.max(1, yaml.getInt("overcook-window", Math.max(1, ideal / 2))));
        String inputFluid = yaml.getString("input-fluid", yaml.getString("input.fluid", ""));
        String outputFluid = output == null ? id : output.getString("fluid", output.getString("id", id));
        ConfigurationSection fuel = yaml.getConfigurationSection("fuel");
        String fuelItem = fuel == null ? "" : fuel.getString("item", "");
        int fuelAmount = fuel == null ? 0 : fuel.getInt("amount", 1);
        List<Byproduct> byproducts = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("byproducts")) {
            Object item = map.get("item"); if (item == null) continue;
            byproducts.add(new Byproduct(String.valueOf(item), number(map.get("amount"), 1), decimal(map.get("chance"), 1)));
        }
        Map<String, String> formulas = strings(yaml.getConfigurationSection("formulas"));
        int weeks = yaml.getInt("weeks", yaml.getInt("age-weeks", 0));
        RecipeDefinition recipe = new RecipeDefinition(id, station, ingredients,
                yaml.getInt("water", yaml.getInt("water-units", 0)), time, ideal, max,
                inputFluid, outputFluid, weeks, fuelItem, fuelAmount, drink, byproducts, formulas);
        registry.add(recipe);
    }

    private DrinkDefinition drink(String id, ConfigurationSection section, ConfigurationSection recipeFormulas) {
        String name = section.getString("name", id);
        org.bukkit.Color color = ColorUtil.parse(section.getString("color", "#FFFFFF"), org.bukkit.Color.WHITE);
        int food = section.getInt("food", section.getInt("hunger", 0));
        double alcohol = section.getDouble("alcohol", 0);
        List<String> lore = section.getStringList("lore");
        List<DrinkEffect> effects = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("effects")) {
            Object type = map.get("type"); if (type == null) continue;
            effects.add(new DrinkEffect(String.valueOf(type), number(map.get("duration"), 100),
                    number(map.get("amplifier"), 0), decimal(map.get("chance"), 1),
                    string(map.get("duration-formula")), string(map.get("amplifier-formula"))));
        }
        List<String> commands = section.getStringList("commands");
        String denizen = section.getString("denizen-script", section.getString("denizen", ""));
        Map<String, String> formulas = strings(section.getConfigurationSection("formulas"));
        if (recipeFormulas != null) formulas = merge(formulas, strings(recipeFormulas));
        String item = section.getString("item", "");
        return new DrinkDefinition(id, name, color, food, alcohol, lore, effects, commands, denizen, formulas, item);
    }

    private List<Ingredient> ingredients(YamlConfiguration yaml, String station) {
        List<Integer> defaults = switch (station) {
            case "boiler" -> List.of(10, 11, 12, 13, 14, 15);
            case "kettle" -> List.of(10, 11, 12, 13);
            default -> List.of();
        };
        List<Ingredient> out = new ArrayList<>(); int index = 0;
        for (Map<?, ?> map : yaml.getMapList("ingredients")) {
            Object item = map.get("item"); if (item == null) continue;
            int slot = number(map.get("slot"), index < defaults.size() ? defaults.get(index) : -1);
            out.add(new Ingredient(String.valueOf(item), number(map.get("amount"), 1), slot)); index++;
        }
        return out;
    }

    private static int ticks(YamlConfiguration yaml, String path, int fallback) {
        return yaml.contains(path) ? Math.max(0, yaml.getInt(path)) : fallback;
    }
    private static String inferStation(File file) {
        File parent = file.getParentFile(); return parent == null ? "boiler" : parent.getName();
    }
    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<>(); if (section == null) return out;
        for (String key : section.getKeys(false)) out.put(key.toLowerCase(Locale.ROOT), section.getString(key, ""));
        return out;
    }
    private static Map<String, String> merge(Map<String, String> left, Map<String, String> right) { Map<String, String> out = new LinkedHashMap<>(left); out.putAll(right); return out; }
    private static int number(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return fallback; } }
    private static double decimal(Object value, double fallback) { try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return fallback; } }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
}

package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;
import dev.moonaticks.betterThanBrewery.drink.DrinkEffect;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class RecipeLoader {
    private static final Set<String> STATIONS = Set.of("boiler", "distiller", "barrel", "kettle");
    private final Logger logger;

    public RecipeLoader(Logger logger) {
        this.logger = logger == null ? Logger.getLogger("BetterThanBrewery") : logger;
    }

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
                try {
                    loadFile(file, registry);
                } catch (RuntimeException ex) {
                    logger.warning("Не удалось загрузить рецепт " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Loads either the historical one-recipe format or a file containing a
     * {@code recipes:} map/list. Keeping the dispatch here means custom files
     * and the bundled files follow exactly the same validation path.
     */
    private void loadFile(File file, RecipeRegistry registry) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection recipes = yaml.getConfigurationSection("recipes");
        if (recipes != null) {
            for (String key : recipes.getKeys(false)) {
                ConfigurationSection recipe = recipes.getConfigurationSection(key);
                if (recipe == null) {
                    throw new IllegalArgumentException("recipes." + key + " должен быть секцией");
                }
                loadOne(file, recipe, registry, key);
            }
            return;
        }

        // A YAML list is also accepted:
        // recipes:
        //   - id: wheat_beer
        //     station: boiler
        List<Map<?, ?>> recipeList = yaml.getMapList("recipes");
        if (!recipeList.isEmpty() || yaml.contains("recipes")) {
            if (recipeList.isEmpty()) {
                throw new IllegalArgumentException("recipes должен быть картой рецептов или списком");
            }
            for (Map<?, ?> values : recipeList) {
                loadOne(file, section(values), registry, null);
            }
            return;
        }

        // Backwards-compatible format: the whole file is one recipe.
        loadOne(file, yaml, registry, null);
    }

    private void loadOne(File file, ConfigurationSection yaml, RecipeRegistry registry, String mapId) {
        String fallbackId = mapId == null || mapId.isBlank()
                ? file.getName().replaceFirst("\\.(yaml|yml)$", "")
                : mapId;
        String id = yaml.getString("id", fallbackId).toLowerCase(Locale.ROOT);
        String station = yaml.getString("station", inferStation(file)).toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("id не может быть пустым");
        if (!STATIONS.contains(station)) throw new IllegalArgumentException("неизвестная station: " + station);
        ConfigurationSection output = yaml.getConfigurationSection("output");
        if (output == null || !output.contains("name") || !output.contains("color")) {
            throw new IllegalArgumentException("output.name и output.color обязательны");
        }
        String drinkId = output.getString("id", id).toLowerCase(Locale.ROOT);
        DrinkDefinition drink = drink(drinkId, output, yaml.getConfigurationSection("formulas"));
        List<Ingredient> ingredients = ingredients(yaml, station);
        int time = ticks(yaml, "time", 0);
        if (yaml.contains("time-seconds")) time = Math.max(0, yaml.getInt("time-seconds") * 20);
        int ideal = ticks(yaml, "ideal-time", time);
        int max = ticks(yaml, "max-time", ideal + Math.max(1, yaml.getInt("overcook-window", Math.max(1, ideal / 2))));
        String inputFluid = yaml.getString("input-fluid", yaml.getString("input.fluid", ""));
        String outputFluid = output.getString("fluid", drink.id());
        ConfigurationSection fuel = yaml.getConfigurationSection("fuel");
        String fuelItem = fuel == null ? "" : fuel.getString("item", "");
        int fuelAmount = fuel == null ? 0 : fuel.getInt("amount", 1);
        List<Byproduct> byproducts = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("byproducts")) {
            Object item = map.get("item");
            if (item == null) continue;
            byproducts.add(new Byproduct(String.valueOf(item), number(map.get("amount"), 1), decimal(map.get("chance"), 1)));
        }
        Map<String, String> formulas = strings(yaml.getConfigurationSection("formulas"));
        int weeks = yaml.getInt("weeks", yaml.getInt("age-weeks", 0));
        RecipeDefinition recipe = new RecipeDefinition(id, station, ingredients,
                yaml.getInt("water", yaml.getInt("water-units", 0)), time, ideal, max,
                inputFluid, outputFluid, weeks, fuelItem, fuelAmount, drink, byproducts, formulas);
        if (ingredients.isEmpty() && (station.equals("boiler") || station.equals("kettle"))) {
            throw new IllegalArgumentException("для " + station + " нужны ingredients");
        }
        if (registry.add(recipe) != null) logger.warning("Рецепт " + id + " был переопределён файлом " + file.getName());
    }

    /** Converts a map from a YAML list into the same ConfigurationSection API used for normal files. */
    private static YamlConfiguration section(Map<?, ?> values) {
        YamlConfiguration section = new YamlConfiguration();
        copyValues(section, values);
        return section;
    }

    private static void copyValues(ConfigurationSection target, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                ConfigurationSection child = target.createSection(key);
                copyValues(child, map);
            } else {
                target.set(key, value);
            }
        }
    }

    private DrinkDefinition drink(String id, ConfigurationSection section, ConfigurationSection recipeFormulas) {
        String name = section.getString("name", id);
        org.bukkit.Color color = ColorUtil.parse(section.getString("color", "#FFFFFF"), org.bukkit.Color.WHITE);
        int food = section.getInt("food", section.getInt("hunger", 0));
        double alcohol = section.getDouble("alcohol", 0);
        List<String> lore = section.getStringList("lore");
        List<DrinkEffect> effects = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("effects")) {
            Object type = map.get("type");
            if (type == null) continue;
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

    private List<Ingredient> ingredients(ConfigurationSection yaml, String station) {
        List<Integer> defaults = switch (station) {
            case "boiler" -> List.of(10, 11, 12, 13, 14, 15);
            case "kettle" -> List.of(10, 11, 12, 13);
            default -> List.of();
        };
        List<Ingredient> out = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> map : yaml.getMapList("ingredients")) {
            Object item = map.get("item");
            if (item == null) continue;
            int slot = number(map.get("slot"), index < defaults.size() ? defaults.get(index) : -1);
            out.add(new Ingredient(String.valueOf(item), number(map.get("amount"), 1), slot));
            index++;
        }
        return out;
    }

    private static int ticks(ConfigurationSection yaml, String path, int fallback) {
        return yaml.contains(path) ? Math.max(0, yaml.getInt(path)) : fallback;
    }

    private static String inferStation(File file) {
        File parent = file.getParentFile();
        return parent == null ? "boiler" : parent.getName();
    }

    private static Map<String, String> strings(ConfigurationSection section) {
        Map<String, String> out = new LinkedHashMap<>();
        if (section == null) return out;
        for (String key : section.getKeys(false)) out.put(key.toLowerCase(Locale.ROOT), section.getString(key, ""));
        return out;
    }

    private static Map<String, String> merge(Map<String, String> left, Map<String, String> right) {
        Map<String, String> out = new LinkedHashMap<>(left);
        out.putAll(right);
        return out;
    }

    private static int number(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double decimal(Object value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

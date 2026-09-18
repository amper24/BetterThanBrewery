package dev.moonaticks.betterThanBrewery;

import dev.moonaticks.betterThanBrewery.config.ConfigManager;
import dev.moonaticks.betterThanBrewery.config.Lang;
import dev.moonaticks.betterThanBrewery.drink.DrinkListener;
import dev.moonaticks.betterThanBrewery.drink.DrinkService;
import dev.moonaticks.betterThanBrewery.drunkenness.DrunkennessManager;
import dev.moonaticks.betterThanBrewery.item.ContainerService;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.recipe.RecipeLoader;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import dev.moonaticks.betterThanBrewery.station.StationManager;
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterThanBrewery extends JavaPlugin {
    private ConfigManager configs;
    private Lang lang;
    private ItemService items;
    private ContainerService containers;
    private DrinkService drinks;
    private RecipeLoader recipeLoader;
    private RecipeRegistry recipes;
    private StationManager stations;
    private DrunkennessManager drunkenness;

    @Override public void onEnable() {
        if (!CustomGuiAPI.isInitialized()) {
            getLogger().severe("CustomGuiReworked 2.x is required; disabling BetterThanBrewery.");
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        configs = new ConfigManager(this); configs.load(); lang = new Lang(configs);
        items = new ItemService(this); containers = new ContainerService(items); containers.load(getConfig());
        recipeLoader = new RecipeLoader(items); recipes = recipeLoader.load(configs.recipesFolder());
        drinks = new DrinkService(this, items, containers); drinks.setRegistry(recipes);
        stations = new StationManager(this, items, containers, drinks, lang); stations.setRecipes(recipes); stations.registerAll();
        drunkenness = new DrunkennessManager(this); drunkenness.load(); drunkenness.start();
        Bukkit.getPluginManager().registerEvents(new DrinkListener(drinks, drunkenness), this);
        Bukkit.getPluginManager().registerEvents(drunkenness, this);
        if (getCommand("betterbrewery") != null) { BetterBreweryCommand command = new BetterBreweryCommand(this, lang, recipeLoader, stations); getCommand("betterbrewery").setExecutor(command); getCommand("betterbrewery").setTabCompleter(command); }
        Bukkit.getScheduler().runTaskTimer(this, () -> CustomGuiAPI.getFunctionalBlocks().tickDataSave(), 200, Math.max(40, getConfig().getInt("settings.autosave-ticks", 200)));
        getLogger().info("BetterThanBrewery enabled: " + recipes.size() + " recipes, " + recipes.drinkCount() + " drinks, " + stations.stationCount() + " stations.");
    }

    public void reloadPlugin() {
        configs.reload(); containers.load(getConfig()); recipes = recipeLoader.load(configs.recipesFolder()); drinks.setRegistry(recipes);
        drunkenness.load(); stations.setRecipes(recipes); stations.registerAll();
    }
    @Override public void onDisable() { if (CustomGuiAPI.isInitialized()) CustomGuiAPI.getFunctionalBlocks().tickDataSave(); }
    public RecipeRegistry recipes() { return recipes; }
}

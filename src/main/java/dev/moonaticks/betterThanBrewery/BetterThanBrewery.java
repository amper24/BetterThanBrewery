package dev.moonaticks.betterThanBrewery;

import dev.moonaticks.betterThanBrewery.config.ConfigManager;
import dev.moonaticks.betterThanBrewery.config.Lang;
import dev.moonaticks.betterThanBrewery.drink.DrinkListener;
import dev.moonaticks.betterThanBrewery.drink.DrinkService;
import dev.moonaticks.betterThanBrewery.drunkenness.DrunkennessManager;
import dev.moonaticks.betterThanBrewery.item.ContainerService;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.recipe.RecipeBookManager;
import dev.moonaticks.betterThanBrewery.recipe.RecipeLoader;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import dev.moonaticks.betterThanBrewery.station.BreweryGuiTypes;
import dev.moonaticks.betterThanBrewery.station.StationManager;
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.GuiCategory;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
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
    private RecipeBookManager recipeBook;
    private DrunkennessManager drunkenness;

    @Override public void onEnable() {
        Plugin cgr = Bukkit.getPluginManager().getPlugin("CustomGuiReworked");
        if (cgr == null || !cgr.isEnabled() || !supportsGuiApi(cgr.getDescription().getVersion())
                || !CustomGuiAPI.isInitialized()) {
            getLogger().severe("CustomGuiReworked 2.4.5+ is required; disabling BetterThanBrewery.");
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        GuiCategory category = CustomGuiAPI.registerCategory(BreweryGuiTypes.stationCategory());
        SlotType fluidSlot = CustomGuiAPI.registerSlotType(BreweryGuiTypes.fluidSlot());
        configs = new ConfigManager(this); configs.load(); lang = new Lang(configs);
        items = new ItemService(this); containers = new ContainerService(items); containers.load(getConfig());
        recipeLoader = new RecipeLoader(getLogger()); recipes = recipeLoader.load(configs.recipesFolder());
        drinks = new DrinkService(this, items, containers); drinks.setRegistry(recipes);
        stations = new StationManager(this, items, containers, drinks, lang, category, fluidSlot);
        stations.setRecipes(recipes); stations.registerAll();
        recipeBook = new RecipeBookManager(this, items); recipeBook.setRecipes(recipes);
        drunkenness = new DrunkennessManager(this); drunkenness.load();
        Bukkit.getPluginManager().registerEvents(recipeBook, this);
        Bukkit.getPluginManager().registerEvents(new DrinkListener(drinks, drunkenness), this);
        Bukkit.getPluginManager().registerEvents(drunkenness, this);
        drunkenness.start();
        if (getCommand("betterbrewery") != null) { BetterBreweryCommand command = new BetterBreweryCommand(this, lang, stations, drunkenness); getCommand("betterbrewery").setExecutor(command); getCommand("betterbrewery").setTabCompleter(command); }
        Bukkit.getScheduler().runTaskTimer(this, () -> CustomGuiAPI.getFunctionalBlocks().tickDataSave(), 200, Math.max(40, getConfig().getInt("settings.autosave-ticks", 200)));
        getLogger().info("BetterThanBrewery enabled: " + recipes.size() + " recipes, " + recipes.drinkCount() + " drinks, " + stations.stationCount() + " stations.");
    }

    public void reloadPlugin() {
        stations.closeOpenGuis();
        recipeBook.closeOpenGuis();
        configs.reload();
        containers.load(getConfig());
        recipes = recipeLoader.load(configs.recipesFolder());
        drinks.setRegistry(recipes);
        drunkenness.load();
        stations.setRecipes(recipes);
        stations.registerAll();
        recipeBook.setRecipes(recipes);
    }
    @Override public void onDisable() {
        if (drunkenness != null) drunkenness.shutdown();
        if (recipeBook != null) recipeBook.closeOpenGuis();
        Plugin cgr = Bukkit.getPluginManager().getPlugin("CustomGuiReworked");
        if (cgr != null && cgr.isEnabled() && CustomGuiAPI.isInitialized()) {
            if (stations != null) {
                stations.closeOpenGuis();
                stations.unregisterHandlers();
                CustomGuiAPI.unregisterSlotType(BreweryGuiTypes.FLUID_ID);
            }
            CustomGuiAPI.getFunctionalBlocks().tickDataSave();
        }
    }
    public RecipeRegistry recipes() { return recipes; }

    private static boolean supportsGuiApi(String version) {
        try {
            String[] parts = version.split("[.-]");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);
            return major > 2 || major == 2 && (minor > 4 || minor == 4 && patch >= 5);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}

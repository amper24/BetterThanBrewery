package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RecipeRegistry {
    private final Map<String, RecipeDefinition> recipes = new LinkedHashMap<>();
    private final Map<String, DrinkDefinition> drinks = new LinkedHashMap<>();
    public void clear() { recipes.clear(); drinks.clear(); }
    public void add(RecipeDefinition recipe) { recipes.put(recipe.id().toLowerCase(Locale.ROOT), recipe); drinks.put(recipe.drink().id(), recipe.drink()); }
    public RecipeDefinition get(String id) { return recipes.get(id == null ? "" : id.toLowerCase(Locale.ROOT)); }
    public DrinkDefinition drink(String id) { return drinks.get(id == null ? "" : id.toLowerCase(Locale.ROOT)); }
    public Collection<RecipeDefinition> all() { return recipes.values(); }
    public int size() { return recipes.size(); }
    public int drinkCount() { return drinks.size(); }
}

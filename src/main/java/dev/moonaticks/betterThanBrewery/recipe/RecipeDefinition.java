package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;

import java.util.List;
import java.util.Map;

public final class RecipeDefinition {
    private final String id;
    private final String station;
    private final List<Ingredient> ingredients;
    private final int water;
    private final int time;
    private final int idealTime;
    private final int maxTime;
    private final String inputFluid;
    private final String outputFluid;
    private final int weeks;
    private final String fuelItem;
    private final int fuelAmount;
    private final DrinkDefinition drink;
    private final List<Byproduct> byproducts;
    private final Map<String, String> formulas;

    public RecipeDefinition(String id, String station, List<Ingredient> ingredients, int water,
                            int time, int idealTime, int maxTime, String inputFluid,
                            String outputFluid, int weeks, String fuelItem, int fuelAmount,
                            DrinkDefinition drink, List<Byproduct> byproducts, Map<String, String> formulas) {
        this.id = id; this.station = station; this.ingredients = List.copyOf(ingredients);
        this.water = Math.max(0, water); this.time = Math.max(0, time);
        this.idealTime = Math.max(1, idealTime); this.maxTime = Math.max(this.idealTime, maxTime);
        this.inputFluid = inputFluid == null ? "" : inputFluid.toLowerCase();
        this.outputFluid = outputFluid == null || outputFluid.isBlank() ? drink.id() : outputFluid.toLowerCase();
        this.weeks = Math.max(0, weeks); this.fuelItem = fuelItem == null ? "" : fuelItem;
        this.fuelAmount = Math.max(0, fuelAmount); this.drink = drink;
        this.byproducts = List.copyOf(byproducts); this.formulas = Map.copyOf(formulas);
    }
    public String id() { return id; }
    public String station() { return station; }
    public List<Ingredient> ingredients() { return ingredients; }
    public int water() { return water; }
    public int time() { return time; }
    public int idealTime() { return idealTime; }
    public int maxTime() { return maxTime; }
    public String inputFluid() { return inputFluid; }
    public String outputFluid() { return outputFluid; }
    public int weeks() { return weeks; }
    public String fuelItem() { return fuelItem; }
    public int fuelAmount() { return fuelAmount; }
    public DrinkDefinition drink() { return drink; }
    public List<Byproduct> byproducts() { return byproducts; }
    public Map<String, String> formulas() { return formulas; }
}

package dev.moonaticks.betterThanBrewery.recipe;

public record Ingredient(String item, int amount, int slot) {
    public Ingredient {
        amount = Math.max(1, amount);
    }
}

package dev.moonaticks.betterThanBrewery.recipe;

public record Byproduct(String item, int amount, double chance) {
    public Byproduct {
        amount = Math.max(1, amount);
        chance = Math.max(0, Math.min(1, chance));
    }
}

package dev.moonaticks.betterThanBrewery.drink;

public record DrinkEffect(String type, int duration, int amplifier, double chance,
                          String durationFormula, String amplifierFormula) {
    public DrinkEffect(String type, int duration, int amplifier, double chance) {
        this(type, duration, amplifier, chance, "", "");
    }
}

package dev.moonaticks.betterThanBrewery.station;

import java.util.List;

public record StationDefinition(String id, String gui, int capacity, int waterCapacity,
                                int waterSlot, int fluidInputSlot, int fuelSlot,
                                List<Integer> fluidSlots, List<Integer> ingredientSlots,
                                List<String> blockIds) {
    public boolean is(String value) { return id.equalsIgnoreCase(value) || gui.equalsIgnoreCase(value); }
}

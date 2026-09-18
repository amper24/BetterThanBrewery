package dev.moonaticks.betterThanBrewery.drink;

import org.bukkit.Color;

import java.util.List;
import java.util.Map;

public record DrinkDefinition(String id, String name, Color color, int food, double alcohol,
                              List<String> lore, List<DrinkEffect> effects, List<String> commands,
                              String denizenScript, Map<String, String> formulas,
                              String itemSpec) {
    public DrinkDefinition {
        id = id == null ? "unknown" : id.toLowerCase();
        name = name == null ? id : name;
        lore = lore == null ? List.of() : List.copyOf(lore);
        effects = effects == null ? List.of() : List.copyOf(effects);
        commands = commands == null ? List.of() : List.copyOf(commands);
        formulas = formulas == null ? Map.of() : Map.copyOf(formulas);
        denizenScript = denizenScript == null ? "" : denizenScript;
        itemSpec = itemSpec == null ? "" : itemSpec;
    }
}

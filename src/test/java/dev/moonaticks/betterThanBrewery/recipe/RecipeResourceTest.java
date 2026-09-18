package dev.moonaticks.betterThanBrewery.recipe;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeResourceTest {
    private static final List<String> RECIPES = List.of(
            "recipes/boiler/apple-cider.yml",
            "recipes/distiller/brandy.yml",
            "recipes/barrel/oak-cider.yml",
            "recipes/kettle/herbal-tea.yml",
            "recipes/brewery/wheat-beer.yml",
            "recipes/brewery/berry-wine.yml",
            "recipes/brewery/honey-mead.yml",
            "recipes/brewery/grain-vodka.yml",
            "recipes/brewery/berry-spirit.yml",
            "recipes/brewery/whiskey.yml",
            "recipes/brewery/rum.yml",
            "recipes/brewery/absinthe.yml",
            "recipes/brewery/oak-beer.yml",
            "recipes/brewery/oak-wine.yml",
            "recipes/brewery/black-tea.yml",
            "recipes/brewery/mint-tea.yml",
            "recipes/brewery/berry-tea.yml",
            "recipes/brewery/coffee.yml");

    @Test
    void bundledRecipesHaveAStationAndDrinkIdentity() throws IOException {
        for (String path : RECIPES) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, "Missing bundled recipe: " + path);
                YamlConfiguration recipe = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                assertTrue(recipe.getString("station", "").matches("boiler|distiller|barrel|kettle"), path);
                assertNotNull(recipe.getConfigurationSection("output"), path);
                assertTrue(!recipe.getString("output.name", "").isBlank(), path);
                assertTrue(!recipe.getString("output.color", "").isBlank(), path);
                assertEquals(recipe.getString("id", ""), recipe.getString("output.id", recipe.getString("id", "")),
                        "Output id should be explicit for " + path);
            }
        }
    }
}

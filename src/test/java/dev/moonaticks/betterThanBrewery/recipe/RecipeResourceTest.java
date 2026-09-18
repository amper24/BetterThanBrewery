package dev.moonaticks.betterThanBrewery.recipe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

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

    private static final Pattern ID = Pattern.compile("(?m)^id:\\s*[A-Za-z0-9_-]+\\s*$");
    private static final Pattern STATION = Pattern.compile("(?m)^station:\\s*(boiler|distiller|barrel|kettle)\\s*$");
    private static final Pattern OUTPUT_NAME = Pattern.compile("(?m)^  name:\\s*.+$");
    private static final Pattern OUTPUT_COLOR = Pattern.compile("(?m)^  color:\\s*.+$");

    @Test
    void bundledRecipesHaveRequiredIdentityFields() throws IOException {
        for (String path : RECIPES) {
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, "Missing bundled recipe: " + path);
                String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(STATION.matcher(yaml).find(), "Missing/invalid station: " + path);
                assertTrue(ID.matcher(yaml).find(), "Missing id: " + path);
                assertTrue(yaml.contains("output:"), "Missing output section: " + path);
                assertTrue(OUTPUT_NAME.matcher(yaml).find(), "Missing output.name: " + path);
                assertTrue(OUTPUT_COLOR.matcher(yaml).find(), "Missing output.color: " + path);
            }
        }
    }
}

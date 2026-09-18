package dev.moonaticks.betterThanBrewery.recipe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RecipeLoaderTest {
    @TempDir
    Path recipesFolder;

    @Test
    void loadsSeveralRecipesFromOneMapFile() throws IOException {
        Files.writeString(recipesFolder.resolve("drinks.yml"), """
                recipes:
                  wheat_beer:
                    station: boiler
                    time-seconds: 35
                    ingredients:
                      - item: minecraft:wheat
                        amount: 3
                    output:
                      name: "&6Пшеничное пиво"
                      color: "#D49A45"
                  berry_wine:
                    id: berry_wine
                    station: boiler
                    ingredients:
                      - item: minecraft:sweet_berries
                        amount: 6
                    output:
                      name: "&dЯгодное вино"
                      color: "#A8326B"
                """);

        RecipeRegistry registry = new RecipeLoader(Logger.getLogger("RecipeLoaderTest")).load(recipesFolder.toFile());

        assertEquals(2, registry.size());
        assertEquals("wheat_beer", registry.get("wheat_beer").drink().id());
        assertEquals(700, registry.get("wheat_beer").time());
        assertEquals("boiler", registry.get("berry_wine").station());
        assertNotNull(registry.drink("berry_wine"));
    }

    @Test
    void loadsSeveralRecipesFromOneListFile() throws IOException {
        Files.writeString(recipesFolder.resolve("tea.yml"), """
                recipes:
                  - id: mint_tea
                    station: kettle
                    ingredients:
                      - item: minecraft:short_grass
                        amount: 2
                    output:
                      name: "&aМятный чай"
                      color: "#73C56B"
                  - id: coffee
                    station: kettle
                    ingredients:
                      - item: minecraft:cocoa_beans
                        amount: 2
                    output:
                      name: "&8Кофе"
                      color: "#6B3E26"
                """);

        RecipeRegistry registry = new RecipeLoader(Logger.getLogger("RecipeLoaderTest")).load(recipesFolder.toFile());

        assertEquals(2, registry.size());
        assertEquals("kettle", registry.get("mint_tea").station());
        assertEquals(2, registry.get("coffee").ingredients().get(0).amount());
    }
}

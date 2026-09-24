package dev.moonaticks.betterThanBrewery.item;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;
import dev.moonaticks.betterThanBrewery.drink.DrinkService;
import dev.moonaticks.betterThanBrewery.recipe.Ingredient;
import dev.moonaticks.betterThanBrewery.recipe.RecipeDefinition;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContainerAndDrinkTest {
    @Test
    void vesselIdentitySurvivesFillingDrinkingAndTransferringWater() {
        BetterThanBrewery plugin = mock(BetterThanBrewery.class);
        when(plugin.namespace()).thenReturn("betterthanbrewery");
        YamlConfiguration config = new YamlConfiguration();
        config.set("containers.bottle.empty", "minecraft:glass_bottle");
        config.set("containers.bottle.filled", "minecraft:potion");
        config.set("containers.bottle.use-drink-item", true);
        config.set("containers.cup.empty", "minecraft:bowl");
        config.set("containers.cup.filled", "minecraft:potion");
        config.set("containers.cup.units", 2);
        config.set("drunkenness.water-sobering", 4.0);
        when(plugin.getConfig()).thenReturn(config);

        ItemService items = mock(ItemService.class);
        ContainerService containers = new ContainerService(items);
        containers.load(config);
        ContainerService.Container cup = containers.byId("cup");
        assertEquals(2, cup.units());
        assertFalse(cup.useDrinkItem());
        assertTrue(containers.byId("BOTTLE").useDrinkItem());

        ItemStack full = mock(ItemStack.class);
        when(full.getType()).thenReturn(Material.POTION);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Object> data = new HashMap<>();
        when(full.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        doAnswer(call -> { data.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(pdc).set(any(), any(), any());
        doAnswer(call -> data.get(call.getArgument(0))).when(pdc).get(any(), any());
        doAnswer(call -> data.getOrDefault(call.getArgument(0), call.getArgument(2)))
                .when(pdc).getOrDefault(any(), any(), any());
        when(items.create("minecraft:potion")).thenReturn(full);
        when(items.cloneWith(eq(full), anyString(), anyList(), any())).thenReturn(full);
        ItemStack bowl = mock(ItemStack.class);
        when(bowl.getType()).thenReturn(Material.BOWL);
        when(items.create("minecraft:bowl")).thenReturn(bowl);

        DrinkService drinks = new DrinkService(plugin, items, containers);
        RecipeRegistry registry = new RecipeRegistry();
        DrinkDefinition definition = new DrinkDefinition("beer", "&6Пиво", Color.ORANGE, 0, 6,
                List.of(), List.of(), "", Map.of(), "itemsadder:brewery:beer_skin");
        registry.add(new RecipeDefinition("beer", "boiler", List.of(new Ingredient("minecraft:wheat", 1, 10)),
                1, 20, 20, 40, "", "beer", 0, "", 0, definition, List.of(), Map.of()));
        drinks.setRegistry(registry);

        assertSame(full, drinks.createFilled("beer", 2, 90, cup));
        verify(items, never()).create("itemsadder:brewery:beer_skin");
        DrinkTags.Tag drink = drinks.read(full);
        assertEquals("beer", drink.id());
        assertEquals("cup", drink.containerId());
        assertEquals(2, drink.units());
        assertSame(bowl, drinks.emptyFor(full));

        // The same container can carry water and be returned after drinking it.
        drinks.createWater(cup);
        assertEquals("water", drinks.read(full).id());
        Player player = mock(Player.class);
        PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
        when(consume.getPlayer()).thenReturn(player);
        when(consume.getItem()).thenReturn(full);
        AtomicReference<Double> sobered = new AtomicReference<>(0.0);
        drinks.consume(consume, (who, amount) -> sobered.updateAndGet(before -> before + amount));
        verify(consume).setReplacement(bowl);
        assertEquals(-8.0, sobered.get());

        assertNull(drinks.createFilled("deleted_recipe", 0, 100, cup),
                "missing output must not silently create water or consume liquid");
        data.remove(new NamespacedKey(plugin, "container"));
        assertEquals("", drinks.read(full).containerId(), "legacy items without a container ID remain readable");
    }

    @Test
    void malformedZeroVolumeIsNotPourable() {
        BetterThanBrewery plugin = mock(BetterThanBrewery.class);
        when(plugin.namespace()).thenReturn("betterthanbrewery");
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        ItemMeta meta = mock(ItemMeta.class);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.POTION);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(eq(new NamespacedKey(plugin, "drink")), any())).thenReturn("beer");
        when(pdc.getOrDefault(eq(new NamespacedKey(plugin, "units")), any(), any())).thenReturn(0);
        assertNull(new DrinkTags(plugin).read(item));
    }
}

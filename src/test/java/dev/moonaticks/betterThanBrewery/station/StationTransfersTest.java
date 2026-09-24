package dev.moonaticks.betterThanBrewery.station;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import dev.moonaticks.betterThanBrewery.config.Lang;
import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;
import dev.moonaticks.betterThanBrewery.drink.DrinkService;
import dev.moonaticks.betterThanBrewery.item.ContainerService;
import dev.moonaticks.betterThanBrewery.item.DrinkTags;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StationTransfersTest {
    @Test
    void bucketIsNotSpentWhenWaterTankCannotHoldAllFourUnits() throws Exception {
        BetterThanBrewery plugin = mock(BetterThanBrewery.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("water.source-items", List.of(Map.of("item", "minecraft:water_bucket", "units", 4, "empty", "minecraft:bucket")));
        when(plugin.getConfig()).thenReturn(config);
        ItemService items = mock(ItemService.class);
        DrinkService drinks = mock(DrinkService.class);
        ContainerService containers = mock(ContainerService.class);
        Lang lang = mock(Lang.class);
        StationManager stations = new StationManager(plugin, items, containers, drinks, lang, null, null);
        StationDefinition boiler = station("boiler", 10, 10);
        Location location = mock(Location.class);
        Player player = mock(Player.class);
        ItemStack bucket = mock(ItemStack.class);
        when(bucket.getType()).thenReturn(Material.WATER_BUCKET);
        when(items.matches(bucket, "minecraft:water_bucket")).thenReturn(true);
        GuiSlotClickEvent click = mock(GuiSlotClickEvent.class);
        when(click.getCursor()).thenReturn(bucket);
        FunctionalBlockData data = mock(FunctionalBlockData.class);
        when(data.getInt("water", 0)).thenReturn(9);

        try (MockedStatic<CustomGuiAPI> api = mockStatic(CustomGuiAPI.class)) {
            api.when(() -> CustomGuiAPI.blockData("block", location)).thenReturn(data);
            Method method = StationManager.class.getDeclaredMethod("handleWater", StationDefinition.class,
                    String.class, Location.class, Player.class, GuiSlotClickEvent.class);
            method.setAccessible(true);
            method.invoke(stations, boiler, "block", location, player, click);
        }
        verify(click).setInteractionCancelled(true);
        verify(lang).send(player, "not-enough-space");
        verify(data, never()).setInt(eq("water"), anyInt());
        verify(player, never()).setItemOnCursor(any());
    }

    @Test
    void oversizedFilledMugCannotBePouredIntoASmallStation() throws Exception {
        BetterThanBrewery plugin = mock(BetterThanBrewery.class);
        ItemService items = mock(ItemService.class);
        DrinkService drinks = mock(DrinkService.class);
        Lang lang = mock(Lang.class);
        StationManager stations = new StationManager(plugin, items, mock(ContainerService.class), drinks, lang, null, null);
        Location location = mock(Location.class);
        Player player = mock(Player.class);
        ItemStack mug = mock(ItemStack.class);
        when(mug.getType()).thenReturn(Material.POTION);
        when(drinks.read(mug)).thenReturn(new DrinkTags.Tag("beer", 0, 6, 100, 3, "mug"));
        when(drinks.definition("beer")).thenReturn(mock(DrinkDefinition.class));
        GuiSlotClickEvent click = mock(GuiSlotClickEvent.class);
        when(click.getCursor()).thenReturn(mug);
        FunctionalBlockData data = mock(FunctionalBlockData.class);
        when(data.getString("fluid", "")).thenReturn("");
        try (MockedStatic<CustomGuiAPI> api = mockStatic(CustomGuiAPI.class)) {
            api.when(() -> CustomGuiAPI.blockData("block", location)).thenReturn(data);
            Method method = StationManager.class.getDeclaredMethod("acceptFluid", StationDefinition.class,
                    String.class, Location.class, Player.class, GuiSlotClickEvent.class);
            method.setAccessible(true);
            assertEquals(true, method.invoke(stations, station("barrel", 2, 0), "block", location, player, click));
        }
        verify(click).setInteractionCancelled(true);
        verify(lang).send(player, "not-enough-space");
        verify(data, never()).setInt(eq("fluid-level"), anyInt());
        verify(player, never()).setItemOnCursor(any());
    }

    private static StationDefinition station(String id, int capacity, int waterCapacity) {
        return new StationDefinition(id, id, capacity, waterCapacity, 40, 40, 42,
                List.of(7), List.of(), List.of("block"));
    }
}

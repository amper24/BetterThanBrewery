package dev.moonaticks.betterThanBrewery.station;

import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.GuiBuilder;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BreweryGuiTypesTest {
    @AfterEach
    void unregister() {
        SlotType.unregister(BreweryGuiTypes.FLUID_ID);
    }

    @Test
    void fluidIsAnActualRegisteredReadOnlyTypeWithLocalVisuals() {
        SlotType fluid = SlotType.register(BreweryGuiTypes.fluidSlot());
        assertEquals("betterthanbrewery:fluid", fluid.id());
        assertEquals(Material.POTION, fluid.icon());
        assertTrue(fluid.isRegistered());
        assertTrue(fluid.isDecorative());
        assertTrue(fluid.allowsLocalDesign());
        assertFalse(fluid.allowsInsert());
        assertFalse(fluid.allowsTake());
        assertFalse(fluid.isPersistable());
        assertFalse(fluid.isTracked());

        Gui station = GuiBuilder.named("boiler").size(54).storage(StorageType.BLOCK)
                .category(BreweryGuiTypes.stationCategory()).slot(43, fluid).build();
        assertEquals(BreweryGuiTypes.CATEGORY_ID, station.category());
        assertSame(fluid, station.slotType(43));
        assertSame(SlotType.DESIGN, station.slotType(42));

        GuiSlotClickEvent click = new GuiSlotClickEvent(null, station, null, 43, fluid,
                true, ClickType.LEFT, null);
        fluid.handleClick(click);
        assertTrue(click.isInteractionCancelled());
    }
}

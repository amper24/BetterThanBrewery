package dev.moonaticks.betterThanBrewery.station;

import dev.moonaticks.customGuiReworked.api.GuiCategory;
import dev.moonaticks.customGuiReworked.api.SlotType;
import org.bukkit.Material;

/** Descriptions registered with CustomGuiReworked before any station GUI is built. */
public final class BreweryGuiTypes {
    public static final String CATEGORY_ID = "betterthanbrewery:stations";
    public static final String FLUID_ID = "betterthanbrewery:fluid";

    private BreweryGuiTypes() { }

    public static GuiCategory stationCategory() {
        return new GuiCategory(CATEGORY_ID, "§6BetterThanBrewery", Material.BREWING_STAND,
                "§7Бойлеры, дистилляторы, бочонки и чайники");
    }

    public static SlotType fluidSlot() {
        // The liquid is stored in FunctionalBlockData, not as an ItemStack.
        // A decorative slot supports per-viewer design, blocks all vanilla
        // transfers (including drag, shift, hotbar and creative clicks), and
        // cannot drop a fake potion when the block is broken.
        return SlotType.builder(FLUID_ID)
                .displayName("§bРезервуар жидкости")
                .description("§7Показывает уровень. Наполнить тару можно кликом по слоту.")
                .icon(Material.POTION)
                .decorative(true)
                .onClick(event -> event.setInteractionCancelled(true))
                .build();
    }
}

package dev.moonaticks.betterThanBrewery.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemReferenceTest {
    @Test
    void separatesProviderNamespaceAndItemId() {
        ItemService.CustomItemReference itemsAdder = ItemService.parseCustomItem("itemsadder:brewery:recipe_book");
        ItemService.CustomItemReference craftEngine = ItemService.parseCustomItem("ce:brewery:recipe_book");

        assertEquals("itemsadder", itemsAdder.provider());
        assertEquals("brewery", itemsAdder.namespace());
        assertEquals("recipe_book", itemsAdder.id());
        assertEquals("brewery:recipe_book", itemsAdder.lookupId());
        assertEquals("craftengine", craftEngine.provider());
        assertEquals("brewery:recipe_book", craftEngine.lookupId());
    }

    @Test
    void triesBothProvidersForPlainNamespacedCustomIds() {
        ItemService.CustomItemReference reference = ItemService.parseCustomItem("brewery:recipe_book");

        assertEquals("auto", reference.provider());
        assertEquals("brewery", reference.namespace());
        assertEquals("recipe_book", reference.id());
        assertNull(ItemService.parseCustomItem("minecraft:book"));
    }
}

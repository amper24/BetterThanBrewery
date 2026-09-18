package dev.moonaticks.betterThanBrewery.util;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorUtilTest {
    @Test
    void parsesHexAndShortHexColors() {
        assertEquals(Color.fromRGB(0xD88932), ColorUtil.parse("#D88932", Color.WHITE));
        assertEquals(Color.fromRGB(0xFF0000), ColorUtil.parse("#F00", Color.WHITE));
    }

    @Test
    void parsesNamedColorsAndUsesFallback() {
        assertEquals(Color.fromRGB(139, 69, 19), ColorUtil.parse("brown", Color.WHITE));
        assertEquals(Color.fromRGB(1, 2, 3), ColorUtil.parse("not-a-color", Color.fromRGB(1, 2, 3)));
    }
}

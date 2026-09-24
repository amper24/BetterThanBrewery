package dev.moonaticks.betterThanBrewery.drunkenness;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackTest {
    private static final Path ROOT = Path.of("resource-pack");

    @Test
    void packContainsAllConfiguredGlyphsAndTrulyTransparentHudArt() throws Exception {
        String meta = Files.readString(ROOT.resolve("pack.mcmeta"));
        assertTrue(meta.contains("\"min_format\": [88, 0]"));
        assertTrue(meta.contains("\"max_format\": [88, 0]"));
        String font = Files.readString(ROOT.resolve("assets/betterthanbrewery/font/intoxication.json"));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        List<Map<?, ?>> stages = config.getMapList("drunkenness.stages");
        assertEquals(4, stages.size());
        for (int i = 1; i <= 4; i++) {
            String glyph = String.valueOf(stages.get(i - 1).get("overlay-glyph"));
            assertEquals(String.valueOf((char) (0xE100 + i)), glyph);
            assertTrue(font.contains(glyph));
            assertTrue(font.contains("betterthanbrewery:font/haze_" + i + ".png"));
            BufferedImage png = ImageIO.read(ROOT.resolve("assets/betterthanbrewery/textures/font/haze_" + i + ".png").toFile());
            assertEquals(256, png.getWidth()); // vanilla bitmap glyphs must not exceed 256x256
            assertEquals(144, png.getHeight());
            assertEquals(0, png.getRGB(128, 72) >>> 24, "the center must not obstruct gameplay");
            assertTrue((png.getRGB(0, 0) >>> 24) > 0, "there must be visible haze at the edges");
        }
        assertNotNull(ImageIO.read(ROOT.resolve("pack.png").toFile()));
    }

    @Test
    void onlyValidZipSha1EnablesTheClientRequest() {
        assertEquals(20, IntoxicationOverlay.parseHash("ab".repeat(20)).length);
        assertNull(IntoxicationOverlay.parseHash(""));
        assertNull(IntoxicationOverlay.parseHash("ab".repeat(19)));
        assertNull(IntoxicationOverlay.parseHash("Z".repeat(40)));
    }
}

package dev.moonaticks.betterThanBrewery.drunkenness;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class DrunkennessManagerTest {
    @Test
    void nauseaIsTheOnlyConfigurableStatusEffectAndRespectsTheThresholds() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        DrunkennessManager.NauseaSettings nausea = DrunkennessManager.NauseaSettings.from(config);

        assertFalse(nausea.active(0));
        assertFalse(nausea.active(19.99));
        assertTrue(nausea.active(20));
        assertEquals(0, nausea.strength(20));
        assertEquals(0, nausea.strength(79));
        assertEquals(1, nausea.strength(80));
        assertEquals(80, nausea.durationTicks());
        assertFalse(config.contains("drunkenness.stages"));
        assertFalse(config.contains("drunkenness.visual"));
        assertFalse(config.contains("drunkenness.voice"));
    }

    @Test
    void customThresholdsWorkAndNegativeSettingsAreClamped() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("drunkenness.nausea.min-level", 35);
        config.set("drunkenness.nausea.strong-level", 60);
        config.set("drunkenness.nausea.amplifier", 2);
        config.set("drunkenness.nausea.strong-amplifier", 3);
        config.set("drunkenness.nausea.duration-ticks", 2);
        DrunkennessManager.NauseaSettings nausea = DrunkennessManager.NauseaSettings.from(config);
        assertFalse(nausea.active(34));
        assertEquals(2, nausea.strength(35));
        assertEquals(3, nausea.strength(60));
        assertEquals(40, nausea.durationTicks());

        config.set("drunkenness.nausea.min-level", -2);
        config.set("drunkenness.nausea.strong-level", -8);
        assertEquals(1, DrunkennessManager.NauseaSettings.from(config).strongLevel());
    }
}

package dev.moonaticks.betterThanBrewery.drunkenness;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntoxicationOverlayTest {
    @Test
    void packIsRequestedOnceAndGlyphAppearsOnlyAfterThisPackHasLoaded() {
        BetterThanBrewery plugin = mock(BetterThanBrewery.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        YamlConfiguration config = new YamlConfiguration();
        String sha = "ab".repeat(20);
        config.set("settings.use-resource-pack-overlays", true);
        config.set("drunkenness.visual.url", "https://example.org/overlay.zip");
        config.set("drunkenness.visual.sha1", sha);
        when(plugin.getConfig()).thenReturn(config);
        UUID pack = UUID.nameUUIDFromBytes(("betterthanbrewery:overlay:" + sha).getBytes(StandardCharsets.UTF_8));
        IntoxicationOverlay overlay = new IntoxicationOverlay(plugin);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            overlay.load(config);
            overlay.start();
            overlay.requestForOnlinePlayers();
            verify(player, times(1)).addResourcePack(eq(pack), eq("https://example.org/overlay.zip"), any(byte[].class), anyString(), eq(false));

            overlay.setVisual(player, "\uE102");
            verify(player, never()).showTitle(any(Title.class));
            status(overlay, player, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
            status(overlay, player, pack, PlayerResourcePackStatusEvent.Status.ACCEPTED);
            verify(player, never()).showTitle(any(Title.class));

            status(overlay, player, pack, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
            overlay.setVisual(player, "\uE102");
            verify(player, times(1)).showTitle(any(Title.class));
            overlay.advance(40);
            overlay.setVisual(player, "\uE102");
            verify(player, times(2)).showTitle(any(Title.class));
            overlay.setVisual(player, "");
            verify(player, times(1)).clearTitle();
            status(overlay, player, pack, PlayerResourcePackStatusEvent.Status.DISCARDED);
            verify(player, times(1)).clearTitle(); // no foreign title cleared when we have no overlay
            overlay.shutdown();
            verify(player).removeResourcePack(pack);
        }
    }

    private static void status(IntoxicationOverlay overlay, Player player, UUID id, PlayerResourcePackStatusEvent.Status value) {
        PlayerResourcePackStatusEvent event = mock(PlayerResourcePackStatusEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getID()).thenReturn(id);
        when(event.getStatus()).thenReturn(value);
        overlay.onStatus(event);
    }
}

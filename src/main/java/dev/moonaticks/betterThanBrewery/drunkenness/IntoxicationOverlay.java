package dev.moonaticks.betterThanBrewery.drunkenness;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** An optional, client-approved translucent HUD layer; never changes the player's actual helmet. */
public final class IntoxicationOverlay implements Listener {
    private static final Key FONT = Key.key("betterthanbrewery:intoxication");
    private record Shown(String glyph, long atTick) { }

    private final BetterThanBrewery plugin;
    private final Set<UUID> requested = new HashSet<>(), ready = new HashSet<>();
    private final Map<UUID, String> desired = new HashMap<>();
    private final Map<UUID, Shown> shown = new HashMap<>();
    private String url = "", prompt = "";
    private byte[] hash;
    private UUID packId;
    private int refreshTicks = 40;
    private long tickCount;
    private boolean started;

    public IntoxicationOverlay(BetterThanBrewery plugin) { this.plugin = plugin; }

    public void load(FileConfiguration config) {
        String newUrl = config.getString("drunkenness.visual.url", "").trim();
        String newHash = config.getString("drunkenness.visual.sha1", "").trim();
        boolean enabled = config.getBoolean("settings.use-resource-pack-overlays", false);
        byte[] bytes = enabled ? parseHash(newHash) : null;
        if (enabled && !newUrl.isBlank() && (bytes == null || !validUrl(newUrl))) {
            plugin.getLogger().warning("Intoxication overlay disabled: set a valid HTTP(S) ZIP URL and its 40-character SHA-1 in drunkenness.visual.");
        }
        UUID newId = enabled && bytes != null && validUrl(newUrl)
                ? UUID.nameUUIDFromBytes(("betterthanbrewery:overlay:" + newHash.toLowerCase()).getBytes(StandardCharsets.UTF_8)) : null;
        int newRefresh = Math.max(20, config.getInt("drunkenness.visual.refresh-ticks", 40));
        String newPrompt = config.getString("drunkenness.visual.prompt", "BetterThanBrewery: эффект опьянения");
        boolean changed = !java.util.Objects.equals(packId, newId) || !url.equals(newUrl);
        if (changed) {
            removeFromOnlinePlayers();
            packId = newId; hash = bytes; url = newUrl;
        }
        refreshTicks = newRefresh; prompt = newPrompt;
        if (changed && started && packId != null) requestForOnlinePlayers();
    }

    public void start() { started = true; requestForOnlinePlayers(); }

    public void requestForOnlinePlayers() {
        if (packId != null) for (Player player : Bukkit.getOnlinePlayers()) request(player);
    }

    private void request(Player player) {
        if (packId == null || !requested.add(player.getUniqueId())) return;
        try {
            // Additive: preserves server.properties and other plugins' resource packs.
            player.addResourcePack(packId, url, hash, prompt, false);
        } catch (RuntimeException ex) {
            requested.remove(player.getUniqueId());
            plugin.getLogger().warning("Cannot send intoxication resource pack: " + ex.getMessage());
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Send after the server's own resource pack, not on every stage change.
        if (packId != null) Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) request(player);
        });
    }

    @EventHandler public void onStatus(PlayerResourcePackStatusEvent event) {
        if (event.isAsynchronous()) {
            UUID id = event.getID(), playerId = event.getPlayer().getUniqueId();
            PlayerResourcePackStatusEvent.Status status = event.getStatus();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) applyStatus(player, id, status);
            });
        } else applyStatus(event.getPlayer(), event.getID(), event.getStatus());
    }

    private void applyStatus(Player player, UUID id, PlayerResourcePackStatusEvent.Status status) {
        UUID playerId = player.getUniqueId();
        if (packId == null || !packId.equals(id) || !requested.contains(playerId)) return;
        if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            ready.add(playerId);
            show(player);
        } else if (status != PlayerResourcePackStatusEvent.Status.ACCEPTED && status != PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
            ready.remove(playerId);
            clearShown(player);
        }
    }

    public void advance(int ticks) { tickCount += ticks; }

    /** Called for every player each update and immediately on a sobriety/stage change. */
    public void setVisual(Player player, String glyph) {
        UUID id = player.getUniqueId();
        if (glyph == null || glyph.isBlank() || packId == null) {
            desired.remove(id);
            clearShown(player);
            return;
        }
        desired.put(id, glyph);
        show(player);
    }

    private void show(Player player) {
        UUID id = player.getUniqueId();
        String glyph = desired.get(id);
        if (glyph == null || !ready.contains(id)) return;
        Shown old = shown.get(id);
        if (old != null && old.glyph().equals(glyph) && tickCount - old.atTick() < refreshTicks) return;
        long stayMillis = (long) (refreshTicks + Math.max(20, refreshTicks / 2)) * 50;
        player.showTitle(Title.title(Component.text(glyph).font(FONT), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayMillis), Duration.ZERO)));
        shown.put(id, new Shown(glyph, tickCount));
    }

    private void clearShown(Player player) {
        // Do not clear anyone else's title if we never displayed our own.
        if (shown.remove(player.getUniqueId()) != null) player.clearTitle();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requested.remove(id); ready.remove(id); desired.remove(id); shown.remove(id);
    }

    private void removeFromOnlinePlayers() {
        UUID oldId = packId;
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearShown(player);
            if (oldId != null && requested.contains(player.getUniqueId())) player.removeResourcePack(oldId);
        }
        requested.clear(); ready.clear(); desired.clear(); shown.clear();
    }

    public void shutdown() {
        started = false;
        removeFromOnlinePlayers();
        packId = null; hash = null;
    }

    static byte[] parseHash(String sha1) {
        if (sha1 == null || !sha1.matches("(?i)[0-9a-f]{40}")) return null;
        return HexFormat.of().parseHex(sha1);
    }
    private static boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException | NullPointerException ex) { return false; }
    }
}

package dev.moonaticks.betterThanBrewery.drunkenness;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent intoxication; the only status effect applied by this plugin is vanilla nausea. */
public final class DrunkennessManager implements Listener {
    private record AppliedNausea(int duration, int amplifier) { }

    /** A single configurable vanilla effect, not an extensible effect/overlay system. */
    record NauseaSettings(int minLevel, int strongLevel, int amplifier, int strongAmplifier, int durationTicks) {
        static NauseaSettings from(FileConfiguration config) {
            int min = Math.max(1, config.getInt("drunkenness.nausea.min-level", 20));
            int strong = Math.max(min, config.getInt("drunkenness.nausea.strong-level", 80));
            return new NauseaSettings(min, strong,
                    Math.max(0, config.getInt("drunkenness.nausea.amplifier", 0)),
                    Math.max(0, config.getInt("drunkenness.nausea.strong-amplifier", 1)),
                    Math.max(40, config.getInt("drunkenness.nausea.duration-ticks", 80)));
        }
        boolean active(double level) { return Double.isFinite(level) && level >= minLevel; }
        int strength(double level) { return level >= strongLevel ? strongAmplifier : amplifier; }
    }

    private final BetterThanBrewery plugin;
    private final NamespacedKey key;
    private final Map<UUID, Double> levels = new ConcurrentHashMap<>();
    private final Map<UUID, AppliedNausea> applied = new HashMap<>();
    private int max = 100;
    private double decayPerMinute;
    private int interval;
    private int scheduledInterval;
    private NauseaSettings nausea;
    private BukkitTask tickTask;

    public DrunkennessManager(BetterThanBrewery plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "drunkenness");
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();
        max = Math.max(1, config.getInt("drunkenness.max", 100));
        decayPerMinute = Math.max(0, config.getDouble("drunkenness.decay-per-minute", 2));
        interval = Math.max(1, config.getInt("drunkenness.update-ticks", 20));
        nausea = NauseaSettings.from(config);
        // A config reload can raise the threshold or disable drunkenness altogether.
        for (Player player : Bukkit.getOnlinePlayers()) updateNausea(player, value(player));
        if (tickTask != null && scheduledInterval != interval) start();
    }

    public void start() {
        if (tickTask != null) tickTask.cancel();
        int period = interval;
        scheduledInterval = period;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) tick(player, period);
        }, period, period);
    }

    public double value(Player player) {
        return levels.computeIfAbsent(player.getUniqueId(), ignored ->
                player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.DOUBLE, 0.0));
    }

    public void add(Player player, double amount) { set(player, value(player) + amount); }

    public void set(Player player, double amount) {
        double clamped = Double.isFinite(amount) ? Math.max(0, Math.min(max, amount)) : 0;
        levels.put(player.getUniqueId(), clamped);
        player.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, clamped);
        if (player.isOnline()) updateNausea(player, clamped);
    }

    private void tick(Player player, int period) {
        if (!plugin.getConfig().getBoolean("drunkenness.enabled", true)) {
            clearNausea(player);
            return;
        }
        double current = value(player);
        if (decayPerMinute > 0 && current > 0) {
            current = Math.max(0, current - decayPerMinute * period / 1200.0);
            levels.put(player.getUniqueId(), current);
            player.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, current);
        }
        updateNausea(player, current);
    }

    private void updateNausea(Player player, double level) {
        if (!plugin.getConfig().getBoolean("drunkenness.enabled", true) || !nausea.active(level)) {
            clearNausea(player);
            return;
        }
        PotionEffectType type = PotionEffectType.NAUSEA;
        UUID id = player.getUniqueId();
        int strength = nausea.strength(level);
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max((long) nausea.durationTicks(), 3L * interval));
        PotionEffect existing = player.getPotionEffect(type);
        AppliedNausea ours = applied.get(id);
        // If another source replaced/extended our effect, leave it alone (including on sobriety).
        if (ours != null && existing != null && (existing.getAmplifier() != ours.amplifier()
                || existing.getDuration() > ours.duration())) {
            applied.remove(id);
            return;
        }
        if (ours == null && existing != null) return; // Never override somebody else's nausea.
        if (ours != null && existing != null && existing.getAmplifier() != strength) {
            // Downgrade our own strong nausea promptly as the player sobers up.
            player.removePotionEffect(type);
            applied.remove(id);
        }
        if (player.addPotionEffect(new PotionEffect(type, duration, strength, false, false, false)))
            applied.put(id, new AppliedNausea(duration, strength));
    }

    private void clearNausea(Player player) {
        AppliedNausea ours = applied.remove(player.getUniqueId());
        if (ours == null) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.NAUSEA);
        // Best effort: avoid removing a stronger or longer effect from another source.
        if (current != null && current.getAmplifier() == ours.amplifier()
                && current.getDuration() <= ours.duration()) player.removePotionEffect(PotionEffectType.NAUSEA);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        clearNausea(event.getPlayer());
        levels.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        for (Player player : Bukkit.getOnlinePlayers()) clearNausea(player);
        applied.clear();
    }
}

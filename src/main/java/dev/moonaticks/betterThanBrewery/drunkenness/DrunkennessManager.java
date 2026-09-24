package dev.moonaticks.betterThanBrewery.drunkenness;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import dev.moonaticks.betterThanBrewery.drink.DrinkEffect;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Persistent 0..100 intoxication, staged feedback and a separately managed client overlay. */
public final class DrunkennessManager implements Listener {
    private final BetterThanBrewery plugin;
    private final NamespacedKey key;
    private final IntoxicationOverlay overlay;
    private volatile List<Stage> stages = List.of();
    private int max = 100;
    private double decayPerMinute;
    private int interval;
    private final Map<UUID, Integer> lastStage = new HashMap<>();
    private final Set<UUID> actionbars = new HashSet<>();
    private final Map<UUID, Double> levels = new ConcurrentHashMap<>();
    private String pitchChannel, registeredChannel;

    public DrunkennessManager(BetterThanBrewery plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "drunkenness");
        overlay = new IntoxicationOverlay(plugin);
    }
    public IntoxicationOverlay overlay() { return overlay; }

    public void load() {
        var config = plugin.getConfig();
        max = Math.max(1, config.getInt("drunkenness.max", 100));
        decayPerMinute = Math.max(0, config.getDouble("drunkenness.decay-per-minute", 2));
        interval = Math.max(1, config.getInt("drunkenness.update-ticks", 20));
        pitchChannel = config.getString("drunkenness.voice.plugin-message-channel", "betterthanbrewery:voice_pitch");
        List<Stage> loaded = new ArrayList<>();
        for (Map<?, ?> map : config.getMapList("drunkenness.stages")) {
            int min = number(map.get("min"), 0), maxStage = number(map.get("max"), 100);
            List<DrinkEffect> effects = new ArrayList<>();
            Object configuredEffects = map.get("effects");
            if (configuredEffects instanceof List<?> list) for (Object object : list) if (object instanceof Map<?, ?> effect) {
                String type = string(effect.get("type"));
                if (!type.isBlank()) effects.add(new DrinkEffect(type, number(effect.get("duration"), 60),
                        number(effect.get("amplifier"), 0), decimal(effect.get("chance"), 1)));
            }
            Map<String, String> replacements = new HashMap<>();
            if (map.get("chat-replacements") instanceof Map<?, ?> replacementMap)
                for (Map.Entry<?, ?> e : replacementMap.entrySet()) replacements.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            String glyph = map.containsKey("overlay-glyph") ? string(map.get("overlay-glyph")) : defaultGlyph(min);
            loaded.add(new Stage(min, maxStage, string(map.get("actionbar")), decimal(map.get("sway-chance"), 0),
                    decimal(map.get("sway-degrees"), 0), effects, replacements, string(map.get("overlay-command")), glyph));
        }
        stages = List.copyOf(loaded);
        if (registeredChannel != null && !registeredChannel.equals(pitchChannel))
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, registeredChannel);
        if (!pitchChannel.equals(registeredChannel)) {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, pitchChannel);
            registeredChannel = pitchChannel;
        }
        overlay.load(config);
        // Re-evaluate existing players after a reload, even if their stage index did not change.
        lastStage.clear();
        for (Player player : Bukkit.getOnlinePlayers()) display(player, value(player));
    }

    public void start() {
        overlay.start();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            overlay.advance(interval);
            for (Player player : Bukkit.getOnlinePlayers()) tick(player);
        }, interval, interval);
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
        if (player.isOnline()) display(player, clamped);
    }

    private void tick(Player player) {
        if (!plugin.getConfig().getBoolean("drunkenness.enabled", true)) {
            display(player, 0);
            return;
        }
        double current = value(player);
        if (decayPerMinute > 0 && current > 0) {
            current = Math.max(0, current - decayPerMinute * interval / 1200.0);
            // Avoid running display twice (and re-applying stage entry commands).
            levels.put(player.getUniqueId(), current);
            player.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, current);
        }
        display(player, current);
        Stage stage = stage(current);
        if (stage == null) return;
        for (DrinkEffect effect : stage.effects()) {
            if (ThreadLocalRandom.current().nextDouble() > effect.chance()) continue;
            PotionEffectType type = PotionEffectType.getByName(effect.type().toUpperCase());
            if (type != null) player.addPotionEffect(new PotionEffect(type, effect.duration(), effect.amplifier()));
        }
        if (stage.swayDegrees() > 0 && ThreadLocalRandom.current().nextDouble() < stage.swayChance()) {
            double power = stage.swayDegrees() * 0.0025;
            player.setVelocity(player.getVelocity().add(new Vector(ThreadLocalRandom.current().nextDouble(-power, power), 0,
                    ThreadLocalRandom.current().nextDouble(-power, power))));
        }
    }

    private void display(Player player, double current) {
        UUID id = player.getUniqueId();
        Stage stage = plugin.getConfig().getBoolean("drunkenness.enabled", true) ? stage(current) : null;
        if (stage == null) {
            lastStage.remove(id);
            if (actionbars.remove(id)) player.sendActionBar(Component.empty());
            overlay.setVisual(player, "");
            return;
        }
        if (!stage.actionbar().isBlank()) {
            Map<String, String> vars = Map.of("value", Integer.toString((int) Math.round(current)), "player", player.getName());
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.replace(ColorUtil.color(stage.actionbar()), vars)));
            actionbars.add(id);
        } else if (actionbars.remove(id)) player.sendActionBar(Component.empty());
        overlay.setVisual(player, stage.glyph());
        int index = stages.indexOf(stage);
        if (lastStage.getOrDefault(id, -1) != index) {
            lastStage.put(id, index);
            onStageEnter(player, stage);
        }
    }

    private void onStageEnter(Player player, Stage stage) {
        if (!stage.overlayCommand().isBlank())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stage.overlayCommand().replace("{player}", player.getName()));
        if (plugin.getConfig().getBoolean("drunkenness.voice.enabled", false)) sendVoicePitch(player);
    }

    private void sendVoicePitch(Player player) {
        double min = plugin.getConfig().getDouble("drunkenness.voice.min-pitch", .82);
        double maxPitch = plugin.getConfig().getDouble("drunkenness.voice.max-pitch", 1.18);
        float pitch = (float) (maxPitch > min ? ThreadLocalRandom.current().nextDouble(min, maxPitch) : 1.0);
        int duration = plugin.getConfig().getInt("drunkenness.voice.duration-ticks", 100);
        byte[] name = player.getName().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(12 + name.length).putFloat(pitch).putInt(duration).putInt(name.length).put(name);
        try { player.sendPluginMessage(plugin, pitchChannel, buffer.array()); } catch (RuntimeException ignored) { }
    }

    private Stage stage(double value) {
        for (Stage stage : stages) if (value >= stage.min() && value < stage.max() + 1.0) return stage;
        return null;
    }
    private static String defaultGlyph(int min) {
        return min >= 80 ? "\uE104" : min >= 50 ? "\uE103" : min >= 20 ? "\uE102" : "\uE101";
    }
    public String replaceChat(Player player, String message) {
        Stage stage = stage(value(player)); if (stage == null || stage.replacements().isEmpty()) return message;
        String result = message;
        for (Map.Entry<String, String> entry : stage.replacements().entrySet()) result = result.replace(entry.getKey(), entry.getValue());
        return result;
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) { event.setMessage(replaceChat(event.getPlayer(), event.getMessage())); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastStage.remove(id); actionbars.remove(id); levels.remove(id);
    }
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) if (actionbars.remove(player.getUniqueId())) player.sendActionBar(Component.empty());
        overlay.shutdown();
        if (registeredChannel != null) {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, registeredChannel);
            registeredChannel = null;
        }
    }

    private static int number(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return fallback; } }
    private static double decimal(Object value, double fallback) { try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException e) { return fallback; } }
    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private record Stage(int min, int max, String actionbar, double swayChance, double swayDegrees,
                         List<DrinkEffect> effects, Map<String, String> replacements, String overlayCommand, String glyph) { }
}

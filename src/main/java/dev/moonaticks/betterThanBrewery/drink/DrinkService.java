package dev.moonaticks.betterThanBrewery.drink;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import dev.moonaticks.betterThanBrewery.item.ContainerService;
import dev.moonaticks.betterThanBrewery.item.DrinkTags;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import dev.moonaticks.betterThanBrewery.util.Formula;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DrinkService {
    private final BetterThanBrewery plugin;
    private final ItemService items;
    private final ContainerService containers;
    private final DrinkTags tags;
    private RecipeRegistry registry = new RecipeRegistry();
    private final NamespacedKey waterKey;

    public DrinkService(BetterThanBrewery plugin, ItemService items, ContainerService containers) {
        this.plugin = plugin; this.items = items; this.containers = containers;
        this.tags = new DrinkTags(plugin); this.waterKey = new NamespacedKey(plugin, "water");
    }
    public void setRegistry(RecipeRegistry registry) { this.registry = registry; }
    public DrinkTags tags() { return tags; }
    public ItemStack createFilled(String fluidId, int ageWeeks, double quality) {
        return createFilled(fluidId, ageWeeks, quality, containers.all().isEmpty() ? null : containers.all().get(0));
    }
    public ItemStack createFilled(String fluidId, int ageWeeks, double quality, ContainerService.Container container) {
        // A missing recipe must never silently turn a full tank into water.
        DrinkDefinition drink = registry.drink(fluidId);
        if (drink == null) return new ItemStack(Material.AIR);
        Prepared prepared = prepare(drink, ageWeeks, quality);
        String spec = container == null ? "minecraft:potion" : container.filled();
        if ((container == null || container.useDrinkItem()) && !prepared.itemSpec().isBlank()) spec = prepared.itemSpec();
        ItemStack item = items.create(spec);
        if (item == null || item.getType().isAir()) return new ItemStack(Material.AIR);
        item = items.cloneWith(item, prepared.name(), prepared.lore(), drink.color());
        tags.write(item, drink.id(), ageWeeks, prepared.alcohol(), quality,
                container == null ? 1 : container.units(), container == null ? "" : container.id());
        return item;
    }
    public ItemStack createWater() { return createWater(containers.all().isEmpty() ? null : containers.all().get(0)); }
    public ItemStack createWater(ContainerService.Container container) {
        String spec = container == null ? "minecraft:potion" : container.filled();
        ItemStack item = items.create(spec);
        if (item == null || item.getType().isAir()) return new ItemStack(Material.AIR);
        item = items.cloneWith(item, plugin.getConfig().getString("water.bottle-name", "&bВода"), List.of(), org.bukkit.Color.AQUA);
        tags.write(item, "water", 0, 0, 100, container == null ? 1 : container.units(),
                container == null ? "" : container.id());
        // Keep the old marker too, so items and older installations coexist.
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(waterKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }
    public DrinkTags.Tag read(ItemStack item) {
        DrinkTags.Tag tag = tags.read(item);
        if (tag != null) return tag;
        // Water produced by older versions did not have the drink/container tags.
        if (item == null || item.getItemMeta() == null || !item.getItemMeta().getPersistentDataContainer().has(waterKey, PersistentDataType.BYTE)) return null;
        return new DrinkTags.Tag("water", 0, 0, 100, 1, "");
    }
    public DrinkDefinition definition(String id) { return registry.drink(id); }

    /** Paper replaces the consumed potion in the correct hand (including the offhand). */
    public void consume(PlayerItemConsumeEvent event, DrunkennessSink drunkenness) {
        if (read(event.getItem()) == null) return;
        event.setReplacement(emptyFor(event.getItem()));
        consumeItem(event.getPlayer(), event.getItem(), drunkenness);
    }
    public ItemStack emptyFor(ItemStack filled) {
        DrinkTags.Tag tag = read(filled);
        if (tag != null) {
            ContainerService.Container container = containers.byId(tag.containerId());
            if (container != null) {
                ItemStack empty = containers.createEmpty(container);
                if (empty != null && !empty.getType().isAir()) return empty;
            }
        }
        // Best-effort compatibility for drinks made before the container ID existed.
        if (filled != null && (filled.getType() == Material.POTION || filled.getType() == Material.HONEY_BOTTLE)) return items.create("minecraft:glass_bottle");
        if (filled != null && filled.getType() == Material.MILK_BUCKET) return items.create("minecraft:bucket");
        return new ItemStack(Material.AIR);
    }
    public void consumeItem(Player player, ItemStack item, DrunkennessSink drunkenness) {
        DrinkTags.Tag tag = read(item);
        if (tag == null) return;
        if (tag.id().equalsIgnoreCase("water")) {
            double sobering = Math.max(0, plugin.getConfig().getDouble("drunkenness.water-sobering", 4));
            if (drunkenness != null && sobering > 0) drunkenness.add(player, -sobering * tag.units());
            return;
        }
        DrinkDefinition drink = registry.drink(tag.id());
        if (drink == null) return;
        Prepared prepared = prepare(drink, tag.ageWeeks(), tag.quality());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (prepared.food() > 0) player.setFoodLevel(Math.min(20, player.getFoodLevel() + prepared.food()));
            for (DrinkEffect effect : drink.effects()) {
                if (ThreadLocalRandom.current().nextDouble() > effect.chance()) continue;
                Map<String, Double> vars = prepared.variables();
                int duration = Math.max(1, (int) Math.round(Formula.evaluate(effect.durationFormula(), vars, effect.duration())));
                int amplifier = Math.max(0, (int) Math.round(Formula.evaluate(effect.amplifierFormula(), vars, effect.amplifier())));
                PotionEffectType type = PotionEffectType.getByName(effect.type().toUpperCase());
                if (type != null) player.addPotionEffect(new PotionEffect(type, duration, amplifier));
            }
            for (String command : drink.commands()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(fill(command, player, prepared, tag)));
            if (!drink.denizenScript().isBlank() && Bukkit.getPluginManager().isPluginEnabled("Denizen")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash("ex run " + fill(drink.denizenScript(), player, prepared, tag)));
            }
            if (drunkenness != null) drunkenness.add(player, prepared.alcohol());
        });
    }

    private Prepared prepare(DrinkDefinition drink, int ageWeeks, double quality) {
        Map<String, Double> vars = new HashMap<>();
        vars.put("age", (double) Math.max(0, ageWeeks)); vars.put("weeks", (double) Math.max(0, ageWeeks));
        vars.put("quality", quality);
        vars.put("base_alcohol", drink.alcohol());
        vars.put("base_food", (double) drink.food());
        double qualityFactor = Math.max(.25, Math.min(1, .5 + quality / 200.0));
        vars.put("potency", qualityFactor);
        vars.put("alcohol", drink.alcohol() * qualityFactor);
        vars.put("food", (double) drink.food());
        for (Map.Entry<String, String> entry : drink.formulas().entrySet()) {
            String key = entry.getKey().toLowerCase();
            double value = Formula.evaluate(entry.getValue(), vars, vars.getOrDefault(key, 0.0));
            vars.put(key, value);
        }
        double alcohol = clamp(vars.getOrDefault("alcohol", drink.alcohol()), 0, 100);
        int food = Math.max(0, (int) Math.round(vars.getOrDefault("food", (double) drink.food())));
        Map<String, String> placeholders = Map.of("age", Integer.toString(ageWeeks), "quality", Integer.toString((int) Math.round(quality)));
        String name = ColorUtil.replace(drink.name(), placeholders);
        List<String> lore = new ArrayList<>();
        for (String line : drink.lore()) lore.add(ColorUtil.replace(line, Map.of(
                "age", Integer.toString(ageWeeks), "quality", Integer.toString((int) Math.round(quality)),
                "alcohol", Integer.toString((int) Math.round(alcohol)))));
        if (ageWeeks > 0 && drink.lore().stream().noneMatch(line -> line.contains("{age}"))) lore.add("&7Выдержка: &f" + ageWeeks + " недель");
        return new Prepared(name, lore, alcohol, food, drink.itemSpec(), vars);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static String stripSlash(String command) { return command != null && command.startsWith("/") ? command.substring(1) : command; }
    private static String fill(String command, Player player, Prepared prepared, DrinkTags.Tag tag) {
        return command.replace("{player}", player.getName()).replace("{uuid}", player.getUniqueId().toString())
                .replace("{age}", Integer.toString(tag.ageWeeks())).replace("{alcohol}", Double.toString(prepared.alcohol()))
                .replace("{quality}", Double.toString(tag.quality()));
    }
    private record Prepared(String name, List<String> lore, double alcohol, int food, String itemSpec, Map<String, Double> variables) { }
    @FunctionalInterface public interface DrunkennessSink { void add(Player player, double units); }
}

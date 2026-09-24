package dev.moonaticks.betterThanBrewery.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Server-side metadata for a filled vessel. Older drinks without a container ID are still readable. */
public final class DrinkTags {
    public record Tag(String id, int ageWeeks, double alcohol, double quality, int units, String containerId) { }
    private final NamespacedKey id, age, alcohol, quality, units, container;
    public DrinkTags(Plugin plugin) {
        id = new NamespacedKey(plugin, "drink"); age = new NamespacedKey(plugin, "age-weeks");
        alcohol = new NamespacedKey(plugin, "alcohol"); quality = new NamespacedKey(plugin, "quality");
        units = new NamespacedKey(plugin, "units"); container = new NamespacedKey(plugin, "container");
    }
    public void write(ItemStack item, String drinkId, int ageWeeks, double alcoholValue, double qualityValue,
                      int unitCount, String containerId) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta(); if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(id, PersistentDataType.STRING, drinkId);
        pdc.set(age, PersistentDataType.INTEGER, Math.max(0, ageWeeks));
        pdc.set(alcohol, PersistentDataType.DOUBLE, alcoholValue);
        pdc.set(quality, PersistentDataType.DOUBLE, qualityValue);
        pdc.set(units, PersistentDataType.INTEGER, Math.max(1, unitCount));
        pdc.set(container, PersistentDataType.STRING, containerId == null ? "" : containerId);
        item.setItemMeta(meta);
    }
    public Tag read(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String drink = pdc.get(id, PersistentDataType.STRING);
        if (drink == null || drink.isBlank()) return null;
        int amount = value(pdc, units, 1);
        if (amount <= 0) return null; // Reject damaged vessels before reading any optional fields.
        double strength = decimal(pdc, alcohol, 0), grade = decimal(pdc, quality, 100);
        if (!Double.isFinite(strength) || !Double.isFinite(grade)) return null;
        String containerId = pdc.getOrDefault(container, PersistentDataType.STRING, "");
        return new Tag(drink, value(pdc, age, 0), strength, grade, amount, containerId == null ? "" : containerId);
    }
    private static int value(PersistentDataContainer pdc, NamespacedKey key, int fallback) {
        Integer number = pdc.getOrDefault(key, PersistentDataType.INTEGER, fallback);
        return number == null ? fallback : number;
    }
    private static double decimal(PersistentDataContainer pdc, NamespacedKey key, double fallback) {
        Double number = pdc.getOrDefault(key, PersistentDataType.DOUBLE, fallback);
        return number == null ? fallback : number;
    }
}

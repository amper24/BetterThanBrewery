package dev.moonaticks.betterThanBrewery.item;

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
        if (item == null || item.getType().isAir()) return;
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
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String drink = pdc.get(id, PersistentDataType.STRING);
        int amount = value(pdc, units, 1);
        double strength = decimal(pdc, alcohol, 0), grade = decimal(pdc, quality, 100);
        if (drink == null || drink.isBlank() || amount <= 0 || !Double.isFinite(strength) || !Double.isFinite(grade)) return null;
        return new Tag(drink, value(pdc, age, 0), strength, grade, amount,
                pdc.getOrDefault(container, PersistentDataType.STRING, ""));
    }
    private static int value(PersistentDataContainer pdc, NamespacedKey key, int fallback) { return pdc.getOrDefault(key, PersistentDataType.INTEGER, fallback); }
    private static double decimal(PersistentDataContainer pdc, NamespacedKey key, double fallback) { return pdc.getOrDefault(key, PersistentDataType.DOUBLE, fallback); }
}

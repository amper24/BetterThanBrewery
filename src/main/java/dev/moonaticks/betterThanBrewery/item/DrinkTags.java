package dev.moonaticks.betterThanBrewery.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class DrinkTags {
    public record Tag(String id, int ageWeeks, double alcohol, double quality, int units) { }
    private final NamespacedKey id, age, alcohol, quality, units;
    public DrinkTags(Plugin plugin) {
        id = new NamespacedKey(plugin, "drink"); age = new NamespacedKey(plugin, "age-weeks");
        alcohol = new NamespacedKey(plugin, "alcohol"); quality = new NamespacedKey(plugin, "quality");
        units = new NamespacedKey(plugin, "units");
    }
    public void write(ItemStack item, String drinkId, int ageWeeks, double alcoholValue, double qualityValue, int unitCount) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta(); if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(id, PersistentDataType.STRING, drinkId);
        pdc.set(age, PersistentDataType.INTEGER, Math.max(0, ageWeeks));
        pdc.set(alcohol, PersistentDataType.DOUBLE, alcoholValue);
        pdc.set(quality, PersistentDataType.DOUBLE, qualityValue);
        pdc.set(units, PersistentDataType.INTEGER, Math.max(1, unitCount));
        item.setItemMeta(meta);
    }
    public Tag read(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String drink = pdc.get(id, PersistentDataType.STRING); if (drink == null) return null;
        return new Tag(drink, value(pdc, age, 0), decimal(pdc, alcohol, 0), decimal(pdc, quality, 100), value(pdc, units, 1));
    }
    private static int value(PersistentDataContainer pdc, NamespacedKey key, int fallback) { return pdc.getOrDefault(key, PersistentDataType.INTEGER, fallback); }
    private static double decimal(PersistentDataContainer pdc, NamespacedKey key, double fallback) { return pdc.getOrDefault(key, PersistentDataType.DOUBLE, fallback); }
}

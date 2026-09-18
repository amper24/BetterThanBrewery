package dev.moonaticks.betterThanBrewery.item;

import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves vanilla, ItemsAdder and CraftEngine item ids without hard-linking optional APIs. */
public final class ItemService {
    private final Plugin plugin;
    private final NamespacedKey itemKey;
    public ItemService(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "item-key");
    }

    public ItemStack create(String spec) {
        if (spec == null || spec.isBlank()) return new ItemStack(Material.AIR);
        String value = spec.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        ItemStack custom = null;
        if (lower.startsWith("itemsadder:") || lower.startsWith("ia:")) custom = itemsAdder(value.substring(value.indexOf(':') + 1));
        else if (lower.startsWith("craftengine:") || lower.startsWith("ce:")) custom = craftEngine(value.substring(value.indexOf(':') + 1));
        if (custom != null) {
            mark(custom, value);
            return custom;
        }
        String materialName = lower.startsWith("minecraft:") ? value.substring(value.indexOf(':') + 1) : value;
        if (materialName.equalsIgnoreCase("potion_water")) {
            ItemStack potion = new ItemStack(Material.POTION);
            if (potion.getItemMeta() instanceof PotionMeta meta) { meta.setColor(org.bukkit.Color.AQUA); potion.setItemMeta(meta); }
            return potion;
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) return new ItemStack(Material.AIR);
        // Do not add a marker to vanilla expectations: a real vanilla item must
        // remain isSimilar() to the expectation. Markers are only needed for
        // optional custom-item providers.
        return new ItemStack(material);
    }

    public boolean matches(ItemStack actual, String spec) {
        if (actual == null || actual.getType().isAir()) return false;
        ItemStack expected = create(spec);
        if (expected.getType().isAir()) return false;
        if (actual.isSimilar(expected)) return true;
        // External providers do not know our marker. Compare their native item
        // data after removing only the BetterThanBrewery marker from copies.
        ItemStack cleanExpected = expected.clone();
        ItemMeta expectedMeta = cleanExpected.getItemMeta();
        if (expectedMeta != null) { expectedMeta.getPersistentDataContainer().remove(itemKey); cleanExpected.setItemMeta(expectedMeta); }
        ItemStack cleanActual = actual.clone();
        ItemMeta actualMeta = cleanActual.getItemMeta();
        if (actualMeta != null) { actualMeta.getPersistentDataContainer().remove(itemKey); cleanActual.setItemMeta(actualMeta); }
        return cleanActual.isSimilar(cleanExpected);
    }

    public ItemStack cloneWith(ItemStack original, String name, List<String> lore, org.bukkit.Color color) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        if (name != null && !name.isBlank()) meta.displayName(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.color(name)));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) lines.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.color(line)));
            meta.lore(lines);
        }
        if (color != null && meta instanceof PotionMeta potionMeta) potionMeta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack appendLore(ItemStack original, List<String> additions) {
        if (original == null || original.getType().isAir() || additions == null || additions.isEmpty()) return original;
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String line : additions) lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.color(line)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void mark(ItemStack item, String key) {
        if (item == null || item.getType().isAir() || key == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, key.toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        }
    }

    private ItemStack itemsAdder(String id) {
        try {
            Class<?> type = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method get = type.getMethod("getInstance", String.class);
            Object stack = get.invoke(null, id);
            if (stack == null) return null;
            Object item = stack.getClass().getMethod("getItemStack").invoke(stack);
            return item instanceof ItemStack result ? result : null;
        } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
    }

    private ItemStack craftEngine(String id) {
        String[] classes = {"net.momirealms.craftengine.bukkit.api.CraftEngineItems", "net.momirealms.craftengine.bukkit.api.CraftEngineBukkit"};
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name);
                for (String methodName : new String[]{"getItemStack", "createItem", "itemStack", "getItem"}) {
                    for (Method method : type.getMethods()) {
                        if (!method.getName().equals(methodName) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != String.class) continue;
                        Object result = method.invoke(null, id);
                        if (result instanceof ItemStack item) return item;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
        }
        return null;
    }
}

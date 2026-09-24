package dev.moonaticks.betterThanBrewery.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The vessel is part of a filled drink's identity, not just its appearance. */
public final class ContainerService {
    public record Container(String id, String empty, String filled, int units, boolean useDrinkItem) { }
    private final ItemService items;
    private final List<Container> containers = new ArrayList<>();
    public ContainerService(ItemService items) { this.items = items; }
    public void load(FileConfiguration config) {
        containers.clear();
        ConfigurationSection section = config.getConfigurationSection("containers");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = "containers." + id;
            containers.add(new Container(id, config.getString(path + ".empty", "minecraft:glass_bottle"),
                    config.getString(path + ".filled", "minecraft:potion"), Math.max(1, config.getInt(path + ".units", 1)),
                    config.getBoolean(path + ".use-drink-item", false)));
        }
    }
    public Container findEmpty(ItemStack item) {
        for (Container container : containers) if (items.matches(item, container.empty())) return container;
        return null;
    }
    public Container byId(String id) {
        if (id == null) return null;
        for (Container container : containers) if (container.id().equalsIgnoreCase(id)) return container;
        return null;
    }
    public List<Container> all() { return List.copyOf(containers); }
    public ItemStack createEmpty(Container container) { return items.create(container.empty()); }
    public ItemStack createFilled(Container container) { return items.create(container.filled()); }
}

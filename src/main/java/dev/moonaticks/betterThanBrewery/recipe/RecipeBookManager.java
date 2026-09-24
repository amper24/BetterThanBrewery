package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A read-only Bukkit inventory, completely independent of CustomGuiReworked. */
public final class RecipeBookManager implements Listener {
    private static final List<Integer> DEFAULT_RECIPE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    /** Identifies only this plugin's inventories, never a matching title or CGR GUI. */
    private static final class BookHolder implements InventoryHolder {
        private final RecipeBookManager owner;
        private final int page;
        private final int pageCount;
        private final String recipeId; // null for the index
        private final Map<Integer, String> entries = new HashMap<>();
        private int previous = -1, next = -1, back = -1, close = -1;
        private Inventory inventory;

        private BookHolder(RecipeBookManager owner, int page, int pageCount, String recipeId) {
            this.owner = owner; this.page = page; this.pageCount = pageCount; this.recipeId = recipeId;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    private final ItemService items;
    private final JavaPlugin plugin;
    private RecipeRegistry recipes = new RecipeRegistry();

    public RecipeBookManager(JavaPlugin plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void setRecipes(RecipeRegistry recipes) {
        this.recipes = recipes == null ? new RecipeRegistry() : recipes;
    }

    /** Called before reloading recipes and when disabling the plugin. */
    public void closeOpenGuis() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BookHolder book
                    && book.owner == this) player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("recipe-book.enabled", true)) return;
        if (event.getClickedBlock() != null && !config.getBoolean("recipe-book.open-on-block", false)) return;
        ItemStack item = event.getItem();
        if (item == null || !bookMatches(item, config)) return;

        event.setCancelled(true);
        openPage(event.getPlayer(), 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BookHolder book)
                || book.owner != this) return;
        // Cancelling the WHOLE view also blocks shift-click, double-click,
        // number keys, offhand swaps and creative cloning from either inventory.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        // Bukkit forbids opening/closing inventories inside InventoryClickEvent.
        // Verify the player is still looking at THIS page on the next tick (a
        // reload or another plugin may have closed it in the meantime).
        Inventory current = event.getView().getTopInventory();
        if (slot == book.close) { navigate(player, current, player::closeInventory); return; }
        if (book.recipeId != null) {
            if (slot == book.back) navigate(player, current, () -> openPage(player, book.page));
            return;
        }
        String recipeId = book.entries.get(slot);
        if (recipeId != null) { navigate(player, current, () -> openDetail(player, recipeId, book.page)); return; }
        if (slot == book.previous && book.page > 0)
            navigate(player, current, () -> openPage(player, book.page - 1));
        else if (slot == book.next && book.page + 1 < book.pageCount)
            navigate(player, current, () -> openPage(player, book.page + 1));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BookHolder book && book.owner == this)
            event.setCancelled(true);
    }

    private void navigate(Player player, Inventory current, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == current) action.run();
        });
    }

    private void openPage(Player player, int requestedPage) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("recipe-book.enabled", true)) return;
        int size = guiSize(config.getInt("recipe-book.gui.size", 54));
        List<RecipeDefinition> visible = visibleRecipes(config);
        Set<Integer> controls = new HashSet<>();
        for (String path : List.of("previous-slot", "next-slot", "close-slot", "page-slot")) {
            int slot = configuredSlot(config, path, switch (path) {
                case "previous-slot" -> 45;
                case "next-slot" -> 53;
                case "close-slot" -> 49;
                default -> 4;
            }, size);
            if (slot >= 0) controls.add(slot);
        }
        List<Integer> recipeSlots = validSlots(config.getIntegerList("recipe-book.gui.recipe-slots"), size, controls);
        if (recipeSlots.isEmpty()) recipeSlots = validSlots(DEFAULT_RECIPE_SLOTS, size, controls);
        if (recipeSlots.isEmpty()) {
            for (int i = 0; i < size; i++) if (!controls.contains(i)) { recipeSlots = List.of(i); break; }
        }
        int pageCount = Math.max(1, recipeSlots.isEmpty() ? 1 : (visible.size() + recipeSlots.size() - 1) / recipeSlots.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        BookHolder holder = new BookHolder(this, page, pageCount, null);
        Inventory inventory = createInventory(holder, size, config.getString("recipe-book.gui.title", "&6Книга рецептов"), config);

        if (!recipeSlots.isEmpty()) {
            int first = page * recipeSlots.size();
            int last = Math.min(visible.size(), first + recipeSlots.size());
            for (int index = first; index < last; index++) {
                int slot = recipeSlots.get(index - first);
                RecipeDefinition recipe = visible.get(index);
                inventory.setItem(slot, recipeIcon(config, recipe));
                holder.entries.put(slot, recipe.id());
            }
        }
        int pageSlot = configuredSlot(config, "page-slot", 4, size);
        if (pageSlot >= 0) inventory.setItem(pageSlot, items.cloneWith(new ItemStack(Material.PAPER),
                "&6Страница &f" + (page + 1) + "&7/&f" + pageCount,
                List.of("&7Всего рецептов: &f" + visible.size()), null));
        if (visible.isEmpty()) {
            int emptySlot = size > 22 && !controls.contains(22) ? 22 : -1;
            if (emptySlot >= 0) inventory.setItem(emptySlot, textItem(Material.BOOK, "&7Рецептов пока нет"));
        }
        if (page > 0) holder.previous = addButton(inventory, config, "previous-slot", "previous-icon", 45,
                Material.ARROW, "&e← Предыдущая страница");
        if (page + 1 < pageCount) holder.next = addButton(inventory, config, "next-slot", "next-icon", 53,
                Material.ARROW, "&eСледующая страница →");
        holder.close = addButton(inventory, config, "close-slot", "close-icon", 49, Material.BARRIER, "&cЗакрыть");
        player.openInventory(inventory);
    }

    private void openDetail(Player player, String recipeId, int parentPage) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("recipe-book.enabled", true)) return;
        RecipeDefinition recipe = recipes.get(recipeId);
        if (recipe == null || !visibleRecipes(config).contains(recipe)) return;
        int size = guiSize(config.getInt("recipe-book.gui.size", 54));
        String title = ColorUtil.replace(config.getString("recipe-book.gui.detail-title", "&6Рецепт: &f{recipe}"),
                Map.of("recipe", recipe.drink().name()));
        BookHolder holder = new BookHolder(this, parentPage, 1, recipe.id());
        Inventory inventory = createInventory(holder, size, title, config);

        int outputSlot = configuredSlot(config, "output-slot", 13, size);
        int infoSlot = configuredSlot(config, "detail-info-slot", 22, size);
        Set<Integer> controls = new HashSet<>();
        for (int slot : new int[]{outputSlot, infoSlot, configuredSlot(config, "back-slot", 48, size),
                configuredSlot(config, "close-slot", 49, size)}) if (slot >= 0) controls.add(slot);
        if (outputSlot >= 0) inventory.setItem(outputSlot, recipeIcon(config, recipe));
        List<Integer> ingredientSlots = validSlots(config.getIntegerList("recipe-book.gui.ingredient-slots"), size, controls);
        if (ingredientSlots.isEmpty()) ingredientSlots = validSlots(List.of(28, 29, 30, 31, 32, 33, 34), size, controls);
        for (int index = 0; index < Math.min(ingredientSlots.size(), recipe.ingredients().size()); index++) {
            Ingredient ingredient = recipe.ingredients().get(index);
            ItemStack display = configuredItem(ingredient.item(), Material.PAPER);
            display.setAmount(Math.min(display.getMaxStackSize(), Math.max(1, ingredient.amount())));
            inventory.setItem(ingredientSlots.get(index), items.appendLore(display,
                    List.of("&7Количество: &f" + ingredient.amount())));
        }
        List<String> details = new ArrayList<>();
        Map<String, String> values = recipePlaceholders(recipe);
        for (String line : config.getStringList("recipe-book.gui.detail-lore"))
            details.add(ColorUtil.replace(line, values));
        if (details.isEmpty()) {
            details.add("&7Станция: &f" + recipe.station());
            details.add("&7Время: &f" + recipe.time() + " тиков");
            if (recipe.water() > 0) details.add("&7Вода: &f" + recipe.water());
            if (!recipe.inputFluid().isBlank()) details.add("&7Входная жидкость: &f" + recipe.inputFluid());
            if (recipe.weeks() > 0) details.add("&7Выдержка: &f" + recipe.weeks() + " нед.");
        }
        if (infoSlot >= 0) inventory.setItem(infoSlot, items.appendLore(
                textItem(Material.WRITABLE_BOOK, "&eПараметры рецепта"), details));
        holder.back = addButton(inventory, config, "back-slot", "back-icon", 48, Material.ARROW, "&e← К списку");
        holder.close = addButton(inventory, config, "close-slot", "close-icon", 49, Material.BARRIER, "&cЗакрыть");
        player.openInventory(inventory);
    }

    private Inventory createInventory(BookHolder holder, int size, String title, FileConfiguration config) {
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.color(title));
        holder.inventory = inventory;
        ItemStack filler = configuredItem(config.getString("recipe-book.gui.filler", "minecraft:black_stained_glass_pane"),
                Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, filler.clone());
        return inventory;
    }

    private int addButton(Inventory inventory, FileConfiguration config, String slotPath, String iconPath,
                          int fallbackSlot, Material fallbackMaterial, String fallbackName) {
        int slot = configuredSlot(config, slotPath, fallbackSlot, inventory.getSize());
        if (slot < 0) return -1;
        ItemStack icon = configuredItem(config.getString("recipe-book.gui." + iconPath, ""), fallbackMaterial);
        boolean hasName = icon.getItemMeta() != null && icon.getItemMeta().displayName() != null;
        inventory.setItem(slot, items.cloneWith(icon, hasName ? null : fallbackName, List.of(), null));
        return slot;
    }

    private ItemStack recipeIcon(FileConfiguration config, RecipeDefinition recipe) {
        String spec = recipe.drink().itemSpec().isBlank()
                ? config.getString("recipe-book.gui.recipe-icon", "minecraft:potion")
                : recipe.drink().itemSpec();
        ItemStack icon = configuredItem(spec, Material.POTION);
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("recipe-book.gui.recipe-lore"))
            lore.add(ColorUtil.replace(line, recipePlaceholders(recipe)));
        if (lore.isEmpty()) {
            lore.add("&7Станция: &f" + recipe.station());
            lore.add("&7Нажмите, чтобы открыть рецепт");
        }
        return items.cloneWith(icon, recipe.drink().name(), lore, recipe.drink().color());
    }

    private List<RecipeDefinition> visibleRecipes(FileConfiguration config) {
        List<String> ids = config.getStringList("recipe-book.recipes");
        if (ids.isEmpty()) return new ArrayList<>(recipes.all());
        List<RecipeDefinition> visible = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : ids) {
            RecipeDefinition recipe = recipes.get(id);
            if (recipe != null && seen.add(recipe.id().toLowerCase(Locale.ROOT))) visible.add(recipe);
        }
        return visible;
    }

    private boolean bookMatches(ItemStack item, FileConfiguration config) {
        List<String> specs = new ArrayList<>(config.getStringList("recipe-book.items"));
        String single = config.getString("recipe-book.item", "");
        if (specs.isEmpty() && !single.isBlank()) specs.add(single);
        if (specs.isEmpty()) specs.add("minecraft:book");
        for (String spec : specs) if (items.matches(item, spec)) return true;
        return false;
    }

    private ItemStack configuredItem(String spec, Material fallback) {
        ItemStack item = items.create(spec);
        return item.getType().isAir() ? new ItemStack(fallback) : item;
    }

    private ItemStack textItem(Material material, String name) {
        return items.cloneWith(new ItemStack(material), name, List.of(), null);
    }

    private static Map<String, String> recipePlaceholders(RecipeDefinition recipe) {
        return Map.of(
                "id", recipe.id(),
                "station", recipe.station(),
                "time", Integer.toString(recipe.time()),
                "ideal-time", Integer.toString(recipe.idealTime()),
                "water", Integer.toString(recipe.water()),
                "weeks", Integer.toString(recipe.weeks()),
                "alcohol", Double.toString(recipe.drink().alcohol()),
                "input-fluid", recipe.inputFluid().isBlank() ? "—" : recipe.inputFluid(),
                "ingredients", Integer.toString(recipe.ingredients().size()));
    }

    private static int configuredSlot(FileConfiguration config, String key, int fallback, int size) {
        int slot = config.getInt("recipe-book.gui." + key, fallback);
        return slot >= 0 && slot < size ? slot : -1;
    }

    private static int guiSize(int value) {
        int size = Math.max(9, Math.min(54, value));
        return ((size + 8) / 9) * 9;
    }

    private static List<Integer> validSlots(List<Integer> slots, int size, Set<Integer> reserved) {
        List<Integer> result = new ArrayList<>();
        for (Integer slot : slots)
            if (slot != null && slot >= 0 && slot < size && !reserved.contains(slot) && !result.contains(slot))
                result.add(slot);
        return result;
    }
}

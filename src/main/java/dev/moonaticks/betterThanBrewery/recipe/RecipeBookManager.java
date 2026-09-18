package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.GuiBuilder;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Opens a configurable recipe book item and renders its pages with CustomGuiReworked. */
public final class RecipeBookManager implements Listener {
    private static final String PAGE_PREFIX = "betterbrewery-recipe-book-page-";
    private static final String DETAIL_PREFIX = "betterbrewery-recipe-book-detail-";
    private static final List<Integer> DEFAULT_RECIPE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final ItemService items;
    private final BetterThanBreweryPluginAccess plugin;
    private final Set<String> registeredGuis = new HashSet<>();
    private final Map<String, Integer> pages = new HashMap<>();
    private final Map<String, Map<Integer, String>> pageRecipes = new HashMap<>();
    private final Map<String, String> detailParents = new HashMap<>();
    private RecipeRegistry recipes = new RecipeRegistry();

    /**
     * Small adapter keeps this class easy to test and avoids coupling the GUI
     * renderer to the plugin's other services.
     */
    public interface BetterThanBreweryPluginAccess {
        FileConfiguration config();
    }

    public RecipeBookManager(BetterThanBreweryPluginAccess plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void setRecipes(RecipeRegistry recipes) {
        this.recipes = recipes == null ? new RecipeRegistry() : recipes;
    }

    public void registerAll() {
        closeOpenGuis();
        unregisterGuis();
        pages.clear();
        pageRecipes.clear();
        detailParents.clear();

        FileConfiguration config = plugin.config();
        if (!config.getBoolean("recipe-book.enabled", true)) return;

        List<RecipeDefinition> visible = visibleRecipes(config);
        int size = guiSize(config.getInt("recipe-book.gui.size", 54));
        List<Integer> recipeSlots = validSlots(config.getIntegerList("recipe-book.gui.recipe-slots"), size);
        if (recipeSlots.isEmpty()) recipeSlots = validSlots(DEFAULT_RECIPE_SLOTS, size);
        if (recipeSlots.isEmpty()) recipeSlots = List.of(0);

        int pageCount = Math.max(1, (visible.size() + recipeSlots.size() - 1) / recipeSlots.size());
        for (int page = 0; page < pageCount; page++) {
            registerPage(config, size, recipeSlots, visible, page, pageCount);
        }
        for (int index = 0; index < visible.size(); index++) {
            RecipeDefinition recipe = visible.get(index);
            int page = index / recipeSlots.size();
            registerDetails(config, size, recipe, page);
        }
    }

    public void closeOpenGuis() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var open = CustomGuiAPI.getOpenGui(player);
            if (open != null && isBookGui(open.name())) player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBookInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        FileConfiguration config = plugin.config();
        if (!config.getBoolean("recipe-book.enabled", true)) return;
        if (event.getClickedBlock() != null && !config.getBoolean("recipe-book.open-on-block", false)) return;

        ItemStack item = event.getItem();
        if (item == null || !bookMatches(item, config)) return;
        event.setCancelled(true);
        if (!registeredGuis.isEmpty()) CustomGuiAPI.openGui(event.getPlayer(), pageName(0));
    }

    @EventHandler
    public void onBookClick(GuiSlotClickEvent event) {
        if (!event.isTopInventory()) return;
        String guiName = event.getGui().name();
        if (!isBookGui(guiName)) return;
        event.setInteractionCancelled(true);

        Player player = event.getPlayer();
        int slot = event.getSlot();
        Integer page = pages.get(guiName);
        if (page != null) {
            Map<Integer, String> recipesOnPage = pageRecipes.getOrDefault(guiName, Map.of());
            String recipeId = recipesOnPage.get(slot);
            if (recipeId != null) {
                String detail = detailName(recipeId);
                if (CustomGuiAPI.guiExists(detail)) CustomGuiAPI.openGui(player, detail);
                return;
            }
            FileConfiguration config = plugin.config();
            int previousSlot = config.getInt("recipe-book.gui.previous-slot", 45);
            int nextSlot = config.getInt("recipe-book.gui.next-slot", 53);
            int closeSlot = config.getInt("recipe-book.gui.close-slot", 49);
            if (slot == previousSlot && page > 0) {
                CustomGuiAPI.openGui(player, pageName(page - 1));
            } else if (slot == nextSlot && page + 1 < pageCount()) {
                CustomGuiAPI.openGui(player, pageName(page + 1));
            } else if (slot == closeSlot) {
                player.closeInventory();
            }
            return;
        }

        String parent = detailParents.get(guiName);
        if (parent == null) return;
        int backSlot = plugin.config().getInt("recipe-book.gui.back-slot", 48);
        int closeSlot = plugin.config().getInt("recipe-book.gui.close-slot", 49);
        if (slot == backSlot) CustomGuiAPI.openGui(player, parent);
        else if (slot == closeSlot) player.closeInventory();
    }

    private void registerPage(FileConfiguration config, int size, List<Integer> recipeSlots,
                              List<RecipeDefinition> visible, int page, int pageCount) {
        String name = pageName(page);
        GuiBuilder builder = CustomGuiAPI.builder(name)
                .title(title(config.getString("recipe-book.gui.title", "&6Книга рецептов")))
                .size(size)
                .storage(StorageType.TEMPORARY);
        ItemStack filler = configuredItem(config.getString("recipe-book.gui.filler", "minecraft:black_stained_glass_pane"), Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size; slot++) builder.design(slot, filler);

        Map<Integer, String> recipesOnPage = new HashMap<>();
        int first = page * recipeSlots.size();
        int last = Math.min(visible.size(), first + recipeSlots.size());
        for (int index = first; index < last; index++) {
            int slot = recipeSlots.get(index - first);
            RecipeDefinition recipe = visible.get(index);
            builder.design(slot, recipeIcon(config, recipe));
            recipesOnPage.put(slot, recipe.id());
        }
        addPageButton(builder, size, config, "previous-slot", "previous-icon", 45, Material.ARROW, "&eПредыдущая страница");
        addPageButton(builder, size, config, "next-slot", "next-icon", 53, Material.ARROW, "&eСледующая страница");
        addPageButton(builder, size, config, "close-slot", "close-icon", 49, Material.BARRIER, "&cЗакрыть");
        int pageSlot = config.getInt("recipe-book.gui.page-slot", 4);
        if (pageSlot >= 0 && pageSlot < size) builder.design(pageSlot, textItem(Material.PAPER,
                "&7Страница &f" + (page + 1) + "&7/&f" + pageCount));

        CustomGuiAPI.registerGui(builder.build(), false);
        registeredGuis.add(name);
        pages.put(name, page);
        pageRecipes.put(name, recipesOnPage);
    }

    private void registerDetails(FileConfiguration config, int size, RecipeDefinition recipe, int parentPage) {
        String name = detailName(recipe.id());
        String title = ColorUtil.replace(config.getString("recipe-book.gui.detail-title", "&6Рецепт: &f{recipe}"),
                Map.of("recipe", ColorUtil.color(recipe.drink().name())));
        GuiBuilder builder = CustomGuiAPI.builder(name).title(title).size(size).storage(StorageType.TEMPORARY);
        ItemStack filler = configuredItem(config.getString("recipe-book.gui.filler", "minecraft:black_stained_glass_pane"), Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < size; slot++) builder.design(slot, filler);

        int outputSlot = config.getInt("recipe-book.gui.output-slot", 13);
        if (outputSlot >= 0 && outputSlot < size) builder.design(outputSlot, recipeIcon(config, recipe));
        List<Integer> ingredientSlots = validSlots(config.getIntegerList("recipe-book.gui.ingredient-slots"), size);
        if (ingredientSlots.isEmpty()) ingredientSlots = List.of(28, 29, 30, 31, 32, 33, 34);
        for (int index = 0; index < Math.min(ingredientSlots.size(), recipe.ingredients().size()); index++) {
            Ingredient ingredient = recipe.ingredients().get(index);
            ItemStack display = configuredItem(ingredient.item(), Material.PAPER);
            display.setAmount(Math.min(display.getMaxStackSize(), Math.max(1, ingredient.amount())));
            builder.design(ingredientSlots.get(index), items.appendLore(display, List.of("&7Количество: &f" + ingredient.amount())));
        }

        List<String> details = new ArrayList<>();
        Map<String, String> values = recipePlaceholders(recipe);
        for (String line : config.getStringList("recipe-book.gui.detail-lore")) {
            details.add(ColorUtil.replace(line, values));
        }
        if (details.isEmpty()) {
            details.add("&7Станция: &f" + recipe.station());
            details.add("&7Время: &f" + recipe.time() + " тиков");
            if (recipe.water() > 0) details.add("&7Вода: &f" + recipe.water());
            if (!recipe.inputFluid().isBlank()) details.add("&7Входная жидкость: &f" + recipe.inputFluid());
            if (recipe.weeks() > 0) details.add("&7Выдержка: &f" + recipe.weeks() + " нед.");
        }
        int detailInfoSlot = config.getInt("recipe-book.gui.detail-info-slot", 22);
        if (detailInfoSlot >= 0 && detailInfoSlot < size) builder.design(detailInfoSlot,
                items.appendLore(textItem(Material.WRITABLE_BOOK, "&eПараметры рецепта"), details));
        addPageButton(builder, size, config, "back-slot", "back-icon", 48, Material.ARROW, "&eНазад к списку");
        addPageButton(builder, size, config, "close-slot", "close-icon", 49, Material.BARRIER, "&cЗакрыть");

        CustomGuiAPI.registerGui(builder.build(), false);
        registeredGuis.add(name);
        detailParents.put(name, pageName(parentPage));
    }

    private void addPageButton(GuiBuilder builder, int size, FileConfiguration config, String slotPath,
                               String iconPath, int fallbackSlot, Material fallbackMaterial, String fallbackName) {
        int slot = config.getInt("recipe-book.gui." + slotPath, fallbackSlot);
        if (slot < 0 || slot >= size) return;
        ItemStack icon = configuredItem(config.getString("recipe-book.gui." + iconPath, ""), fallbackMaterial);
        boolean hasName = icon.getItemMeta() != null && icon.getItemMeta().displayName() != null;
        builder.design(slot, items.cloneWith(icon, hasName ? null : fallbackName, List.of(), null));
    }

    private ItemStack recipeIcon(FileConfiguration config, RecipeDefinition recipe) {
        String spec = recipe.drink().itemSpec().isBlank()
                ? config.getString("recipe-book.gui.recipe-icon", "minecraft:potion")
                : recipe.drink().itemSpec();
        ItemStack icon = configuredItem(spec, Material.POTION);
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("recipe-book.gui.recipe-lore")) {
            lore.add(ColorUtil.replace(line, recipePlaceholders(recipe)));
        }
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

    private Map<String, String> recipePlaceholders(RecipeDefinition recipe) {
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

    private int pageCount() {
        return pages.size();
    }

    private String pageName(int page) {
        return PAGE_PREFIX + Math.max(0, page);
    }

    private String detailName(String recipeId) {
        String normalized = recipeId.toLowerCase(Locale.ROOT);
        return DETAIL_PREFIX + normalized.replaceAll("[^a-z0-9_-]", "-") + "-" + Integer.toHexString(normalized.hashCode());
    }

    private boolean isBookGui(String name) {
        return registeredGuis.contains(name);
    }

    private void unregisterGuis() {
        for (String name : registeredGuis) CustomGuiAPI.unregisterGui(name, false);
        registeredGuis.clear();
    }

    private static int guiSize(int value) {
        int size = Math.max(9, Math.min(54, value));
        return Math.max(9, Math.min(54, ((size + 8) / 9) * 9));
    }

    private static List<Integer> validSlots(List<Integer> slots, int size) {
        List<Integer> result = new ArrayList<>();
        for (Integer slot : slots) if (slot != null && slot >= 0 && slot < size && !result.contains(slot)) result.add(slot);
        return result;
    }

    private static String title(String value) {
        return ColorUtil.color(value == null ? "" : value);
    }
}

package dev.moonaticks.betterThanBrewery.station;

import dev.moonaticks.betterThanBrewery.BetterThanBrewery;
import dev.moonaticks.betterThanBrewery.config.Lang;
import dev.moonaticks.betterThanBrewery.drink.DrinkService;
import dev.moonaticks.betterThanBrewery.item.ContainerService;
import dev.moonaticks.betterThanBrewery.item.DrinkTags;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import dev.moonaticks.betterThanBrewery.recipe.Byproduct;
import dev.moonaticks.betterThanBrewery.recipe.Ingredient;
import dev.moonaticks.betterThanBrewery.recipe.RecipeDefinition;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import dev.moonaticks.betterThanBrewery.util.ColorUtil;
import dev.moonaticks.customGuiReworked.api.CustomGuiAPI;
import dev.moonaticks.customGuiReworked.api.Gui;
import dev.moonaticks.customGuiReworked.api.SlotType;
import dev.moonaticks.customGuiReworked.api.StorageType;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlock;
import dev.moonaticks.customGuiReworked.api.functional.FunctionalBlockData;
import dev.moonaticks.customGuiReworked.api.event.GuiSlotClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Station state lives in CustomGuiReworked's persistent block storage, not in viewers. */
public final class StationManager {
    private static final String FLUID = "fluid";
    private static final String LEVEL = "fluid-level";
    private static final String WATER = "water";
    private static final String AGE = "age-ticks";
    private static final String QUALITY = "quality";
    private static final String PROGRESS = "progress";
    private static final String RECIPE = "recipe";

    private final BetterThanBrewery plugin;
    private final ItemService items;
    private final ContainerService containers;
    private final DrinkService drinks;
    private final Lang lang;
    private final Map<String, StationDefinition> stations = new HashMap<>();
    private final Map<String, StationDefinition> byGui = new HashMap<>();
    private RecipeRegistry recipes = new RecipeRegistry();
    private List<Integer> fluidSlots = List.of(7, 8, 16, 17, 25, 26, 34, 35, 43, 44);
    private ItemStack filler;

    public StationManager(BetterThanBrewery plugin, ItemService items, ContainerService containers, DrinkService drinks, Lang lang) {
        this.plugin = plugin; this.items = items; this.containers = containers; this.drinks = drinks; this.lang = lang;
    }
    public void setRecipes(RecipeRegistry recipes) { this.recipes = recipes; }
    public int stationCount() { return stations.size(); }

    public void registerAll() {
        for (StationDefinition old : stations.values()) for (String blockId : old.blockIds()) {
            CustomGuiAPI.getFunctionalBlocks().unregisterHandler(blockId);
            CustomGuiAPI.unregisterBlockGui(blockId);
        }
        stations.clear(); byGui.clear();
        FileConfiguration config = plugin.getConfig();
        fluidSlots = config.getIntegerList("gui.fluid-slots");
        if (fluidSlots.isEmpty()) fluidSlots = List.of(7, 8, 16, 17, 25, 26, 34, 35, 43, 44);
        filler = items.create("minecraft:" + config.getString("gui.filler", "BLACK_STAINED_GLASS_PANE"));
        if (filler.getType().isAir()) filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ConfigurationSection section = config.getConfigurationSection("stations");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = "stations." + id;
            if (!config.getBoolean(path + ".enabled", true)) continue;
            StationDefinition station = new StationDefinition(id.toLowerCase(Locale.ROOT),
                    config.getString(path + ".gui", id).toLowerCase(Locale.ROOT),
                    Math.max(1, config.getInt(path + ".capacity", 10)), Math.max(0, config.getInt(path + ".water-capacity", 10)),
                    config.getInt(path + ".water-input-slot", 40), config.getInt(path + ".fluid-input-slot", 40),
                    config.getInt(path + ".fuel-slot", 42), config.getInt(path + ".result-slot", 22),
                    config.getIntegerList(path + ".ingredient-slots"), config.getIntegerList(path + ".byproduct-slots"),
                    config.getStringList(path + ".blocks"));
            stations.put(station.id(), station); byGui.put(station.gui(), station);
            registerGui(station);
            for (String blockId : station.blockIds()) registerFunctionalBlock(station, blockId);
        }
    }

    private void registerGui(StationDefinition station) {
        String titleKey = "gui." + station.id() + "-title";
        String title = offset(ColorUtil.color(plugin.getConfig().getString(titleKey, station.id())), plugin.getConfig().getInt("gui.title-offset", 0));
        dev.moonaticks.customGuiReworked.api.GuiBuilder builder = CustomGuiAPI.builder(station.gui())
                .title(title).size(54).storage(StorageType.BLOCK);
        for (int slot = 0; slot < 54; slot++) builder.design(slot, filler);
        for (int slot : station.ingredientSlots()) builder.slot(slot, SlotType.CRAFT);
        if (station.id().equals("distiller")) builder.slot(station.fuelSlot(), SlotType.FUEL);
        for (int slot : station.byproductSlots()) builder.slot(slot, SlotType.RESULT);
        // Input/result/fluid slots intentionally stay DESIGN: liquid can only move through our vessel handler.
        CustomGuiAPI.registerGui(builder.build(), true);
    }

    private void registerFunctionalBlock(StationDefinition station, String blockId) {
        if (blockId == null || blockId.isBlank()) return;
        FunctionalBlock.builder(blockId).gui(station.gui())
                .canOpen((player, block) -> player.hasPermission("betterthanbrewery.use"))
                .onOpen((player, block, inventory) -> { updateWorking(station, blockId, block); render(station, block, player); })
                .onTick((block, inventory) -> renderAll(station, block))
                .onClick((player, block, slot, type, event) -> handleClick(station, blockId, block, player, slot, event))
                .onItemChanged((player, block, slot, type, oldItem, newItem) -> {
                    handleChanged(station, blockId, block, slot);
                })
                .onBlockTick((block, data) -> tick(station, blockId, block, data))
                .register();
    }

    private void handleChanged(StationDefinition station, String blockId, Location block, int slot) {
        if (station.ingredientSlots().contains(slot) || slot == station.waterInputSlot()
                || slot == station.fluidInputSlot() || slot == station.fuelSlot()) updateWorking(station, blockId, block);
    }

    private void updateWorking(StationDefinition station, String blockId, Location block) {
        FunctionalBlockData data = CustomGuiAPI.blockData(blockId, block);
        if (data == null) return;
        if (station.id().equals("barrel")) {
            CustomGuiAPI.setWorking(blockId, block, getFluid(data) != null && findRecipe(station, block, data) != null);
            return;
        }
        RecipeDefinition recipe = findRecipe(station, block, data);
        boolean ready = recipe != null && (station.id().equals("boiler") || station.id().equals("kettle")
                ? getInt(data, WATER, 0) >= recipe.water()
                : hasFuel(station, block, recipe));
        if (station.id().equals("distiller") || station.id().equals("kettle")) ready = ready && heated(block);
        if (getFluid(data) != null && !station.id().equals("boiler") && !station.id().equals("kettle")) {
            if (!station.id().equals("distiller")) ready = true;
        }
        if (ready && data.getString(RECIPE, "").isBlank()) data.set(RECIPE, recipe.id());
        CustomGuiAPI.setWorking(blockId, block, ready && !hasFinishedOutput(station, data));
    }

    private void tick(StationDefinition station, String blockId, Location block, FunctionalBlockData data) {
        if (station.id().equals("barrel")) { tickBarrel(station, blockId, block, data); return; }
        String recipeId = data.getString(RECIPE, "");
        RecipeDefinition recipe = recipeId.isBlank() ? findRecipe(station, block, data) : recipes.get(recipeId);
        if (recipe == null) { CustomGuiAPI.setWorking(blockId, block, false); return; }
        if (station.id().equals("boiler")) { tickBoiler(station, blockId, block, data, recipe); return; }
        if ((station.id().equals("kettle") || station.id().equals("distiller")) && !heated(block)) {
            playHeat(block, false); CustomGuiAPI.setWorking(blockId, block, false); return;
        }
        int progress = data.getInt(PROGRESS, 0) + 5;
        data.setInt(PROGRESS, progress); data.set(RECIPE, recipe.id());
        int required = Math.max(5, recipe.time());
        if (progress >= required) completeTimed(station, blockId, block, data, recipe);
        else if (plugin.getServer().getCurrentTick() % 20 == 0) playHeat(block, true);
    }

    private void tickBoiler(StationDefinition station, String blockId, Location block, FunctionalBlockData data, RecipeDefinition recipe) {
        if (getInt(data, WATER, 0) < recipe.water() || !matchesIngredients(station, block, recipe)) {
            CustomGuiAPI.setWorking(blockId, block, false); return;
        }
        int progress = data.getInt(PROGRESS, 0) + 5;
        data.setInt(PROGRESS, Math.min(progress, recipe.maxTime())); data.set(RECIPE, recipe.id());
        if (progress >= recipe.maxTime()) finishBoiler(station, blockId, block, data, recipe);
        else if (plugin.getServer().getCurrentTick() % 20 == 0) playHeat(block, true);
    }

    private void completeTimed(StationDefinition station, String blockId, Location block, FunctionalBlockData data, RecipeDefinition recipe) {
        if (station.id().equals("kettle")) {
            if (!matchesIngredients(station, block, recipe) || getInt(data, WATER, 0) < recipe.water()) { CustomGuiAPI.setWorking(blockId, block, false); return; }
            consumeIngredients(station, block, recipe); data.setInt(WATER, getInt(data, WATER, 0) - recipe.water());
            setFluid(data, recipe.outputFluid(), station.capacity(), 100, 0); produceByproducts(station, block, recipe); completeEffects(block);
        } else {
            if (getFluid(data) == null || !recipe.inputFluid().equalsIgnoreCase(getFluid(data)) || !hasFuel(station, block, recipe)) { CustomGuiAPI.setWorking(blockId, block, false); return; }
            consumeFuel(station, block, recipe); setFluid(data, recipe.outputFluid(), Math.min(station.capacity(), getLevel(data)), 100, getInt(data, AGE, 0));
            produceByproducts(station, block, recipe); completeEffects(block);
        }
        data.setInt(PROGRESS, 0); data.remove(RECIPE); CustomGuiAPI.setWorking(blockId, block, false);
    }

    private void finishBoiler(StationDefinition station, String blockId, Location block, FunctionalBlockData data, RecipeDefinition recipe) {
        if (!matchesIngredients(station, block, recipe) || getInt(data, WATER, 0) < recipe.water()) return;
        int progress = data.getInt(PROGRESS, 0);
        double distance = Math.abs(progress - recipe.idealTime());
        double quality = Math.max(0, 100 - distance * 100.0 / Math.max(1, recipe.maxTime() - recipe.idealTime()));
        consumeIngredients(station, block, recipe); data.setInt(WATER, getInt(data, WATER, 0) - recipe.water());
        setFluid(data, recipe.outputFluid(), station.capacity(), quality, 0); produceByproducts(station, block, recipe); completeEffects(block);
        data.setInt(PROGRESS, 0); data.remove(RECIPE); CustomGuiAPI.setWorking(blockId, block, false);
    }

    private void tickBarrel(StationDefinition station, String blockId, Location block, FunctionalBlockData data) {
        if (getFluid(data) == null || findRecipe(station, block, data) == null) { CustomGuiAPI.setWorking(blockId, block, false); return; }
        data.setInt(AGE, getInt(data, AGE, 0) + 5);
        if (plugin.getServer().getCurrentTick() % Math.max(20, plugin.getConfig().getInt("aging.update-ticks", 100)) == 0) playHeat(block, false);
    }

    private RecipeDefinition findRecipe(StationDefinition station, Location block, FunctionalBlockData data) {
        String fluid = getFluid(data);
        for (RecipeDefinition recipe : recipes.all()) {
            if (!recipe.station().equalsIgnoreCase(station.id())) continue;
            if (station.id().equals("distiller") || station.id().equals("barrel")) {
                if (fluid != null && !recipe.inputFluid().equalsIgnoreCase(fluid)) continue;
                if (fluid == null) continue;
                if (station.id().equals("distiller") && !hasFuel(station, block, recipe)) continue;
                return recipe;
            }
            if (matchesIngredients(station, block, recipe)) return recipe;
        }
        return null;
    }

    private boolean matchesIngredients(StationDefinition station, Location block, RecipeDefinition recipe) {
        if (recipe.ingredients().isEmpty()) return false;
        Set<Integer> used = new HashSet<>();
        for (Ingredient needed : recipe.ingredients()) {
            boolean found = false;
            if (needed.slot() >= 0) {
                ItemStack item = CustomGuiAPI.getBlockSlotItem(block, needed.slot());
                found = items.matches(item, needed.item()) && item.getAmount() >= needed.amount();
                if (found) used.add(needed.slot());
            } else {
                for (int slot : station.ingredientSlots()) {
                    if (used.contains(slot)) continue;
                    ItemStack item = CustomGuiAPI.getBlockSlotItem(block, slot);
                    if (items.matches(item, needed.item()) && item.getAmount() >= needed.amount()) { found = true; used.add(slot); break; }
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private void consumeIngredients(StationDefinition station, Location block, RecipeDefinition recipe) {
        Set<Integer> used = new HashSet<>();
        for (Ingredient needed : recipe.ingredients()) {
            int foundSlot = needed.slot();
            if (foundSlot < 0) for (int slot : station.ingredientSlots()) {
                if (used.contains(slot)) continue;
                if (items.matches(CustomGuiAPI.getBlockSlotItem(block, slot), needed.item())) { foundSlot = slot; break; }
            }
            if (foundSlot >= 0) { CustomGuiAPI.consumeBlockSlotItem(block, foundSlot, needed.amount()); used.add(foundSlot); }
        }
    }

    private boolean hasFuel(StationDefinition station, Location block, RecipeDefinition recipe) {
        if (recipe.fuelItem().isBlank()) return true;
        ItemStack fuel = CustomGuiAPI.getBlockSlotItem(block, station.fuelSlot());
        return items.matches(fuel, recipe.fuelItem()) && fuel.getAmount() >= recipe.fuelAmount();
    }
    private void consumeFuel(StationDefinition station, Location block, RecipeDefinition recipe) { if (!recipe.fuelItem().isBlank()) CustomGuiAPI.consumeBlockSlotItem(block, station.fuelSlot(), recipe.fuelAmount()); }

    private void handleClick(StationDefinition station, String blockId, Location block, Player player, int slot, GuiSlotClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (station.id().equals("boiler") && cursor != null && cursor.getType() == Material.CLOCK) {
            event.setInteractionCancelled(true);
            FunctionalBlockData clockData = CustomGuiAPI.blockData(blockId, block);
            RecipeDefinition clockRecipe = recipes.get(clockData.getString(RECIPE, ""));
            if (clockRecipe != null) lang.send(player, "boiler-time", Map.of("elapsed", Integer.toString(clockData.getInt(PROGRESS, 0)), "ideal", Integer.toString(clockRecipe.idealTime())));
            else player.sendMessage(ColorUtil.color("&7Бойлер сейчас не готовит."));
            return;
        }
        boolean fluidSlot = fluidSlots.contains(slot) || slot == station.resultSlot();
        if (station.id().equals("boiler") || station.id().equals("kettle")) {
            if (slot == station.waterInputSlot()) { handleWater(station, blockId, block, player, event); return; }
        }
        if ((station.id().equals("distiller") || station.id().equals("barrel")) && slot == station.fluidInputSlot()) {
            if (acceptFluid(station, blockId, block, player, event)) return;
        }
        if (fluidSlot && cursor != null && !cursor.getType().isAir()) {
            RecipeDefinition recipe = recipes.get(CustomGuiAPI.blockData(blockId, block).getString(RECIPE, ""));
            FunctionalBlockData data = CustomGuiAPI.blockData(blockId, block);
            if (station.id().equals("boiler") && getFluid(data) == null && recipe != null && data.getInt(PROGRESS, 0) > 0) {
                if (data.getInt(PROGRESS, 0) < recipe.idealTime()) { event.setInteractionCancelled(true); player.sendMessage(lang.text("not-ready", Map.of("time", Integer.toString(recipe.idealTime())))); return; }
                finishBoiler(station, blockId, block, data, recipe);
            }
            if (takeFluid(station, blockId, block, player, event)) return;
            event.setInteractionCancelled(true); lang.send(player, "not-container");
        }
    }

    private void handleWater(StationDefinition station, String blockId, Location block, Player player, GuiSlotClickEvent event) {
        ItemStack cursor = event.getCursor(); if (cursor == null || cursor.getType().isAir()) return;
        FunctionalBlockData data = CustomGuiAPI.blockData(blockId, block);
        if (isWaterSource(cursor)) {
            event.setInteractionCancelled(true); int units = plugin.getConfig().getInt("water.bucket-units", 4);
            data.setInt(WATER, Math.min(station.waterCapacity(), getInt(data, WATER, 0) + units));
            replaceOne(player, cursor, items.create("minecraft:bucket")); updateWorking(station, blockId, block); return;
        }
        ContainerService.Container container = containers.findEmpty(cursor);
        if (container != null && getInt(data, WATER, 0) >= container.units()) {
            event.setInteractionCancelled(true); data.setInt(WATER, getInt(data, WATER, 0) - container.units());
            deliver(player, event.getClick(), cursor, drinks.createWater(container)); updateWorking(station, blockId, block);
        }
    }

    private boolean acceptFluid(StationDefinition station, String blockId, Location block, Player player, GuiSlotClickEvent event) {
        ItemStack cursor = event.getCursor(); DrinkTags.Tag tag = drinks.read(cursor); if (tag == null) return false;
        FunctionalBlockData data = CustomGuiAPI.blockData(blockId, block); if (getFluid(data) != null) return false;
        // A barrel accepts any BetterThanBrewery-tagged liquid. If there is
        // no ageing recipe, it simply remains a non-ageing liquid.
        event.setInteractionCancelled(true); setFluid(data, tag.id(), Math.min(station.capacity(), tag.units()), tag.quality(), tag.ageWeeks() * plugin.getConfig().getInt("aging.week-ticks", 12096000));
        replaceOne(player, cursor, new ItemStack(Material.AIR)); updateWorking(station, blockId, block); return true;
    }

    private boolean takeFluid(StationDefinition station, String blockId, Location block, Player player, GuiSlotClickEvent event) {
        FunctionalBlockData data = CustomGuiAPI.blockData(blockId, block);
        String fluid = getFluid(data); if (fluid == null || getLevel(data) <= 0) return false;
        ContainerService.Container container = containers.findEmpty(event.getCursor()); if (container == null) return false;
        if (getLevel(data) < container.units()) { event.setInteractionCancelled(true); lang.send(player, "not-enough-fluid"); return true; }
        RecipeDefinition barrel = station.id().equals("barrel") ? findRecipe(station, block, data) : null;
        String resultFluid = barrel == null ? fluid : barrel.outputFluid();
        int ageWeeks = Math.max(0, getInt(data, AGE, 0) / Math.max(1, plugin.getConfig().getInt("aging.week-ticks", 12096000)));
        ItemStack output = resultFluid.equalsIgnoreCase("water") ? drinks.createWater(container) : drinks.createFilled(resultFluid, ageWeeks, getDouble(data, QUALITY, 100), container);
        event.setInteractionCancelled(true); data.setInt(LEVEL, getLevel(data) - container.units());
        if (getLevel(data) <= 0) { data.remove(FLUID); data.remove(LEVEL); data.remove(AGE); data.remove(QUALITY); data.remove(RECIPE); CustomGuiAPI.setWorking(blockId, block, false); }
        deliver(player, event.getClick(), event.getCursor(), output); plugin.getServer().getScheduler().runTask(plugin, () -> renderAll(station, block)); return true;
    }

    private void renderAll(StationDefinition station, Location block) { for (Player viewer : CustomGuiAPI.getViewers(block)) render(station, block, viewer); }
    private void render(StationDefinition station, Location block, Player viewer) {
        FunctionalBlockData data = CustomGuiAPI.blockData(block); if (data == null) return;
        String fluid = getFluid(data); RecipeDefinition active = recipes.get(data.getString(RECIPE, ""));
        if (fluid == null && station.id().equals("boiler") && active != null && data.getInt(PROGRESS, 0) > 0) fluid = active.outputFluid();
        if (fluid == null && (station.id().equals("boiler") || station.id().equals("kettle")) && getInt(data, WATER, 0) > 0) fluid = "water";
        ItemStack visual = null;
        if (fluid != null) {
            int ageWeeks = Math.max(0, getInt(data, AGE, 0) / Math.max(1, plugin.getConfig().getInt("aging.week-ticks", 12096000)));
            visual = drinks.createFilled(fluid, ageWeeks, getDouble(data, QUALITY, 100));
            visual.setAmount(1);
        }
        for (int slot : fluidSlots) CustomGuiAPI.setLocalDesign(viewer, block, slot, visual);
        CustomGuiAPI.setLocalDesign(viewer, block, station.resultSlot(), visual);
        String title = offset(ColorUtil.color(plugin.getConfig().getString("gui." + station.id() + "-title", station.id())), plugin.getConfig().getInt("gui.title-offset", 0));
        CustomGuiAPI.setLocalTitle(viewer, block, title);
    }

    private void produceByproducts(StationDefinition station, Location block, RecipeDefinition recipe) {
        int index = 0;
        for (Byproduct byproduct : recipe.byproducts()) {
            if (ThreadLocalRandom.current().nextDouble() > byproduct.chance() || index >= station.byproductSlots().size()) continue;
            int slot = station.byproductSlots().get(index++); ItemStack item = items.create(byproduct.item()); item.setAmount(byproduct.amount());
            ItemStack existing = CustomGuiAPI.getBlockSlotItem(block, slot);
            if (existing == null || existing.getType().isAir()) CustomGuiAPI.setBlockSlotItem(block, slot, item);
            else block.getWorld().dropItemNaturally(block.clone().add(0.5, 1, 0.5), item);
        }
    }

    private boolean heated(Location block) {
        Material below = block.getBlock().getRelative(BlockFace.DOWN).getType();
        for (String name : plugin.getConfig().getStringList("heat.materials")) {
            Material material = Material.matchMaterial(name); if (material == below) return true;
        }
        return below == Material.FIRE || below == Material.LAVA || below == Material.CAMPFIRE;
    }
    private void playHeat(Location block, boolean particles) {
        if (!heated(block)) return;
        if (particles) {
            Particle particle; try { particle = Particle.valueOf(plugin.getConfig().getString("heat.particle", "FLAME")); } catch (IllegalArgumentException ex) { particle = Particle.FLAME; }
            block.getWorld().spawnParticle(particle, block.clone().add(.5, .8, .5), plugin.getConfig().getInt("heat.particle-count", 2), .18, .18, .18, .01);
        }
        try { block.getWorld().playSound(block, Sound.valueOf(plugin.getConfig().getString("heat.sound", "BLOCK_FIRE_AMBIENT")), (float) plugin.getConfig().getDouble("heat.sound-volume", .35), (float) plugin.getConfig().getDouble("heat.sound-pitch", 1)); } catch (IllegalArgumentException ignored) { }
    }
    private void completeEffects(Location block) {
        try { block.getWorld().playSound(block, Sound.valueOf(plugin.getConfig().getString("effects.sound-complete", "BLOCK_BREWING_STAND_BREW")), 1, 1); } catch (IllegalArgumentException ignored) { }
        try { Particle particle = Particle.valueOf(plugin.getConfig().getString("effects.particle-complete", "END_ROD")); block.getWorld().spawnParticle(particle, block.clone().add(.5, 1, .5), plugin.getConfig().getInt("effects.particle-count", 12), .25, .35, .25, .02); } catch (IllegalArgumentException ignored) { }
    }

    private boolean isWaterSource(ItemStack item) { for (String spec : plugin.getConfig().getStringList("water.source-items")) if (items.matches(item, spec)) return true; return false; }
    private boolean hasFinishedOutput(StationDefinition station, FunctionalBlockData data) { return getFluid(data) != null && (station.id().equals("boiler") || station.id().equals("kettle") || station.id().equals("distiller")); }
    private static String getFluid(FunctionalBlockData data) { String fluid = data.getString(FLUID, ""); return fluid.isBlank() ? null : fluid; }
    private static int getLevel(FunctionalBlockData data) { return data.getInt(LEVEL, 0); }
    private static int getInt(FunctionalBlockData data, String key, int fallback) { return data.getInt(key, fallback); }
    private static double getDouble(FunctionalBlockData data, String key, double fallback) { return data.getDouble(key, fallback); }
    private static void setFluid(FunctionalBlockData data, String fluid, int level, double quality, int age) { data.set(FLUID, fluid); data.setInt(LEVEL, level); data.setDouble(QUALITY, quality); data.setInt(AGE, age); }
    private static String offset(String title, int amount) { return "\u00a0".repeat(Math.max(0, Math.min(amount, 32))) + title; }

    private void replaceOne(Player player, ItemStack cursor, ItemStack replacement) {
        if (cursor.getAmount() <= 1) player.setItemOnCursor(replacement);
        else { ItemStack rest = cursor.clone(); rest.setAmount(rest.getAmount() - 1); player.setItemOnCursor(rest); player.getInventory().addItem(replacement); }
    }
    private void deliver(Player player, ClickType click, ItemStack cursor, ItemStack output) {
        if (cursor.getAmount() <= 1 && !click.isShiftClick()) player.setItemOnCursor(output);
        else {
            ItemStack rest = cursor.clone(); rest.setAmount(rest.getAmount() - 1); player.setItemOnCursor(rest.getAmount() <= 0 ? new ItemStack(Material.AIR) : rest);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(output); for (ItemStack left : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        try { player.playSound(player.getLocation(), Sound.valueOf(plugin.getConfig().getString("effects.sound-take", "ITEM_BOTTLE_FILL")), 1, 1); } catch (IllegalArgumentException ignored) { }
    }
}

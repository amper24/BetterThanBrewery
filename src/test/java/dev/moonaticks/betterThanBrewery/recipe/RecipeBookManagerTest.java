package dev.moonaticks.betterThanBrewery.recipe;

import dev.moonaticks.betterThanBrewery.drink.DrinkDefinition;
import dev.moonaticks.betterThanBrewery.item.ItemService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipeBookManagerTest {
    @Test
    void standaloneBookPaginatesAndBlocksTransfersEvenDuringReload() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("recipe-book.items", List.of("minecraft:book"));
        config.set("recipe-book.gui.recipe-slots", List.of(10)); // two recipes -> two pages
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        ItemService items = mock(ItemService.class);
        ItemStack icon = mock(ItemStack.class);
        when(icon.getType()).thenReturn(Material.BOOK);
        when(icon.clone()).thenReturn(icon);
        when(icon.getMaxStackSize()).thenReturn(64);
        when(items.create(anyString())).thenReturn(icon);
        when(items.matches(icon, "minecraft:book")).thenReturn(true);
        when(items.cloneWith(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(0));
        when(items.appendLore(any(), anyList())).thenAnswer(call -> call.getArgument(0));

        RecipeBookManager book = new RecipeBookManager(plugin, items);
        RecipeRegistry recipes = new RecipeRegistry();
        recipes.add(recipe("first"));
        recipes.add(recipe("second"));
        book.setRecipes(recipes);

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        InventoryView defaultView = mock(InventoryView.class);
        Inventory playerInventory = mock(Inventory.class);
        when(defaultView.getTopInventory()).thenReturn(playerInventory);
        AtomicReference<InventoryView> currentView = new AtomicReference<>(defaultView);
        when(player.getOpenInventory()).thenAnswer(call -> currentView.get());
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            InventoryView view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(call.getArgument(0));
            currentView.set(view);
            return view;
        });
        doAnswer(call -> { currentView.set(defaultView); return null; }).when(player).closeInventory();

        ArrayDeque<Runnable> nextTick = new ArrayDeque<>();
        List<Inventory> created = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            nextTick.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        // Paper's real ItemStack constructors need a running server/registry.
        // Mock only those construction calls; GUI behavior stays under test.
        try (MockedConstruction<ItemStack> stacks = mockConstruction(ItemStack.class, (stack, context) -> {
                 if (context.arguments().get(0) instanceof Material material) when(stack.getType()).thenReturn(material);
                 when(stack.clone()).thenReturn(stack);
                 when(stack.getMaxStackSize()).thenReturn(64);
             });
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), anyString()))
                    .thenAnswer(call -> {
                        Inventory inventory = mock(Inventory.class);
                        when(inventory.getSize()).thenReturn(call.getArgument(1));
                        when(inventory.getHolder()).thenReturn(call.getArgument(0));
                        created.add(inventory);
                        return inventory;
                    });

            PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
            when(interact.getHand()).thenReturn(EquipmentSlot.HAND);
            when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
            when(interact.getItem()).thenReturn(icon);
            when(interact.getPlayer()).thenReturn(player);
            book.onBookInteract(interact);
            verify(interact).setCancelled(true);
            assertEquals(1, created.size());
            assertEquals(54, created.get(0).getSize());

            // All clicks in a book view are cancelled, including the player's
            // own inventory: a shift-click cannot insert items into the book.
            InventoryClickEvent bottom = click(currentView.get(), player, 60);
            book.onBookClick(bottom);
            verify(bottom).setCancelled(true);
            assertTrue(nextTick.isEmpty());
            InventoryDragEvent drag = mock(InventoryDragEvent.class);
            when(drag.getView()).thenReturn(currentView.get());
            book.onBookDrag(drag);
            verify(drag).setCancelled(true);

            InventoryClickEvent next = click(currentView.get(), player, 53);
            book.onBookClick(next);
            verify(next).setCancelled(true);
            assertEquals(1, created.size(), "navigation must wait until after InventoryClickEvent");
            nextTick.removeFirst().run();
            assertEquals(2, created.size());
            assertSame(created.get(1), currentView.get().getTopInventory());

            book.onBookClick(click(currentView.get(), player, 10)); // page 2 entry
            nextTick.removeFirst().run();
            assertEquals(3, created.size()); // detail
            book.onBookClick(click(currentView.get(), player, 48)); // back to page 2
            nextTick.removeFirst().run();
            assertEquals(4, created.size());
            book.onBookClick(click(currentView.get(), player, 45)); // page 1
            nextTick.removeFirst().run();
            assertEquals(5, created.size());

            // Reload closes any open book and invalidates pending navigation.
            book.onBookClick(click(currentView.get(), player, 10));
            assertEquals(1, nextTick.size());
            book.closeOpenGuis();
            verify(player).closeInventory();
            nextTick.removeFirst().run();
            assertEquals(5, created.size(), "a stale click must not reopen the book after reload");
        }
    }

    private static InventoryClickEvent click(InventoryView view, Player player, int slot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        return event;
    }

    private static RecipeDefinition recipe(String id) {
        DrinkDefinition drink = new DrinkDefinition(id, "&6" + id, Color.AQUA, 0, 4,
                List.of(), List.of(), "", Map.of(), "");
        return new RecipeDefinition(id, "boiler", List.of(new Ingredient("minecraft:wheat", 2, -1)),
                1, 100, 100, 200, "", id, 0, "", 0, drink, List.of(), Map.of());
    }
}

package dev.moonaticks.betterThanBrewery.drink;

import dev.moonaticks.betterThanBrewery.drunkenness.DrunkennessManager;
import dev.moonaticks.betterThanBrewery.item.DrinkTags;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DrinkListenerTest {
    @Test
    void customNonPotionDrinkReturnsItsOwnEmptyItemIntoTheUsedHand() {
        DrinkService drinks = mock(DrinkService.class);
        DrunkennessManager drunkenness = mock(DrunkennessManager.class);
        DrinkListener listener = new DrinkListener(drinks, drunkenness);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack filled = mock(ItemStack.class), consumed = mock(ItemStack.class), bowl = mock(ItemStack.class);
        when(filled.getType()).thenReturn(Material.BOWL);
        when(filled.getAmount()).thenReturn(1);
        when(filled.clone()).thenReturn(consumed);
        when(drinks.read(filled)).thenReturn(new DrinkTags.Tag("tea", 0, 0, 100, 1, "cup"));
        when(drinks.emptyFor(filled)).thenReturn(bowl);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItem()).thenReturn(filled);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);

        listener.onInteract(event);

        verify(event).setCancelled(true);
        verify(consumed).setAmount(1);
        verify(inventory).setItemInOffHand(bowl);
        verify(drinks).consumeItem(eq(player), eq(consumed), any());
        verify(inventory, never()).setItemInMainHand(any());
    }
}

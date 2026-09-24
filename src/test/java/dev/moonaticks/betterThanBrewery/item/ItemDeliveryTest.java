package dev.moonaticks.betterThanBrewery.item;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class ItemDeliveryTest {
    @Test
    void offhandStackKeepsItsRemainderAndDropsTheReturnedVesselIfInventoryIsFull() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        ItemStack filled = mock(ItemStack.class), remainder = mock(ItemStack.class), empty = mock(ItemStack.class);
        when(filled.getAmount()).thenReturn(3);
        when(filled.clone()).thenReturn(remainder);
        when(empty.getType()).thenReturn(Material.BOWL);
        when(empty.clone()).thenReturn(empty);
        when(inventory.addItem(empty)).thenReturn(new HashMap<>(Map.of(0, empty)));

        ItemDelivery.replaceHand(player, EquipmentSlot.OFF_HAND, filled, empty);
        verify(remainder).setAmount(2);
        verify(inventory).setItemInOffHand(remainder);
        verify(world).dropItemNaturally(location, empty);
        verify(inventory, never()).setItemInMainHand(any());
    }
}

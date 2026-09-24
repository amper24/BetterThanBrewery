package dev.moonaticks.betterThanBrewery.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Exchange exactly one item; never silently discard a return item when the inventory is full. */
public final class ItemDelivery {
    private ItemDelivery() { }

    public static void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        for (ItemStack overflow : player.getInventory().addItem(item.clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    public static void replaceHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack empty) {
        ItemStack replacement = empty == null ? new ItemStack(Material.AIR) : empty;
        if (held.getAmount() > 1) {
            replacement = held.clone();
            replacement.setAmount(held.getAmount() - 1);
            giveOrDrop(player, empty);
        }
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(replacement);
        else player.getInventory().setItemInMainHand(replacement);
    }
}

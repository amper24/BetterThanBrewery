package dev.moonaticks.betterThanBrewery.drink;

import dev.moonaticks.betterThanBrewery.drunkenness.DrunkennessManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class DrinkListener implements Listener {
    private final DrinkService drinks;
    private final DrunkennessManager drunkenness;
    public DrinkListener(DrinkService drinks, DrunkennessManager drunkenness) { this.drinks = drinks; this.drunkenness = drunkenness; }
    @EventHandler public void onConsume(PlayerItemConsumeEvent event) { drinks.consume(event, drunkenness::add); }
    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() != null) return; // block interactions are reserved for station pouring
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        ItemStack item = event.getItem(); if (item == null || item.getType() == Material.POTION) return;
        if (drinks.read(item) == null) return;
        event.setCancelled(true);
        ItemStack consumed = item.clone();
        item.setAmount(item.getAmount() - 1);
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            event.getPlayer().getInventory().setItemInOffHand(item.getAmount() <= 0 ? new ItemStack(Material.AIR) : item);
        } else {
            event.getPlayer().getInventory().setItemInMainHand(item.getAmount() <= 0 ? new ItemStack(Material.AIR) : item);
        }
        drinks.consumeItem(event.getPlayer(), consumed, drunkenness::add);
    }
}

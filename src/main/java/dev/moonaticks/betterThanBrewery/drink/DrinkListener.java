package dev.moonaticks.betterThanBrewery.drink;

import dev.moonaticks.betterThanBrewery.drunkenness.DrunkennessManager;
import dev.moonaticks.betterThanBrewery.item.ItemDelivery;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class DrinkListener implements Listener {
    private final DrinkService drinks;
    private final DrunkennessManager drunkenness;
    public DrinkListener(DrinkService drinks, DrunkennessManager drunkenness) { this.drinks = drinks; this.drunkenness = drunkenness; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) { drinks.consume(event, drunkenness::add); }

    // Paper pre-cancels right-click-air when vanilla would do nothing (e.g. a
    // custom bowl); ignoreCancelled here would make those vessels undrinkable.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() != null) return; // block interactions are reserved for station pouring
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
        ItemStack item = event.getItem();
        if (item == null || drinks.read(item) == null) return;
        if (item.getType() == Material.POTION) return; // finish the vanilla drinking animation; setReplacement returns the correct vessel

        // Non-potion custom items have no reliable vanilla drinking animation.
        // Consume exactly one in the used hand, bypassing their vanilla effects.
        event.setCancelled(true);
        ItemStack consumed = item.clone();
        consumed.setAmount(1);
        ItemDelivery.replaceHand(event.getPlayer(), event.getHand(), item, drinks.emptyFor(item));
        drinks.consumeItem(event.getPlayer(), consumed, drunkenness::add);
    }
}

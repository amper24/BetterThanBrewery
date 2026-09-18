package dev.moonaticks.betterThanBrewery;

import dev.moonaticks.betterThanBrewery.config.Lang;
import dev.moonaticks.betterThanBrewery.recipe.RecipeLoader;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import dev.moonaticks.betterThanBrewery.station.StationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class BetterBreweryCommand implements CommandExecutor, TabCompleter {
    private final BetterThanBrewery plugin;
    private final Lang lang;
    private final RecipeLoader loader;
    private final StationManager stations;
    public BetterBreweryCommand(BetterThanBrewery plugin, Lang lang, RecipeLoader loader, StationManager stations) { this.plugin = plugin; this.lang = lang; this.loader = loader; this.stations = stations; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("betterthanbrewery.admin")) { lang.send(sender, "no-permission"); return true; }
        if (args.length == 0) { lang.send(sender, "unknown-command"); return true; }
        if (args[0].equalsIgnoreCase("reload")) { plugin.reloadPlugin(); lang.send(sender, "reloaded"); return true; }
        if (args[0].equalsIgnoreCase("info")) { RecipeRegistry registry = plugin.recipes(); lang.send(sender, "info", java.util.Map.of("version", plugin.getDescription().getVersion(), "recipes", Integer.toString(registry.size()), "drinks", Integer.toString(registry.drinkCount()))); sender.sendMessage("§7Станций зарегистрировано: §f" + stations.stationCount()); return true; }
        lang.send(sender, "unknown-command"); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return args.length == 1 ? List.of("reload", "info").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of(); }
}

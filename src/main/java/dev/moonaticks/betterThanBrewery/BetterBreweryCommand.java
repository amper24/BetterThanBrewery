package dev.moonaticks.betterThanBrewery;

import dev.moonaticks.betterThanBrewery.config.Lang;
import dev.moonaticks.betterThanBrewery.drunkenness.DrunkennessManager;
import dev.moonaticks.betterThanBrewery.recipe.RecipeRegistry;
import dev.moonaticks.betterThanBrewery.station.StationManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BetterBreweryCommand implements CommandExecutor, TabCompleter {
    private final BetterThanBrewery plugin;
    private final Lang lang;
    private final StationManager stations;
    private final DrunkennessManager drunkenness;
    public BetterBreweryCommand(BetterThanBrewery plugin, Lang lang, StationManager stations, DrunkennessManager drunkenness) {
        this.plugin = plugin; this.lang = lang; this.stations = stations; this.drunkenness = drunkenness;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("betterthanbrewery.admin")) { lang.send(sender, "no-permission"); return true; }
        if (args.length == 0) { lang.send(sender, "unknown-command"); return true; }
        if (args[0].equalsIgnoreCase("reload")) { plugin.reloadPlugin(); lang.send(sender, "reloaded"); return true; }
        if (args[0].equalsIgnoreCase("info")) {
            RecipeRegistry registry = plugin.recipes();
            lang.send(sender, "info", Map.of("version", plugin.getDescription().getVersion(),
                    "recipes", Integer.toString(registry.size()), "drinks", Integer.toString(registry.drinkCount())));
            sender.sendMessage("§7Станций зарегистрировано: §f" + stations.stationCount()); return true;
        }
        if (args[0].equalsIgnoreCase("drunk")) {
            if (args.length != 3) { lang.send(sender, "drunk-usage"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { lang.send(sender, "player-not-online"); return true; }
            try {
                double value = Double.parseDouble(args[2]);
                if (!Double.isFinite(value) || value < 0 || value > plugin.getConfig().getInt("drunkenness.max", 100))
                    throw new NumberFormatException(args[2]);
                drunkenness.set(target, value);
                lang.send(sender, "drunk-set", Map.of("player", target.getName(), "value", Integer.toString((int) value)));
            } catch (NumberFormatException ex) { lang.send(sender, "drunk-usage"); }
            return true;
        }
        lang.send(sender, "unknown-command"); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("betterthanbrewery.admin")) return List.of();
        if (args.length == 1) return List.of("reload", "info", "drunk").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args[0].equalsIgnoreCase("drunk") && args.length == 2) return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args[0].equalsIgnoreCase("drunk") && args.length == 3) return List.of("0", "20", "50", "80", "100").stream()
                .filter(s -> s.startsWith(args[2])).toList();
        return List.of();
    }
}

package dev.toprak.combat.command;

import dev.toprak.combat.CombatPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CombatCommand implements CommandExecutor, TabCompleter {
    private final CombatPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public CombatCommand(CombatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("combat.admin")) {
                sender.sendMessage(mm.deserialize("<red>No permission.</red>"));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(mm.deserialize("<green>TovekCombat configuration reloaded.</green>"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /combat reload");
            return true;
        }

        if (plugin.getCombatManager().isInCombat(player)) {
            double sec = plugin.getCombatManager().getRemainingSeconds(player);
            player.sendMessage(mm.deserialize("<red>You are in combat! Time remaining: <white>"
                    + String.format(Locale.ROOT, "%.1f", sec) + "s</white></red>"));
        } else {
            player.sendMessage(mm.deserialize("<green>You are not in combat.</green>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(2);
        if (sender instanceof Player && "status".startsWith(prefix)) {
            options.add("status");
        }
        if (sender.hasPermission("combat.admin") && "reload".startsWith(prefix)) {
            options.add("reload");
        }
        return options;
    }
}

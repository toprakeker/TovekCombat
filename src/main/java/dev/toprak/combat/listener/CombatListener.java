package dev.toprak.combat.listener;

import dev.toprak.combat.CombatPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class CombatListener implements Listener {
    private final CombatPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public CombatListener(CombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) return;

        plugin.getCombatManager().tag(victim, attacker);
        plugin.getCombatManager().tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player)) return;
        if (player.hasPermission("combat.bypass")) return;

        String raw = event.getMessage().toLowerCase();
        List<String> blocked = plugin.getConfig().getStringList("blocked-commands");
        boolean isBlocked = blocked.stream().anyMatch(cmd -> raw.startsWith(cmd.toLowerCase()));

        if (isBlocked) {
            event.setCancelled(true);
            double sec = plugin.getCombatManager().getRemainingSeconds(player);
            String msg = plugin.getConfig().getString("messages.command-blocked", "")
                    .replace("%time%", String.format("%.1f", sec));
            player.sendMessage(mm.deserialize(msg));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!plugin.getConfig().getBoolean("disable-elytra", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.isGliding() && plugin.getCombatManager().isInCombat(player)) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.elytra-blocked", "")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player)) return;

        if (plugin.getConfig().getBoolean("punish-on-quit", true)) {
            // Kill player so inventory drops naturally
            player.setHealth(0.0);

            if (plugin.getConfig().getBoolean("broadcast-punishment", true)) {
                String bcast = plugin.getConfig().getString("messages.quit-broadcast", "")
                        .replace("%player%", player.getName());
                Bukkit.broadcast(mm.deserialize(bcast));
            }
        }

        plugin.getCombatManager().untag(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getCombatManager().untag(event.getEntity());
    }
}

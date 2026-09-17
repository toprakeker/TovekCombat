package dev.toprak.combat.listener;

import dev.toprak.combat.CombatPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CombatListener implements Listener {
    private final CombatPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Effect keys considered offensive enough to start a combat tag.
    private static final Set<String> HARMFUL_EFFECTS = Set.of(
            "POISON",
            "HARM",
            "SLOWNESS",
            "WEAKNESS",
            "WITHER",
            "BLINDNESS",
            "CONFUSION"
    );

    public CombatListener(CombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        if (attacker.hasPermission("combat.bypass") && victim.hasPermission("combat.bypass")) return;

        plugin.getCombatManager().tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!(shooter instanceof Player attacker)) return;

        boolean isHarmful = false;
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (HARMFUL_EFFECTS.contains(effect.getType().getName().toUpperCase(Locale.ROOT))) {
                isHarmful = true;
                break;
            }
        }
        if (!isHarmful) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && !victim.equals(attacker)) {
                plugin.getCombatManager().tag(attacker, victim);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        ProjectileSource source = cloud.getSource();
        if (!(source instanceof Player attacker)) return;

        boolean isHarmful = false;
        if (cloud.getBasePotionType() != null && HARMFUL_EFFECTS.contains(cloud.getBasePotionType().name().toUpperCase(Locale.ROOT))) {
            isHarmful = true;
        } else {
            for (PotionEffect effect : cloud.getCustomEffects()) {
                if (HARMFUL_EFFECTS.contains(effect.getType().getName().toUpperCase(Locale.ROOT))) {
                    isHarmful = true;
                    break;
                }
            }
        }
        if (!isHarmful) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && !victim.equals(attacker)) {
                plugin.getCombatManager().tag(attacker, victim);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player) || player.hasPermission("combat.bypass")) return;

        // Split on whitespace to get the root command e.g. "/spawn player" -> "/spawn"
        String fullCmd = event.getMessage().toLowerCase(Locale.ROOT).trim();
        String[] parts = fullCmd.split("\\s+");
        if (parts.length == 0) return;

        String commandRoot = parts[0];

        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        for (String allowed : allowedCommands) {
            String allowedLower = allowed.toLowerCase(Locale.ROOT).trim();
            // Prefix the config value with a slash if the operator forgot it
            if (!allowedLower.startsWith("/")) allowedLower = "/" + allowedLower;

            if (commandRoot.equals(allowedLower)) return;
        }

        event.setCancelled(true);
        String msg = plugin.getConfig().getString("messages.command-blocked", "");
        if (!msg.isBlank()) {
            player.sendMessage(mm.deserialize(msg));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getCombatManager().isInCombat(player)) return;

        // Punish combat loggers
        player.setHealth(0);
        plugin.getCombatManager().untag(player);
        
        String msg = plugin.getConfig().getString("messages.combat-log-broadcast", "");
        if (!msg.isBlank()) {
            plugin.getServer().broadcast(mm.deserialize(msg.replace("%player%", player.getName())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getCombatManager().untag(event.getEntity());
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}

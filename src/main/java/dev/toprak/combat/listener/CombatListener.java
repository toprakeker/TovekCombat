package dev.toprak.combat.listener;

import dev.toprak.combat.CombatPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.Set;

public class CombatListener implements Listener {
    private final CombatPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Effect keys considered offensive enough to start a combat tag.
    private static final Set<String> HARMFUL_EFFECTS = Set.of(
            "harm", "instant_damage", "poison", "wither", "slow", "slowness",
            "weakness", "blindness", "confusion", "nausea", "hunger", "levitation",
            "unluck", "darkness", "slow_digging", "mining_fatigue", "bad_omen",
            "infested", "oozing", "weaving", "wind_charged", "trial_omen");

    public CombatListener(CombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        plugin.getCombatManager().tag(victim, attacker);
        plugin.getCombatManager().tag(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower)) return;
        if (!hasHarmfulEffect(potion.getEffects())) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player victim)) continue;
            if (victim.getUniqueId().equals(thrower.getUniqueId())) continue;
            if (event.getIntensity(affected) <= 0.0) continue;
            plugin.getCombatManager().tag(victim, thrower);
            plugin.getCombatManager().tag(thrower, victim);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLingeringPotion(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        ProjectileSource source = cloud.getSource();
        if (!(source instanceof Player thrower)) return;

        boolean harmful = hasHarmfulEffect(cloud.getCustomEffects());
        if (!harmful && cloud.getBasePotionType() != null) {
            harmful = hasHarmfulEffect(cloud.getBasePotionType().getPotionEffects());
        }
        if (!harmful) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (!(affected instanceof Player victim)) continue;
            if (victim.getUniqueId().equals(thrower.getUniqueId())) continue;
            plugin.getCombatManager().tag(victim, thrower);
            plugin.getCombatManager().tag(thrower, victim);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player)) return;
        if (player.hasPermission("combat.bypass")) return;

        String root = commandRoot(event.getMessage());
        if (root.isEmpty()) return;

        boolean isBlocked = plugin.getConfig().getStringList("blocked-commands").stream()
                .map(CombatListener::normalizeBlockedEntry)
                .anyMatch(root::equals);

        if (isBlocked) {
            event.setCancelled(true);
            double sec = plugin.getCombatManager().getRemainingSeconds(player);
            String msg = plugin.getConfig().getString("messages.command-blocked", "");
            if (msg != null && !msg.isBlank()) {
                player.sendMessage(mm.deserialize(msg.replace("%time%", String.format(Locale.ROOT, "%.1f", sec))));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!plugin.getConfig().getBoolean("disable-elytra", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.isGliding() && plugin.getCombatManager().isInCombat(player) && !player.hasPermission("combat.bypass")) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString("messages.elytra-blocked", "");
            if (msg != null && !msg.isBlank()) {
                player.sendMessage(mm.deserialize(msg));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCombatManager().isInCombat(player)) return;

        // Clear state first so the forced death does not double-process.
        plugin.getCombatManager().untagSilent(player);

        if (!plugin.getConfig().getBoolean("punish-on-quit", true)) return;
        if (player.isDead()) return;

        // Creative/spectator players are immune to damage-based kills, so normalize
        // the game mode before forcing the death; setHealth also bypasses totems.
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setHealth(0.0);

        if (plugin.getConfig().getBoolean("broadcast-punishment", true)) {
            String bcast = plugin.getConfig().getString("messages.quit-broadcast", "");
            if (bcast != null && !bcast.isBlank()) {
                Bukkit.broadcast(mm.deserialize(bcast.replace("%player%", player.getName())));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getCombatManager().untagSilent(event.getEntity());
    }

    private static Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p && p.isOnline()) {
            return p;
        }
        return null;
    }

    private boolean hasHarmfulEffect(Iterable<PotionEffect> effects) {
        if (effects == null) return false;
        for (PotionEffect effect : effects) {
            if (HARMFUL_EFFECTS.contains(effect.getType().getKey().getKey().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Extracts the bare command name: strips leading slash, arguments and any namespace prefix. */
    private static String commandRoot(String message) {
        String s = message.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        if (space != -1) {
            s = s.substring(0, space);
        }
        int colon = s.indexOf(':');
        if (colon != -1) {
            s = s.substring(colon + 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static String normalizeBlockedEntry(String entry) {
        String s = entry.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int colon = s.indexOf(':');
        if (colon != -1) {
            s = s.substring(colon + 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }
}

package dev.toprak.combat.manager;

import dev.toprak.combat.CombatPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {
    private final CombatPlugin plugin;
    private final Map<UUID, Long> combatMap = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastOpponent = new ConcurrentHashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private BukkitTask actionbarTask;

    public CombatManager(CombatPlugin plugin) {
        this.plugin = plugin;
        startActionbarTicker();
    }

    public void tag(Player player, Player opponent) {
        if (player == null || !player.isOnline()) return;
        if (player.hasPermission("combat.bypass")) return;

        int seconds = Math.max(1, plugin.getConfig().getInt("combat-duration-seconds", 15));
        long durationMs = seconds * 1000L;
        boolean wasTagged = isInCombat(player);

        combatMap.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        if (opponent != null && !opponent.getUniqueId().equals(player.getUniqueId())) {
            lastOpponent.put(player.getUniqueId(), opponent.getUniqueId());
        }

        // Stop any active elytra glide so tagging can't be evaded mid-flight.
        if (player.isGliding() && plugin.getConfig().getBoolean("disable-elytra", true)) {
            player.setGliding(false);
        }

        if (!wasTagged) {
            String msg = plugin.getConfig().getString("messages.tagged", "");
            if (msg != null && !msg.isBlank()) {
                player.sendMessage(mm.deserialize(
                        msg.replace("%opponent%", opponent != null ? opponent.getName() : "an enemy")));
            }
        }
    }

    public boolean isInCombat(Player player) {
        if (player == null) return false;
        Long expire = combatMap.get(player.getUniqueId());
        if (expire == null) return false;
        if (System.currentTimeMillis() > expire) {
            combatMap.remove(player.getUniqueId());
            lastOpponent.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public double getRemainingSeconds(Player player) {
        Long expire = combatMap.get(player.getUniqueId());
        if (expire == null) return 0.0;
        long diff = expire - System.currentTimeMillis();
        return Math.max(0.0, diff / 1000.0);
    }

    public void untag(Player player) {
        if (player == null) return;
        if (combatMap.remove(player.getUniqueId()) != null) {
            lastOpponent.remove(player.getUniqueId());
            String msg = plugin.getConfig().getString("messages.untagged", "");
            if (player.isOnline() && msg != null && !msg.isBlank()) {
                player.sendMessage(mm.deserialize(msg));
            }
        }
    }

    /** Removes combat state without notifying the player (used on quit/death punishment). */
    public void untagSilent(Player player) {
        if (player == null) return;
        combatMap.remove(player.getUniqueId());
        lastOpponent.remove(player.getUniqueId());
    }

    public Player getLastOpponent(Player player) {
        UUID oppId = lastOpponent.get(player.getUniqueId());
        return oppId != null ? Bukkit.getPlayer(oppId) : null;
    }

    private void startActionbarTicker() {
        this.actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (combatMap.isEmpty()) return;
            long now = System.currentTimeMillis();
            String untaggedMsg = plugin.getConfig().getString("messages.untagged", "");
            Component untaggedComponent = (untaggedMsg != null && !untaggedMsg.isBlank())
                    ? mm.deserialize(untaggedMsg) : null;

            Iterator<Map.Entry<UUID, Long>> it = combatMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID id = entry.getKey();
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) {
                    it.remove();
                    lastOpponent.remove(id);
                    continue;
                }
                long diff = entry.getValue() - now;
                if (diff <= 0) {
                    it.remove();
                    lastOpponent.remove(id);
                    if (untaggedComponent != null) {
                        p.sendMessage(untaggedComponent);
                    }
                } else {
                    double sec = Math.round(diff / 100.0) / 10.0;
                    p.sendActionBar(Component.textOfChildren(
                            Component.text("⚔ COMBAT", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                            Component.text(" | ", NamedTextColor.DARK_GRAY),
                            Component.text(sec + "s", NamedTextColor.WHITE)));
                }
            }
        }, 10L, 10L);
    }

    public void shutdown() {
        if (actionbarTask != null && !actionbarTask.isCancelled()) {
            actionbarTask.cancel();
        }
        actionbarTask = null;
        combatMap.clear();
        lastOpponent.clear();
    }
}

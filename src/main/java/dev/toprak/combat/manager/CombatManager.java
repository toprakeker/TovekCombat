package dev.toprak.combat.manager;

import dev.toprak.combat.CombatPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
        long durationMs = plugin.getConfig().getInt("combat-duration-seconds", 15) * 1000L;
        boolean wasTagged = isInCombat(player);

        combatMap.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        if (opponent != null) {
            lastOpponent.put(player.getUniqueId(), opponent.getUniqueId());
        }

        if (!wasTagged) {
            String msg = plugin.getConfig().getString("messages.tagged", "")
                    .replace("%opponent%", opponent != null ? opponent.getName() : "an enemy");
            player.sendMessage(mm.deserialize(msg));
        }
    }

    public boolean isInCombat(Player player) {
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
        if (combatMap.remove(player.getUniqueId()) != null) {
            lastOpponent.remove(player.getUniqueId());
            player.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.untagged", "")));
        }
    }

    public Player getLastOpponent(Player player) {
        UUID oppId = lastOpponent.get(player.getUniqueId());
        return oppId != null ? Bukkit.getPlayer(oppId) : null;
    }

    private void startActionbarTicker() {
        this.actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : combatMap.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) {
                    combatMap.remove(entry.getKey());
                    lastOpponent.remove(entry.getKey());
                    continue;
                }
                long diff = entry.getValue() - now;
                if (diff <= 0) {
                    combatMap.remove(entry.getKey());
                    lastOpponent.remove(entry.getKey());
                    p.sendMessage(mm.deserialize(plugin.getConfig().getString("messages.untagged", "")));
                } else {
                    double sec = Math.round((diff / 100.0)) / 10.0;
                    p.sendActionBar(mm.deserialize("<red><bold>⚔ COMBAT</bold> <dark_gray>|</dark_gray> <white>" + sec + "s</white></red>"));
                }
            }
        }, 10L, 10L);
    }

    public void shutdown() {
        if (actionbarTask != null && !actionbarTask.isCancelled()) {
            actionbarTask.cancel();
        }
        combatMap.clear();
        lastOpponent.clear();
    }
}

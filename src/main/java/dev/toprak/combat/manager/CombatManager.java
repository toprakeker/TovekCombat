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
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final Component ACTIONBAR_PREFIX = Component.textOfChildren(
            Component.text("⚔ COMBAT", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text(" | ", NamedTextColor.DARK_GRAY));

    private final CombatPlugin plugin;
    /**
     * Player UUID -> expiry timestamp in {@link System#nanoTime()} units. A monotonic clock is used so
     * wall-clock adjustments (NTP, DST) can neither extend nor cut a tag short. Always compare with
     * subtraction ({@code expire - now > 0}), never with {@code <}/{@code >} directly.
     */
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
        if (player.hasMetadata("NPC")) return; // Citizens & co. expose NPCs as Player instances.

        int seconds = Math.max(1, plugin.getConfig().getInt("combat-duration-seconds", 15));
        boolean wasTagged = isInCombat(player);

        combatMap.put(player.getUniqueId(), System.nanoTime() + seconds * NANOS_PER_SECOND);
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
                // Names are inserted into a MiniMessage template; escape so no name can inject tags.
                String opponentName = opponent != null ? mm.escapeTags(opponent.getName()) : "an enemy";
                player.sendMessage(mm.deserialize(msg.replace("%opponent%", opponentName)));
            }
        }
    }

    /**
     * Read-only check. Expired entries are left in place for the ticker to remove so the
     * "no longer in combat" notification is delivered exactly once regardless of who observed
     * the expiry first.
     */
    public boolean isInCombat(Player player) {
        if (player == null) return false;
        Long expire = combatMap.get(player.getUniqueId());
        return expire != null && expire - System.nanoTime() > 0;
    }

    public double getRemainingSeconds(Player player) {
        if (player == null) return 0.0;
        Long expire = combatMap.get(player.getUniqueId());
        if (expire == null) return 0.0;
        long diff = expire - System.nanoTime();
        return diff > 0 ? diff / (double) NANOS_PER_SECOND : 0.0;
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
        if (player == null) return null;
        UUID oppId = lastOpponent.get(player.getUniqueId());
        return oppId != null ? Bukkit.getPlayer(oppId) : null;
    }

    private void startActionbarTicker() {
        this.actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (combatMap.isEmpty()) return;
            long now = System.nanoTime();
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
                    double sec = Math.round(diff / 100_000_000.0) / 10.0;
                    p.sendActionBar(ACTIONBAR_PREFIX.append(Component.text(sec + "s", NamedTextColor.WHITE)));
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

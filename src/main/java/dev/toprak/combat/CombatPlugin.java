package dev.toprak.combat;

import dev.toprak.combat.command.CombatCommand;
import dev.toprak.combat.listener.CombatListener;
import dev.toprak.combat.manager.CombatManager;
import dev.toprak.combat.util.CommandUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public final class CombatPlugin extends JavaPlugin {
    private static CombatPlugin instance;
    private CombatManager combatManager;

    /** Normalised blocked-command roots, rebuilt on every config (re)load so the hot path never re-parses the list. */
    private volatile Set<String> blockedCommands = Set.of();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        this.combatManager = new CombatManager(this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        PluginCommand cmd = getCommand("combat");
        if (cmd != null) {
            CombatCommand executor = new CombatCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getLogger().info("TovekCombat v" + getPluginMeta().getVersion() + " loaded.");
    }

    @Override
    public void onDisable() {
        if (combatManager != null) {
            combatManager.shutdown();
            combatManager = null;
        }
        instance = null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        this.blockedCommands = CommandUtil.normalizeBlockedEntries(getConfig().getStringList("blocked-commands"));
    }

    public static CombatPlugin getInstance() {
        return instance;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    /** Unmodifiable, lower-cased, namespace-free command roots that are blocked while tagged. */
    public Set<String> getBlockedCommands() {
        return blockedCommands;
    }
}

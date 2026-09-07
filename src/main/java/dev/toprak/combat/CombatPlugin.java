package dev.toprak.combat;

import dev.toprak.combat.command.CombatCommand;
import dev.toprak.combat.listener.CombatListener;
import dev.toprak.combat.manager.CombatManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatPlugin extends JavaPlugin {
    private static CombatPlugin instance;
    private CombatManager combatManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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

    public static CombatPlugin getInstance() {
        return instance;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }
}

package com.wilder0p.arenamanager;

import com.wilder0p.arenamanager.commands.ArenaCommand;
import com.wilder0p.arenamanager.commands.ArenaEditCommand;
import com.wilder0p.arenamanager.listeners.PlayerListener;
import com.wilder0p.arenamanager.managers.ArenaInstanceManager;
import com.wilder0p.arenamanager.managers.BuilderSessionManager;
import com.wilder0p.arenamanager.managers.ConfigManager;
import com.wilder0p.arenamanager.managers.PlaytimeManager;
import com.wilder0p.arenamanager.managers.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArenaManager extends JavaPlugin {

    private static ArenaManager instance;
    private PlaytimeManager playtimeManager;
    private BuilderSessionManager builderSessionManager;

    public static ArenaManager get() {
        return instance;
    }

    public PlaytimeManager playtime() {
        return playtimeManager;
    }

    public BuilderSessionManager builders() {
        return builderSessionManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        new ConfigManager();
        playtimeManager = new PlaytimeManager();
        builderSessionManager = new BuilderSessionManager();

        WorldManager.purgeLeftoverInstances();
        WorldManager.loadAllTemplates();

        ArenaCommand arenaCommand = new ArenaCommand();
        var arena = getCommand("arena");
        if (arena != null) {
            arena.setExecutor(arenaCommand);
            arena.setTabCompleter(arenaCommand);
        }

        ArenaEditCommand editCommand = new ArenaEditCommand();
        var edit = getCommand("arenaedit");
        if (edit != null) {
            edit.setExecutor(editCommand);
            edit.setTabCompleter(editCommand);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);

        World lobby = ConfigManager.lobbyWorld();
        if (lobby != null) {
            lobby.setPVP(false);
        }

        getLogger().info("[Arena] Broken Arena Manager enabled. Lobby: "
                + ConfigManager.lobbyWorldName()
                + " | build gate: " + ConfigManager.minSurvivalHours() + "h survival");
    }

    @Override
    public void onDisable() {
        ArenaInstanceManager.shutdown();
        getLogger().info("[Arena] Plugin disabled.");
    }
}

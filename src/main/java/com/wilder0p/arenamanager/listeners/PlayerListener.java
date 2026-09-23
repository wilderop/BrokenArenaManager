package com.wilder0p.arenamanager.listeners;

import com.wilder0p.arenamanager.ArenaManager;
import com.wilder0p.arenamanager.data.ActiveInstance;
import com.wilder0p.arenamanager.data.TemplateArena;
import com.wilder0p.arenamanager.managers.ArenaInstanceManager;
import com.wilder0p.arenamanager.managers.ConfigManager;
import com.wilder0p.arenamanager.managers.WorldManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        World lobby = ConfigManager.lobbyWorld();
        if (lobby == null || !to.getWorld().equals(lobby)) {
            return;
        }
        if (ArenaInstanceManager.isPending(player) || ArenaInstanceManager.getInstancePlayerIsIn(player) != null) {
            return;
        }

        for (String name : WorldManager.getAllTemplateNames()) {
            TemplateArena arena = WorldManager.getTemplate(name);
            if (!arena.isOpen || !arena.hasPad()) {
                continue;
            }
            boolean wasInside = arena.containsPad(from);
            boolean nowInside = arena.containsPad(to);
            if (!wasInside && nowInside) {
                ArenaInstanceManager.handleJoin(player, name);
                return;
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ActiveInstance instance = ArenaInstanceManager.getInstancePlayerIsIn(player);
        if (instance == null) {
            return;
        }
        if (ConfigManager.clearDropsOnDeath()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        ActiveInstance instance = ArenaInstanceManager.getInstancePlayerIsIn(player);
        if (instance == null) {
            return;
        }
        event.setRespawnLocation(ConfigManager.lobbySpawn());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World from = event.getFrom();
        World to = player.getWorld();

        ActiveInstance fromInstance = ArenaInstanceManager.getByWorld(from);
        if (fromInstance != null && (to == null || !fromInstance.world.equals(to))) {
            fromInstance.removePlayer(player, false);
        }

        ArenaManager.get().builders().handleWorldChange(player, from, to);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ActiveInstance instance = ArenaInstanceManager.getInstancePlayerIsIn(player);
        if (instance != null) {
            instance.removePlayer(player, false);
        }
        ArenaManager.get().builders().clear(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (ArenaManager.get().builders().canEditWorld(player, player.getWorld())) {
            ArenaManager.get().builders().applyBuilderMode(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("arenamanager.edit")) {
            return;
        }
        if (event.getNewGameMode() != GameMode.CREATIVE) {
            return;
        }
        if (!ArenaManager.get().builders().canEditWorld(player, player.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(ConfigManager.msg("creative_denied"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("arenamanager.edit")) {
            return;
        }
        if (!ArenaManager.get().builders().isWorldEditCommand(event.getMessage())) {
            return;
        }
        if (!ArenaManager.get().builders().canEditWorld(player, player.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(ConfigManager.msg("we_denied"));
        }
    }
}

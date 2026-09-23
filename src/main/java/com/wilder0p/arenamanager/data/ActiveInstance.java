package com.wilder0p.arenamanager.data;

import com.wilder0p.arenamanager.ArenaManager;
import com.wilder0p.arenamanager.managers.ArenaInstanceManager;
import com.wilder0p.arenamanager.managers.ConfigManager;
import com.wilder0p.arenamanager.managers.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ActiveInstance {

    public final String templateName;
    public final String instanceName;
    public final World world;
    public final UUID creator;
    public boolean locked;
    private final Set<UUID> players = new HashSet<>();
    private final Map<UUID, PlayerState> snapshots = new HashMap<>();
    private final BossBar lockBar;

    public ActiveInstance(String template, UUID creator, World world, String instanceName) {
        this.templateName = template;
        this.instanceName = instanceName;
        this.world = world;
        this.creator = creator;
        this.lockBar = Bukkit.createBossBar("§6🔒 LOCKED ARENA", BarColor.RED, BarStyle.SOLID);
    }

    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    public void addPlayer(Player player) {
        if (players.contains(player.getUniqueId())) {
            return;
        }

        snapshots.put(player.getUniqueId(), PlayerState.capture(player));
        players.add(player.getUniqueId());

        TemplateArena template = WorldManager.getTemplate(templateName);
        Location dest = template.spawnIn(world);
        double radius = template.spawnRadius > 0 ? template.spawnRadius : 3.0;
        dest.add((Math.random() * radius * 2) - radius, 0, (Math.random() * radius * 2) - radius);

        player.teleport(dest);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        var maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        template.applyKit(player);

        if (locked) {
            lockBar.addPlayer(player);
        }
    }

    public void removePlayer(Player player, boolean teleportToLobby) {
        if (!players.remove(player.getUniqueId())) {
            return;
        }

        lockBar.removePlayer(player);
        PlayerState state = snapshots.remove(player.getUniqueId());
        if (state != null) {
            state.restore(player);
        } else {
            player.getInventory().clear();
            ArenaManager.get().builders().applyLobbyMode(player);
        }

        if (teleportToLobby) {
            player.teleport(ConfigManager.lobbySpawn());
        }

        if (players.isEmpty()) {
            destroy();
        }
    }

    public void lock(Player who) {
        locked = true;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                lockBar.addPlayer(player);
            }
        }
        who.sendMessage(ConfigManager.msg("arena_locked"));
    }

    public void unlock(Player who) {
        locked = false;
        lockBar.removeAll();
        who.sendMessage(ConfigManager.msg("arena_unlocked"));
    }

    public void destroy() {
        Location lobby = ConfigManager.lobbySpawn();
        for (UUID uuid : Set.copyOf(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                PlayerState state = snapshots.remove(uuid);
                if (state != null) {
                    state.restore(player);
                }
                if (player.getWorld().equals(world)) {
                    player.teleport(lobby);
                }
            }
        }
        players.clear();
        snapshots.clear();
        lockBar.removeAll();

        File folder = world.getWorldFolder();
        String worldName = world.getName();
        boolean unloaded = Bukkit.unloadWorld(world, false);
        ArenaInstanceManager.getActive().remove(instanceName);
        if (!unloaded) {
            ArenaManager.get().getLogger().warning("[Arena] unloadWorld failed for " + worldName + "; retrying delete next tick");
        }
        // Deleting the folder in the same tick leaves Paper saving a ghost world.
        Bukkit.getScheduler().runTaskLater(ArenaManager.get(), () -> {
            World still = Bukkit.getWorld(worldName);
            if (still != null) {
                Bukkit.unloadWorld(still, false);
            }
            WorldManager.deleteRecursive(folder);
            ArenaManager.get().getLogger().info("[Arena] Destroyed instance " + worldName);
        }, 20L);
    }

    public int getPlayerCount() {
        return players.size();
    }
}

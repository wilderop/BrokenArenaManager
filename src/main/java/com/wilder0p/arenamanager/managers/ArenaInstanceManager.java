package com.wilder0p.arenamanager.managers;

import com.wilder0p.arenamanager.ArenaManager;
import com.wilder0p.arenamanager.data.ActiveInstance;
import com.wilder0p.arenamanager.data.TemplateArena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ArenaInstanceManager {

    private static final Map<String, ActiveInstance> activeInstances = new LinkedHashMap<>();
    private static final Map<String, AtomicInteger> instanceCounters = new ConcurrentHashMap<>();
    private static final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    public static Map<String, ActiveInstance> getActive() {
        return activeInstances;
    }

    public static void handleJoin(Player player, String templateName) {
        String name = templateName.toLowerCase();
        if (pending.contains(player.getUniqueId())) {
            return;
        }
        if (getInstancePlayerIsIn(player) != null) {
            player.sendMessage(ConfigManager.msg("already_in_arena"));
            return;
        }
        if (isCoolingDown(player)) {
            return;
        }
        if (!WorldManager.templateExists(name)) {
            player.sendMessage(ConfigManager.msg("arena_missing", "%world%", name));
            return;
        }

        TemplateArena template = WorldManager.getTemplate(name);
        if (!template.isOpen) {
            player.sendMessage(ConfigManager.msg("arena_closed_join"));
            return;
        }

        ActiveInstance existing = findMostRecentUnlocked(name);
        if (existing != null) {
            existing.addPlayer(player);
            markCooldown(player);
            player.sendMessage(ConfigManager.msg("joined_existing",
                    "%world%", name,
                    "%players%", String.valueOf(existing.getPlayerCount())));
            return;
        }

        if (activeInstances.size() >= ConfigManager.maxInstances()) {
            player.sendMessage(ConfigManager.msg("instance_limit_reached"));
            return;
        }

        createNewInstance(player, name);
    }

    public static void handleLeave(Player player) {
        ActiveInstance instance = getInstancePlayerIsIn(player);
        if (instance != null) {
            instance.removePlayer(player, true);
            player.sendMessage(ConfigManager.msg("left_arena"));
            return;
        }
        if (WorldManager.isTemplateWorld(player.getWorld().getName())) {
            ArenaManager.get().builders().applyLobbyMode(player);
            player.teleport(ConfigManager.lobbySpawn());
            player.sendMessage(ConfigManager.msg("left_arena"));
            return;
        }
        player.teleport(ConfigManager.lobbySpawn());
        player.sendMessage(ConfigManager.msg("left_arena"));
    }

    private static ActiveInstance findMostRecentUnlocked(String templateName) {
        ActiveInstance mostRecent = null;
        for (ActiveInstance instance : activeInstances.values()) {
            if (instance.templateName.equals(templateName) && !instance.locked) {
                mostRecent = instance;
            }
        }
        return mostRecent;
    }

    private static void createNewInstance(Player creator, String templateName) {
        World templateWorld = WorldManager.loadTemplateWorld(templateName);
        if (templateWorld == null) {
            creator.sendMessage(ConfigManager.msg("arena_missing_world", "%world%", templateName));
            return;
        }

        pending.add(creator.getUniqueId());
        creator.sendMessage(ConfigManager.msg("creating_instance", "%world%", templateName));
        templateWorld.save();

        int count = instanceCounters.computeIfAbsent(templateName, k -> new AtomicInteger(0)).incrementAndGet();
        String instanceName = "inst_" + templateName + "_" + count;
        File dest = new File(Bukkit.getWorldContainer(), instanceName);
        File source = templateWorld.getWorldFolder();
        UUID creatorId = creator.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(ArenaManager.get(), () -> {
            try {
                WorldManager.copyWorldFolder(source, dest);
                Bukkit.getScheduler().runTask(ArenaManager.get(), () -> finishCreate(creatorId, templateName, instanceName));
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(ArenaManager.get(), () -> {
                    pending.remove(creatorId);
                    WorldManager.deleteRecursive(dest);
                    Player player = Bukkit.getPlayer(creatorId);
                    if (player != null) {
                        player.sendMessage(ConfigManager.msg("copy_failed"));
                    }
                });
            }
        });
    }

    private static void finishCreate(UUID creatorId, String templateName, String instanceName) {
        pending.remove(creatorId);
        World instanceWorld = WorldManager.loadWorld(instanceName);
        if (instanceWorld == null) {
            WorldManager.deleteRecursive(new File(Bukkit.getWorldContainer(), instanceName));
            Player player = Bukkit.getPlayer(creatorId);
            if (player != null) {
                player.sendMessage(ConfigManager.msg("copy_failed"));
            }
            return;
        }

        WorldManager.applyInstanceRules(instanceWorld);
        TemplateArena template = WorldManager.getTemplate(templateName);
        Location spawn = template.spawnIn(instanceWorld);
        instanceWorld.setSpawnLocation(spawn);

        ActiveInstance instance = new ActiveInstance(templateName, creatorId, instanceWorld, instanceName);
        activeInstances.put(instanceName, instance);

        Player creator = Bukkit.getPlayer(creatorId);
        if (creator == null || !creator.isOnline()) {
            instance.destroy();
            return;
        }

        instance.addPlayer(creator);
        markCooldown(creator);
        creator.sendMessage(ConfigManager.msg("joined_new", "%world%", templateName));
        ArenaManager.get().getLogger().info("[Arena] New instance " + instanceName + " by " + creator.getName());
    }

    public static ActiveInstance getInstancePlayerIsIn(Player player) {
        for (ActiveInstance instance : activeInstances.values()) {
            if (instance.hasPlayer(player.getUniqueId()) || instance.world.getPlayers().contains(player)) {
                return instance;
            }
        }
        return null;
    }

    public static ActiveInstance getByWorld(World world) {
        if (world == null) {
            return null;
        }
        for (ActiveInstance instance : activeInstances.values()) {
            if (instance.world.equals(world)) {
                return instance;
            }
        }
        return null;
    }

    public static void shutdown() {
        for (ActiveInstance instance : Set.copyOf(activeInstances.values())) {
            instance.destroy();
        }
        activeInstances.clear();
        pending.clear();
    }

    public static boolean isPending(Player player) {
        return pending.contains(player.getUniqueId());
    }

    private static boolean isCoolingDown(Player player) {
        Long until = cooldownUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private static void markCooldown(Player player) {
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + ConfigManager.joinCooldownSeconds() * 1000L);
    }
}

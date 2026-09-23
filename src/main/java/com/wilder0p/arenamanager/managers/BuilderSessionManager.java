package com.wilder0p.arenamanager.managers;

import com.wilder0p.arenamanager.ArenaManager;
import com.wilder0p.arenamanager.data.TemplateArena;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BuilderSessionManager {

    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public boolean canEdit(Player player, TemplateArena arena) {
        if (player.hasPermission("arenamanager.edit")) {
            return true;
        }
        return arena.isOwner(player.getUniqueId()) && ArenaManager.get().playtime().canBuild(player);
    }

    public boolean canEditWorld(Player player, World world) {
        if (world == null || !WorldManager.isTemplateWorld(world.getName())) {
            return false;
        }
        String arenaName = WorldManager.arenaFromTemplateWorld(world.getName());
        if (arenaName == null || !WorldManager.templateExists(arenaName)) {
            return player.hasPermission("arenamanager.edit");
        }
        return canEdit(player, WorldManager.getTemplate(arenaName));
    }

    public void enterTemplate(Player player, TemplateArena arena) {
        World world = WorldManager.loadTemplateWorld(arena.name);
        if (world == null) {
            player.sendMessage(ConfigManager.msg("arena_missing_world", "%world%", arena.name));
            return;
        }
        player.teleport(arena.spawnIn(world));
        applyBuilderMode(player);
        player.sendMessage(ConfigManager.msg("world_teleported", "%world%", arena.name));
        player.sendMessage(ConfigManager.msg("builder_tools"));
    }

    public void applyBuilderMode(Player player) {
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        grantWorldEdit(player);
    }

    public void applyLobbyMode(Player player) {
        revokeWorldEdit(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    public void handleWorldChange(Player player, World from, World to) {
        if (canEditWorld(player, to)) {
            applyBuilderMode(player);
            return;
        }
        revokeWorldEdit(player);
        if (to != null && to.getName().equals(ConfigManager.lobbyWorldName())) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    public void grantWorldEdit(Player player) {
        revokeWorldEdit(player);
        PermissionAttachment attachment = player.addAttachment(ArenaManager.get());
        List<String> nodes = ConfigManager.worldEditPermissions();
        if (nodes.isEmpty()) {
            attachment.setPermission("worldedit.wand", true);
            attachment.setPermission("worldedit.region.set", true);
            attachment.setPermission("worldedit.clipboard.copy", true);
            attachment.setPermission("worldedit.clipboard.cut", true);
            attachment.setPermission("worldedit.clipboard.paste", true);
            attachment.setPermission("worldedit.history.undo", true);
            attachment.setPermission("worldedit.history.redo", true);
        } else {
            for (String node : nodes) {
                attachment.setPermission(node, true);
            }
        }
        attachments.put(player.getUniqueId(), attachment);
        player.recalculatePermissions();
        player.updateCommands();
    }

    public void revokeWorldEdit(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            player.removeAttachment(attachment);
            player.recalculatePermissions();
            player.updateCommands();
        }
    }

    public void clear(Player player) {
        revokeWorldEdit(player);
    }

    public boolean isWorldEditCommand(String message) {
        String cmd = message.toLowerCase(java.util.Locale.ROOT);
        if (cmd.startsWith("//") || cmd.startsWith("/worldedit") || cmd.startsWith("/fawe")) {
            return true;
        }
        return cmd.equals("/we") || cmd.startsWith("/we ") || cmd.startsWith("/brush") || cmd.startsWith("/tool ");
    }
}

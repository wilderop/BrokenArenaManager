package com.wilder0p.arenamanager.commands;

import com.wilder0p.arenamanager.data.ActiveInstance;
import com.wilder0p.arenamanager.data.TemplateArena;
import com.wilder0p.arenamanager.managers.ArenaInstanceManager;
import com.wilder0p.arenamanager.managers.ConfigManager;
import com.wilder0p.arenamanager.managers.WorldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ArenaCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ConfigManager.msg("players_only"));
            return true;
        }

        if (args.length == 0) {
            showUsage(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: §e/arena join <name>");
                    return true;
                }
                ArenaInstanceManager.handleJoin(player, args[1]);
            }
            case "leave" -> ArenaInstanceManager.handleLeave(player);
            case "lock" -> {
                ActiveInstance instance = ArenaInstanceManager.getInstancePlayerIsIn(player);
                if (instance == null) {
                    player.sendMessage(ConfigManager.msg("not_in_arena"));
                    return true;
                }
                instance.lock(player);
            }
            case "unlock" -> {
                ActiveInstance instance = ArenaInstanceManager.getInstancePlayerIsIn(player);
                if (instance == null) {
                    player.sendMessage(ConfigManager.msg("not_in_arena"));
                    return true;
                }
                instance.unlock(player);
            }
            case "list" -> listArenas(player);
            default -> {
                player.sendMessage(ConfigManager.msg("unknown_command", "%arg%", args[0]));
                showUsage(player);
            }
        }
        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage("§6=== Arena ===");
        player.sendMessage("§e/arena join <name> §7- Join or create an instance");
        player.sendMessage("§e/arena leave §7- Return to the lobby");
        player.sendMessage("§e/arena lock §7- Stop new players joining this fight");
        player.sendMessage("§e/arena unlock §7- Allow more players in");
        player.sendMessage("§e/arena list §7- Open arenas");
    }

    private void listArenas(Player player) {
        List<String> names = WorldManager.getAllTemplateNames().stream().sorted().toList();
        if (names.isEmpty()) {
            player.sendMessage("§cNo arenas yet.");
            return;
        }

        Map<String, Integer> counts = new java.util.HashMap<>();
        Map<String, Integer> players = new java.util.HashMap<>();
        for (ActiveInstance instance : ArenaInstanceManager.getActive().values()) {
            counts.merge(instance.templateName, 1, Integer::sum);
            players.merge(instance.templateName, instance.getPlayerCount(), Integer::sum);
        }

        player.sendMessage("§6=== Arenas ===");
        for (String name : names) {
            TemplateArena arena = WorldManager.getTemplate(name);
            String status = arena.isOpen ? "§aOPEN" : "§cCLOSED";
            int inst = counts.getOrDefault(name, 0);
            int online = players.getOrDefault(name, 0);
            String owner = arena.ownerName != null ? arena.ownerName : "staff";
            player.sendMessage("§e• " + name + " §7- " + status + " §8by " + owner
                    + " §7(" + inst + " instance" + (inst == 1 ? "" : "s") + ", " + online + " fighting)");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(List.of("join", "leave", "lock", "unlock", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return prefix(WorldManager.getAllTemplateNames().stream()
                    .filter(name -> WorldManager.getTemplate(name).isOpen)
                    .sorted()
                    .collect(Collectors.toList()), args[1]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}

package com.wilder0p.arenamanager.commands;

import com.wilder0p.arenamanager.ArenaManager;
import com.wilder0p.arenamanager.data.ActiveInstance;
import com.wilder0p.arenamanager.data.TemplateArena;
import com.wilder0p.arenamanager.managers.ArenaInstanceManager;
import com.wilder0p.arenamanager.managers.ConfigManager;
import com.wilder0p.arenamanager.managers.WorldManager;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ArenaEditCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "confirm", "tp", "pos1", "pos2", "open", "close",
            "setspawn", "savekit", "spawnradius", "delete", "info"
    );

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

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("list")) {
            listTemplates(player);
            return true;
        }
        if (first.equals("hours")) {
            double hours = ArenaManager.get().playtime().hours(player.getUniqueId());
            player.sendMessage(ConfigManager.msg("hours_self",
                    "%hours%", String.format(Locale.US, "%.1f", hours),
                    "%required%", String.valueOf(ConfigManager.minSurvivalHours())));
            return true;
        }
        if (first.equals("reload")) {
            if (!player.hasPermission("arenamanager.edit")) {
                player.sendMessage(ConfigManager.msg("no_permission"));
                return true;
            }
            ConfigManager.reloadAll();
            WorldManager.loadAllTemplates();
            player.sendMessage(ConfigManager.msg("reload_done"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /arenaedit <name> <subcommand>");
            showUsage(player);
            return true;
        }

        String arenaName = args[0].toLowerCase(Locale.ROOT);
        String sub = args[1].toLowerCase(Locale.ROOT);

        if (!SUBCOMMANDS.contains(sub)) {
            player.sendMessage(ConfigManager.msg("unknown_command", "%arg%", sub));
            showUsage(player);
            return true;
        }

        if (sub.equals("create") || sub.equals("confirm")) {
            createTemplate(player, arenaName);
            return true;
        }

        if (!WorldManager.isValidName(arenaName)) {
            player.sendMessage(ConfigManager.msg("invalid_name"));
            return true;
        }

        if (!WorldManager.templateExists(arenaName)) {
            player.sendMessage(ConfigManager.msg("create_hint", "%world%", arenaName));
            return true;
        }

        TemplateArena arena = WorldManager.getTemplate(arenaName);
        if (!ArenaManager.get().builders().canEdit(player, arena)) {
            player.sendMessage(ConfigManager.msg("not_owner", "%world%", arenaName));
            return true;
        }

        switch (sub) {
            case "tp" -> ArenaManager.get().builders().enterTemplate(player, arena);
            case "pos1" -> setPadCorner(player, arena, true);
            case "pos2" -> setPadCorner(player, arena, false);
            case "open" -> {
                arena.isOpen = true;
                arena.save();
                player.sendMessage(ConfigManager.msg("arena_opened", "%world%", arenaName));
            }
            case "close" -> {
                arena.isOpen = false;
                arena.save();
                for (ActiveInstance instance : Set.copyOf(ArenaInstanceManager.getActive().values())) {
                    if (instance.templateName.equals(arenaName)) {
                        instance.destroy();
                    }
                }
                player.sendMessage(ConfigManager.msg("arena_closed", "%world%", arenaName));
            }
            case "setspawn" -> {
                if (!player.getWorld().getName().equals(WorldManager.templateWorldName(arenaName))) {
                    player.sendMessage("§cStand in the template world to set spawn. Use §e/arenaedit " + arenaName + " tp");
                    return true;
                }
                arena.setSpawn(player.getLocation());
                arena.save();
                player.getWorld().setSpawnLocation(player.getLocation());
                player.sendMessage(ConfigManager.msg("spawn_set"));
            }
            case "savekit" -> {
                arena.saveKit(player);
                player.sendMessage(ConfigManager.msg("kit_saved"));
            }
            case "spawnradius" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /arenaedit " + arenaName + " spawnradius <number>");
                    return true;
                }
                try {
                    int radius = Integer.parseInt(args[2]);
                    if (radius < 0 || radius > 32) {
                        player.sendMessage("§cRadius must be 0-32.");
                        return true;
                    }
                    arena.spawnRadius = radius;
                    arena.save();
                    player.sendMessage(ConfigManager.msg("radius_set", "%radius%", String.valueOf(radius)));
                } catch (NumberFormatException e) {
                    player.sendMessage("§cRadius must be a number.");
                }
            }
            case "delete" -> deleteTemplate(player, arenaName, args.length > 2 && args[2].equalsIgnoreCase("confirm"));
            case "info" -> showInfo(player, arena);
        }
        return true;
    }

    private void createTemplate(Player player, String arenaName) {
        if (!WorldManager.isValidName(arenaName)) {
            player.sendMessage(ConfigManager.msg("invalid_name"));
            return;
        }
        if (!player.hasPermission("arenamanager.build") && !player.hasPermission("arenamanager.edit")) {
            player.sendMessage(ConfigManager.msg("no_permission"));
            return;
        }
        if (!ArenaManager.get().playtime().canBuild(player)) {
            double hours = ArenaManager.get().playtime().hours(player.getUniqueId());
            player.sendMessage(ConfigManager.msg("need_playtime",
                    "%required%", String.valueOf(ConfigManager.minSurvivalHours()),
                    "%hours%", String.format(Locale.US, "%.1f", hours)));
            return;
        }
        if (WorldManager.templateExists(arenaName) || WorldManager.loadTemplateWorld(arenaName) != null) {
            player.sendMessage(ConfigManager.msg("template_exists"));
            return;
        }
        if (!player.hasPermission("arenamanager.edit")
                && WorldManager.countOwned(player.getUniqueId()) >= ConfigManager.maxArenasPerPlayer()) {
            player.sendMessage(ConfigManager.msg("max_arenas", "%max%", String.valueOf(ConfigManager.maxArenasPerPlayer())));
            return;
        }

        World world = WorldManager.createTemplateWorld(player, arenaName);
        if (world == null) {
            player.sendMessage(ConfigManager.msg("copy_failed"));
            return;
        }

        TemplateArena arena = WorldManager.getTemplate(arenaName);
        ArenaManager.get().builders().enterTemplate(player, arena);
        player.sendMessage(ConfigManager.msg("arena_created", "%world%", arenaName));
    }

    private void setPadCorner(Player player, TemplateArena arena, boolean first) {
        World lobby = ConfigManager.lobbyWorld();
        if (lobby == null || !player.getWorld().equals(lobby)) {
            player.sendMessage(ConfigManager.msg("pad_not_lobby"));
            return;
        }

        if (first) {
            arena.pos1 = player.getLocation().toVector();
        } else {
            arena.pos2 = player.getLocation().toVector();
        }
        arena.lobbyWorld = lobby.getName();

        if (arena.hasPad() && !player.hasPermission("arenamanager.edit")
                && arena.padVolume() > ConfigManager.maxPadVolume()) {
            if (first) arena.pos1 = null;
            else arena.pos2 = null;
            player.sendMessage(ConfigManager.msg("pad_too_big", "%max%", String.valueOf(ConfigManager.maxPadVolume())));
            return;
        }

        arena.save();
        player.sendMessage(ConfigManager.msg(first ? "pos1_set" : "pos2_set"));
    }

    private void deleteTemplate(Player player, String name, boolean confirmed) {
        if (!confirmed) {
            player.sendMessage(ConfigManager.msg("delete_confirm", "%world%", name));
            return;
        }
        for (ActiveInstance instance : Set.copyOf(ArenaInstanceManager.getActive().values())) {
            if (instance.templateName.equals(name)) {
                instance.destroy();
            }
        }
        WorldManager.deleteTemplate(name);
        player.sendMessage(ConfigManager.msg("template_deleted", "%world%", name));
    }

    private void showInfo(Player player, TemplateArena arena) {
        player.sendMessage("§6=== " + arena.name + " ===");
        player.sendMessage("§7Owner: §e" + (arena.ownerName != null ? arena.ownerName : "staff"));
        player.sendMessage("§7Open: " + (arena.isOpen ? "§ayes" : "§cno"));
        player.sendMessage("§7Lobby pad: " + (arena.hasPad() ? "§aset" : "§cnot set"));
        player.sendMessage("§7Spawn: " + (arena.hasSpawn ? "§aset" : "§cnot set"));
        player.sendMessage("§7Spawn radius: §e" + arena.spawnRadius);
    }

    private void listTemplates(Player player) {
        List<String> names = WorldManager.getAllTemplateNames().stream().sorted().toList();
        if (names.isEmpty()) {
            player.sendMessage("§cNo arena templates found.");
            player.sendMessage("§7Create one with §e/arenaedit <name> create");
            return;
        }

        player.sendMessage("§6=== Arena Templates ===");
        boolean staff = player.hasPermission("arenamanager.edit");
        for (String name : names) {
            TemplateArena arena = WorldManager.getTemplate(name);
            if (!staff && !arena.isOwner(player.getUniqueId())) {
                continue;
            }
            String status = arena.isOpen ? "§aOPEN" : "§cCLOSED";
            String pad = arena.hasPad() ? "§7[pad]" : "§8[no pad]";
            String owner = arena.ownerName != null ? arena.ownerName : "staff";
            player.sendMessage("§e• " + name + " §7- " + status + " §8by " + owner + " " + pad);
        }
    }

    private void showUsage(Player player) {
        player.sendMessage("§6=== ArenaEdit ===");
        player.sendMessage("§e/arenaedit hours §7- Check survival playtime");
        player.sendMessage("§e/arenaedit list §7- Your templates");
        player.sendMessage("§e/arenaedit <name> create §7- New template world");
        player.sendMessage("§e/arenaedit <name> tp §7- Build with Creative + WorldEdit");
        player.sendMessage("§e/arenaedit <name> setspawn §7- Spawn players here");
        player.sendMessage("§e/arenaedit <name> savekit §7- Kit given on join");
        player.sendMessage("§e/arenaedit <name> pos1/pos2 §7- Lobby pad (stand in lobby)");
        player.sendMessage("§e/arenaedit <name> open/close");
        player.sendMessage("§e/arenaedit <name> delete confirm");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("list", "hours"));
            if (player.hasPermission("arenamanager.edit")) {
                options.add("reload");
                options.addAll(WorldManager.getAllTemplateNames());
            } else {
                for (String name : WorldManager.getAllTemplateNames()) {
                    if (WorldManager.getTemplate(name).isOwner(player.getUniqueId())) {
                        options.add(name);
                    }
                }
            }
            return prefix(options, args[0]);
        }
        if (args.length == 2) {
            return prefix(SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("delete")) {
            return prefix(List.of("confirm"), args[2]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}

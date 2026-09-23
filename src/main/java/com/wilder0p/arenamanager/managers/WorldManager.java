package com.wilder0p.arenamanager.managers;

import com.wilder0p.arenamanager.ArenaManager;
import com.wilder0p.arenamanager.data.TemplateArena;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class WorldManager {

    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,16}$");
    private static final Set<String> SKIP_ROOT = Set.of(
            "session.lock", "uid.dat", "playerdata", "advancements", "stats", "level.dat_old"
    );

    private static final Map<String, TemplateArena> templates = new HashMap<>();

    public static boolean isValidName(String name) {
        return NAME_PATTERN.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    public static String templateWorldName(String arena) {
        return "arena_template_" + arena.toLowerCase(Locale.ROOT);
    }

    public static boolean isTemplateWorld(String worldName) {
        return worldName != null && worldName.startsWith("arena_template_");
    }

    public static boolean isInstanceWorld(String worldName) {
        return worldName != null && worldName.startsWith("inst_");
    }

    public static String arenaFromTemplateWorld(String worldName) {
        if (!isTemplateWorld(worldName)) {
            return null;
        }
        return worldName.substring("arena_template_".length());
    }

    public static boolean templateExists(String name) {
        return ConfigManager.templateFile(name.toLowerCase(Locale.ROOT)).exists();
    }

    public static Set<String> getAllTemplateNames() {
        File folder = new File(ArenaManager.get().getDataFolder(), "arenas");
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        Set<String> names = new HashSet<>();
        if (files != null) {
            for (File file : files) {
                names.add(file.getName().replace(".yml", ""));
            }
        }
        return names;
    }

    public static TemplateArena getTemplate(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return templates.computeIfAbsent(key, TemplateArena::new);
    }

    public static void evictTemplate(String name) {
        templates.remove(name.toLowerCase(Locale.ROOT));
        ConfigManager.evictTemplate(name.toLowerCase(Locale.ROOT));
    }

    public static void loadAllTemplates() {
        templates.clear();
        for (String name : getAllTemplateNames()) {
            TemplateArena arena = getTemplate(name);
            loadTemplateWorld(name);
            ArenaManager.get().getLogger().info("[Arena] Loaded template " + name
                    + (arena.isOpen ? " (open)" : " (closed)"));
        }
    }

    public static int countOwned(UUID owner) {
        int count = 0;
        for (String name : getAllTemplateNames()) {
            if (getTemplate(name).isOwner(owner)) {
                count++;
            }
        }
        return count;
    }

    public static World loadTemplateWorld(String arena) {
        String worldName = templateWorldName(arena);
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (!folder.exists()) {
            return null;
        }
        world = new WorldCreator(worldName).createWorld();
        if (world != null) {
            applyTemplateRules(world);
        }
        return world;
    }

    public static World createTemplateWorld(Player creator, String arena) {
        String worldName = templateWorldName(arena);
        if (Bukkit.getWorld(worldName) != null) {
            return Bukkit.getWorld(worldName);
        }

        WorldCreator wc = new WorldCreator(worldName);
        wc.type(WorldType.FLAT);
        wc.generateStructures(false);
        wc.generatorSettings("{\"layers\":[{\"block\":\"bedrock\",\"height\":1},{\"block\":\"dirt\",\"height\":3},{\"block\":\"grass_block\",\"height\":1}],\"biome\":\"plains\"}");

        World world = wc.createWorld();
        if (world == null) {
            return null;
        }

        applyTemplateRules(world);
        world.setSpawnLocation(0, 4, 0);

        int border = ConfigManager.templateBorderSize();
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(0.5, 0.5);
        wb.setSize(border);
        wb.setDamageAmount(0);
        wb.setWarningDistance(5);

        TemplateArena template = getTemplate(arena);
        template.setOwner(creator);
        template.setSpawn(world.getSpawnLocation());
        template.save();
        return world;
    }

    public static void applyTemplateRules(World world) {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setPVP(false);
        world.setSpawnFlags(false, false);
    }

    public static void applyInstanceRules(World world) {
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setPVP(true);
        world.setSpawnFlags(false, false);
    }

    public static World loadWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world != null) {
            return world;
        }
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (!folder.exists()) {
            return null;
        }
        return new WorldCreator(name).createWorld();
    }

    public static void copyWorldFolder(File source, File dest) throws IOException {
        Path src = source.toPath();
        Path dst = dest.toPath();
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(dir);
                if (rel.getNameCount() > 0 && SKIP_ROOT.contains(rel.getName(0).toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dst.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = src.relativize(file);
                if (rel.getNameCount() > 0 && SKIP_ROOT.contains(rel.getName(0).toString())) {
                    return FileVisitResult.CONTINUE;
                }
                String fileName = file.getFileName().toString();
                if (fileName.equals("session.lock") || fileName.equals("uid.dat")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, dst.resolve(rel), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteRecursive(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        try {
            Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | UncheckedIOException e) {
            ArenaManager.get().getLogger().warning("Failed to delete " + folder.getName() + ": " + e.getMessage());
        }
    }

    public static void deleteTemplate(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        World world = Bukkit.getWorld(templateWorldName(key));
        if (world != null) {
            Location lobby = ConfigManager.lobbySpawn();
            for (Player player : world.getPlayers()) {
                player.teleport(lobby);
            }
            Bukkit.unloadWorld(world, false);
        }
        deleteRecursive(new File(Bukkit.getWorldContainer(), templateWorldName(key)));
        ConfigManager.deleteTemplateFile(key);
        evictTemplate(key);
    }

    public static void purgeLeftoverInstances() {
        for (World loaded : List.copyOf(Bukkit.getWorlds())) {
            if (!isInstanceWorld(loaded.getName())) {
                continue;
            }
            File folder = loaded.getWorldFolder();
            Bukkit.unloadWorld(loaded, false);
            deleteRecursive(folder);
            ArenaManager.get().getLogger().info("[Arena] Unloaded leftover instance world " + loaded.getName());
        }

        File[] files = Bukkit.getWorldContainer().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.isDirectory() || !isInstanceWorld(file.getName())) {
                continue;
            }
            World loaded = Bukkit.getWorld(file.getName());
            if (loaded != null) {
                Bukkit.unloadWorld(loaded, false);
            }
            deleteRecursive(file);
            ArenaManager.get().getLogger().info("[Arena] Deleted leftover instance " + file.getName());
        }
    }
}

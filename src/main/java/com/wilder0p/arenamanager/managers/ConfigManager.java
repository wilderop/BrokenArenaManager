package com.wilder0p.arenamanager.managers;

import com.wilder0p.arenamanager.ArenaManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private static FileConfiguration config;
    private static FileConfiguration messages;
    private static File messagesFile;
    private static final Map<String, FileConfiguration> templateConfigs = new HashMap<>();

    public ConfigManager() {
        ArenaManager plugin = ArenaManager.get();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        config.addDefault("lobby.world", "world");
        config.addDefault("lobby.spawn.x", 0.5);
        config.addDefault("lobby.spawn.y", -52.0);
        config.addDefault("lobby.spawn.z", 0.5);
        config.addDefault("lobby.spawn.yaw", 91.0);
        config.addDefault("lobby.spawn.pitch", 5.0);
        config.addDefault("max_instances", 50);
        config.addDefault("spawn_radius", 3);
        config.addDefault("join_cooldown_seconds", 2);
        config.addDefault("clear_drops_on_death", true);
        config.addDefault("y_pad", 3);
        config.addDefault("builder.min_survival_hours", 100);
        config.addDefault("builder.max_arenas_per_player", 3);
        config.addDefault("builder.max_pad_volume", 120);
        config.addDefault("builder.template_border_size", 256);
        config.addDefault("builder.survival_stats_folder", "/mnt/pool/survival/world/players/stats");
        config.options().copyDefaults(true);
        plugin.saveConfig();

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public static FileConfiguration get() {
        return config;
    }

    public static String msg(String key) {
        return messages.getString(key, "§cMessage missing: " + key).replace("&", "§");
    }

    public static String msg(String key, String... replacements) {
        String text = msg(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    public static FileConfiguration getTemplate(String templateName) {
        if (templateConfigs.containsKey(templateName)) {
            return templateConfigs.get(templateName);
        }
        File file = templateFile(templateName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        templateConfigs.put(templateName, cfg);
        return cfg;
    }

    public static void saveTemplate(String templateName) {
        try {
            getTemplate(templateName).save(templateFile(templateName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void evictTemplate(String templateName) {
        templateConfigs.remove(templateName);
    }

    public static void deleteTemplateFile(String templateName) {
        evictTemplate(templateName);
        File file = templateFile(templateName);
        if (file.exists() && !file.delete()) {
            ArenaManager.get().getLogger().warning("Could not delete " + file.getName());
        }
    }

    public static void reloadAll() {
        ArenaManager plugin = ArenaManager.get();
        plugin.reloadConfig();
        config = plugin.getConfig();
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        templateConfigs.clear();
        plugin.getLogger().info("[Arena] Config reloaded.");
    }

    public static File templateFile(String name) {
        return new File(ArenaManager.get().getDataFolder(), "arenas/" + name + ".yml");
    }

    public static String lobbyWorldName() {
        return config.getString("lobby.world", "world");
    }

    public static World lobbyWorld() {
        World world = Bukkit.getWorld(lobbyWorldName());
        if (world != null) {
            return world;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public static Location lobbySpawn() {
        World world = lobbyWorld();
        if (world == null) {
            return new Location(Bukkit.getWorlds().get(0), 0.5, -52, 0.5);
        }
        return new Location(
                world,
                config.getDouble("lobby.spawn.x", 0.5),
                config.getDouble("lobby.spawn.y", -52.0),
                config.getDouble("lobby.spawn.z", 0.5),
                (float) config.getDouble("lobby.spawn.yaw", 91.0),
                (float) config.getDouble("lobby.spawn.pitch", 5.0)
        );
    }

    public static int maxInstances() {
        return config.getInt("max_instances", 50);
    }

    public static int spawnRadius() {
        return config.getInt("spawn_radius", 3);
    }

    public static int joinCooldownSeconds() {
        return config.getInt("join_cooldown_seconds", 2);
    }

    public static boolean clearDropsOnDeath() {
        return config.getBoolean("clear_drops_on_death", true);
    }

    public static int yPad() {
        return config.getInt("y_pad", 3);
    }

    public static int minSurvivalHours() {
        return config.getInt("builder.min_survival_hours", 100);
    }

    public static int maxArenasPerPlayer() {
        return config.getInt("builder.max_arenas_per_player", 3);
    }

    public static int maxPadVolume() {
        return config.getInt("builder.max_pad_volume", 120);
    }

    public static int templateBorderSize() {
        return config.getInt("builder.template_border_size", 256);
    }

    public static File survivalStatsFolder() {
        return new File(config.getString("builder.survival_stats_folder", "/mnt/pool/survival/world/players/stats"));
    }

    public static List<String> worldEditPermissions() {
        return config.getStringList("builder.worldedit_permissions");
    }
}

package com.wilder0p.arenamanager.managers;

import com.wilder0p.arenamanager.ArenaManager;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaytimeManager {

    private static final Pattern PLAYTIME = Pattern.compile(
            "\"minecraft:(?:play_time|play_one_minute)\"\\s*:\\s*(\\d+)"
    );
    private static final long CACHE_MS = 60_000L;

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

    public boolean isStaff(Player player) {
        return player.hasPermission("arenamanager.edit");
    }

    public boolean canBuild(Player player) {
        return isStaff(player) || hours(player.getUniqueId()) >= ConfigManager.minSurvivalHours();
    }

    public double hours(UUID uuid) {
        CacheEntry cached = cache.get(uuid);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.at < CACHE_MS) {
            return cached.hours;
        }
        double hours = readHours(uuid);
        cache.put(uuid, new CacheEntry(hours, now));
        return hours;
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    private double readHours(UUID uuid) {
        File file = new File(ConfigManager.survivalStatsFolder(), uuid + ".json");
        if (!file.isFile()) {
            return 0;
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = PLAYTIME.matcher(text);
            long ticks = 0;
            while (matcher.find()) {
                ticks = Math.max(ticks, Long.parseLong(matcher.group(1)));
            }
            return ticks / 20.0 / 3600.0;
        } catch (Exception e) {
            ArenaManager.get().getLogger().warning("Failed to read survival playtime for " + uuid + ": " + e.getMessage());
            return 0;
        }
    }

    private record CacheEntry(double hours, long at) {}
}

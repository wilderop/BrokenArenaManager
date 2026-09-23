package com.wilder0p.arenamanager.data;

import com.wilder0p.arenamanager.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TemplateArena {

    public final String name;
    public UUID owner;
    public String ownerName;
    public boolean isOpen;
    public String lobbyWorld;
    public Vector pos1;
    public Vector pos2;
    public double spawnX;
    public double spawnY = 64;
    public double spawnZ;
    public float spawnYaw;
    public float spawnPitch;
    public boolean hasSpawn;
    public int spawnRadius;
    private ItemStack[] kit;
    private ItemStack[] armor;

    public TemplateArena(String name) {
        this.name = name.toLowerCase(Locale.ROOT);
        FileConfiguration cfg = ConfigManager.getTemplate(this.name);

        this.isOpen = cfg.getBoolean("open", false);
        this.lobbyWorld = cfg.getString("lobby_world");
        this.spawnRadius = cfg.getInt("spawn_radius", ConfigManager.spawnRadius());

        if (cfg.contains("owner")) {
            try {
                this.owner = UUID.fromString(cfg.getString("owner"));
            } catch (IllegalArgumentException ignored) {
                this.owner = null;
            }
        }
        this.ownerName = cfg.getString("owner_name");

        if (cfg.contains("pos1")) this.pos1 = cfg.getVector("pos1");
        if (cfg.contains("pos2")) this.pos2 = cfg.getVector("pos2");

        if (cfg.contains("spawn.x")) {
            this.hasSpawn = true;
            this.spawnX = cfg.getDouble("spawn.x");
            this.spawnY = cfg.getDouble("spawn.y");
            this.spawnZ = cfg.getDouble("spawn.z");
            this.spawnYaw = (float) cfg.getDouble("spawn.yaw");
            this.spawnPitch = (float) cfg.getDouble("spawn.pitch");
        } else if (cfg.contains("spawn")) {
            try {
                Location loc = cfg.getLocation("spawn");
                if (loc != null) {
                    this.hasSpawn = true;
                    this.spawnX = loc.getX();
                    this.spawnY = loc.getY();
                    this.spawnZ = loc.getZ();
                    this.spawnYaw = loc.getYaw();
                    this.spawnPitch = loc.getPitch();
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Arena] Bad spawn location in template '" + this.name + "'");
            }
        }

        this.kit = readItems(cfg, "kit");
        this.armor = readItems(cfg, "armor");
    }

    public void save() {
        FileConfiguration cfg = ConfigManager.getTemplate(name);
        cfg.set("open", isOpen);
        cfg.set("owner", owner == null ? null : owner.toString());
        cfg.set("owner_name", ownerName);
        cfg.set("lobby_world", lobbyWorld);
        cfg.set("pos1", pos1);
        cfg.set("pos2", pos2);
        cfg.set("spawn_radius", spawnRadius);
        if (hasSpawn) {
            cfg.set("spawn.x", spawnX);
            cfg.set("spawn.y", spawnY);
            cfg.set("spawn.z", spawnZ);
            cfg.set("spawn.yaw", spawnYaw);
            cfg.set("spawn.pitch", spawnPitch);
        }
        cfg.set("kit", kit);
        cfg.set("armor", armor);
        ConfigManager.saveTemplate(name);
    }

    public void setOwner(Player player) {
        this.owner = player.getUniqueId();
        this.ownerName = player.getName();
    }

    public void setSpawn(Location loc) {
        this.hasSpawn = true;
        this.spawnX = loc.getX();
        this.spawnY = loc.getY();
        this.spawnZ = loc.getZ();
        this.spawnYaw = loc.getYaw();
        this.spawnPitch = loc.getPitch();
    }

    public Location spawnIn(World world) {
        if (hasSpawn) {
            return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
        }
        return world.getSpawnLocation().clone();
    }

    public void saveKit(Player player) {
        PlayerInventory inv = player.getInventory();
        this.kit = cloneItems(inv.getContents());
        this.armor = cloneItems(inv.getArmorContents());
        save();
    }

    public void applyKit(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (kit != null) inv.setContents(cloneItems(kit));
        if (armor != null) inv.setArmorContents(cloneItems(armor));
    }

    public boolean hasPad() {
        return pos1 != null && pos2 != null;
    }

    public boolean containsPad(Location loc) {
        if (!hasPad() || loc.getWorld() == null) {
            return false;
        }
        String worldName = lobbyWorld != null ? lobbyWorld : ConfigManager.lobbyWorldName();
        if (!loc.getWorld().getName().equals(worldName)) {
            return false;
        }

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY()) - 1;
        double maxY = Math.max(pos1.getY(), pos2.getY()) + ConfigManager.yPad();
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int padVolume() {
        if (!hasPad()) return 0;
        int dx = Math.abs(pos1.getBlockX() - pos2.getBlockX()) + 1;
        int dy = Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1;
        int dz = Math.abs(pos1.getBlockZ() - pos2.getBlockZ()) + 1;
        return dx * dy * dz;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    private static ItemStack[] readItems(FileConfiguration cfg, String path) {
        try {
            Object obj = cfg.get(path);
            if (obj instanceof List<?> list) {
                return list.toArray(new ItemStack[0]);
            }
            if (obj instanceof ItemStack[] items) {
                return items;
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Arena] Bad " + path + " data — ignoring it.");
        }
        return null;
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}

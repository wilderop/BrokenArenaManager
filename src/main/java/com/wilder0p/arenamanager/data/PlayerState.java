package com.wilder0p.arenamanager.data;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PlayerState {

    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final GameMode gameMode;
    private final boolean allowFlight;

    private PlayerState(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, GameMode gameMode, boolean allowFlight) {
        this.contents = contents;
        this.armor = armor;
        this.offhand = offhand;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
    }

    public static PlayerState capture(Player player) {
        PlayerInventory inv = player.getInventory();
        return new PlayerState(
                cloneItems(inv.getContents()),
                cloneItems(inv.getArmorContents()),
                cloneItem(inv.getItemInOffHand()),
                player.getGameMode(),
                player.getAllowFlight()
        );
    }

    public void restore(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setContents(cloneItems(contents));
        inv.setArmorContents(cloneItems(armor));
        inv.setItemInOffHand(cloneItem(offhand));
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        if (!allowFlight) {
            player.setFlying(false);
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}

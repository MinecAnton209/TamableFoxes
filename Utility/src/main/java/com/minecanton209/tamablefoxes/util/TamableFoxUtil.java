package com.minecanton209.tamablefoxes.util;

import java.util.UUID;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.sqlite.SQLiteHelper;

/**
 * Static utility methods shared by all version-specific EntityTamableFox classes.
 * Eliminates duplicated SQLite, Config, and UUID logic across 26 NMS modules.
 */
public final class TamableFoxUtil {

    private TamableFoxUtil() {}

    public static boolean isTamed(boolean tamed, UUID ownerUUID) {
        return tamed && (ownerUUID != null && !ownerUUID.equals(new UUID(0, 0)));
    }

    public static boolean isValidOwner(UUID uuid) {
        return uuid != null && !uuid.equals(new UUID(0, 0));
    }

    public static boolean isTameLimitExceeded(UUID playerUuid) {
        if (Config.getMaxPlayerFoxTames() <= 0) return false;
        SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
        int current = db.getPlayerFoxAmount(playerUuid);
        return current >= Config.getMaxPlayerFoxTames();
    }

    public static void incrementTameCount(UUID playerUuid) {
        if (Config.getMaxPlayerFoxTames() > 0) {
            SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
            db.addPlayerFoxAmount(playerUuid, 1);
        }
    }

    public static void decrementTameCount(UUID playerUuid) {
        if (Config.getMaxPlayerFoxTames() > 0) {
            SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
            db.removePlayerFoxAmount(playerUuid, 1);
        }
    }

    public static UUID parseOwnerUUID(String uuidString) {
        try {
            UUID uuid = UUID.fromString(uuidString);
            return isValidOwner(uuid) ? uuid : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Find the nearest tamed fox owned by the player within range.
     * Uses NMS reflection to cast CraftEntity → EntityTamableFox (ITamableFoxAdapter).
     */
    public static ITamableFoxAdapter findNearestOwnedFox(org.bukkit.entity.Player player, double range) {
        java.util.List<org.bukkit.entity.Entity> nearby = player.getNearbyEntities(range, range, range);
        ITamableFoxAdapter closest = null;
        double closestDist = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity entity : nearby) {
            ITamableFoxAdapter adapter = toAdapter(entity);
            if (adapter != null) {
                UUID owner = adapter.getOwnerUUID();
                if (owner != null && owner.equals(player.getUniqueId())) {
                    double dist = entity.getLocation().distanceSquared(player.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = adapter;
                    }
                }
            }
        }
        return closest;
    }

    /**
     * Find any tamed fox owned by the player within range (not nearest — any).
     */
    public static ITamableFoxAdapter findAnyOwnedFox(org.bukkit.entity.Player player, double range) {
        java.util.List<org.bukkit.entity.Entity> nearby = player.getNearbyEntities(range, range, range);
        for (org.bukkit.entity.Entity entity : nearby) {
            ITamableFoxAdapter adapter = toAdapter(entity);
            if (adapter != null) {
                UUID owner = adapter.getOwnerUUID();
                if (owner != null && owner.equals(player.getUniqueId())) {
                    return adapter;
                }
            }
        }
        return null;
    }

    /**
     * Convert a Bukkit Entity to ITamableFoxAdapter via NMS reflection.
     * Works because CraftFox.getHandle() returns EntityTamableFox which implements ITamableFoxAdapter.
     */
    public static ITamableFoxAdapter toAdapter(org.bukkit.entity.Entity entity) {
        try {
            java.lang.reflect.Method getHandle = entity.getClass().getMethod("getHandle");
            Object nmsEntity = getHandle.invoke(entity);
            if (nmsEntity instanceof ITamableFoxAdapter adapter) {
                return adapter;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Find a fox by its Bukkit entity UUID in a world.
     */
    public static ITamableFoxAdapter findFoxByUUID(org.bukkit.World world, UUID foxUUID) {
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getUniqueId().equals(foxUUID)) {
                return toAdapter(entity);
            }
        }
        return null;
    }
}

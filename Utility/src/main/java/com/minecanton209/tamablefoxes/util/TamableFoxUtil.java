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
}

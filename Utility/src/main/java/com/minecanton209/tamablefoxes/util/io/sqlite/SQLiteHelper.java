package com.minecanton209.tamablefoxes.util.io.sqlite;

import com.minecanton209.tamablefoxes.util.io.Config;
import org.bukkit.plugin.Plugin;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SQLiteHelper {
    public static Plugin plugin;
    public static SQLiteHandler sqLiteHandler;

    private static SQLiteHelper instance;
    private static String userAmountTableName = "USER_FOX_AMT";
    private static String foxesTableName = "FOXES";

    public static SQLiteHelper getInstance(Plugin plugin) {
        if (instance == null) {
            instance = new SQLiteHelper();
            SQLiteHelper.plugin = plugin;
        }

        return instance;
    }

    public void createTablesIfNotExist() {
        sqLiteHandler = SQLiteHandler.getInstance();

        String userFoxAmountQuery =
                "CREATE TABLE IF NOT EXISTS `" + userAmountTableName + "` ( " +
                    "`UUID` TEXT PRIMARY KEY ,  " +
                    "`AMOUNT` INT NOT NULL);";

        String foxesQuery =
                "CREATE TABLE IF NOT EXISTS `" + foxesTableName + "` ( " +
                    "`FOX_UUID` TEXT PRIMARY KEY, " +
                    "`OWNER_UUID` TEXT NOT NULL, " +
                    "`NAME` TEXT, " +
                    "`WORLD` TEXT, " +
                    "`X` DOUBLE, " +
                    "`Y` DOUBLE, " +
                    "`Z` DOUBLE, " +
                    "`HEALTH` DOUBLE, " +
                    "`MAX_HEALTH` DOUBLE, " +
                    "`SITTING` BOOLEAN, " +
                    "`SLEEPING` BOOLEAN);";

        try {
            sqLiteHandler.connect(plugin);
            DatabaseMetaData dbm = sqLiteHandler.getConnection().getMetaData();

            ResultSet tables = dbm.getTables(null, null, userAmountTableName, null);
            if (!tables.next()) {
                PreparedStatement statement = sqLiteHandler.getConnection().prepareStatement(userFoxAmountQuery);
                statement.executeUpdate();
            }

            ResultSet foxTables = dbm.getTables(null, null, foxesTableName, null);
            if (!foxTables.next()) {
                PreparedStatement statement = sqLiteHandler.getConnection().prepareStatement(foxesQuery);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
    }

    public int getPlayerFoxAmount(UUID uuid) {
        sqLiteHandler = SQLiteHandler.getInstance();

        try {
            sqLiteHandler.connect(plugin);
            PreparedStatement statement = sqLiteHandler.getConnection()
                    .prepareStatement("SELECT * FROM " + userAmountTableName + " WHERE UUID=?");
            statement.setString(1, uuid.toString());
            ResultSet results = statement.executeQuery();

            if (results.next()) {
                return results.getInt("AMOUNT");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (sqLiteHandler.getConnection() != null) {
                try {
                    sqLiteHandler.getConnection().close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        return -1;
    }

    public void addPlayerFoxAmount(UUID uuid, int amt) {
        sqLiteHandler = SQLiteHandler.getInstance();

        try {
            String query = "UPDATE " + userAmountTableName + " SET AMOUNT = AMOUNT + " + amt + " WHERE UUID = '" + uuid.toString() + "'";
            if (getPlayerFoxAmount(uuid) == -1) {
                query = "INSERT INTO " + userAmountTableName + " (UUID, AMOUNT) VALUES('" + uuid.toString() + "'," + amt + ")";
            }

            sqLiteHandler.connect(plugin);
            PreparedStatement statement = sqLiteHandler.getConnection().prepareStatement(query);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (sqLiteHandler.getConnection() != null) {
                try {
                    sqLiteHandler.getConnection().close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void removePlayerFoxAmount(UUID uuid, int amt) {
        sqLiteHandler = SQLiteHandler.getInstance();

        try {
            String query = "UPDATE " + userAmountTableName + " SET AMOUNT = AMOUNT - " + amt + " WHERE UUID = '" + uuid.toString() + "'";

            sqLiteHandler.connect(plugin);
            PreparedStatement statement = sqLiteHandler.getConnection().prepareStatement(query);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
    }

    // === Fox registry ===

    public void registerFox(UUID foxUUID, UUID ownerUUID, String name, String world, double x, double y, double z, double health, double maxHealth, boolean sitting, boolean sleeping) {
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            String sql = "INSERT OR REPLACE INTO " + foxesTableName +
                " (FOX_UUID, OWNER_UUID, NAME, WORLD, X, Y, Z, HEALTH, MAX_HEALTH, SITTING, SLEEPING) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = sqLiteHandler.getConnection().prepareStatement(sql);
            stmt.setString(1, foxUUID.toString());
            stmt.setString(2, ownerUUID.toString());
            stmt.setString(3, name);
            stmt.setString(4, world);
            stmt.setDouble(5, x);
            stmt.setDouble(6, y);
            stmt.setDouble(7, z);
            stmt.setDouble(8, health);
            stmt.setDouble(9, maxHealth);
            stmt.setBoolean(10, sitting);
            stmt.setBoolean(11, sleeping);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
    }

    public void unregisterFox(UUID foxUUID) {
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            PreparedStatement stmt = sqLiteHandler.getConnection()
                .prepareStatement("DELETE FROM " + foxesTableName + " WHERE FOX_UUID = ?");
            stmt.setString(1, foxUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
    }

    public void updateFox(UUID foxUUID, String name, double health, double maxHealth, boolean sitting, boolean sleeping) {
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            String sql = "UPDATE " + foxesTableName +
                " SET NAME=?, HEALTH=?, MAX_HEALTH=?, SITTING=?, SLEEPING=? WHERE FOX_UUID=?";
            PreparedStatement stmt = sqLiteHandler.getConnection().prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setDouble(2, health);
            stmt.setDouble(3, maxHealth);
            stmt.setBoolean(4, sitting);
            stmt.setBoolean(5, sleeping);
            stmt.setString(6, foxUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
    }

    public void updateFoxLocation(UUID foxUUID, String world, double x, double y, double z) {
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            String sql = "UPDATE " + foxesTableName + " SET WORLD=?, X=?, Y=?, Z=? WHERE FOX_UUID=?";
            PreparedStatement stmt = sqLiteHandler.getConnection().prepareStatement(sql);
            stmt.setString(1, world);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setString(5, foxUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
    }

    public List<Map<String, Object>> getPlayerFoxes(UUID ownerUUID) {
        List<Map<String, Object>> result = new ArrayList<>();
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            PreparedStatement stmt = sqLiteHandler.getConnection()
                .prepareStatement("SELECT * FROM " + foxesTableName + " WHERE OWNER_UUID=?");
            stmt.setString(1, ownerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("foxUUID", rs.getString("FOX_UUID"));
                row.put("ownerUUID", rs.getString("OWNER_UUID"));
                row.put("name", rs.getString("NAME"));
                row.put("world", rs.getString("WORLD"));
                row.put("x", rs.getDouble("X"));
                row.put("y", rs.getDouble("Y"));
                row.put("z", rs.getDouble("Z"));
                row.put("health", rs.getDouble("HEALTH"));
                row.put("maxHealth", rs.getDouble("MAX_HEALTH"));
                row.put("sitting", rs.getBoolean("SITTING"));
                row.put("sleeping", rs.getBoolean("SLEEPING"));
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
        return result;
    }

    public Map<String, Object> getFox(UUID foxUUID) {
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            PreparedStatement stmt = sqLiteHandler.getConnection()
                .prepareStatement("SELECT * FROM " + foxesTableName + " WHERE FOX_UUID=?");
            stmt.setString(1, foxUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("foxUUID", rs.getString("FOX_UUID"));
                row.put("ownerUUID", rs.getString("OWNER_UUID"));
                row.put("name", rs.getString("NAME"));
                row.put("world", rs.getString("WORLD"));
                row.put("x", rs.getDouble("X"));
                row.put("y", rs.getDouble("Y"));
                row.put("z", rs.getDouble("Z"));
                row.put("health", rs.getDouble("HEALTH"));
                row.put("maxHealth", rs.getDouble("MAX_HEALTH"));
                row.put("sitting", rs.getBoolean("SITTING"));
                row.put("sleeping", rs.getBoolean("SLEEPING"));
                return row;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
        return null;
    }

    public boolean isFoxRegistered(UUID foxUUID) {
        sqLiteHandler = SQLiteHandler.getInstance();
        try {
            sqLiteHandler.connect(plugin);
            PreparedStatement stmt = sqLiteHandler.getConnection()
                .prepareStatement("SELECT 1 FROM " + foxesTableName + " WHERE FOX_UUID=?");
            stmt.setString(1, foxUUID.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeQuietly();
        }
        return false;
    }

    private void closeQuietly() {
        if (sqLiteHandler.getConnection() != null) {
            try {
                sqLiteHandler.getConnection().close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

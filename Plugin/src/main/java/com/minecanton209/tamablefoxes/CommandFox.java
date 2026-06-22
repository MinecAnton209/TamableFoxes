package com.minecanton209.tamablefoxes;

import com.minecanton209.tamablefoxes.util.ITamableFoxAdapter;
import com.minecanton209.tamablefoxes.util.TamableFoxLogic;
import com.minecanton209.tamablefoxes.util.TamableFoxUtil;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.LanguageConfig;
import com.minecanton209.tamablefoxes.util.io.sqlite.SQLiteHelper;
import com.minecanton209.tamablefoxes.util.gui.Gui;
import com.minecanton209.tamablefoxes.util.gui.ItemBuilder;
import com.minecanton209.tamablefoxes.util.gui.PagedGui;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CommandFox implements TabExecutor {

    private static final int RANGE = 32;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Config.getPrefix() + ChatColor.RED + LanguageConfig.getOnlyRunPlayer());
            return true;
        }

        if (!player.hasPermission("tamablefoxes.command.fox")) {
            player.sendMessage(Config.getPrefix() + ChatColor.RED + LanguageConfig.getNoPermMessage());
            return true;
        }

        ITamableFoxAdapter fox = TamableFoxUtil.findNearestOwnedFox(player, RANGE);
        if (fox != null) {
            openFoxActionMenu(player, fox);
        } else {
            openFoxList(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }

    // === Fox action menu (when fox is nearby) ===

    private void openFoxActionMenu(Player player, ITamableFoxAdapter fox) {
        org.bukkit.entity.Entity bukkitFox = fox.getBukkitEntity();
        String foxName = (bukkitFox != null && bukkitFox.getCustomName() != null)
            ? ChatColor.stripColor(bukkitFox.getCustomName()) : "Fox";

        String title = "&8" + foxName + " &7Menu";
        Gui gui = new Gui(title, 5);

        // Border
        ItemStack border = new ItemBuilder(XMaterial.GRAY_STAINED_GLASS_PANE).name(" ").build();
        gui.fill(border);

        ItemStack cyanGlass = new ItemBuilder(XMaterial.CYAN_STAINED_GLASS_PANE).name(" ").build();
        for (int slot : new int[]{4, 5, 36, 40, 44}) gui.item(slot, cyanGlass);

        // Fox info header
        int health = (int) fox.getHealth();
        int maxHealth = (int) fox.getMaxHealth();
        String state = fox.isOrderedToSleep() ? "Sleeping" : fox.isOrderedToSit() ? "Sitting" : "Standing";
        String healthColor = health > maxHealth * 0.6 ? "&a" : health > maxHealth * 0.3 ? "&e" : "&c";

        gui.item(4, new ItemBuilder(XMaterial.FOX_SPAWN_EGG)
            .name("&6" + foxName)
            .lore("",
                "&7Health: " + healthColor + health + " &7/ " + maxHealth,
                "&7State: &f" + state,
                "&7World: &f" + (bukkitFox != null ? bukkitFox.getWorld().getName() : "?"),
                "")
            .build());

        // Teleport
        gui.item(10, new ItemBuilder(XMaterial.ENDER_PEARL)
            .name("&bTeleport to Fox")
            .lore("&7Teleport your fox to you")
            .build());
        gui.onClick(10, (event, g) -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            ITamableFoxAdapter target = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (target == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            target.getBukkitEntity().teleport(p);
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox teleported!");
            p.closeInventory();
        });

        // Sit
        gui.item(12, new ItemBuilder(XMaterial.OAK_FENCE_GATE)
            .name("&eToggle Sit")
            .lore("&7Make your fox sit or stand")
            .build());
        gui.onClick(12, (event, g) -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            ITamableFoxAdapter target = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (target == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            target.setOrderedToSleep(false);
            target.setOrderedToSit(!target.isOrderedToSit());
            target.setDeltaMovement(0, 0, 0);
            String s = target.isOrderedToSit() ? ChatColor.GREEN + "sitting" : ChatColor.YELLOW + "standing";
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox is now " + s);
            updateFoxDb(target);
            p.closeInventory();
        });

        // Sleep
        gui.item(14, new ItemBuilder(XMaterial.RED_BED)
            .name("&dToggle Sleep")
            .lore("&7Make your fox sleep or wake up")
            .build());
        gui.onClick(14, (event, g) -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            ITamableFoxAdapter target = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (target == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            target.setOrderedToSit(false);
            target.setOrderedToSleep(!target.isOrderedToSleep());
            target.setDeltaMovement(0, 0, 0);
            String s = target.isOrderedToSleep() ? ChatColor.LIGHT_PURPLE + "sleeping" : ChatColor.YELLOW + "awake";
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox is now " + s);
            updateFoxDb(target);
            p.closeInventory();
        });

        // Heal
        gui.item(16, new ItemBuilder(XMaterial.GOLDEN_APPLE)
            .name("&aHeal Fox")
            .lore("&7Fully heal your fox")
            .build());
        gui.onClick(16, (event, g) -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            ITamableFoxAdapter target = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (target == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            float missing = target.getMaxHealth() - target.getHealth();
            if (missing <= 0) {
                p.sendMessage(Config.getPrefix() + ChatColor.YELLOW + "Fox is already at full health!");
            } else {
                target.heal(missing);
                p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox healed!");
                updateFoxDb(target);
            }
            p.closeInventory();
        });

        // Rename
        gui.item(22, new ItemBuilder(XMaterial.NAME_TAG)
            .name("&dRename Fox")
            .lore("&7Give your fox a name")
            .build());
        gui.onClick(22, (event, g) -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            ITamableFoxAdapter target = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (target == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            p.closeInventory();
            TamableFoxLogic.openRenameGui(target);
        });

        // Health bar display
        String bar = buildProgressBar(health, maxHealth, 20);
        gui.item(31, new ItemBuilder(XMaterial.RED_DYE)
            .name("&cHealth: " + healthColor + health + "&7 / " + maxHealth)
            .lore("", bar, "")
            .build());

        // Status display
        XMaterial stateMat = fox.isOrderedToSleep() ? XMaterial.RED_BED : fox.isOrderedToSit() ? XMaterial.OAK_FENCE_GATE : XMaterial.FEATHER;
        String stateColor = fox.isOrderedToSleep() ? "&d" : fox.isOrderedToSit() ? "&e" : "&a";
        gui.item(32, new ItemBuilder(stateMat)
            .name("&6State: " + stateColor + state)
            .build());

        gui.sound(true).open(player);
    }

    // === Fox list (when no fox nearby — from SQLite) ===

    private void openFoxList(Player player) {
        List<Map<String, Object>> foxes = SQLiteHelper.getInstance(plugin()).getPlayerFoxes(player.getUniqueId());

        if (foxes.isEmpty()) {
            player.sendMessage(Config.getPrefix() + ChatColor.RED + LanguageConfig.getFoxNoFoxFound());
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> foxData : foxes) {
            String name = foxData.get("name") != null && !((String) foxData.get("name")).isEmpty()
                ? (String) foxData.get("name") : "Fox";
            String world = (String) foxData.get("world");
            double x = (double) foxData.get("x");
            double y = (double) foxData.get("y");
            double z = (double) foxData.get("z");
            double health = (double) foxData.get("health");
            double maxHealth = (double) foxData.get("maxHealth");
            boolean sitting = (boolean) foxData.get("sitting");
            boolean sleeping = (boolean) foxData.get("sleeping");

            String state = sleeping ? "Sleeping" : sitting ? "Sitting" : "Standing";
            String healthColor = health > maxHealth * 0.6 ? "&a" : health > maxHealth * 0.3 ? "&e" : "&c";

            items.add(new ItemBuilder(XMaterial.FOX_SPAWN_EGG)
                .name("&6" + name)
                .lore("",
                    "&7Health: " + healthColor + (int) health + "&7/" + (int) maxHealth,
                    "&7State: &f" + state,
                    "&7World: &f" + world,
                    "&7XYZ: &f" + (int) x + " / " + (int) y + " / " + (int) z,
                    "",
                    "&aClick to teleport to this fox",
                    "")
                .build());
        }

        PagedGui paged = PagedGui.create("&8Your Foxes (" + foxes.size() + ")", 6, items);
        paged.previousPage(45, XMaterial.ARROW, "&cPrevious Page");
        paged.nextPage(53, XMaterial.ARROW, "&aNext Page");
        paged.sound(true);

        paged.onItemClick((event, gui) -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= p.getInventory().getSize()) return;

            // Find which fox was clicked based on slot position
            int slots = 6 * 9 - 2; // minus nav buttons
            int startIdx = paged.getCurrentPage() * slots;
            // Map raw slot to item index (skip nav slots)
            int contentIndex = 0;
            for (int i = 0; i < slot; i++) {
                if (i != 45 && i != 53) contentIndex++;
            }
            int foxIndex = startIdx + contentIndex;

            if (foxIndex >= 0 && foxIndex < foxes.size()) {
                Map<String, Object> foxData = foxes.get(foxIndex);
                teleportToFox(p, foxData);
            }
        });

        paged.open(player);
    }

    private void teleportToFox(Player player, Map<String, Object> foxData) {
        String worldName = (String) foxData.get("world");
        World world = player.getServer().getWorld(worldName);
        if (world == null) {
            player.sendMessage(Config.getPrefix() + ChatColor.RED + "World '" + worldName + "' is not loaded!");
            return;
        }

        double x = (double) foxData.get("x");
        double y = (double) foxData.get("y");
        double z = (double) foxData.get("z");

        // Try to find the actual fox entity first
        UUID foxUUID = UUID.fromString((String) foxData.get("foxUUID"));
        ITamableFoxAdapter fox = TamableFoxUtil.findFoxByUUID(world, foxUUID);
        if (fox != null) {
            fox.getBukkitEntity().teleport(player);
            player.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox teleported to you!");
        } else {
            // Fox not loaded — teleport player to fox's last location
            player.teleport(new org.bukkit.Location(world, x, y, z));
            player.sendMessage(Config.getPrefix() + ChatColor.YELLOW + "Teleported to fox's last known location.");
        }
    }

    // === Helpers ===

    private void updateFoxDb(ITamableFoxAdapter fox) {
        try {
            org.bukkit.entity.Entity bukkit = fox.getBukkitEntity();
            if (bukkit == null) return;
            String name = bukkit.getCustomName() != null ? ChatColor.stripColor(bukkit.getCustomName()) : "";
            SQLiteHelper.getInstance(plugin()).updateFox(
                bukkit.getUniqueId(), name,
                fox.getHealth(), fox.getMaxHealth(),
                fox.isOrderedToSit(), fox.isOrderedToSleep()
            );
        } catch (Exception ignored) {}
    }

    private String buildProgressBar(int current, int max, int bars) {
        double ratio = Math.min(1.0, (double) current / max);
        int filled = (int) (ratio * bars);
        int empty = bars - filled;
        StringBuilder bar = new StringBuilder("&7[");
        for (int i = 0; i < filled; i++) bar.append("&a|");
        for (int i = 0; i < empty; i++) bar.append("&c|");
        bar.append("&7]");
        return bar.toString();
    }

    private static TamableFoxes plugin() {
        return TamableFoxes.getPlugin();
    }
}

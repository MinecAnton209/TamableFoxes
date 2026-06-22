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
    private static final long COOLDOWN_TELEPORT = 120_000;
    private static final long COOLDOWN_STATE    = 5_000;
    private static final long COOLDOWN_RENAME   = 3_600_000;

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> clickGuard = new HashSet<>();

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

    // === Cooldowns ===

    private boolean isOnCooldown(Player player, String action, long cooldownMs) {
        Map<String, Long> pc = cooldowns.get(player.getUniqueId());
        if (pc == null) return false;
        Long last = pc.get(action);
        return last != null && System.currentTimeMillis() - last < cooldownMs;
    }

    private long getCooldownRemaining(Player player, String action, long cooldownMs) {
        Map<String, Long> pc = cooldowns.get(player.getUniqueId());
        if (pc == null) return 0;
        Long last = pc.get(action);
        if (last == null) return 0;
        return Math.max(0, cooldownMs - (System.currentTimeMillis() - last));
    }

    private void setCooldown(Player player, String action) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
            .put(action, System.currentTimeMillis());
    }

    private String fmtCd(long ms) {
        if (ms <= 0) return null;
        long sec = ms / 1000;
        if (sec >= 60) return (sec / 60) + "m " + (sec % 60) + "s";
        return sec + "s";
    }

    // === Action menu ===

    private void openFoxActionMenu(Player player, ITamableFoxAdapter fox) {
        org.bukkit.entity.Entity bukkitFox = fox.getBukkitEntity();
        String foxName = (bukkitFox != null && bukkitFox.getCustomName() != null)
            ? ChatColor.stripColor(bukkitFox.getCustomName()) : "Fox";

        Gui gui = new Gui("&8" + foxName + " &7Menu", 5);

        // Borders
        ItemStack gray = new ItemBuilder(XMaterial.GRAY_STAINED_GLASS_PANE).name(" ").build();
        gui.fill(gray);
        ItemStack cyan = new ItemBuilder(XMaterial.CYAN_STAINED_GLASS_PANE).name(" ").build();
        for (int s : new int[]{4, 5, 36, 40, 44}) gui.item(s, cyan);

        // === Row 1: Fox info header ===
        int health = (int) fox.getHealth();
        int maxHp = (int) fox.getMaxHealth();
        String state = fox.isOrderedToSleep() ? "Sleeping" : fox.isOrderedToSit() ? "Sitting" : "Standing";
        String hc = health > maxHp * 0.6 ? "&a" : health > maxHp * 0.3 ? "&e" : "&c";
        String worldName = bukkitFox != null ? bukkitFox.getWorld().getName() : "?";

        gui.item(4, new ItemBuilder(XMaterial.FOX_SPAWN_EGG)
            .name("&6" + foxName)
            .lore("",
                "&7Health: " + hc + health + "&7/&c" + maxHp,
                "&7State: &f" + state,
                "&7World: &f" + worldName,
                "")
            .build());

        // === Row 2: Teleport / Follow ===

        // Teleport fox → player
        long tpCd = getCooldownRemaining(player, "teleport", COOLDOWN_TELEPORT);
        gui.item(11, tpCd > 0
            ? new ItemBuilder(XMaterial.ENDER_PEARL).name("&7Teleport Fox to You").lore("&cCooldown: " + fmtCd(tpCd)).build()
            : new ItemBuilder(XMaterial.ENDER_PEARL).name("&bTeleport Fox to You").lore("&7Bring your fox to you").build());
        gui.onClick(11, (e, g) -> {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (isOnCooldown(p, "teleport", COOLDOWN_TELEPORT)) { refresh(p); return; }
            ITamableFoxAdapter t = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (t == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            t.getBukkitEntity().teleport(p);
            setCooldown(p, "teleport");
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox teleported to you!");
            refresh(p);
        });

        // Follow / Wander
        UUID foxUUID = bukkitFox != null ? bukkitFox.getUniqueId() : null;
        boolean following = foxUUID != null && TamableFoxLogic.isFollowing(foxUUID);
        gui.item(13, new ItemBuilder(following ? XMaterial.LEAD : XMaterial.FEATHER)
            .name("&6Follow Mode")
            .lore("", "&7State: " + (following ? "&aFollowing" : "&eWandering"), "&7Click to toggle", "")
            .build());
        gui.onClick(13, (e, g) -> {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (foxUUID == null) return;
            TamableFoxLogic.markKnown(foxUUID);
            boolean nf = !TamableFoxLogic.isFollowing(foxUUID);
            TamableFoxLogic.setFollowing(foxUUID, nf);
            SQLiteHelper.getInstance(plugin()).setFoxFollowing(foxUUID, nf);
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox mode: " + (nf ? ChatColor.GREEN + "Following" : ChatColor.YELLOW + "Wandering"));
            refresh(p);
        });

        // === Row 3: Sit / Rename / Sleep ===

        // Sit
        long sitCd = getCooldownRemaining(player, "sit", COOLDOWN_STATE);
        gui.item(20, sitCd > 0
            ? new ItemBuilder(XMaterial.OAK_FENCE_GATE).name("&7Toggle Sit").lore("&cCooldown: " + fmtCd(sitCd)).build()
            : new ItemBuilder(XMaterial.OAK_FENCE_GATE).name("&eToggle Sit").lore("&7Make your fox sit or stand").build());
        gui.onClick(20, (e, g) -> {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (isOnCooldown(p, "sit", COOLDOWN_STATE)) { refresh(p); return; }
            ITamableFoxAdapter t = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (t == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            t.setOrderedToSleep(false);
            t.setOrderedToSit(!t.isOrderedToSit());
            t.setDeltaMovement(0, 0, 0);
            setCooldown(p, "sit");
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox is now " + (t.isOrderedToSit() ? ChatColor.GREEN + "sitting" : ChatColor.YELLOW + "standing"));
            updateFoxDb(t);
            refresh(p);
        });

        // Rename
        long rnCd = getCooldownRemaining(player, "rename", COOLDOWN_RENAME);
        gui.item(22, rnCd > 0
            ? new ItemBuilder(XMaterial.NAME_TAG).name("&7Rename Fox").lore("&cCooldown: " + fmtCd(rnCd)).build()
            : new ItemBuilder(XMaterial.NAME_TAG).name("&dRename Fox").lore("&7Give your fox a name").build());
        gui.onClick(22, (e, g) -> {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (isOnCooldown(p, "rename", COOLDOWN_RENAME)) { refresh(p); return; }
            ITamableFoxAdapter t = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (t == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            setCooldown(p, "rename");
            p.closeInventory();
            TamableFoxLogic.openRenameGui(t);
        });

        // Sleep
        long slCd = getCooldownRemaining(player, "sleep", COOLDOWN_STATE);
        gui.item(24, slCd > 0
            ? new ItemBuilder(XMaterial.RED_BED).name("&7Toggle Sleep").lore("&cCooldown: " + fmtCd(slCd)).build()
            : new ItemBuilder(XMaterial.RED_BED).name("&dToggle Sleep").lore("&7Make your fox sleep or wake up").build());
        gui.onClick(24, (e, g) -> {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (isOnCooldown(p, "sleep", COOLDOWN_STATE)) { refresh(p); return; }
            ITamableFoxAdapter t = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
            if (t == null) { p.sendMessage(Config.getPrefix() + ChatColor.RED + "Fox not found!"); return; }
            t.setOrderedToSit(false);
            t.setOrderedToSleep(!t.isOrderedToSleep());
            t.setDeltaMovement(0, 0, 0);
            setCooldown(p, "sleep");
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox is now " + (t.isOrderedToSleep() ? ChatColor.LIGHT_PURPLE + "sleeping" : ChatColor.YELLOW + "awake"));
            updateFoxDb(t);
            refresh(p);
        });

        // === Row 4: Health / Combat / Status ===

        String bar = buildProgressBar(health, maxHp, 20);
        gui.item(31, new ItemBuilder(XMaterial.RED_DYE)
            .name("&cHealth: " + hc + health + "&7/&c" + maxHp)
            .lore("", bar, "")
            .build());

        // Aggressive
        boolean aggressive = foxUUID != null && TamableFoxLogic.isAggressive(foxUUID);
        gui.item(32, new ItemBuilder(aggressive ? XMaterial.IRON_SWORD : XMaterial.SHIELD)
            .name("&6Combat Mode")
            .lore("", "&7State: " + (aggressive ? "&cAggressive" : "&aPassive"), "&7Attack hostile mobs near you", "")
            .build());
        gui.onClick(32, (e, g) -> {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (foxUUID == null) return;
            TamableFoxLogic.markKnown(foxUUID);
            boolean na = !TamableFoxLogic.isAggressive(foxUUID);
            TamableFoxLogic.setAggressive(foxUUID, na);
            SQLiteHelper.getInstance(plugin()).setFoxAggressive(foxUUID, na);
            if (!na) {
                ITamableFoxAdapter t = TamableFoxUtil.findNearestOwnedFox(p, RANGE);
                if (t != null) t.clearTarget();
            }
            p.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox mode: " + (na ? ChatColor.RED + "Aggressive" : ChatColor.GREEN + "Passive"));
            refresh(p);
        });

        // State
        XMaterial stateMat = fox.isOrderedToSleep() ? XMaterial.RED_BED : fox.isOrderedToSit() ? XMaterial.OAK_FENCE_GATE : XMaterial.FEATHER;
        String stateColor = fox.isOrderedToSleep() ? "&d" : fox.isOrderedToSit() ? "&e" : "&a";
        gui.item(33, new ItemBuilder(stateMat).name("&6State: " + stateColor + state).build());

        gui.sound(true).open(player);
    }

    private void refresh(Player player) {
        UUID pid = player.getUniqueId();
        if (clickGuard.contains(pid)) return;
        clickGuard.add(pid);
        org.bukkit.Bukkit.getScheduler().runTask(plugin(), () -> {
            try {
                ITamableFoxAdapter fox = TamableFoxUtil.findNearestOwnedFox(player, RANGE);
                if (fox != null) openFoxActionMenu(player, fox);
            } finally {
                clickGuard.remove(pid);
            }
        });
    }

    // === Fox list (from SQLite) ===

    private void openFoxList(Player player) {
        List<Map<String, Object>> foxes = SQLiteHelper.getInstance(plugin()).getPlayerFoxes(player.getUniqueId());

        if (foxes.isEmpty()) {
            player.sendMessage(Config.getPrefix() + ChatColor.RED + LanguageConfig.getFoxNoFoxFound());
            return;
        }

        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> fd : foxes) {
            String name = fd.get("name") != null && !((String) fd.get("name")).isEmpty()
                ? (String) fd.get("name") : "Fox";
            String world = (String) fd.get("world");
            double x = (double) fd.get("x"), y = (double) fd.get("y"), z = (double) fd.get("z");
            double hp = (double) fd.get("health"), mhp = (double) fd.get("maxHealth");
            boolean sit = (boolean) fd.get("sitting"), slp = (boolean) fd.get("sleeping");
            String state = slp ? "Sleeping" : sit ? "Sitting" : "Standing";
            String hc = hp > mhp * 0.6 ? "&a" : hp > mhp * 0.3 ? "&e" : "&c";

            items.add(new ItemBuilder(XMaterial.FOX_SPAWN_EGG)
                .name("&6" + name)
                .lore("",
                    "&7Health: " + hc + (int) hp + "&7/" + (int) mhp,
                    "&7State: &f" + state,
                    "&7World: &f" + world,
                    "&7XYZ: &f" + (int) x + " / " + (int) y + " / " + (int) z,
                    "",
                    "&aClick to teleport to this fox", "")
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
            int contentSlots = 6 * 9 - 2;
            int startIdx = paged.getCurrentPage() * contentSlots;
            int ci = 0;
            for (int i = 0; i < slot; i++) { if (i != 45 && i != 53) ci++; }
            int fi = startIdx + ci;
            if (fi >= 0 && fi < foxes.size()) teleportToFox(p, foxes.get(fi));
        });

        paged.open(player);
    }

    private void teleportToFox(Player player, Map<String, Object> fd) {
        String wn = (String) fd.get("world");
        World w = player.getServer().getWorld(wn);
        if (w == null) { player.sendMessage(Config.getPrefix() + ChatColor.RED + "World not loaded!"); return; }

        UUID foxUUID = UUID.fromString((String) fd.get("foxUUID"));
        ITamableFoxAdapter fox = TamableFoxUtil.findFoxByUUID(w, foxUUID);
        if (fox != null) {
            fox.getBukkitEntity().teleport(player);
            player.sendMessage(Config.getPrefix() + ChatColor.GREEN + "Fox teleported to you!");
        } else {
            player.teleport(new org.bukkit.Location(w, (double) fd.get("x"), (double) fd.get("y"), (double) fd.get("z")));
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

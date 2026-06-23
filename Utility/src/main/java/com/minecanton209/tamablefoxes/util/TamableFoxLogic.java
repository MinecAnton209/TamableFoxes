package com.minecanton209.tamablefoxes.util;

import java.util.*;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.LanguageConfig;
import com.minecanton209.tamablefoxes.util.io.sqlite.SQLiteHelper;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;

/**
 * Shared logic for all version-specific EntityTamableFox implementations.
 * Uses ITamableFoxAdapter to call version-specific NMS operations.
 * 
 * WHEN YOU ADD A NEW FEATURE: add the logic here, not in 26 separate files.
 */
public final class TamableFoxLogic {

    private TamableFoxLogic() {}

    /**
     * Core mobInteract logic. Call this from each version's mobInteract().
     * Returns the result to return from mobInteract, or null if super should be called.
     */
    public static Object handleMobInteract(
            ITamableFoxAdapter fox,
            Object itemstack,      // NMS ItemStack
            Object hand            // NMS InteractionHand
    ) {
        // Skip spawn eggs
        if (fox.isSpawnEgg(itemstack)) {
            return null; // signal: call super.mobInteract()
        }

        if (fox.isTamed()) {
            return handleTamedInteraction(fox, itemstack, hand);
        } else {
            return handleUntamedInteraction(fox, itemstack);
        }
    }

    private static Object handleTamedInteraction(
            ITamableFoxAdapter fox,
            Object itemstack,
            Object hand
    ) {
        // Heal with meat
        if (fox.isEdible(itemstack) && fox.isMeat(itemstack) && fox.getHealth() < fox.getMaxHealth()) {
            int nutrition = fox.getFoodNutrition(itemstack);
            if (nutrition > 0) {
                Player player = fox.getBukkitPlayer();
                if (player.getGameMode() != GameMode.CREATIVE) {
                    fox.shrinkItem(itemstack, 1);
                }
                fox.heal(nutrition);
                return "CONSUME"; // InteractionResult.CONSUME
            }
        }

        // Interact with owner — use UUID comparison (Bukkit Player != NMS LivingEntity)
        if (isOwnedBy(fox) && isMainHand(hand)) {
            Player player = fox.getBukkitPlayer();
            boolean sneaking = player != null && player.isSneaking();

            if (!sneaking) {
                // Not sneaking: toggle sit or show nametag
                if (isNameTag(fox, itemstack)) {
                    openRenameGui(fox);
                    return "PASS";
                }

                // Toggle sit
                fox.setOrderedToSleep(false);
                fox.setOrderedToSit(!fox.isOrderedToSit());
                fox.setDeltaMovement(0, 0, 0);
                return "SUCCESS";
            } else {
                // Sneaking: item management
                if (isBucket(fox, itemstack)) {
                    return "PASS";
                }

                if (fox.hasItemInMainHand()) {
                    // Fox has item in mouth → drop it
                    fox.dropItemFromMouth();
                    fox.setItemSlotMainHandAir();
                } else if (!fox.hasItemInMainHand() && !isEmptyItem(fox, itemstack) && fox.hasPermission("tamablefoxes.sneak.interact")) {
                    // Player holds item + has permission → put item in fox's mouth
                    Bukkit.getScheduler().runTaskLaterAsynchronously(
                        Utils.tamableFoxesPlugin, () -> {
                            if (fox.hasPermission("tamablefoxes.sneak.interact")) {
                                Object copy = fox.copyItemStack(itemstack);
                                setItemCount(copy, 1);
                                if (player.getGameMode() != GameMode.CREATIVE) {
                                    fox.shrinkItem(itemstack, 1);
                                }
                                fox.setItemSlotMainHand(copy);
                            }
                        }, 1L
                    );
                    return "SUCCESS";
                } else {
                    // Empty hand → toggle sleep
                    fox.setOrderedToSit(false);
                    fox.setOrderedToSleep(!fox.isOrderedToSleep());
                    fox.setDeltaMovement(0, 0, 0);
                    return "SUCCESS";
                }
            }
        }

        return null; // call super
    }

    private static Object handleUntamedInteraction(
            ITamableFoxAdapter fox,
            Object itemstack
    ) {
        if (!isTamingFood(fox, itemstack)) {
            return null;
        }

        Player player = fox.getBukkitPlayer();

        // Permission check
        if (!Config.canPlayerTameFox(player)) {
            return "SUCCESS";
        }

        // Tame limit check
        if (!player.hasPermission("tamablefoxes.tame.unlimited")
                && TamableFoxUtil.isTameLimitExceeded(player.getUniqueId())) {
            sendFoxMessage(player, LanguageConfig.getFoxDoesntTrust());
            return "SUCCESS";
        }

        // Remove taming food item
        if (player.getGameMode() != GameMode.CREATIVE) {
            fox.shrinkItem(itemstack, 1);
        }

        // Attempt tame
        double chance = Config.getTamingChance();
        if (Math.random() < chance) {
            // Success — set owner BEFORE taming so reassessTameGoals() works
            fox.setOwnerUUID(player.getUniqueId());
            fox.setTamed(true);
            TamableFoxUtil.incrementTameCount(player.getUniqueId());

            // Wake up fox if it was sleeping
            fox.setOrderedToSleep(false);
            fox.setOrderedToSit(false);
            fox.setDeltaMovement(0, 0, 0);

            // Heal to full
            fox.heal((float) (fox.getMaxHealth() - fox.getHealth()));

            // Apply stats from config
            applyTamedStats(fox);

            // Register in SQLite
            registerFoxInDb(fox);

            // Taming effects
            spawnTamingEffects(fox);

            // Show name if configured
            if (Config.doesShowOwnerInFoxName()) {
                fox.setFoxCustomName(ChatColor.GREEN + player.getName() + "'s Fox");
                fox.setFoxCustomNameVisible(true);
            }

            // Ask for name
            if (Config.askForNameAfterTaming()) {
                Bukkit.getScheduler().runTaskLater(
                    Utils.tamableFoxesPlugin,
                    () -> openRenameGui(fox),
                    1L
                );
            }

            return "SUCCESS";
        } else {
            // Failure particles
            fox.spawnSmokeParticle();
            sendFoxMessage(player, LanguageConfig.getFoxDoesntTrust());
            return "SUCCESS";
        }
    }

    // === SQLite fox registry ===

    private static void registerFoxInDb(ITamableFoxAdapter fox) {
        try {
            org.bukkit.entity.Entity bukkit = fox.getBukkitEntity();
            if (bukkit == null) {
                if (Config.isDebug()) Utils.tamableFoxesPlugin.getLogger().info("[FoxDB] registerFoxInDb: bukkit entity is null");
                return;
            }
            Location loc = bukkit.getLocation();
            String name = bukkit.getCustomName() != null ? ChatColor.stripColor(bukkit.getCustomName()) : "";
            if (Config.isDebug()) Utils.tamableFoxesPlugin.getLogger().info("[FoxDB] Registering fox " + bukkit.getUniqueId() + " owner=" + fox.getOwnerUUID());
            SQLiteHelper.getInstance(Utils.tamableFoxesPlugin).registerFox(
                bukkit.getUniqueId(),
                fox.getOwnerUUID(),
                name,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                fox.getHealth(),
                fox.getMaxHealth(),
                fox.isOrderedToSit(),
                fox.isOrderedToSleep()
            );
            if (Config.isDebug()) Utils.tamableFoxesPlugin.getLogger().info("[FoxDB] Fox registered successfully");
            aggressiveFoxes.add(bukkit.getUniqueId());
            knownFoxes.add(bukkit.getUniqueId());
        } catch (Exception e) {
            Utils.tamableFoxesPlugin.getLogger().severe("[FoxDB] Failed to register fox: " + e.getMessage());
            if (Config.isDebug()) e.printStackTrace();
        }
    }

    // === Taming food check ===

    private static boolean isTamingFood(ITamableFoxAdapter fox, Object itemstack) {
        org.bukkit.inventory.ItemStack bukkit = fox.toBukkitItemStack(itemstack);
        if (bukkit == null) return false;
        String material = bukkit.getType().name();
        for (String food : Config.getTamingFoodItems()) {
            if (food.equalsIgnoreCase(material)) return true;
        }
        return false;
    }

    // === Taming effects ===

    private static void applyTamedStats(ITamableFoxAdapter fox) {
        double maxHealth = Config.getTamedMaxHealth();
        fox.setMaxHealth(maxHealth);
        fox.heal((float) (maxHealth - fox.getHealth()));

        double attackDamage = Config.getTamedAttackDamage();
        if (attackDamage > 0) {
            fox.setAttributeAttackDamage(attackDamage);
        }
    }

    private static void spawnTamingEffects(ITamableFoxAdapter fox) {
        if (Config.doesTamingShowHearts()) {
            fox.spawnHeartParticle();
        }

        String soundName = Config.getTamingSound();
        if (soundName != null && !soundName.equalsIgnoreCase("disabled")) {
            Player player = fox.getBukkitPlayer();
            if (player != null) {
                XSound.matchXSound(soundName).ifPresent(xsound ->
                    xsound.play(player, 1.0f, 1.0f));
            }
        }
    }

    // === Rename GUI ===

    public static void openRenameGui(ITamableFoxAdapter fox) {
        Player player = fox.getBukkitPlayer();
        try {
            new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    String text = stateSnapshot.getText();
                    if (slot == AnvilGUI.Slot.OUTPUT && !text.isEmpty()) {
                        String foxName = LanguageConfig.getFoxNameFormat(text, player.getDisplayName());
                        fox.setFoxCustomName(foxName);
                        fox.setFoxCustomNameVisible(true);
                        org.bukkit.entity.Entity bukkit = fox.getBukkitEntity();
                        if (bukkit != null) {
                            SQLiteHelper.getInstance(Utils.tamableFoxesPlugin).updateFoxName(
                                bukkit.getUniqueId(), ChatColor.stripColor(foxName));
                        }
                        if (!LanguageConfig.getTamingChosenPerfect(text).equalsIgnoreCase("disabled")) {
                            stateSnapshot.getPlayer().sendMessage(
                                Config.getPrefix() + ChatColor.GREEN + LanguageConfig.getTamingChosenPerfect(text));
                        }
                    } else if (!LanguageConfig.getTamingChosenPerfect(text).equalsIgnoreCase("disabled")) {
                        stateSnapshot.getPlayer().sendMessage(
                            Config.getPrefix() + ChatColor.GRAY + "The fox was not named");
                    }
                    return Arrays.asList(AnvilGUI.ResponseAction.close());
                })
                .text("Fox name")
                .title("Name your new friend!")
                .plugin(Utils.tamableFoxesPlugin)
                .open(player);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // === Utility helpers ===

    public static boolean isTamed(boolean tamed, UUID ownerUUID) {
        return TamableFoxUtil.isTamed(tamed, ownerUUID);
    }

    public static void sendFoxMessage(Player player, String message) {
        if (message != null && !message.equalsIgnoreCase("disabled")) {
            player.sendMessage(Config.getPrefix() + ChatColor.RED + message);
        }
    }

    // === NMS-agnostic checks (using XMaterial) ===

    private static boolean isOwnedBy(ITamableFoxAdapter fox) {
        Player player = fox.getBukkitPlayer();
        if (player == null) return false;
        java.util.UUID ownerUUID = fox.getOwnerUUID();
        return ownerUUID != null && ownerUUID.equals(player.getUniqueId());
    }

    private static boolean isMainHand(Object hand) {
        return hand != null && hand.toString().contains("MAIN_HAND");
    }

    private static boolean isNameTag(ITamableFoxAdapter fox, Object itemstack) {
        org.bukkit.inventory.ItemStack bukkit = fox.toBukkitItemStack(itemstack);
        return bukkit != null && XMaterial.matchXMaterial(bukkit.getType()) == XMaterial.NAME_TAG;
    }

    private static boolean isBucket(ITamableFoxAdapter fox, Object itemstack) {
        org.bukkit.inventory.ItemStack bukkit = fox.toBukkitItemStack(itemstack);
        if (bukkit == null) return false;
        XMaterial mat = XMaterial.matchXMaterial(bukkit.getType());
        return mat.name().endsWith("_BUCKET") || mat == XMaterial.BUCKET;
    }

    private static boolean isEmptyItem(ITamableFoxAdapter fox, Object itemstack) {
        org.bukkit.inventory.ItemStack bukkit = fox.toBukkitItemStack(itemstack);
        return bukkit == null || bukkit.getType() == org.bukkit.Material.AIR;
    }

    private static void setItemCount(Object itemstack, int count) {
        try {
            java.lang.reflect.Method setCount = itemstack.getClass().getMethod("setCount", int.class);
            setCount.invoke(itemstack, count);
        } catch (Exception ignored) {}
    }

    // === Follow behavior (rubber band) ===

    private static BukkitRunnable followTask;
    private static final Set<UUID> followingFoxes = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> aggressiveFoxes = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> knownFoxes = Collections.synchronizedSet(new HashSet<>());
    private static final double FOLLOW_RANGE_SQ = 10.0 * 10.0;
    private static final double FOLLOW_TP_RANGE_SQ = 16.0 * 16.0;

    public static void markKnown(UUID foxUUID) {
        knownFoxes.add(foxUUID);
    }

    public static boolean isRegisteredFox(UUID foxUUID) {
        return knownFoxes.contains(foxUUID);
    }

    public static void setFollowing(UUID foxUUID, boolean following) {
        if (following) followingFoxes.add(foxUUID);
        else followingFoxes.remove(foxUUID);
    }

    public static boolean isFollowing(UUID foxUUID) {
        return followingFoxes.contains(foxUUID);
    }

    public static void setAggressive(UUID foxUUID, boolean aggressive) {
        if (aggressive) aggressiveFoxes.add(foxUUID);
        else aggressiveFoxes.remove(foxUUID);
    }

    public static boolean isAggressive(UUID foxUUID) {
        return aggressiveFoxes.contains(foxUUID);
    }

    public static void startFollowTask() {
        // Load existing fox states from DB
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                ITamableFoxAdapter adapter = TamableFoxUtil.toAdapter(entity);
                if (adapter != null && adapter.getOwnerUUID() != null) {
                    UUID foxUUID = entity.getUniqueId();
                    SQLiteHelper db = SQLiteHelper.getInstance(Utils.tamableFoxesPlugin);
                    knownFoxes.add(foxUUID);
                    if (db.isFoxRegistered(foxUUID)) {
                        if (db.getFoxFollowing(foxUUID)) followingFoxes.add(foxUUID);
                        if (db.getFoxAggressive(foxUUID)) aggressiveFoxes.add(foxUUID);
                    } else {
                        aggressiveFoxes.add(foxUUID);
                    }
                }
            }
        }

        if (followTask != null) return;
        followTask = new BukkitRunnable() {
            private int tick = 0;
            @Override
            public void run() {
                if (followingFoxes.isEmpty()) return;
                tick++;
                boolean updatePos = tick % 100 == 0;
                SQLiteHelper db = updatePos ? SQLiteHelper.getInstance(Utils.tamableFoxesPlugin) : null;
                for (UUID foxUUID : new HashSet<>(followingFoxes)) {
                    ITamableFoxAdapter fox = null;
                    for (org.bukkit.World world : Bukkit.getWorlds()) {
                        fox = TamableFoxUtil.findFoxByUUID(world, foxUUID);
                        if (fox != null) break;
                    }
                    if (fox == null) continue;

                    UUID ownerUUID = fox.getOwnerUUID();
                    if (ownerUUID == null) {
                        followingFoxes.remove(foxUUID);
                        continue;
                    }
                    Player owner = Bukkit.getPlayer(ownerUUID);
                    if (owner == null || !owner.isOnline()) continue;

                    org.bukkit.entity.Entity bukkitFox = fox.getBukkitEntity();
                    if (bukkitFox == null) continue;

                    if (fox.isOrderedToSleep() || fox.isOrderedToSit()) {
                        if (updatePos) {
                            org.bukkit.Location loc = bukkitFox.getLocation();
                            db.updateFoxLocation(foxUUID, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                        }
                        continue;
                    }

                    if (!bukkitFox.getWorld().equals(owner.getWorld())) {
                        if (!fox.isOrderedToSit()) {
                            fox.setOrderedToSit(true);
                            fox.setDeltaMovement(0, 0, 0);
                        }
                        if (updatePos) {
                            org.bukkit.Location loc = bukkitFox.getLocation();
                            db.updateFoxLocation(foxUUID, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                        }
                        continue;
                    }

                    double dist = bukkitFox.getLocation().distanceSquared(owner.getLocation());
                    if (dist > FOLLOW_TP_RANGE_SQ) {
                        bukkitFox.teleport(owner.getLocation().add(
                            (Math.random() - 0.5) * 3,
                            0,
                            (Math.random() - 0.5) * 3
                        ));
                        org.bukkit.Location loc = bukkitFox.getLocation();
                        db.updateFoxLocation(foxUUID, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                    } else if (dist > FOLLOW_RANGE_SQ) {
                        bukkitFox.teleport(owner.getLocation().add(
                            (Math.random() - 0.5) * 2,
                            0,
                            (Math.random() - 0.5) * 2
                        ));
                        org.bukkit.Location loc = bukkitFox.getLocation();
                        db.updateFoxLocation(foxUUID, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                    } else if (updatePos) {
                        org.bukkit.Location loc = bukkitFox.getLocation();
                        db.updateFoxLocation(foxUUID, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                    }
                    break;
                }
            }
        };
        followTask.runTaskTimer(Utils.tamableFoxesPlugin, 20L, 20L);
    }

    public static void stopFollowTask() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }
}

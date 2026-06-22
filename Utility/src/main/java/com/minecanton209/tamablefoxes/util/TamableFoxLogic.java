package com.minecanton209.tamablefoxes.util;

import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.LanguageConfig;
import com.minecanton209.tamablefoxes.util.io.sqlite.SQLiteHelper;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import java.util.Arrays;

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
            if (bukkit == null) return;
            Location loc = bukkit.getLocation();
            String name = bukkit.getCustomName() != null ? ChatColor.stripColor(bukkit.getCustomName()) : "";
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
        } catch (Exception ignored) {}
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
}

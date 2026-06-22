package com.minecanton209.tamablefoxes.util;

import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.LanguageConfig;
import com.cryptomorin.xseries.XMaterial;
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

        // Interact with owner
        if (fox.isOwnedBy(fox.getBukkitPlayer()) && isMainHand(hand)) {
            Object superResult = null; // placeholder for super.mobInteract result

            if (!fox.isCrouching()) {
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
                    fox.dropItemFromMouth();
                    fox.setItemSlotMainHandAir();
                } else if (!fox.hasPermission("tamablefoxes.sneak.interact")) {
                    // Empty hand + sneaking: toggle sleep
                    fox.setOrderedToSit(false);
                    fox.setOrderedToSleep(!fox.isOrderedToSleep());
                    fox.setDeltaMovement(0, 0, 0);
                } else {
                    // Put item in mouth
                    Bukkit.getScheduler().runTaskLaterAsynchronously(
                        Utils.tamableFoxesPlugin, () -> {
                            if (fox.hasPermission("tamablefoxes.sneak.interact")) {
                                Object copy = fox.copyItemStack(itemstack);
                                setItemCount(copy, 1);
                                Player player = fox.getBukkitPlayer();
                                if (player.getGameMode() != GameMode.CREATIVE) {
                                    fox.shrinkItem(itemstack, 1);
                                }
                                fox.setItemSlotMainHand(copy);
                            }
                        }, 1L
                    );
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
        if (!fox.isChicken(itemstack)) {
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

        // Remove chicken item
        if (player.getGameMode() != GameMode.CREATIVE) {
            fox.shrinkItem(itemstack, 1);
        }

        // Attempt tame
        if (Math.random() < 0.3D) {
            // Success
            fox.setTamed(true);
            fox.setOwnerUUID(player.getUniqueId());
            TamableFoxUtil.incrementTameCount(player.getUniqueId());

            // Heal to full
            fox.heal((float) (fox.getMaxHealth() - fox.getHealth()));

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

    private static void setItemCount(Object itemstack, int count) {
        try {
            java.lang.reflect.Method setCount = itemstack.getClass().getMethod("setCount", int.class);
            setCount.invoke(itemstack, count);
        } catch (Exception ignored) {}
    }
}

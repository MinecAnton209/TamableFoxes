package com.minecanton209.tamablefoxes.util;

import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Adapter interface that bridges NMS-specific operations to common logic.
 * Each version module provides an implementation of this interface.
 * TamableFoxLogic uses only this interface — it never touches NMS directly.
 *
 * Methods that may clash with NMS final methods (getHealth, getMaxHealth, getBukkitEntity)
 * are default to avoid override conflicts.
 */
public interface ITamableFoxAdapter {

    // === Entity state ===
    boolean isTamed();
    void setTamed(boolean tamed);
    UUID getOwnerUUID();
    void setOwnerUUID(UUID uuid);
    Object getOwner();
    void heal(float amount);
    boolean isBaby();
    boolean isCrouching();
    boolean isSpectator();
    void setDeltaMovement(double x, double y, double z);

    // Default: avoids clash with NMS LivingEntity.getMaxHealth() being final
    default float getMaxHealth() { return 20.0f; }
    // Default: avoids clash with NMS LivingEntity.getHealth() being final
    default float getHealth() { return 20.0f; }
    // Default: avoids clash with NMS Entity.getBukkitEntity() returning CraftEntity
    default org.bukkit.entity.Entity getBukkitEntity() { return null; }

    // === Item checks (version-specific NMS) ===
    boolean isSpawnEgg(Object itemstack);
    boolean isEdible(Object itemstack);
    boolean isMeat(Object itemstack);
    default boolean isChicken(Object itemstack) {
        if (itemstack == null) return false;
        try {
            java.lang.reflect.Method getItem = itemstack.getClass().getMethod("getItem");
            Object item = getItem.invoke(itemstack);
            if (item == null) return false;
            String[] pkgs = {
                "net.minecraft.world.item.Items",
                "net.minecraft.server.v1_14_R1.Items",
                "net.minecraft.server.v1_15_R1.Items",
                "net.minecraft.server.v1_16_R1.Items",
                "net.minecraft.server.v1_16_R2.Items",
                "net.minecraft.server.v1_16_R3.Items"
            };
            for (String pkg : pkgs) {
                try {
                    Object chicken = Class.forName(pkg).getField("CHICKEN").get(null);
                    if (item == chicken) return true;
                } catch (ClassNotFoundException ignored) {}
            }
        } catch (Exception ignored) {}
        return false;
    }
    int getFoodNutrition(Object itemstack);
    Object copyItemStack(Object itemstack);
    void shrinkItem(Object itemstack, int amount);
    boolean hasItemInMainHand();
    Object getItemInMainHand();
    void setItemSlotMainHand(Object item);
    void setItemSlotMainHandAir();
    void dropItemFromMouth();

    // === Goals (version-specific) ===
    void setOrderedToSit(boolean sit);
    boolean isOrderedToSit();
    void setOrderedToSleep(boolean sleep);
    boolean isOrderedToSleep();

    // === Particles ===
    void spawnSmokeParticle();

    // === Combat ===
    // Default: avoids clash with NMS LivingEntity parameter types
    default boolean isOwnedBy(Object entity) { return false; }
    default boolean wantsToAttack(Object target, Object owner) { return true; }
    boolean isDefending();
    void setDefending(boolean defending);

    // === Info ===
    void setInteractingPlayer(Player player);
    Player getBukkitPlayer();
    boolean hasPermission(String permission);

    // === Fox-specific ===
    default void setVariant(Object variant) {}
    default Object getVariant() { return null; }
    void setFoxCustomName(String name);
    void setFoxCustomNameVisible(boolean visible);
}

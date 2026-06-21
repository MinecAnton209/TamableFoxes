package com.minecanton209.tamablefoxes.util;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Adapter interface that bridges NMS-specific operations to common logic.
 * Each version module provides an implementation of this interface.
 * TamableFoxLogic uses only this interface — it never touches NMS directly.
 */
public interface ITamableFoxAdapter {

    // === Entity state (delegated to Fox parent) ===
    boolean isTamed();
    void setTamed(boolean tamed);
    UUID getOwnerUUID();
    void setOwnerUUID(UUID uuid);
    Object getOwner();
    float getHealth();
    float getMaxHealth();
    void heal(float amount);
    boolean isBaby();
    boolean isCrouching();
    boolean isSpectator();
    Entity getBukkitEntity();
    void setDeltaMovement(double x, double y, double z);

    // === Item checks (version-specific NMS) ===
    boolean isSpawnEgg(Object itemstack);
    boolean isEdible(Object itemstack);
    boolean isMeat(Object itemstack);
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
    boolean isOwnedBy(LivingEntity entity);
    boolean wantsToAttack(LivingEntity target, LivingEntity owner);
    boolean isDefending();
    void setDefending(boolean defending);

    // === Info ===
    void setInteractingPlayer(Player player);
    Player getBukkitPlayer();
    boolean hasPermission(String permission);

    // === Fox-specific ===
    // Default implementations: versions where Fox implements VariantHolder<Fox.Type>
    // inherit setVariant/getVariant from Fox directly and don't need to override.
    default void setVariant(Object variant) {}
    default Object getVariant() { return null; }
    void setFoxCustomName(String name);
    void setFoxCustomNameVisible(boolean visible);
}

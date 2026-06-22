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

    // Convert NMS ItemStack to Bukkit ItemStack for cross-version checks
    default org.bukkit.inventory.ItemStack toBukkitItemStack(Object itemstack) { return null; }

    // === Goals (version-specific) ===
    void setOrderedToSit(boolean sit);
    boolean isOrderedToSit();
    void setOrderedToSleep(boolean sleep);
    boolean isOrderedToSleep();

    // === Particles ===
    void spawnSmokeParticle();

    default void spawnHeartParticle() {
        org.bukkit.entity.Entity e = getBukkitEntity();
        if (e != null) {
            e.getWorld().spawnParticle(org.bukkit.Particle.HEART, e.getLocation(), 5);
        }
    }

    // === Stats (Bukkit API — works across all versions) ===
    default void setMaxHealth(double health) {
        try {
            org.bukkit.entity.Entity e = getBukkitEntity();
            if (!(e instanceof org.bukkit.entity.LivingEntity le)) return;
            org.bukkit.attribute.Attribute attr = null;
            try { attr = org.bukkit.attribute.Attribute.valueOf("MAX_HEALTH"); }
            catch (IllegalArgumentException ex) {
                try { attr = org.bukkit.attribute.Attribute.valueOf("GENERIC_MAX_HEALTH"); }
                catch (IllegalArgumentException ex2) { return; }
            }
            var instance = le.getAttribute(attr);
            if (instance != null) instance.setBaseValue(health);
        } catch (Exception ignored) {}
    }

    default void setAttributeAttackDamage(double damage) {
        try {
            org.bukkit.entity.Entity e = getBukkitEntity();
            if (!(e instanceof org.bukkit.entity.LivingEntity le)) return;
            org.bukkit.attribute.Attribute attr = null;
            try { attr = org.bukkit.attribute.Attribute.valueOf("ATTACK_DAMAGE"); }
            catch (IllegalArgumentException ex) {
                try { attr = org.bukkit.attribute.Attribute.valueOf("GENERIC_ATTACK_DAMAGE"); }
                catch (IllegalArgumentException ex2) { return; }
            }
            var instance = le.getAttribute(attr);
            if (instance != null) instance.setBaseValue(damage);
        } catch (Exception ignored) {}
    }

    // === Combat ===
    // Default: avoids clash with NMS LivingEntity parameter types
    default boolean isOwnedBy(Object entity) { return false; }
    default boolean wantsToAttack(Object target, Object owner) {
        try {
            org.bukkit.entity.Entity self = getBukkitEntity();
            if (self == null) return true;
            return TamableFoxLogic.isAggressive(self.getUniqueId());
        } catch (Exception e) {
            return true;
        }
    }

    default void clearTarget() {
        try {
            org.bukkit.entity.Entity self = getBukkitEntity();
            if (self instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(null);
            }
        } catch (Exception ignored) {}
    }
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

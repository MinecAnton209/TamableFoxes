package com.minecanton209.tamablefoxes.util;

import java.util.UUID;
import org.bukkit.entity.LivingEntity;

/**
 * Interface for tamed fox entities. Used by pathfinding goals and utility code
 * to reference fox entities without depending on NMS.
 */
public interface ITamableFox {

    boolean isTamed();
    void setTamed(boolean tamed);

    UUID getOwnerUUID();
    void setOwnerUUID(UUID uuid);
    LivingEntity getOwner();

    boolean isOrderedToSit();
    void setOrderedToSit(boolean sit);
    boolean isOrderedToSleep();
    void setOrderedToSleep(boolean sleep);

    boolean isOwnedBy(LivingEntity entity);
    boolean wantsToAttack(LivingEntity target, LivingEntity owner);
    boolean isDefending();
    void setDefending(boolean defending);
}

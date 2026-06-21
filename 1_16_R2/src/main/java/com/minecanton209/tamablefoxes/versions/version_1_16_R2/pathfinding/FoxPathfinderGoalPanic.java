package com.minecanton209.tamablefoxes.versions.version_1_16_R2.pathfinding;

import net.minecraft.server.v1_16_R2.PathfinderGoalPanic;
import com.minecanton209.tamablefoxes.versions.version_1_16_R2.EntityTamableFox;

public class FoxPathfinderGoalPanic extends PathfinderGoalPanic {
    EntityTamableFox tamableFox;

    public FoxPathfinderGoalPanic(EntityTamableFox tamableFox, double d0) {
        super(tamableFox, d0);
        this.tamableFox = tamableFox;
    }

    public boolean a() {
        return !tamableFox.isTamed() && tamableFox.isDefending() && super.a();
    }
}

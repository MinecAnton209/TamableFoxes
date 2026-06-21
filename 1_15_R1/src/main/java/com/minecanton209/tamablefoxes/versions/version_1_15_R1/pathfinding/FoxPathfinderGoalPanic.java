package com.minecanton209.tamablefoxes.versions.version_1_15_R1.pathfinding;

import net.minecraft.server.v1_15_R1.EntityFox;
import net.minecraft.server.v1_15_R1.PathfinderGoalPanic;
import com.minecanton209.tamablefoxes.versions.version_1_15_R1.EntityTamableFox;

import java.lang.reflect.Method;

public class FoxPathfinderGoalPanic extends PathfinderGoalPanic {
    EntityTamableFox tamableFox;

    public FoxPathfinderGoalPanic(EntityTamableFox tamableFox, double d0) {
        super(tamableFox, d0);
        this.tamableFox = tamableFox;
    }

    public boolean a() {
        return !tamableFox.isTamed() && !tamableFox.isDefending() && super.a();
    }
}

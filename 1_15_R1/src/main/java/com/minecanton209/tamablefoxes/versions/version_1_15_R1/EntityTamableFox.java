package com.minecanton209.tamablefoxes.versions.version_1_15_R1;

import net.minecraft.server.v1_15_R1.*;
import com.minecanton209.tamablefoxes.util.ITamableFoxAdapter;
import com.minecanton209.tamablefoxes.util.TamableFoxLogic;
import com.minecanton209.tamablefoxes.util.Utils;
import com.minecanton209.tamablefoxes.versions.version_1_15_R1.pathfinding.*;
import org.bukkit.GameMode;
import org.bukkit.craftbukkit.v1_15_R1.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

public class EntityTamableFox extends EntityFox implements ITamableFoxAdapter {

    protected static final DataWatcherObject<Byte> tamed;
    protected static final DataWatcherObject<Optional<UUID>> ownerUUID;
    private static final DataWatcherObject<Byte> bx;
    private static final Predicate<Entity> bD;

    static {
        tamed = DataWatcher.a(EntityTamableFox.class, DataWatcherRegistry.a);
        ownerUUID = DataWatcher.a(EntityTamableFox.class, DataWatcherRegistry.o);
        bx = DataWatcher.a(EntityFox.class, DataWatcherRegistry.a);
        bD = (entity) -> !entity.bm() && IEntitySelector.e.test(entity);
    }

    List<PathfinderGoal> untamedGoals;
    private FoxPathfinderGoalSit goalSit;
    private org.bukkit.entity.Player interactingPlayer;

    public EntityTamableFox(EntityTypes<? extends EntityFox> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    public void initPathfinder() {
        try {
            this.goalSit = new FoxPathfinderGoalSit(this);
            this.goalSelector.a(1, goalSit);

            Field landTargetGoal = this.getClass().getSuperclass().getDeclaredField("bE");
            landTargetGoal.setAccessible(true);
            landTargetGoal.set(this, new PathfinderGoalNearestAttackableTarget(this, EntityAnimal.class, 10, false, false, (entityliving) -> {
                return (!isTamed() || (com.minecanton209.tamablefoxes.util.io.Config.doesTamedAttackWildAnimals() && isTamed())) && (entityliving instanceof EntityChicken || entityliving instanceof EntityRabbit);
            }));
            landTargetGoal.setAccessible(false);

            Field turtleEggTargetGoal = this.getClass().getSuperclass().getDeclaredField("bF");
            turtleEggTargetGoal.setAccessible(true);
            turtleEggTargetGoal.set(this, new PathfinderGoalNearestAttackableTarget(this, EntityTurtle.class, 10, false, false, (entityLiving) -> {
                return (!isTamed() || (com.minecanton209.tamablefoxes.util.io.Config.doesTamedAttackWildAnimals() && isTamed())) && EntityTurtle.bw.test((EntityLiving) entityLiving);
            }));
            turtleEggTargetGoal.setAccessible(false);

            Field fishTargetGoal = this.getClass().getSuperclass().getDeclaredField("bG");
            fishTargetGoal.setAccessible(true);
            fishTargetGoal.set(this, new PathfinderGoalNearestAttackableTarget(this, EntityFish.class, 20, false, false, (entityliving) -> {
                return (!isTamed() || (com.minecanton209.tamablefoxes.util.io.Config.doesTamedAttackWildAnimals() && isTamed())) && entityliving instanceof EntityFishSchool;
            }));
            fishTargetGoal.setAccessible(false);

            this.goalSelector.a(0, getFoxInnerPathfinderGoal("g"));
            this.goalSelector.a(1, getFoxInnerPathfinderGoal("b"));
            this.goalSelector.a(2, new FoxPathfinderGoalPanic(this, 2.2D));
            this.goalSelector.a(2, new FoxPathfinderGoalSleepWithOwner(this));
            this.goalSelector.a(3, getFoxInnerPathfinderGoal("e", Arrays.asList(1.0D), Arrays.asList(double.class)));

            this.goalSelector.a(4, new PathfinderGoalAvoidTarget(this, EntityHuman.class, 16.0F, 1.6D, 1.4D, (entityliving) -> {
                return !isTamed() && bD.test((EntityLiving) entityliving) && !this.isDefending();
            }));
            this.goalSelector.a(4, new PathfinderGoalAvoidTarget(this, EntityWolf.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
                return !((EntityWolf) entityliving).isTamed() && !this.isDefending();
            }));

            this.goalSelector.a(5, getFoxInnerPathfinderGoal("u"));
            this.goalSelector.a(6, getFoxInnerPathfinderGoal("o"));
            this.goalSelector.a(7, getFoxInnerPathfinderGoal("l", Arrays.asList(1.2000000476837158D, true), Arrays.asList(double.class, boolean.class)));
            this.goalSelector.a(8, getFoxInnerPathfinderGoal("h", Arrays.asList(this, 1.25D), Arrays.asList(EntityFox.class, double.class)));
            this.goalSelector.a(8, new FoxPathfinderGoalSleepWithOwner(this));
            this.goalSelector.a(9, new FoxPathfinderGoalFollowOwner(this, 1.3D, 10.0F, 2.0F, false));
            this.goalSelector.a(10, new PathfinderGoalLeapAtTarget(this, 0.4F));
            this.goalSelector.a(11, new PathfinderGoalRandomStrollLand(this, 1.0D));
            this.goalSelector.a(11, getFoxInnerPathfinderGoal("p"));
            this.goalSelector.a(12, getFoxInnerPathfinderGoal("j", Arrays.asList(this, EntityHuman.class, 24.0F), Arrays.asList(EntityInsentient.class, Class.class, float.class)));

            this.targetSelector.a(1, new FoxPathfinderGoalOwnerHurtByTarget(this));
            this.targetSelector.a(2, new FoxPathfinderGoalOwnerHurtTarget(this));
            this.targetSelector.a(3, (new FoxPathfinderGoalHurtByTarget(this)).a(new Class[0]));

            untamedGoals = new ArrayList<>();

            PathfinderGoal sleep = getFoxInnerPathfinderGoal("t");
            this.goalSelector.a(7, sleep);
            untamedGoals.add(sleep);

            PathfinderGoal perchAndSearch = getFoxInnerPathfinderGoal("r");
            this.goalSelector.a(13, perchAndSearch);
            untamedGoals.add(perchAndSearch);

            PathfinderGoal eatBerries = new f(1.2000000476837158D, 12, 2);
            this.goalSelector.a(10, eatBerries);
            untamedGoals.add(eatBerries);

            PathfinderGoal seekShelter = getFoxInnerPathfinderGoal("s", Arrays.asList(1.25D), Arrays.asList(double.class));
            this.goalSelector.a(6, seekShelter);
            untamedGoals.add(seekShelter);

            PathfinderGoal strollThroughVillage = getFoxInnerPathfinderGoal("q", Arrays.asList(32, 200), Arrays.asList(int.class, int.class));
            this.goalSelector.a(9, strollThroughVillage);
            untamedGoals.add(strollThroughVillage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttributeInstance(GenericAttributes.MOVEMENT_SPEED).setValue(0.33000001192092896D);
        if (!isTamed()) {
            this.getAttributeInstance(GenericAttributes.MAX_HEALTH).setValue(10.0D);
            this.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).setValue(2.0D);
        } else {
            this.getAttributeInstance(GenericAttributes.MAX_HEALTH).setValue(24.0D);
            this.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).setValue(3.0D);
        }
    }

    private boolean t(int i) {
        return ((Byte)this.datawatcher.get(bx) & i) != 0;
    }

    public boolean isDefending() {
        return this.t(128);
    }

    protected void initDatawatcher() {
        super.initDatawatcher();
        this.datawatcher.register(tamed, (byte) 0);
        this.datawatcher.register(ownerUUID, Optional.empty());
    }

    public void b(NBTTagCompound compound) {
        super.b(compound);
        if (this.getOwnerUUID() == null) {
            compound.setString("OwnerUUID", "");
        } else {
            compound.setString("OwnerUUID", this.getOwnerUUID().toString());
        }
        compound.setBoolean("Sitting", this.goalSit.isWillSit());
    }

    @Override
    public void a(NBTTagCompound compound) {
        super.a(compound);
        String ownerUuid = "";
        if (compound.hasKeyOfType("OwnerUUID", 8)) {
            ownerUuid = compound.getString("OwnerUUID");
        }
        if (!ownerUuid.isEmpty()) {
            try {
                this.setOwnerUUID(UUID.fromString(ownerUuid));
                this.setTamed(true);
            } catch (Throwable throwable) {
                this.setTamed(false);
            }
        }
        if (this.goalSit != null) {
            this.goalSit.setSitting(compound.getBoolean("Sitting"));
        }
        if (!this.isTamed()) {
            goalSit.setSitting(false);
        }
    }

    public boolean isTamed() {
        return ((Byte) this.datawatcher.get(tamed) & 4) != 0;
    }

    public void setTamed(boolean tamed_) {
        byte isTamed = this.datawatcher.get(tamed);
        if (tamed_) {
            this.datawatcher.set(tamed, (byte) (isTamed | 4));
        } else {
            this.datawatcher.set(tamed, (byte) (isTamed & -5));
        }
        this.reassessTameGoals();
        if (tamed_) {
            this.getAttributeInstance(GenericAttributes.MAX_HEALTH).setValue(24.0D);
            this.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).setValue(3.0D);
            this.setHealth(this.getMaxHealth());
        } else {
            this.getAttributeInstance(GenericAttributes.MAX_HEALTH).setValue(10.0D);
            this.getAttributeInstance(GenericAttributes.ATTACK_DAMAGE).setValue(2.0D);
        }
    }

    private void reassessTameGoals() {
        if (!isTamed()) return;
        for (PathfinderGoal untamedGoal : untamedGoals) {
            this.goalSelector.a(untamedGoal);
        }
    }

    public void rename(org.bukkit.entity.Player player) {
        TamableFoxLogic.openRenameGui(this);
    }

    public boolean a(EntityHuman entityhuman, EnumHand enumhand) {
        this.setInteractingPlayer((org.bukkit.entity.Player) entityhuman.getBukkitEntity());
        Object result = TamableFoxLogic.handleMobInteract(this, this.getEquipment(EnumItemSlot.MAINHAND), enumhand);
        if (result == null) {
            return super.a(entityhuman, enumhand);
        }
        return true;
    }

    @Override
    public EntityTamableFox createChild(EntityAgeable entityageable) {
        EntityTamableFox entityFox = (EntityTamableFox) EntityTypes.FOX.a(this.world);
        entityFox.setFoxType(this.getFoxType());
        UUID uuid = this.getOwnerUUID();
        if (uuid != null) {
            entityFox.setOwnerUUID(uuid);
            entityFox.setTamed(true);
        }
        return entityFox;
    }

    public boolean mate(EntityAnimal entityanimal) {
        if (entityanimal == this) return false;
        if (!(entityanimal instanceof EntityTamableFox)) return false;
        EntityTamableFox entityFox = (EntityTamableFox) entityanimal;
        return (!entityFox.isSitting() && (this.isInLove() && entityFox.isInLove()));
    }

    public UUID getOwnerUUID() {
        return (UUID) ((Optional) this.datawatcher.get(ownerUUID)).orElse(null);
    }

    public void setOwnerUUID(UUID ownerUuid) {
        this.datawatcher.set(ownerUUID, Optional.ofNullable(ownerUuid));
    }

    public void tame(EntityHuman owner) {
        this.setTamed(true);
        this.setOwnerUUID(owner.getUniqueID());
        if (owner instanceof EntityPlayer) {
            CriterionTriggers.x.a((EntityPlayer)owner, this);
        }
    }

    public EntityLiving getOwner() {
        try {
            UUID ownerUuid = this.getOwnerUUID();
            return ownerUuid == null ? null : this.world.b(ownerUuid);
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }

    public boolean c(EntityLiving entity) {
        return !this.isOwnedBy(entity) && super.c(entity);
    }

    public boolean isOwnedBy(EntityLiving entity) {
        return entity == this.getOwner();
    }

    public boolean a(EntityLiving entityliving, EntityLiving entityliving1) {
        if (!(entityliving instanceof EntityCreeper) && !(entityliving instanceof EntityGhast)) {
            if (entityliving instanceof EntityTamableFox) {
                EntityTamableFox entityFox = (EntityTamableFox) entityliving;
                return !entityFox.isTamed() || entityFox.getOwner() != entityliving1;
            } else {
                return (!(entityliving instanceof EntityHuman)
                        || !(entityliving1 instanceof EntityHuman) ||
                        ((EntityHuman) entityliving1).a((EntityHuman) entityliving)) && ((!(entityliving instanceof EntityHorseAbstract)
                        || !((EntityHorseAbstract) entityliving).isTamed()) && (!(entityliving instanceof EntityTameableAnimal)
                        || !((EntityTameableAnimal) entityliving).isTamed()));
            }
        } else {
            return false;
        }
    }

    public ScoreboardTeamBase getScoreboardTeam() {
        if (this.isTamed()) {
            EntityLiving var0 = this.getOwner();
            if (var0 != null) {
                return var0.getScoreboardTeam();
            }
        }
        return super.getScoreboardTeam();
    }

    public boolean r(Entity entity) {
        if (this.isTamed()) {
            EntityLiving entityOwner = this.getOwner();
            if (entity == entityOwner) return true;
            if (entityOwner != null) return entityOwner.r(entity);
        }
        return super.r(entity);
    }

    public void die(DamageSource damageSource) {
        EntityLiving owner = this.getOwner();
        if (!this.world.isClientSide && this.world.getGameRules().getBoolean(GameRules.SHOW_DEATH_MESSAGES) && owner instanceof EntityPlayer) {
            owner.sendMessage(this.getCombatTracker().getDeathMessage());
        }
        if (owner != null) {
            com.minecanton209.tamablefoxes.util.TamableFoxUtil.decrementTameCount(owner.getUniqueID());
        }
        super.die(damageSource);
    }

    private PathfinderGoal getFoxInnerPathfinderGoal(String innerName, List<Object> args, List<Class<?>> argTypes) {
        return (PathfinderGoal) Utils.instantiatePrivateInnerClass(EntityFox.class, innerName, this, args, argTypes);
    }

    private PathfinderGoal getFoxInnerPathfinderGoal(String innerName) {
        return (PathfinderGoal) Utils.instantiatePrivateInnerClass(EntityFox.class, innerName, this, Arrays.asList(), Arrays.asList());
    }

    // === ITamableFoxAdapter implementation ===

    @Override
    public void setInteractingPlayer(org.bukkit.entity.Player player) {
        this.interactingPlayer = player;
    }

    @Override
    public org.bukkit.entity.Player getBukkitPlayer() {
        return interactingPlayer;
    }

    @Override
    public boolean isSpawnEgg(Object itemstack) {
        return itemstack instanceof ItemStack stack && stack.getItem() instanceof ItemMonsterEgg;
    }

    @Override
    public boolean isEdible(Object itemstack) {
        return itemstack instanceof ItemStack stack && stack.getItem().isFood();
    }

    @Override
    public boolean isMeat(Object itemstack) {
        return itemstack instanceof ItemStack stack && stack.getItem().isFood() && stack.getItem().getFoodInfo().c();
    }

    @Override
    public int getFoodNutrition(Object itemstack) {
        if (itemstack instanceof ItemStack stack) {
            return stack.getItem().getFoodInfo().getNutrition();
        }
        return 0;
    }

    @Override
    public Object copyItemStack(Object itemstack) {
        return itemstack instanceof ItemStack stack ? stack.cloneItemStack() : itemstack;
    }

    @Override
    public void shrinkItem(Object itemstack, int amount) {
        if (itemstack instanceof ItemStack stack) stack.subtract(amount);
    }

    @Override
    public boolean hasItemInMainHand() {
        return !this.getEquipment(EnumItemSlot.MAINHAND).isEmpty();
    }

    @Override
    public void setItemSlotMainHand(Object item) {
        if (item instanceof ItemStack stack) this.setSlot(EnumItemSlot.MAINHAND, stack);
    }

    @Override
    public void setItemSlotMainHandAir() {
        this.setSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.AIR));
    }

    @Override
    public void dropItemFromMouth() {
        getBukkitEntity().getWorld().dropItem(
            getBukkitEntity().getLocation(),
            CraftItemStack.asBukkitCopy(this.getEquipment(EnumItemSlot.MAINHAND)));
    }

    @Override
    public void setDeltaMovement(double x, double y, double z) {
        this.setMot(x, y, z);
    }

    @Override
    public void spawnSmokeParticle() {
        getBukkitEntity().getWorld().spawnParticle(
            org.bukkit.Particle.SMOKE_NORMAL, getBukkitEntity().getLocation(), 10);
    }

    @Override
    public boolean hasPermission(String permission) {
        return interactingPlayer != null && interactingPlayer.hasPermission(permission);
    }

    @Override
    public void setOrderedToSit(boolean sit) {
        this.goalSit.setSitting(sit);
    }

    @Override
    public boolean isOrderedToSit() {
        return this.goalSit.isWillSit();
    }

    @Override
    public void setOrderedToSleep(boolean sleep) {
        this.setSleeping(sleep);
    }

    @Override
    public boolean isOrderedToSleep() {
        return this.isSleeping();
    }

    public boolean isOwnedBy(org.bukkit.entity.LivingEntity entity) {
        EntityLiving owner = this.getOwner();
        return owner != null && owner.getBukkitEntity() == entity;
    }

    public boolean wantsToAttack(org.bukkit.entity.LivingEntity target, org.bukkit.entity.LivingEntity owner) {
        return this.a((EntityLiving) target, (EntityLiving) owner);
    }

    @Override
    public void setDefending(boolean defending) {
    }

    @Override
    public void setVariant(Object variant) {
        if (variant instanceof EntityFox.Type foxType) {
            this.setFoxType(foxType);
        }
    }

    @Override
    public Object getVariant() {
        return this.getFoxType();
    }

    @Override
    public void setFoxCustomName(String name) {
        getBukkitEntity().setCustomName(name);
    }

    @Override
    public void setFoxCustomNameVisible(boolean visible) {
        getBukkitEntity().setCustomNameVisible(visible);
    }

    @Override
    public void heal(float amount) {
        super.heal(amount, EntityRegainHealthEvent.RegainReason.EATING);
    }

    @Override
    public boolean isBaby() {
        return super.isBaby();
    }

    @Override
    public boolean isCrouching() {
        return super.isCrouching();
    }

    @Override
    public boolean isSpectator() {
        return interactingPlayer != null && interactingPlayer.getGameMode() == GameMode.SPECTATOR;
    }
}

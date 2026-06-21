package com.minecanton209.tamablefoxes.versions.version_26_R1;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import com.minecanton209.tamablefoxes.util.ITamableFoxAdapter;
import com.minecanton209.tamablefoxes.util.TamableFoxLogic;
import com.minecanton209.tamablefoxes.util.TamableFoxUtil;
import com.minecanton209.tamablefoxes.util.Utils;
import com.minecanton209.tamablefoxes.util.io.Config;
import com.minecanton209.tamablefoxes.util.io.LanguageConfig;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalFollowOwner;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalHurtByTarget;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalOwnerHurtByTarget;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalOwnerHurtTarget;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalPanic;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalSitWhenOrdered;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalSleepWhenOrdered;
import com.minecanton209.tamablefoxes.versions.version_26_R1.pathfinding.FoxPathfinderGoalSleepWithOwner;

public class EntityTamableFox extends Fox implements ITamableFoxAdapter {

    //private static final EntityDataAccessor<Byte> bw; // DATA_FLAGS_ID
    private static final Predicate<Entity> AVOID_PLAYERS; // AVOID_PLAYERS

    static {
        AVOID_PLAYERS = (entity) -> !entity.isCrouching();// && EntitySelector.test(entity);
    }

    List<Goal> untamedGoals;
    private FoxPathfinderGoalSitWhenOrdered goalSitWhenOrdered;

    private FoxPathfinderGoalSleepWhenOrdered goalSleepWhenOrdered;

    private boolean tamed;
    private org.bukkit.entity.Player interactingPlayer;

    public EntityTamableFox(EntityType<? extends Fox> entitytype, Level world) {
        super(entitytype, world);

        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.33000001192092896D); // Set movement speed
        if (isTamed()) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24.0D);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0D);
            this.setHealth((float) this.getMaxHealth());
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0D);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
        }

        this.setTamed(false);
    }

    @Override
    public void registerGoals() {
        try {
            this.goalSitWhenOrdered = new FoxPathfinderGoalSitWhenOrdered(this);
            this.goalSelector.addGoal(1, goalSitWhenOrdered);
            this.goalSleepWhenOrdered = new FoxPathfinderGoalSleepWhenOrdered(this);
            this.goalSelector.addGoal(1, goalSleepWhenOrdered);

            // For reflection, we must use the non remapped names, since this is done at runtime
            // and the user will be using a normal spigot jar.

            // Wild animal attacking
            Field landTargetGoal = this.getClass().getSuperclass().getDeclaredField("landTargetGoal"); // landTargetGoal
            landTargetGoal.setAccessible(true);
            landTargetGoal.set(this, new NearestAttackableTargetGoal(this, Animal.class, 10, false, false, (entityliving, level) -> {
                return (!isTamed() || (Config.doesTamedAttackWildAnimals() && isTamed())) && (entityliving instanceof Chicken || entityliving instanceof Rabbit);
            }));

            Field turtleEggTargetGoal = this.getClass().getSuperclass().getDeclaredField("turtleEggTargetGoal"); // turtleEggTargetGoal
            turtleEggTargetGoal.setAccessible(true);
            turtleEggTargetGoal.set(this, new NearestAttackableTargetGoal(this, Turtle.class, 10, false, false, Turtle.BABY_ON_LAND_SELECTOR));

            Field fishTargetGoal = this.getClass().getSuperclass().getDeclaredField("fishTargetGoal"); // fishTargetGoal
            fishTargetGoal.setAccessible(true);
            fishTargetGoal.set(this, new NearestAttackableTargetGoal(this, AbstractFish.class, 20, false, false, (entityliving, level) -> {
                return (!isTamed() || (Config.doesTamedAttackWildAnimals() && isTamed())) && entityliving instanceof AbstractSchoolingFish;
            }));

            this.goalSelector.addGoal(0, getFoxInnerPathfinderGoal("FoxFloatGoal"));
            this.goalSelector.addGoal(1, getFoxInnerPathfinderGoal("FaceplantGoal"));
            this.goalSelector.addGoal(2, new FoxPathfinderGoalPanic(this, 2.2D));
            this.goalSelector.addGoal(2, new FoxPathfinderGoalSleepWithOwner(this));
            this.goalSelector.addGoal(3, getFoxInnerPathfinderGoal("FoxBreedGoal", Arrays.asList(1.0D), Arrays.asList(double.class)));

            this.goalSelector.addGoal(4, new AvoidEntityGoal(this, Player.class, 16.0F, 1.6D, 1.4D, (entityliving) -> {
                return !isTamed() && AVOID_PLAYERS.test((LivingEntity) entityliving) && !this.isDefending();
            }));
            this.goalSelector.addGoal(4, new AvoidEntityGoal(this, Wolf.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
                return !((Wolf)entityliving).isTame() && !this.isDefending();
            }));
            this.goalSelector.addGoal(4, new AvoidEntityGoal(this, PolarBear.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
                return !this.isDefending();
            }));

            this.goalSelector.addGoal(5, getFoxInnerPathfinderGoal("StalkPreyGoal"));
            this.goalSelector.addGoal(6, new FoxPounceGoal());
            this.goalSelector.addGoal(7, getFoxInnerPathfinderGoal("FoxMeleeAttackGoal", Arrays.asList(1.2000000476837158D, true), Arrays.asList(double.class, boolean.class)));
            this.goalSelector.addGoal(8, getFoxInnerPathfinderGoal("FoxFollowParentGoal", Arrays.asList(1.25D), Arrays.asList(double.class)));
            this.goalSelector.addGoal(8, new FoxPathfinderGoalSleepWithOwner(this));
            this.goalSelector.addGoal(9, new FoxPathfinderGoalFollowOwner(this, 1.3D, 10.0F, 2.0F, false));
            this.goalSelector.addGoal(10, new LeapAtTargetGoal(this, 0.4F));
            this.goalSelector.addGoal(11, new RandomStrollGoal(this, 1.0D));
            this.goalSelector.addGoal(11, getFoxInnerPathfinderGoal("FoxSearchForItemsGoal"));
            this.goalSelector.addGoal(12, getFoxInnerPathfinderGoal("FoxLookAtPlayerGoal", Arrays.asList(this, Player.class, 24.0f),
                        Arrays.asList(Mob.class, Class.class, float.class)));

            this.targetSelector.addGoal(1, new FoxPathfinderGoalOwnerHurtByTarget(this));
            this.targetSelector.addGoal(2, new FoxPathfinderGoalOwnerHurtTarget(this));
            this.targetSelector.addGoal(3, (new FoxPathfinderGoalHurtByTarget(this)).setAlertOthers(new Class[0]));

            // Assign all the untamed goals that will later be removed.
            untamedGoals = new ArrayList<>();

            Goal sleep = getFoxInnerPathfinderGoal("SleepGoal");
            this.goalSelector.addGoal(7, sleep);
            untamedGoals.add(sleep);

            Goal perchAndSearch = getFoxInnerPathfinderGoal("PerchAndSearchGoal");
            this.goalSelector.addGoal(13, perchAndSearch);
            untamedGoals.add(perchAndSearch);

            Goal eatBerries = new FoxEatBerriesGoal(1.2000000476837158D, 12, 2);
            this.goalSelector.addGoal(11, eatBerries);
            untamedGoals.add(eatBerries); // Maybe this should be configurable too?

            Goal seekShelter = getFoxInnerPathfinderGoal("SeekShelterGoal", Arrays.asList(1.25D), Arrays.asList(double.class));
            this.goalSelector.addGoal(6, seekShelter);
            untamedGoals.add(seekShelter);

            Goal strollThroughVillage = getFoxInnerPathfinderGoal("FoxStrollThroughVillageGoal", Arrays.asList(32, 200), Arrays.asList(int.class, int.class));
            this.goalSelector.addGoal(9, strollThroughVillage);
            untamedGoals.add(strollThroughVillage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected EntityDataAccessor<Byte> getDataFlagsId() throws NoSuchFieldException, IllegalAccessException {
        Field dataFlagsField = Fox.class.getDeclaredField("DATA_FLAGS_ID"); // DATA_FLAGS_ID
        dataFlagsField.setAccessible(true);
        EntityDataAccessor<Byte> dataFlagsId = (EntityDataAccessor<Byte>) dataFlagsField.get(null);
        dataFlagsField.setAccessible(false);

        return dataFlagsId;
    }

    protected boolean getFlag(int i) {
        try {
            EntityDataAccessor<Byte> dataFlagsId = getDataFlagsId();

            return (super.entityData.get(dataFlagsId) & i) != 0;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }

        return false;
    }

    protected void setFlag(int i, boolean flag) {
        try {
            EntityDataAccessor<Byte> dataFlagsId = getDataFlagsId();

            if (flag) {
                this.entityData.set(dataFlagsId, (byte)(this.entityData.get(dataFlagsId) | i));
            } else {
                this.entityData.set(dataFlagsId, (byte)(this.entityData.get(dataFlagsId) & ~i));
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public boolean isDefending() {
        return getFlag(128);
    }

    public void setDefending(boolean defending) {
        setFlag(128, defending);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        UUID uuid = this.getOwnerUUID();
        if (uuid == null) uuid = new UUID(0L, 0L);
        output.putString("OwnerUUID", uuid.toString()); // Changed to store as string for consistency
        output.putBoolean("Sitting", this.goalSitWhenOrdered.isOrderedToSit());
        output.putBoolean("Sleeping", this.goalSleepWhenOrdered.isOrderedToSleep());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // 尝试读取UUID字符串，如果不存在则会返回默认值
        String uuidString = input.getStringOr("OwnerUUID", "00000000-0000-0000-0000-000000000000");
        UUID ownerUuid = TamableFoxUtil.parseOwnerUUID(uuidString);
        if (ownerUuid != null) {
            this.setOwnerUUID(ownerUuid);
            this.setTamed(true);
        } else {
            this.setTamed(false);
        }

        if (this.goalSitWhenOrdered != null) {
            this.goalSitWhenOrdered.setOrderedToSit(input.getBooleanOr("Sitting", false));
        }

        if (this.goalSleepWhenOrdered != null) {
            this.goalSleepWhenOrdered.setOrderedToSleep(input.getBooleanOr("Sleeping", false));
        }

        if (!this.isTamed()) {
            goalSitWhenOrdered.setOrderedToSit(false);
            goalSleepWhenOrdered.setOrderedToSleep(false);
        }
    }

    public boolean isTamed() {
        return TamableFoxUtil.isTamed(this.tamed, getOwnerUUID());
    }

    public void setTamed(boolean tamed) {
        this.tamed = tamed;
        this.reassessTameGoals();

        if (tamed) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24.0D);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0D);
        } else {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0D);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
        }
        this.setHealth((float) this.getMaxHealth());
    }

    // Remove untamed goals if its tamed.
    private void reassessTameGoals() {
        if (!isTamed()) return;

        for (Goal untamedGoal : untamedGoals) {
            this.goalSelector.removeGoal(untamedGoal);
        }
    }

    public void rename(org.bukkit.entity.Player player) {
        TamableFoxLogic.openRenameGui(this);
    }

    @Override
    public InteractionResult mobInteract(Player entityhuman, InteractionHand enumhand) {
        this.setInteractingPlayer((org.bukkit.entity.Player) entityhuman.getBukkitEntity());
        Object result = TamableFoxLogic.handleMobInteract(this, this.getItemBySlot(EquipmentSlot.MAINHAND), enumhand);
        if (result == null) {
            return super.mobInteract(entityhuman, enumhand);
        }
        return switch (result.toString()) {
            case "CONSUME" -> InteractionResult.CONSUME;
            case "SUCCESS" -> InteractionResult.SUCCESS;
            case "PASS" -> InteractionResult.PASS;
            default -> super.mobInteract(entityhuman, enumhand);
        };
    }

    @Override
    public EntityTamableFox getBreedOffspring(ServerLevel worldserver, AgeableMob entityageable) {
        EntityTamableFox entityfox = (EntityTamableFox) ((EntityType<Fox>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("fox"))).create(worldserver, EntitySpawnReason.BREEDING);
        entityfox.setVariant(this.getRandom().nextBoolean() ? this.getVariant() : ((Fox)entityageable).getVariant());

        UUID uuid = this.getOwnerUUID();
        if (uuid != null) {
            entityfox.setOwnerUUID(uuid);
            entityfox.setTamed(true);
        }

        return entityfox;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData == null ? null : this.entityData.get(DATA_TRUSTED_ID_0).map(EntityReference::getUUID).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID ownerUuid) {
        this.entityData.set(DATA_TRUSTED_ID_0, Optional.ofNullable(ownerUuid == null ? null : EntityReference.of(ownerUuid)));
    }

    public void tame(Player owner) {
        this.setTamed(true);
        this.setOwnerUUID(owner.getUUID());

        // Give the player the taming advancement.
        if (owner instanceof ServerPlayer serverPlayer) {
            try {
                Class<?> ctClass = Class.forName("net.minecraft.advancements.triggers.CriteriaTriggers");
                Object field = ctClass.getField("TAME_ANIMAL").get(null);
                java.lang.reflect.Method triggerMethod = field.getClass().getMethod("trigger", ServerPlayer.class, net.minecraft.world.entity.Entity.class);
                triggerMethod.invoke(field, serverPlayer, this);
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    public LivingEntity getOwner() {
        try {
            UUID ownerUuid = this.getOwnerUUID();
            return ownerUuid == null ? null : this.level().getPlayerByUUID(ownerUuid);
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }


    // Only attack entity if its not attacking owner.
    @Override
    public boolean canAttack(LivingEntity entity) {
        return !this.isOwnedBy(entity) && super.canAttack(entity);
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity == this.getOwner();
    }

    /*
     deobf: wantsToAttack (Copied from EntityWolf)
     This code being from EntityWolf also means that wolves will want to attack foxes
     Our life would be so much easier if we could extend both EntityFox and EntityTameableAnimal
    */
    public boolean wantsToAttack(LivingEntity entityliving, LivingEntity entityliving1) {
        if (!(entityliving instanceof Creeper) && !(entityliving instanceof Ghast)) {
            if (entityliving instanceof EntityTamableFox) {
                EntityTamableFox entityFox = (EntityTamableFox) entityliving;
                return !entityFox.isTamed() || entityFox.getOwner() != entityliving1;
            } else {
                return (!(entityliving instanceof Player)
                        || !(entityliving1 instanceof Player) ||
                        ((Player) entityliving1).canHarmPlayer((Player) entityliving)) && ((!(entityliving instanceof AbstractHorse)
                        || !((AbstractHorse) entityliving).isTamed()) && (!(entityliving instanceof TamableAnimal)
                        || !((TamableAnimal) entityliving).isTame()));
            }
        } else {
            return false;
        }
    }

    // Set the scoreboard team to the same as the owner if its tamed.
    @Override
    public PlayerTeam getTeam() {
        if (this.isTamed()) {
            LivingEntity var0 = this.getOwner();
            if (var0 != null) {
                return var0.getTeam();
            }
        }

        return super.getTeam();
    }

    // Override considersEntityAsAlly (Entity::r(Entity))
    // This used to be isAlliedTo(Entity) but that is now final and cannot be overridden. Internally that calls this, which can be
    // Overridden, so this is probably fine
    @Override
    public boolean considersEntityAsAlly(Entity entity) {
        if (this.isTamed() && Objects.equals(entity.getUUID(), this.getOwnerUUID())) {
            return true;
        }
        return super.considersEntityAsAlly(entity);
    }


    // When the fox dies, show a chat message, and remove the player's stored tamed foxed.
    @Override
    public void die(DamageSource damageSource) {
        LivingEntity owner = this.getOwner();

        if (!this.level().isClientSide() && Boolean.TRUE.equals(
            this.level().getWorld().getGameRuleValue(
                GameRule.SHOW_DEATH_MESSAGES)) && owner instanceof ServerPlayer) {
            //owner.sendMessage(this.getCombatTracker().getDeathMessage(), getOwnerUUID());
            if(owner instanceof ServerPlayer player) {
                player.sendSystemMessage(this.getCombatTracker().getDeathMessage());
            }
        }

        // Remove the amount of foxes the player has tamed if the limit is enabled.
        if (owner != null) {
            TamableFoxUtil.decrementTameCount(owner.getUUID());
        }

        super.die(damageSource);
    }


    private Goal getFoxInnerPathfinderGoal(String innerName, List<Object> args, List<Class<?>> argTypes) {
        return (Goal) Utils.instantiatePrivateInnerClass(Fox.class, innerName, this, args, argTypes);
    }

    private Goal getFoxInnerPathfinderGoal(String innerName) {
        return (Goal) Utils.instantiatePrivateInnerClass(Fox.class, innerName, this, Arrays.asList(), Arrays.asList());
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
        return itemstack instanceof SpawnEggItem;
    }

    @Override
    public boolean isEdible(Object itemstack) {
        if (itemstack instanceof ItemStack stack) {
            FoodProperties fp = stack.get(DataComponents.FOOD);
            return fp != null;
        }
        return false;
    }

    @Override
    public boolean isMeat(Object itemstack) {
        if (itemstack instanceof ItemStack stack) {
            return stack.is(ItemTags.MEAT);
        }
        return false;
    }

    @Override
    public int getFoodNutrition(Object itemstack) {
        if (itemstack instanceof ItemStack stack) {
            FoodProperties fp = stack.getComponents().get(DataComponents.FOOD);
            return fp != null ? fp.nutrition() : 0;
        }
        return 0;
    }

    @Override
    public Object copyItemStack(Object itemstack) {
        return itemstack instanceof ItemStack stack ? stack.copy() : itemstack;
    }

    @Override
    public void shrinkItem(Object itemstack, int amount) {
        if (itemstack instanceof ItemStack stack) stack.shrink(amount);
    }

    @Override
    public boolean hasItemInMainHand() {
        return this.hasItemInSlot(EquipmentSlot.MAINHAND);
    }

    @Override
    public Object getItemInMainHand() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    @Override
    public void setItemSlotMainHand(Object item) {
        if (item instanceof ItemStack stack) this.setItemSlot(EquipmentSlot.MAINHAND, stack, false);
    }

    @Override
    public void setItemSlotMainHandAir() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.AIR), false);
    }

    @Override
    public void dropItemFromMouth() {
        getBukkitEntity().getWorld().dropItem(
            getBukkitEntity().getLocation(),
            CraftItemStack.asBukkitCopy(this.getItemBySlot(EquipmentSlot.MAINHAND)));
    }

    @Override
    public void setDeltaMovement(double x, double y, double z) {
        this.setDeltaMovement(new Vec3(x, y, z));
    }

    @Override
    public void spawnSmokeParticle() {
        getBukkitEntity().getWorld().spawnParticle(
            org.bukkit.Particle.SMOKE, getBukkitEntity().getLocation(), 10);
    }

    @Override
    public boolean hasPermission(String permission) {
        return interactingPlayer != null && interactingPlayer.hasPermission(permission);
    }

    @Override
    public void setFoxCustomName(String name) {
        ((org.bukkit.entity.Entity) getBukkitEntity()).setCustomName(name);
    }

    @Override
    public void setFoxCustomNameVisible(boolean visible) {
        ((org.bukkit.entity.Entity) getBukkitEntity()).setCustomNameVisible(visible);
    }

    @Override
    public void heal(float amount) {
        super.heal(amount, EntityRegainHealthEvent.RegainReason.EATING);
    }

    @Override
    public boolean isCrouching() {
        return super.isCrouching();
    }

    @Override
    public boolean isSpectator() {
        return interactingPlayer != null && interactingPlayer.getGameMode() == GameMode.SPECTATOR;
    }

    @Override
    public void setOrderedToSit(boolean sit) {
        this.goalSitWhenOrdered.setOrderedToSit(sit);
    }

    @Override
    public boolean isOrderedToSit() {
        return this.goalSitWhenOrdered.isOrderedToSit();
    }

    @Override
    public void setOrderedToSleep(boolean sleep) {
        this.goalSleepWhenOrdered.setOrderedToSleep(sleep);
    }

    @Override
    public boolean isOrderedToSleep() {
        return this.goalSleepWhenOrdered.isOrderedToSleep();
    }
}
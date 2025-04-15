package org.dawnoftime.onceuponatown.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.entity.ai.goal.core.NpcPanicGoal;
import org.dawnoftime.onceuponatown.entity.ai.goal.fight.SelfDefenseGoal;
import org.dawnoftime.onceuponatown.entity.ai.goal.work.BuildGoal;
import org.dawnoftime.onceuponatown.menu.BuildingsMenu;
import org.dawnoftime.onceuponatown.menu.InteractingNpc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class Npc extends AgeableMob implements InteractingNpc, RangedAttackMob, CrossbowAttackMob {
    //TODO make a clean and custom client serialization
    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CROSSING_ARMS = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_READING = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> CULTURE = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PROFESSION = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.STRING);
    public static final double DEFAULT_SPEED = 0.25D;
    public static final double RUN_SPEED_MODIFIER = 0.65D;
    public static final double SPRINT_SPEED_MODIFIER = 0.75D;
    private Player interactingPlayer;
    private Activity currentActivity;
    private int blockBreakTime;
    private int lastBreakProgress = -1;
    private NpcFishingHook fishingHook;
    private Profession profession;
    private BlockPos attackedBlock;
    private Town town;
    private int townId = -1;

    public Npc(EntityType<Npc> entityType, Level level) {
        super(entityType, level);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        getNavigation().setCanFloat(true);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_UNHAPPY_COUNTER, 0);
        entityData.define(DATA_IS_CHARGING_CROSSBOW, false);
        entityData.define(DATA_CROSSING_ARMS, true);
        entityData.define(DATA_READING, false);
        entityData.define(CULTURE, "plains");
        entityData.define(PROFESSION, "default");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, DEFAULT_SPEED).add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.FOLLOW_RANGE, 50.0D);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCultureId(tag.getString("Culture"));
        setProfessionId(tag.getString("Profession"));
        townId = tag.getInt("TownId");
        if (townId != -1 && level() instanceof ServerLevel serverLevel) {
            town = LevelTowns.of(serverLevel).getTownById(townId);
        }
        if (town != null) {
            addTownGoals();
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Culture", getCultureId());
        tag.putString("Profession", getProfessionId());
        tag.putInt("TownId", townId);
    }

    @Override
    protected void registerGoals() {
        addCoreGoals();
    }

    public void clearAi() {
        goalSelector.removeAllGoals(goal -> true);
        addCoreGoals();
    }

    public void addTownGoals() {
        addRaidGoals();
        addSleepingGoals();
        addRestingGoals();
        addWorkGoals();
        addFreeTimeGoals();
    }

    private void addCoreGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new OpenDoorGoal(this, true));
        goalSelector.addGoal(1, new NpcPanicGoal(this));
        goalSelector.addGoal(2, new SelfDefenseGoal(this, 1.0D));
        //goalSelector.addGoal(++priority, new NpcCrossbowAttackGoal(this, 1.4D, 8.0F));
        //goalSelector.addGoal(++priority, new RangedBowAttackGoal<>(this, 1.0D, 20, 15.0F));

        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, WitherBoss.class, 40.0F, SPRINT_SPEED_MODIFIER, SPRINT_SPEED_MODIFIER));
        goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Raider.class, 15.0F, RUN_SPEED_MODIFIER, SPRINT_SPEED_MODIFIER));
        goalSelector.addGoal(5, new AvoidEntityGoal<>(this, Hoglin.class, 15.0F, RUN_SPEED_MODIFIER, SPRINT_SPEED_MODIFIER));
        goalSelector.addGoal(6, new AvoidEntityGoal<>(this, Zombie.class, 10.0F, RUN_SPEED_MODIFIER, SPRINT_SPEED_MODIFIER));
        goalSelector.addGoal(7, new AvoidEntityGoal<>(this, LivingEntity.class, 10F, RUN_SPEED_MODIFIER, SPRINT_SPEED_MODIFIER, (livingEntity) -> {
            LivingEntity lastHurtBy = this.getLastHurtByMob();
            if (lastHurtBy == null)
                return false;
            return (livingEntity.is(lastHurtBy));
        }));

        goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, LivingEntity.class, 10.0F));
        goalSelector.addGoal(13, new RandomLookAroundGoal(this));

        targetSelector.addGoal(0, new HurtByTargetGoal(this));
    }

    private void addRaidGoals() {
    }

    private void addSleepingGoals() {
    }

    private void addRestingGoals() {

    }

    private void addWorkGoals() {
        if (getProfessionId().equals(Profession.BUILDER.getId())) {
            goalSelector.addGoal(8, new BuildGoal(this));
        }
    }

    private void addFreeTimeGoals() {

    }

    public void onFinalizeSpawnEvent() {
        //setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        //setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        //setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        //setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    @Override
    public void tick() {
        super.tick();
        if (getUnhappyCounter() > 0) {
            setUnhappyCounter(getUnhappyCounter() - 1);
        }
    }

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    public boolean tickBreakBlock(InteractionHand swingHand) {
        if (attackedBlock != null) {
            BlockState stateToBreak = level().getBlockState(attackedBlock);
            float destroySpeed = stateToBreak.getDestroySpeed(level(), attackedBlock);
            if (blockBreakTime % 2 == 0) {
                swing(swingHand);
                SoundType soundType = stateToBreak.getSoundType();
                playSound(soundType.getHitSound(), (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
            }
            ++blockBreakTime;
            int i = (int) ((float) blockBreakTime / destroySpeed);
            //System.out.println("destroySpeed : " + destroySpeed + " | breakTime : " + blockBreakTime + " | i : " + i);
            if (i != lastBreakProgress) {
                level().destroyBlockProgress(getId(), attackedBlock, i);
                this.lastBreakProgress = i;
            }
            if (blockBreakTime >= destroySpeed * 10.0F) {
                blockBreakTime = 0;
                return true;
            }
        }
        return false;
    }

    public void startBreakingBlock(BlockPos blockPos) {
        stopBreakingBlock();
        blockBreakTime = 0;
        lastBreakProgress = -1;
        attackedBlock = blockPos;
    }

    public void stopBreakingBlock() {
        if (attackedBlock != null) {
            level().destroyBlockProgress(getId(), attackedBlock, -1);
            attackedBlock = null;
        }
    }

    public BlockPos getAttackedBlock() {
        return attackedBlock;
    }

    @Override
    public void rideTick() {
        super.rideTick();//if (this.getVehicle() instanceof AbstractHorse horse && !horse.isTamed())//horse.setTamed(true);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        //setCrossingArms(false);
        return super.hurt(source, amount);
    }

    @Override
    public void die(@NotNull DamageSource cause) {
        super.die(cause);
        //tradingHandler.stopTrading();
        Ouat.LOG.info("Npc {} died, message: '{}'", this, cause.getLocalizedDeathMessage(this).getString());
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (!level().isClientSide()
            && player instanceof ServerPlayer serverPlayer
            && !itemStack.is(ItemRegistry.REGISTRY.NPC_SPAWN_EGG.get())
            && isAlive()
            && !isSleeping()
            && (hand == InteractionHand.MAIN_HAND)
        ) {
            if (town != null
                && !getProfessionId().equals("default")
                && !getProfessionId().equals("unemployed")
            ) {
                interactingPlayer = serverPlayer;
                Ouat.COMMON.openMenu(serverPlayer, new SimpleMenuProvider((containerID, playerInventory, p) ->
                    new BuildingsMenu(containerID, playerInventory, this, town.getTownMapData()), Ouat.translatable("buildings")), buffer -> {
                    buffer.writeInt(this.getId());
                    buffer.writeNbt(town.getTownMapData());
                });
            } else {
                setUnhappy();
            }
        }
        return super.mobInteract(player, hand);
    }

    private void setUnhappy() {
        setUnhappyCounter(40);
        if (!level().isClientSide()) {
            playSound(SoundEvents.VILLAGER_NO, getSoundVolume(), getVoicePitch());
        }
    }

    @Override
    public void notifyDealMade(NpcOffer deal) {

    }

    public ItemStack getMeleeWeapon() {
        return new ItemStack(Items.WOODEN_HOE);
    }

    public void holdInMainHand(ItemStack stack) {
        setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    public void holdInOffHand(ItemStack stack) {
        setItemInHand(InteractionHand.OFF_HAND, stack);
    }

    public void freeMainHand() {
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    public void freeOffHand() {
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    public void freeHands() {
        freeMainHand();
        freeOffHand();
    }

    public void sayInPlayerActionBar(Player player, Component component) {
        if (component != null) {
            player.displayClientMessage(component, true);
        }
    }

    @Override
    public void performRangedAttack(@NotNull LivingEntity target, float velocity) {
        if (isHolding(stack -> stack.getItem() instanceof BowItem)) {
            performBowAttack(target);
        } else if (isHolding(stack -> stack.getItem() instanceof CrossbowItem)) {
            performCrossbowAttack(this, 1.6F);
        }
    }

    private void performBowAttack(LivingEntity target) { // Vanilla
        AbstractArrow arrow = Ouat.COMMON.getArrow(this.level(), this, this.getMainHandItem());
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.3333333333333333D) - arrow.getY();
        double d2 = target.getZ() - this.getZ();
        double d3 = Math.sqrt(d0 * d0 + d2 * d2);
        arrow.shoot(d0, d1 + d3 * (double) 0.2F, d2, 1.6F, (float) (14 - this.level().getDifficulty().getId() * 4));
        playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    @Override
    public @NotNull ItemStack getProjectile(ItemStack weaponStack) {
        if (weaponStack.getItem() instanceof ProjectileWeaponItem projectileWeaponItem) {
            Predicate<ItemStack> predicate = projectileWeaponItem.getSupportedHeldProjectiles();
            ItemStack heldProjectile = ProjectileWeaponItem.getHeldProjectile(this, predicate);
            return Ouat.COMMON.getProjectile(this, weaponStack, heldProjectile.isEmpty() ? new ItemStack(Items.ARROW) : heldProjectile);
        } else {
            return Ouat.COMMON.getProjectile(this, weaponStack, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean canFireProjectileWeapon(@NotNull ProjectileWeaponItem projectileWeaponItem) {
        return projectileWeaponItem == Items.CROSSBOW || projectileWeaponItem == Items.BOW;
    }

    @Override
    public List<NpcOffer> getOffers() {
        return NpcTrades.getOffers();
    }

    @Override
    public void thunderHit(ServerLevel level, @NotNull LightningBolt lightningBolt) {
        if (level.getDifficulty() != Difficulty.PEACEFUL && Ouat.COMMON.canLivingConvert(this, EntityType.WITCH)) {
            Ouat.LOG.info("Npc {} was struck by lightning {}.", this, lightningBolt);
            Witch witch = EntityType.WITCH.create(level);
            if (witch != null) {
                witch.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                witch.finalizeSpawn(level, level.getCurrentDifficultyAt(witch.blockPosition()), MobSpawnType.CONVERSION, (SpawnGroupData) null, (CompoundTag) null);
                witch.setNoAi(this.isNoAi());
                if (this.hasCustomName()) {
                    witch.setCustomName(this.getCustomName());
                    witch.setCustomNameVisible(this.isCustomNameVisible());
                }

                witch.setPersistenceRequired();
                Ouat.COMMON.onLivingConvert(this, witch);
                level.addFreshEntityWithPassengers(witch);
                this.discard();
            } else {
                super.thunderHit(level, lightningBolt);
            }
        } else {
            super.thunderHit(level, lightningBolt);
        }
    }

    @Override
    public void setLastHurtByMob(@Nullable LivingEntity livingEntity) {
        if (!level().isClientSide() && livingEntity instanceof Player) {
            sendParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
        }
        super.setLastHurtByMob(livingEntity);
    }

    protected void sendParticlesAroundSelf(ParticleOptions particleOption) {
        if (level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 5; i++) {
                serverLevel.sendParticles(particleOption, getRandomX(1.0), getRandomY() + 1.0, getRandomZ(1.0), 1, 0.0, 0.0, 0.0, random.nextGaussian() * 0.02);
            }
        }
    }

    public void assignTown(Town town) {
        if (town != null && town.getLevel() == level()) {
            this.town = town;
            this.townId = town.getId();
        }
    }

    public void setCultureId(String cultureId) {
        entityData.set(CULTURE, cultureId);
    }

    public void setProfessionId(String professionId) {
        entityData.set(PROFESSION, professionId);
    }

    public int getUnhappyCounter() {
        return entityData.get(DATA_UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int unhappyCounter) {
        entityData.set(DATA_UNHAPPY_COUNTER, unhappyCounter);
    }

    @Override
    public void shootCrossbowProjectile(@NotNull LivingEntity target, @NotNull ItemStack crossbowStack, @NotNull Projectile projectile, float projectileAngle) {
        shootCrossbowProjectile(this, target, projectile, projectileAngle, 1.6F);
    }

    @Override
    public void onCrossbowAttackPerformed() {
        noActionTime = 0;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    public String getCultureId() {
        return entityData.get(CULTURE);
    }

    public String getProfessionId() {
        return entityData.get(PROFESSION);
    }

    @Override
    public int getAmbientSoundInterval() {
        return super.getAmbientSoundInterval();
    }

    public void playCelebrateSound() {
        playSound(SoundEvents.VILLAGER_CELEBRATE, getSoundVolume(), getVoicePitch());
    }

    @Override
    protected float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions size) {
        return isBaby() ? 0.81F : 1.62F;
    }

    @Override
    public double getMyRidingOffset() {
        return isBaby() ? 0.0D : -0.30D;
    }

    @Override
    public AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob otherParent) {
        return EntityRegistry.REGISTRY.NPC.get().create(level);
    }

    @Override
    public boolean canChangeDimensions() {
        return false; // Todo : set to true
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ---- GETTERS & SETTERS ---- //


    public Town getTown() {
        return town;
    }

    public boolean isCrossingArms() {
        return entityData.get(DATA_CROSSING_ARMS);
    }

    public void setCrossingArms(boolean crossingArms) {
        entityData.set(DATA_CROSSING_ARMS, crossingArms);
    }

    public boolean isReading() {
        return entityData.get(DATA_READING);
    }

    public void setReading(boolean reading) {
        entityData.set(DATA_READING, reading);
    }

    public boolean isChargingCrossbow() {
        return entityData.get(DATA_IS_CHARGING_CROSSBOW);
    }

    @Override
    public void setChargingCrossbow(boolean chargingCrossbow) {
        entityData.set(DATA_IS_CHARGING_CROSSBOW, chargingCrossbow);
    }

    @Override
    public Player getInteractingPlayer() {
        return interactingPlayer;
    }

    @Override
    public void setInteractingPlayer(@Nullable Player player) {
        interactingPlayer = player;
    }

    public void setFishingHook(NpcFishingHook hook) {
        fishingHook = hook;
    }

    @Override
    public Npc getNpc() {
        return this;
    }

    private enum Activity {
        WORKING,
        FREE_TIME,
        RESTING,
        SLEEPING,
        RAID
    }
}

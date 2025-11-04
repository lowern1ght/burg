package org.dawnoftime.onceuponatown.entity;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.BuildProject;
import org.dawnoftime.onceuponatown.culture.Profession;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.entity.ai.NpcAi;
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
import java.util.Set;
import java.util.function.Predicate;

public class Npc extends AgeableMob implements InteractingNpc, RangedAttackMob, CrossbowAttackMob {
    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CROSSING_ARMS = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_READING = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> CULTURE = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PROFESSION = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.STRING);
    private static final Set<Item> WANTED_ITEMS = ImmutableSet.of(Items.BREAD, Items.POTATO, Items.CARROT, Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT, Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD, Items.COD, Items.SALMON);
    public static final double WALK_SPEED = 0.5D;
    public static final double RUN_SPEED = 0.65D;
    public static final double SPRINT_SPEED = 0.75D;
    public static final float STROLL_SPEED = 0.4F;
    @Nullable
    private Player interactingPlayer;
    private int blockBreakTime;
    private int lastBreakProgress = -1;
    private NpcFishingHook fishingHook;
    private Profession profession;
    private BlockPos attackedBlock;
    private int townId = -1;
    private BuildProject project;
    // TODO gossips
    // TODO golems
    // TODO food

    public Npc(EntityType<Npc> entityType, Level level) {
        super(entityType, level);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 16.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        getNavigation().setCanFloat(true);
        setCanPickUpLoot(true);
        if (!level().isClientSide()) {
            profession = ServerCultures.getCultureOrDefault(getCultureId()).getProfessionOrDefault(getProfessionId());
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
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setCulture(tag.getString("Culture"));
        entityData.set(PROFESSION, tag.getString("Profession"));
        if (!level().isClientSide()) {
            profession = ServerCultures.getCultureOrDefault(getCultureId()).getProfessionOrDefault(getProfessionId());
        }
        townId = tag.getInt("TownId");
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_UNHAPPY_COUNTER, 0);
        entityData.define(DATA_IS_CHARGING_CROSSBOW, false);
        entityData.define(DATA_CROSSING_ARMS, true);
        entityData.define(DATA_READING, false);
        entityData.define(CULTURE, "plains");
        entityData.define(PROFESSION, "unemployed");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, WALK_SPEED).add(Attributes.FOLLOW_RANGE, 48.0D).add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    public @NotNull Brain<Npc> getBrain() {
        return (Brain<Npc>) super.getBrain();
    }

    @Override
    protected Brain.@NotNull Provider<Npc> brainProvider() {
        return Brain.provider(NpcAi.MEMORIES, NpcAi.SENSORS);
    }

    @Override
    protected @NotNull Brain<?> makeBrain(@NotNull Dynamic<?> dynamic) {
        Brain<Npc> brain = this.brainProvider().makeBrain(dynamic);
        NpcAi.setupBrain(brain, this);
        return brain;
    }

    public void refreshAi(ServerLevel serverLevel) {
        Brain<Npc> brain = this.getBrain();
        brain.stopAll(serverLevel, this);
        this.brain = brain.copyWithoutBehaviors();
        NpcAi.setupBrain(this.getBrain(), this);
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (level() instanceof ServerLevel serverLevel) {
            this.refreshAi(serverLevel);
        }
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return (WANTED_ITEMS.contains(stack.getItem()));
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
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getItem();
        if (wantsToPickUp(itemStack)) {
            onItemPickup(itemEntity);
            take(itemEntity, itemStack.getCount());
            itemEntity.discard();
        }
    }

    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    public void setAssignedProject(BuildProject project) {
        this.project = project;
    }

    public BuildProject getAssignedProject() {
        return project;
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("ouatNpcBrain");
        this.getBrain().tick((ServerLevel) this.level(), this);
        this.level().getProfiler().pop();
        super.customServerAiStep();
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
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // TODO unregister from town
    }

    public void quitTown() {
        Town town = getTown();
        if (town != null) {
            town.removeCitizen(this);
        }
    }



    @Override
    public void die(@NotNull DamageSource cause) {
        Ouat.LOG.info("Npc {} died, message: '{}'", this, cause.getLocalizedDeathMessage(this).getString());
        // tellWitnessesThatIWasMurdered
        super.die(cause);
    }

    public int getPlayerReputation(Player player) {
        return 1;
    }

    @Override
    public void handleEntityEvent(byte id) {
        /*
        if (id == 12) {
            this.addParticlesAroundSelf(ParticleTypes.HEART);
        } else if (id == 13) {
            this.addParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
        } else if (id == 14) {
            this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
        } else if (id == 42) {
            this.addParticlesAroundSelf(ParticleTypes.SPLASH);
        } else {
            super.handleEntityEvent(id);
        }
         */
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        // TODO rewrite this
        ItemStack itemStack = player.getItemInHand(hand);
        if (!level().isClientSide()
            && player instanceof ServerPlayer serverPlayer
            && !itemStack.is(ItemRegistry.REGISTRY.NPC_SPAWN_EGG.get())
            && isAlive()
            && !isSleeping()
            && (hand == InteractionHand.MAIN_HAND)
        ) {
            Town town = getTown();
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

    public void holdInHands(ItemStack mainHandStack, ItemStack offHandStack) {
        holdInMainHand(mainHandStack);
        holdInOffHand(offHandStack);
    }

    public void holdInMainHand(ItemStack stack) {
        setItemInHand(InteractionHand.MAIN_HAND, stack);
        setCrossingArms(false);
    }

    public void holdInOffHand(ItemStack stack) {
        setItemInHand(InteractionHand.OFF_HAND, stack);
        setCrossingArms(false);
    }

    public void freeMainHand() {
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            setCrossingArms(true);
        }
    }

    public void freeOffHand() {
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            setCrossingArms(true);
        }
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
        // TODO rewrite this
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
        // TODO rewrite this
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

    public void setTown(Town town) {
        if (town != null && level() instanceof ServerLevel serverLevel && town.getLevel() == serverLevel) {
            refreshAi(serverLevel);
            townId = town.getId();
        }
    }

    public void setCulture(String cultureId) {
        entityData.set(CULTURE, cultureId);
    }

    public void setProfession(Profession profession) {
        entityData.set(PROFESSION, profession.getId());
        this.profession = profession;
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
    protected void sendDebugPackets() {
        super.sendDebugPackets();
        DebugPackets.sendEntityBrain(this);
    }

    @Override
    public void startSleeping(BlockPos pos) {
        super.startSleeping(pos);
        brain.setMemory(MemoryModuleType.LAST_SLEPT, level().getGameTime());
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    public void stopSleeping() {
        super.stopSleeping();
        brain.setMemory(MemoryModuleType.LAST_WOKEN, level().getGameTime());
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

    public void playWorkSound() {
        /*
        SoundEvent soundEvent = profession.workSound();
        if (soundEvent != null) {
            this.playSound(soundEvent, this.getSoundVolume(), this.getVoicePitch());
        }
         */
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (this.isSleeping()) {
            return null;
        } else {
            return SoundEvents.VILLAGER_AMBIENT;
        }
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

    public Profession getProfession() {
        return profession == null ? Profession.UNEMPLOYED : profession;
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
        // TODO false
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTicks) {
        float f = Mth.lerp(partialTicks, this.yBodyRotO, this.yBodyRot) * (float) (Math.PI / 180.0);
        Vec3 vec3 = new Vec3(0.0, this.getBoundingBox().getYsize() - 1.0, 0.2);
        return this.getPosition(partialTicks).add(vec3.yRot(-f));
    }

    @Nullable
    public Town getTown() {
        if (townId != -1 && level() instanceof ServerLevel serverLevel) {
            Town town = LevelTowns.of(serverLevel).getTownById(townId);
            if (town == null) {
                townId = -1;
            }
            return town;
        } else {
            return null;
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ---- GETTERS & SETTERS ---- //

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

    public NpcFishingHook getFishingHook() {
        return fishingHook;
    }

    @Override
    public Npc getNpc() {
        return this;
    }
}

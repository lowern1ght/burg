package org.dawnoftime.onceuponatown.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
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
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.entity.ai.goal.core.NpcPanicGoal;
import org.dawnoftime.onceuponatown.entity.ai.goal.fight.SelfDefenseGoal;
import org.dawnoftime.onceuponatown.entity.ai.goal.work.FishermanWorkGoal;
import org.dawnoftime.onceuponatown.menu.InteractingNpc;
import org.dawnoftime.onceuponatown.menu.TradeMenu;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.trade.BuyDeal;
import org.dawnoftime.onceuponatown.trade.MerchantDeal;
import org.dawnoftime.onceuponatown.trade.SellDeal;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Npc extends AgeableMob implements InteractingNpc, RangedAttackMob, CrossbowAttackMob {
    private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING_CROSSBOW = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CROSSING_ARMS = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_READING = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    //TODO make a clean and custom client serialization
    private static final EntityDataAccessor<CompoundTag> PROF = SynchedEntityData.defineId(Npc.class, EntityDataSerializers.COMPOUND_TAG);
    public static final double DEFAULT_SPEED = 0.25D;
    public static final double RUN_SPEED_MODIFIER = 0.65D;
    public static final double SPRINT_SPEED_MODIFIER = 0.75D;
    private Player interactingPlayer;
    private Activity currentActivity;
    private Town town;
    private int blockBreakTime;
    private int lastBreakProgress = -1;
    private NpcFishingHook fishingHook;
    private Profession profession = Profession.BUILDER;
    private String cultureId = "plains";

    public Npc(EntityType<Npc> entityType, Level level) {
        super(entityType, level);
        CompoundTag clientData = new CompoundTag();
        clientData.putString("CultureId", cultureId);
        clientData.putString("ProfessionId", profession.getId());
        entityData.set(PROF, clientData);
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CHARGING_CROSSBOW, false);
        entityData.define(DATA_CROSSING_ARMS, false);
        entityData.define(DATA_READING, false);
        entityData.define(PROF, new CompoundTag());
    }

    public CompoundTag getClientData() {
        return entityData.get(PROF);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, DEFAULT_SPEED).add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.FOLLOW_RANGE, 50.0D);
    }

    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
    }

    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
    }

    protected void registerGoals() {
        addCoreGoals();
        addRaidGoals();
        addSleepingGoals();
        addRestingGoals();
        addWorkGoals();
        addFreeTimeGoals();
        targetSelector.addGoal(0, new HurtByTargetGoal(this));
    }

    private void addCoreGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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

        //goalSelector.addGoal(8, new FishermanWorkGoal(this));
        goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, LivingEntity.class, 10.0F));
        goalSelector.addGoal(13, new RandomLookAroundGoal(this));

    }

    private void addRaidGoals() {
    }

    private void addSleepingGoals() {
    }

    private void addRestingGoals() {

    }

    private void addWorkGoals() {
        //goalSelector.addGoal(12, new BuilderWorkGoal(this));
    }

    private void addFreeTimeGoals() {

    }

    public void onFinalizeSpawnEvent() {
        //setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        //setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        //setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        //setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }

    public void aiStep() {
        updateSwingTime();
        super.aiStep();
    }

    @Override
    protected void customServerAiStep() {
        BlockPos posAboveHead = blockPosition().above(2);
        BlockState stateAboveHead = level().getBlockState(posAboveHead);
        if (stateAboveHead.isSolid()) {
            float destroySpeed = stateAboveHead.getDestroySpeed(level(), posAboveHead);
            if (blockBreakTime % 4 == 0) {
                swing(getUsedItemHand());
                SoundType soundType = stateAboveHead.getSoundType();
                playSound(soundType.getHitSound(), (soundType.getVolume() + 1.0F) / 8.0F, soundType.getPitch() * 0.5F);
            }

            ++blockBreakTime;
            int i = (int)((float) blockBreakTime / destroySpeed);
            System.out.println("destroySpeed : " + destroySpeed + " | breakTime : " + blockBreakTime + " | i : " + i);
            if (i != lastBreakProgress) {
                level().destroyBlockProgress(getId(), posAboveHead, i);
                this.lastBreakProgress = i;
            }

            if (blockBreakTime >= destroySpeed * 10.0F) {
                //level().removeBlock(posAboveHead, false);
                level().destroyBlock(posAboveHead, false);
                blockBreakTime = 0;
            }
        }
    }

    public void rideTick() {
        super.rideTick(); //if (this.getVehicle() instanceof AbstractHorse horse && !horse.isTamed())//horse.setTamed(true);
    }

    public boolean hurt(@NotNull DamageSource source, float amount) {
        setCrossingArms(false);
        return super.hurt(source, amount);
    }

    public void die(@NotNull DamageSource cause) {
        super.die(cause);
        //tradingHandler.stopTrading();
        Ouat.LOG.info("Npc {} died, message: '{}'", this, cause.getLocalizedDeathMessage(this).getString());
    }

    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!level().isClientSide() && (hand == InteractionHand.MAIN_HAND)) {
            this.interactingPlayer = player;
            if (player instanceof ServerPlayer serverPlayer) {
                /*
                Ouat.COMMON.openMenu(serverPlayer, new SimpleMenuProvider((containerID, playerInventory, p) -> new BuyMenu(containerID, playerInventory, this), Component.literal("Buy")), buffer -> {
                    buffer.writeInt(this.getId());
                    TradeUtils.writeBuyDealsToStream(getBuyDeals(), buffer);
                });

                 */
                Ouat.COMMON.openMenu(serverPlayer, new SimpleMenuProvider((containerID, playerInventory, p) -> new TradeMenu(containerID, playerInventory, this), Component.literal("Buy")), buffer -> {
                    buffer.writeInt(this.getId());
                    TradeUtils.writeMerchantDealsToStream(getMerchantDeals(), buffer);
                });

            }

        }
        return super.mobInteract(player, hand);
    }

    public void notifyDealMade(BuyDeal deal) {

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
        arrow.shoot(d0, d1 + d3 * (double)0.2F, d2, 1.6F, (float)(14 - this.level().getDifficulty().getId() * 4));
        playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        level().addFreshEntity(arrow);
    }

    public @NotNull ItemStack getProjectile(ItemStack weaponStack) {
        if (weaponStack.getItem() instanceof ProjectileWeaponItem projectileWeaponItem) {
            Predicate<ItemStack> predicate = projectileWeaponItem.getSupportedHeldProjectiles();
            ItemStack heldProjectile = ProjectileWeaponItem.getHeldProjectile(this, predicate);
            return Ouat.COMMON.getProjectile(this, weaponStack, heldProjectile.isEmpty() ? new ItemStack(Items.ARROW) : heldProjectile);
        } else {
            return Ouat.COMMON.getProjectile(this, weaponStack, ItemStack.EMPTY);
        }
    }

    public boolean canFireProjectileWeapon(@NotNull ProjectileWeaponItem projectileWeaponItem) {
        return projectileWeaponItem == Items.CROSSBOW || projectileWeaponItem == Items.BOW;
    }

    public List<BuyDeal> getBuyDeals() {
        List<BuyDeal> deals = new ArrayList<>();
        deals.add(TradeUtils.buyDeal(Items.BROWN_MUSHROOM,1));
        deals.add(TradeUtils.buyDeal(Items.RED_MUSHROOM,1));
        deals.add(TradeUtils.buyDeal(Items.MANGROVE_PROPAGULE,3));
        deals.add(TradeUtils.buyDeal(Items.CHERRY_SAPLING,2));
        deals.add(TradeUtils.buyDeal(Items.AZALEA,2));
        deals.add(TradeUtils.buyDeal(Items.FEATHER,1));
        deals.add(TradeUtils.buyDeal(Items.DANDELION,1, 0, 1, 26));
        deals.add(TradeUtils.buyDeal(Items.FERN,1, 5, 10, 42));
        deals.add(TradeUtils.buyDeal(Items.FERN,1,1,0,2));
        deals.add(TradeUtils.buyDeal(Items.DIAMOND_SWORD,1));
        return deals;
    }

    public List<SellDeal> getSellDeals() {
        List<SellDeal> deals = new ArrayList<>();
        deals.add(TradeUtils.sellDeal(Items.WATER_BUCKET, 1, 3, 0, 0));
        deals.add(TradeUtils.sellDeal(Items.RABBIT, 1, 0, 5,3));
        deals.add(TradeUtils.sellDeal(Items.COAL, 5));
        deals.add(TradeUtils.sellDeal(Items.EGG, 5));
        deals.add(TradeUtils.sellDeal(Items.STICK, 5));
        deals.add(TradeUtils.sellDeal(Items.WHITE_WOOL, 2));
        return deals;
    }

    @Override
    public List<MerchantDeal> getMerchantDeals() {
        List<MerchantDeal> deals = new ArrayList<>();
        // Buy deals
        deals.add(MerchantDeal.Builder.buyDeal(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.BROWN_MUSHROOM, 1)).requiredB(new ItemStack(Items.OAK_PLANKS, 7)).build());
        deals.add(MerchantDeal.Builder.buyDeal(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.RED_MUSHROOM, 1)).build());
        deals.add(MerchantDeal.Builder.buyDeal(new ItemStack(Items.EMERALD, 6), new ItemStack(Items.MANGROVE_PROPAGULE, 1)).build());
        deals.add(MerchantDeal.Builder.buyDeal(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.CHERRY_SAPLING, 1)).build());
        deals.add(MerchantDeal.Builder.buyDeal(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.AZALEA, 1)).build());
        deals.add(MerchantDeal.Builder.buyDeal(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.FEATHER, 1)).build());
        // Sell deals
        deals.add(MerchantDeal.Builder.sellDeal(new ItemStack(Items.WHITE_WOOL, 1), new ItemStack(Items.EMERALD, 2)).build());
        deals.add(MerchantDeal.Builder.sellDeal(new ItemStack(Items.COAL, 16), new ItemStack(Items.EMERALD, 1)).build());
        deals.add(MerchantDeal.Builder.sellDeal(new ItemStack(Items.RABBIT, 4), new ItemStack(Items.EMERALD, 3)).build());
        deals.add(MerchantDeal.Builder.sellDeal(new ItemStack(Items.WATER_BUCKET, 1), new ItemStack(Items.EMERALD, 1)).build());
        return deals;
    }

    public void thunderHit(ServerLevel level, @NotNull LightningBolt lightningBolt) {
        if (level.getDifficulty() != Difficulty.PEACEFUL && Ouat.COMMON.canLivingConvert(this, EntityType.WITCH)) {
            Ouat.LOG.info("Npc {} was struck by lightning {}.", this, lightningBolt);
            Witch witch = EntityType.WITCH.create(level);
            if (witch != null) {
                witch.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                witch.finalizeSpawn(level, level.getCurrentDifficultyAt(witch.blockPosition()), MobSpawnType.CONVERSION, (SpawnGroupData)null, (CompoundTag)null);
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

    public void shootCrossbowProjectile(@NotNull LivingEntity target, @NotNull ItemStack crossbowStack, @NotNull Projectile projectile, float projectileAngle) {
        shootCrossbowProjectile(this, target, projectile, projectileAngle, 1.6F);
    }

    public void onCrossbowAttackPerformed() {
        noActionTime = 0;
    }

    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.VILLAGER_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    protected SoundEvent getAmbientSound() {
        return null;
        //return SoundEvents.VILLAGER_AMBIENT; //return tradingHandler.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
    }

    public int getAmbientSoundInterval() {
        return 600;
    }

    protected float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions size) {
        return isBaby() ? 0.81F : 1.62F;
    }

    public double getMyRidingOffset() {
        return isBaby() ? 0.0D : -0.30D;
    }

    public AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob otherParent) {
        return EntityRegistry.REGISTRY.NPC.get().create(level);
    }

    public boolean canChangeDimensions() {
        return false; // Todo : set to true
    }

    public boolean canBeLeashed(@NotNull Player player) {
        return true;
    }

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

    public void setChargingCrossbow(boolean chargingCrossbow) {
        entityData.set(DATA_IS_CHARGING_CROSSBOW, chargingCrossbow);
    }

    public Player getInteractingPlayer() {
        return interactingPlayer;
    }

    public void setInteractingPlayer(@Nullable Player player) {
        interactingPlayer = player;
    }

    public void setFishingHook(NpcFishingHook hook) {
        fishingHook = hook;
    }

    public Town getTown(){
        return town;
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

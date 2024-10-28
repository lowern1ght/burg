package org.dawnoftime.onceuponatown.entity;

import org.dawnoftime.onceuponatown.menu.BuyMenu;
import org.dawnoftime.onceuponatown.menu.InteractableCitizen;
import org.dawnoftime.onceuponatown.platform.Platform;
import org.dawnoftime.onceuponatown.registry.OuatEntitiesRegistry;
import org.dawnoftime.onceuponatown.trade.BuyDeal;
import org.dawnoftime.onceuponatown.trade.SellDeal;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import com.mojang.logging.LogUtils;
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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class Citizen extends AgeableMob implements InteractableCitizen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Boolean> DATA_CROSSING_ARMS = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_READING = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_CULTURE = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_PROFESSION = SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.BYTE);
    private Player interactingPlayer;

    public Citizen(EntityType<Citizen> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean save(@NotNull CompoundTag compound) {
        return super.save(compound);
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    public void onFinalizeSpawnEvent() {
        setCulture(CitizenCulture.byBiome(level().getBiome(blockPosition())));
        setProfession(CitizenProfession.BUILDER);
        setPersistenceRequired();
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RABBIT_STEW));
        setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.MILK_BUCKET));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CROSSING_ARMS, false);
        this.entityData.define(DATA_READING, false);
        this.entityData.define(DATA_CULTURE, CitizenCulture.PLAINS.getId());
        this.entityData.define(DATA_PROFESSION, CitizenProfession.UNEMPLOYED.getId());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5D).add(Attributes.ATTACK_DAMAGE, 7.0D).add(Attributes.FOLLOW_RANGE, 200.0D);
    }

    @Override
    protected void registerGoals() {
        int priority = -1;
        this.goalSelector.addGoal(++priority, new FloatGoal(this));
        //this.goalSelector.addGoal(++priority, new BuilderWorkAI(this));
        this.goalSelector.addGoal(++priority, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(++priority, new LookAtPlayerGoal(this, LivingEntity.class, 10.0F));
        this.goalSelector.addGoal(++priority, new RandomLookAroundGoal(this));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_CULTURE, tag.getByte("Culture"));
        this.entityData.set(DATA_PROFESSION, tag.getByte("Profession"));
        if (!level().isClientSide() && tag.contains("AdminOrder")) {
            CompoundTag orderTag = tag.getCompound("AdminOrder");
            switch (orderTag.getString("OrderType")) {
                //case "Build" -> this.adminOrder = new AdminOrder.BuildOrder(ConstructionProjectManager.get((ServerLevel)getLevel()).getProjectByName(orderTag.getString("BuildingSite")));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Culture", this.entityData.get(DATA_CULTURE));
        tag.putByte("Profession", this.entityData.get(DATA_PROFESSION));
    }

    @Override
    public void aiStep() {
        this.updateSwingTime();
        super.aiStep();
    }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!level().isClientSide() && (hand == InteractionHand.MAIN_HAND)) {
            this.interactingPlayer = player;
            if (player instanceof ServerPlayer serverPlayer) {
                Platform.PLATFORM.openMenu(serverPlayer, new SimpleMenuProvider((containerID, playerInventory, p) -> new BuyMenu(containerID, playerInventory, this), Component.literal("Buy")), buffer -> {
                    buffer.writeInt(this.getId());
                    TradeUtils.writeBuyDealsToStream(getBuyDeals(), buffer);
                });
            }

        }
        return super.mobInteract(player, hand);
    }

    public void sayInPlayerActionBar(Player player, Component component) {
        if (component != null) {
            player.displayClientMessage(component, true);
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        setCrossingArms(false);
        return super.hurt(source, amount);
    }

    @Override
    public void die(@NotNull DamageSource cause) {
        super.die(cause);
        //tradingHandler.stopTrading();
        LOGGER.info("Citizen {} died, message: '{}'", this, cause.getLocalizedDeathMessage(this).getString());
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(source);
    }

    @Override
    public AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob otherParent) {
        return OuatEntitiesRegistry.ENTITY_REGISTRY.CITIZEN.get().create(level);
    }

    @Override
    protected float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions size) {
        return this.isBaby() ? 0.81F : 1.62F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null; //SoundEvents.VILLAGER_AMBIENT;
        //return tradingHandler.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    public boolean canBeLeashed(@NotNull Player player) {
        return false;
    }

    @Override
    public void thunderHit(ServerLevel level, @NotNull LightningBolt lightningBolt) {
        if (level.getDifficulty() != Difficulty.PEACEFUL && Platform.PLATFORM.canLivingConvert(this, EntityType.WITCH)) {
            LOGGER.info("Citizen {} was struck by lightning {}.", this, lightningBolt);
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
                Platform.PLATFORM.onLivingConvert(this, witch);
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
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {return SoundEvents.VILLAGER_HURT;}

    @Override
    protected SoundEvent getDeathSound() {return SoundEvents.VILLAGER_DEATH;}

    @Override
    public int getAmbientSoundInterval() {return 200;}

    public CitizenCulture getCulture() {return CitizenCulture.byId(this.entityData.get(DATA_CULTURE));}

    public void setCulture(CitizenCulture culture) {this.entityData.set(DATA_CULTURE, culture.getId());}

    public CitizenProfession getProfession() {return CitizenProfession.byId(this.entityData.get(DATA_PROFESSION));}

    public void setProfession(CitizenProfession profession) {this.entityData.set(DATA_PROFESSION, profession.getId());}

    public boolean isCrossingArms() {return this.entityData.get(DATA_CROSSING_ARMS);}

    public void setCrossingArms(boolean crossing) {this.entityData.set(DATA_CROSSING_ARMS, crossing);}

    public boolean isReading() {return this.entityData.get(DATA_READING);}

    public void setReading(boolean reading) {this.entityData.set(DATA_READING, reading);}

    @Override
    public Player getInteractingPlayer() {return this.interactingPlayer;}

    @Override
    public void setInteractingPlayer(@Nullable Player player) {
        this.interactingPlayer = player;
    }


    @Override
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

    @Override
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
    public void notifyDealMade(BuyDeal deal) {

    }

    @Override
    public Citizen getCitizen() {
        return this;
    }
}

package org.dawnoftime.onceuponatown.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;

import java.util.List;

public class Npc extends PathfinderMob {
    private SimpleStateMachine stateMachine;

    public Npc(EntityType<? extends Npc> type, Level level) {
        super(type, level);
        // Builders must never despawn naturally — Minecraft would silently remove them
        // when no player is nearby, breaking the town's builder UUID reference.
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // Keep only basic survival goals - construction logic lives in SimpleStateMachine
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (stateMachine == null) stateMachine = new SimpleStateMachine(this);
            stateMachine.tick();
        }
    }

    // Called by BuildGoal when construction is complete.
    // rotation and entryConnectorWorldPos are precomputed by SimpleStateMachine/BuildGoal.
    public void onBuildComplete(BlockPos builtAt, String buildingId, ConnectionPoint usedConnection,
                                Rotation rotation, BlockPos entryConnectorWorldPos) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        LevelTowns.get(serverLevel).getAllTowns().stream()
            .filter(t -> getUUID().equals(t.getBuilderNpcId()))
            .findFirst()
            .ifPresent(town -> {
                List<ConnectionPoint> connections = BuildSchematic.readJigsawPoints(
                    serverLevel, builtAt, buildingId, rotation, entryConnectorWorldPos);
                BoundingBox bb = BuildingDataHandler.get(buildingId)
                    .flatMap(def -> BuildSchematic.computeBoundingBox(serverLevel, builtAt, def.nbt, rotation))
                    .orElseGet(() -> new BoundingBox(
                        builtAt.getX(), builtAt.getY(), builtAt.getZ(),
                        builtAt.getX(), builtAt.getY(), builtAt.getZ()
                    ));
                town.registerBuilding(builtAt, buildingId, connections, bb, rotation);
                LevelTowns.get(serverLevel).markDirty();
            });
    }

    // --- Debug immortality ---

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.5)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // Stub accessors used by NpcModel for animations - simplified in v1
    public int getUnhappyCounter() { return 0; }
    public boolean isCrossingArms() { return false; }
    public boolean isReading() { return false; }
}

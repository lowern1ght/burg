package org.lowern1ght.burg.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.entity.ai.OuatWalkNodeEvaluator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.lowern1ght.burg.entity.ai.OpenFenceGateGoal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.lowern1ght.burg.building.schematic.BuildSchematic;
import org.lowern1ght.burg.datapack.BuildingDataHandler;
import org.lowern1ght.burg.entity.ai.SimpleStateMachine;
import org.lowern1ght.burg.town.ConnectionPoint;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

import java.util.List;

public class Npc extends AgeableMob implements TownNpc,
        net.minecraft.world.item.trading.Merchant {

    /**
     * What this person is for. Persisted, because it decides whether they get the build brain.
     *
     * <p>There was no role before, and the absence was load-bearing in a way that would have
     * looked like a spawn bug: {@link #tick} discards any {@code Npc} whose UUID is not in
     * {@code town.getBuilderNpcIds()}, so the first ordinary resident ever spawned would have
     * deleted itself on its first tick, silently. And {@link SimpleStateMachine} was built for
     * every Npc unconditionally, so had it survived it would have started scanning the
     * construction queue and trying to build the town.
     */
    public enum Role {
        /** Builds the town. One per builder slot, immortal, self-validates against the slot list. */
        BUILDER,
        /** Lives in the town. Works if there is work, otherwise idles — and can die. */
        SETTLER
    }

    private static final EntityDataAccessor<Boolean> DATA_IS_READING =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.BOOLEAN);
    // Incremented on each block placement; client reads changes to trigger the swing animation.
    private static final EntityDataAccessor<Integer> DATA_BUILD_GENERATION =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.INT);
    /**
     * Which person this body is currently lending itself to. SYNCED, and that is the point.
     *
     * <p>Everything the client draws — name, face, build, hair, clothes — derives from the
     * PERSON's id, never from this entity's UUID. A body is disposable and gets recycled as the
     * player walks across town; key the look to the body and somebody who leaves the window and
     * comes back returns as a different human being.
     */
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> DATA_PERSON =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.OPTIONAL_UUID);
    /**
     * Wealth tier, synced.
     *
     * <p>The first thing about a citizen's appearance that is NOT derivable from an id, because
     * it changes: a person who earns climbs the tiers and their clothes have to say so. Sent as
     * the ordinal rather than the purse — the client needs to know how to draw them, not how much
     * they have.
     */
    private static final EntityDataAccessor<Integer> DATA_WEALTH =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.INT);
    /**
     * What the body is doing, as one value. See {@link NpcPose} for why it is not a flag per
     * pose: four independent booleans can say reading AND sitting AND talking at once, and by
     * the third pose the model is asking questions that contradict each other.
     */
    private static final EntityDataAccessor<Integer> DATA_POSE =
        SynchedEntityData.defineId(Npc.class, EntityDataSerializers.INT);

    // Client-side animation state: written by NpcModel.setupAnim(), never synced or saved.
    public int clientLastBuildGeneration = -1;
    public float clientBuildPlacedAtAge = -1000f;

    private SimpleStateMachine stateMachine;
    // A settler's working day. Built lazily like the state machine, and for the same reason:
    // the entity exists before its town does on a fresh load.
    private org.lowern1ght.burg.entity.ai.WorkShift workShift;
    // Where this person works, or null for the idle. Persisted so a trade survives a reload.
    private BlockPos jobSite = null;
    // How good they are at it. Grows a step per completed shift, capped by the job config.
    private int skill = 0;
    // What this person is for. Defaults to BUILDER so that every Npc already saved in a world
    // keeps behaving exactly as it did before this field existed.
    private Role role = Role.BUILDER;
    // Server-side countdown -- cleared to 0 when the reading animation ends.
    private int readingTicksRemaining = 0;
    // Anchor position of the town this builder belongs to; saved so the builder can self-validate on load.
    private BlockPos townAnchorPos = null;
    // Whether the anchor ownership check has been performed this session.
    private boolean anchorValidated = false;
    // --- trading. A person deals in what their trade makes; see Trading. ---------------
    private net.minecraft.world.entity.player.Player tradingWith;
    private net.minecraft.world.item.trading.MerchantOffers offers;

    public Npc(EntityType<? extends Npc> type, Level level) {
        super(type, level);
        // Builders must never despawn naturally — Minecraft would silently remove them
        // when no player is nearby, breaking the town's builder UUID reference.
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_READING, false);
        builder.define(DATA_BUILD_GENERATION, 0);
        builder.define(DATA_PERSON, java.util.Optional.empty());
        builder.define(DATA_WEALTH, 0);
        builder.define(DATA_POSE, NpcPose.STANDING.index());
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level) {
            @Override
            protected PathFinder createPathFinder(int maxVisitedNodes) {
                this.nodeEvaluator = new OuatWalkNodeEvaluator();
                this.nodeEvaluator.setCanPassDoors(true);
                return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
            }
        };
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, false));
        this.goalSelector.addGoal(2, new OpenFenceGateGoal(this));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (!anchorValidated && townAnchorPos != null && level() instanceof ServerLevel sl) {
                anchorValidated = true;
                Town town = LevelTowns.get(sl).getTownAt(townAnchorPos).orElse(null);
                // Validated against the list for its own role. A builder that has lost its slot
                // is a leak and goes; a settler is checked against the resident roll instead,
                // because before this it was checked against the builder slots and so every
                // settler deleted itself on its first tick without a word.
                if (town == null) { discard(); return; }
                if (role == Role.BUILDER) {
                    // A builder is nobody: it holds a slot, not a life.
                    if (!town.getBuilderNpcIds().contains(getUUID())) { discard(); return; }
                } else {
                    // A body validates against the PERSON it represents, not against a list of
                    // entities. That is the whole change: the roll of entities could only ever
                    // describe who was loaded, and a settler whose chunk had been unloaded looked
                    // exactly like a settler who had never existed.
                    java.util.UUID pid = getPersonId().orElse(null);
                    var person = pid == null ? null : town.people().get(pid);
                    if (person == null || !person.alive()) { discard(); return; }
                }
            }
            // The build brain belongs to builders. A settler running it would scan the
            // construction queue and start putting the town up by itself.
            if (role == Role.BUILDER) {
                if (stateMachine == null) stateMachine = new SimpleStateMachine(this);
                stateMachine.tick();
            } else {
                if (workShift == null) {
                    workShift = new org.lowern1ght.burg.entity.ai.WorkShift(this);
                }
                workShift.tick();
            }
            if (readingTicksRemaining > 0 && --readingTicksRemaining == 0) {
                entityData.set(DATA_IS_READING, false);
                setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (workShift != null && level() instanceof ServerLevel sl && townAnchorPos != null) {
            Town town = LevelTowns.get(sl).getTownAt(townAnchorPos).orElse(null);
            workShift.onRemoved(town);
            // Struck off the roll only on a real death or removal, NOT when the chunk unloads:
            // a settler whose chunk is unloaded is still a resident, and removing them here would
            // quietly depopulate any town the player walks away from.
            if (town != null && reason == RemovalReason.KILLED) {
                town.removeResident(getUUID());
                LevelTowns.get(sl).markDirty();
            }
        }
        super.remove(reason);
    }


    /**
     * Right-click a person.
     *
     * <p>The first thing in this mod that lets the player ADDRESS anybody. Until now {@code
     * mobInteract} was unimplemented on every entity we own, so forty walking people could not be
     * spoken to at all — the largest hole on the road, and the cheapest to close.
     *
     * <p>What happens depends on whether they hold a trade, because that is the honest answer to
     * "what is this person to me":
     *
     * <ul>
     *   <li><b>A trade</b> — vanilla's own trade screen, titled with their name. {@code Merchant}
     *       is a standalone interface, verified from the jar: twelve methods and a default {@code
     *       openTradingScreen}. So this is the real window every player already knows rather than
     *       an imitation of it.</li>
     *   <li><b>No trade</b> — a line about who they are and how they are. An empty trade window
     *       reads as a broken mod; somebody telling you they have no work reads as a person.</li>
     *   <li><b>A builder</b> — nobody's merchant. He says what he is doing.</li>
     * </ul>
     *
     * <p>Everything said comes from the PERSON record and not from this body. The body is a puppet
     * lent while the player is near, so asking it about itself would give a different answer after
     * a walk out of range and back.
     */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return net.minecraft.world.InteractionResult.sidedSuccess(true);
        }
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        if (role == Role.BUILDER) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[" + givenName() + "] " + (isReading()
                    ? "Reading the plans. Do not stand on them."
                    : "There is work to do.")));
            return net.minecraft.world.InteractionResult.CONSUME;
        }

        org.lowern1ght.burg.people.Person person = person();
        if (person == null) {
            // A body whose record has gone. A window would open on nothing, and Embodiment takes
            // the body back on its next pass anyway.
            return net.minecraft.world.InteractionResult.PASS;
        }

        if (person.trade() != null && Trading.deals(person.trade(), person.skill())) {
            getOffers();
            openTradingScreen(player,
                net.minecraft.network.chat.Component.literal(CitizenNames.of(person.id())), 1);
            return net.minecraft.world.InteractionResult.CONSUME;
        }

        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "[" + CitizenNames.of(person.id()) + "] " + describe(person)));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    /** The record this body stands for, or null. Server-side; the record lives on the town. */
    public org.lowern1ght.burg.people.Person person() {
        if (level().isClientSide || townAnchorPos == null) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        Town town = LevelTowns.get(sl).getTownAt(townAnchorPos).orElse(null);
        java.util.UUID pid = getPersonId().orElse(null);
        return (town == null || pid == null) ? null : town.people().get(pid);
    }

    /**
     * How somebody without work describes themselves.
     *
     * <p>In their own voice and about their own state — hunger, mood, what they own. A status
     * readout would say the same things and mean less, and these are exactly the numbers the
     * player can do something about.
     */
    private static String describe(org.lowern1ght.burg.people.Person p) {
        if (p.isChild()) return "Still too young to work.";
        StringBuilder sb = new StringBuilder("No trade to my name.");
        if (p.hungryDays() > 0) {
            sb.append(" I have not eaten for ").append(p.hungryDays())
              .append(p.hungryDays() == 1 ? " day." : " days.");
        }
        if (p.discontent() >= 60) sb.append(" I am thinking of leaving.");
        else if (p.discontent() >= 30) sb.append(" Things could be better here.");
        sb.append(" (").append(p.wealth().name().toLowerCase(java.util.Locale.ROOT)).append(")");
        return sb.toString();
    }

    // --- Merchant --------------------------------------------------------------------------
    //
    // Implemented directly rather than by extending AbstractVillager, and that is the point:
    // Merchant is a standalone interface, so our own humanoid gets vanilla's real trade screen
    // without inheriting a villager's brain, model, sounds or breeding.

    @Override
    public void setTradingPlayer(net.minecraft.world.entity.player.Player player) {
        this.tradingWith = player;
    }

    @Override
    public net.minecraft.world.entity.player.Player getTradingPlayer() {
        return tradingWith;
    }

    @Override
    public net.minecraft.world.item.trading.MerchantOffers getOffers() {
        if (offers == null) {
            org.lowern1ght.burg.people.Person p = person();
            offers = Trading.offersFor(p == null ? null : p.trade(), p == null ? 0 : p.skill());
        }
        return offers;
    }

    @Override
    public void overrideOffers(net.minecraft.world.item.trading.MerchantOffers newOffers) {
        this.offers = newOffers;
    }

    @Override
    public void notifyTrade(net.minecraft.world.item.trading.MerchantOffer offer) {
        offer.increaseUses();
        // The coin goes to the PERSON, not into the town treasury, and that is the mechanic:
        // wealth is earned by an individual and shows on their clothes. A till would make the
        // whole visible-wealth axis dead on arrival.
        org.lowern1ght.burg.people.Person p = person();
        if (p != null && level() instanceof ServerLevel sl) {
            p.earn(offer.getCostA().getCount());
            setWealthTier(p.wealth().tier());
            LevelTowns.get(sl).markDirty();
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.NOTE_BLOCK_BELL.value();
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (townAnchorPos != null) tag.put("TownAnchorPos", Constants.writeBlockPos(townAnchorPos));
        tag.putString("Role", role.name());
        // Persisted as well as synced: a body that saves to disk must come back attached to the
        // same person, or a reload silently reassigns every face in the town.
        getPersonId().ifPresent(id -> tag.putUUID("PersonId", id));
        tag.putInt("WealthTier", getWealthTier());
        if (jobSite != null) tag.put("JobSite", Constants.writeBlockPos(jobSite));
        tag.putInt("Skill", skill);
    }

    /** What this person is for. Never null. */
    public Role getRole() { return role; }

    /**
     * The record this body represents, or empty for a body that is nobody — which a builder is.
     *
     * <p>Readable on both sides; the client uses it for the entire look.
     */
    public java.util.Optional<java.util.UUID> getPersonId() { return entityData.get(DATA_PERSON); }

    public void setPersonId(java.util.UUID personId) {
        entityData.set(DATA_PERSON, java.util.Optional.ofNullable(personId));
    }

    /** What this body is doing. Server-decided; the client only draws it. */
    public NpcPose getNpcPose() { return NpcPose.byIndex(entityData.get(DATA_POSE)); }

    public void setNpcPose(NpcPose pose) {
        if (getNpcPose() != pose) entityData.set(DATA_POSE, pose.index());
    }

    /** How this body's clothes should read. Pushed from the record, never rolled here. */
    public int getWealthTier() { return entityData.get(DATA_WEALTH); }

    public void setWealthTier(int tier) { entityData.set(DATA_WEALTH, Math.max(0, tier)); }

    /** The building this person works at, or null for the idle. */
    public BlockPos getJobSite() { return jobSite; }
    public void setJobSite(BlockPos pos) { this.jobSite = pos; }

    /**
     * How good they are at their trade, 0 up to the job config's cap.
     *
     * <p>Ours, not vanilla's villager experience. That was the obvious place to put it and it is
     * the wrong one: villager XP is spent by vanilla to decide trade unlocks and is stripped by
     * `LoseJobOnSiteLoss`, so a mechanic built on it would be fighting rules we do not own.
     */
    public int getSkill() { return skill; }
    public void setSkill(int skill) { this.skill = skill; }

    /**
     * Set before the entity enters the world, and not changed afterwards.
     *
     * <p>A role switch mid-life would leave a builder's queue claims held by somebody who is no
     * longer looking at the queue, so the entry would never be built and never be released.
     */
    public void setRole(Role role) { this.role = role; }

    /**
     * A child, born to two settlers.
     *
     * <p>Required by {@code AgeableMob}, which is why this entity extends it: children that grow
     * up need vanilla's age ticking, its {@code isBaby} and the model scaling that comes with
     * them. Returns a SETTLER with no town yet — the caller enrols it, because a baby that
     * enrols itself would be counted before anyone decided there was room for it.
     */
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        Npc child = org.lowern1ght.burg.registry.EntityRegistry.NPC.create(level);
        if (child != null) {
            child.setRole(Role.SETTLER);
            child.setBaby(true);
        }
        return child;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TownAnchorPos")) townAnchorPos = Constants.readBlockPos(tag, "TownAnchorPos");
        // Absent means BUILDER: every Npc saved before the role existed was one.
        if (tag.hasUUID("PersonId")) setPersonId(tag.getUUID("PersonId"));
        setWealthTier(tag.getInt("WealthTier"));
        if (tag.contains("JobSite")) jobSite = Constants.readBlockPos(tag, "JobSite");
        skill = tag.getInt("Skill");
        if (tag.contains("Role")) {
            try {
                role = Role.valueOf(tag.getString("Role"));
            } catch (IllegalArgumentException ignored) {
                role = Role.BUILDER;
            }
        }
    }

    public void setTownAnchorPos(BlockPos pos) { this.townAnchorPos = pos; }
    public BlockPos getTownAnchorPos() { return townAnchorPos; }

    // Called by BuildGoal when construction is complete.
    // rotation and entryConnectorWorldPos are precomputed by SimpleStateMachine/BuildGoal.
    public void onBuildComplete(BlockPos builtAt, String buildingId, ConnectionPoint usedConnection,
                                Rotation rotation, BlockPos entryConnectorWorldPos) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        LevelTowns.get(serverLevel).getAllTowns().stream()
            .filter(t -> t.getBuilderNpcIds().contains(getUUID()))
            .findFirst()
            .ifPresent(town -> {
                List<ConnectionPoint> connections = BuildSchematic.readJigsawPoints(
                    serverLevel, builtAt, buildingId, rotation, entryConnectorWorldPos);
                BoundingBox bb = BuildingDataHandler.get(buildingId)
                    .flatMap(def -> def.terrainMatching
                        ? BuildSchematic.computeFootprintBoundingBox(serverLevel, builtAt, def.nbt, rotation)
                        : BuildSchematic.computeBoundingBox(serverLevel, builtAt, def.nbt, rotation))
                    .orElseGet(() -> new BoundingBox(
                        builtAt.getX(), builtAt.getY(), builtAt.getZ(),
                        builtAt.getX(), builtAt.getY(), builtAt.getZ()
                    ));
                town.registerBuilding(builtAt, buildingId, connections, bb, rotation);
                LevelTowns.get(serverLevel).markDirty();
            });
    }

    /**
     * Silent, and deliberately so.
     *
     * <p>These three returned {@code VILLAGER_AMBIENT}, {@code VILLAGER_HURT} and {@code
     * VILLAGER_DEATH} — the "hurrr" every player in the world has learned to read as
     * <em>villager</em>. The whole point of this entity is that its people are not villagers:
     * the rig was rebuilt off the player skeleton, the faces were redrawn away from the
     * monobrow, and the borrowed crossed-arms pose was cut for exactly the same reason. Leaving
     * the noise in undoes all of that the moment one of them is nudged, because the ear is
     * quicker than the eye and far harder to argue with.
     *
     * <p>{@code null} is a supported answer throughout {@code Mob} — vanilla's own silent mobs
     * return it — so nothing has to be registered to say nothing. Human voices are their own
     * job: a settlement's people should mutter, greet and grumble, and until those exist silence
     * is the honest placeholder. A villager's voice is not.
     */
    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return null; }

    @Override
    protected SoundEvent getDeathSound() { return null; }

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

    public static AttributeSupplier.Builder createAttributes() {
        return AgeableMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.5)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // --- Hand and animation helpers called by BuildGoal ---

    // Starts the "reading plan" animation for the given number of ticks.
    // Puts the town scroll in the main hand; tick() clears it when the timer expires.
    public void startReading(int durationTicks) {
        setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.MAP));
        readingTicksRemaining = durationTicks;
        entityData.set(DATA_IS_READING, true);
        playSound(SoundEvents.BOOK_PAGE_TURN, 0.6f, 0.9f + getRandom().nextFloat() * 0.2f);
    }

    public void holdInMainHand(ItemStack stack) { setItemInHand(InteractionHand.MAIN_HAND, stack); }

    public void freeHands() {
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    // Called by BuildGoal each time a block is placed; increments the synced generation counter
    // so NpcModel.setupAnim() detects the change and triggers the swing animation on the client.
    public void notifyBlockPlaced() {
        entityData.set(DATA_BUILD_GENERATION, entityData.get(DATA_BUILD_GENERATION) + 1);
    }

    @Override public boolean isCrossingArms() { return false; }
    /**
     * This builder's name.
     *
     * <p>A builder is as much a person as a resident — naming only the citizens left the one
     * NPC the player watches all day anonymous, which reads as an oversight rather than a
     * distinction. Derived from the UUID, so nothing is saved and nothing can drift.
     *
     * <p>Not {@code setCustomName}, which is what this used to do. A custom name IS the name
     * tag: storing identity in it meant a player with one anvil could overwrite who somebody
     * was, permanently. The renderer draws this instead, and a tag laid over it hides it
     * without destroying it.
     */
    public String givenName() {
        return CitizenNames.of(getUUID());
    }

    @Override public boolean isReading() { return entityData.get(DATA_IS_READING); }
    @Override public int getBuildGeneration() { return entityData.get(DATA_BUILD_GENERATION); }

    // The builder's own outfit. Held on the entity rather than in the render layer so a
    // single layer serves every town NPC; the layer used to name one texture as a constant.
    private static final ResourceLocation BUILDER_CLOTHES =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/npc/builder_clothes.png");

    @Override public ResourceLocation clothesTexture() { return BUILDER_CLOTHES; }

    @Override public int getClientLastBuildGeneration() { return clientLastBuildGeneration; }
    @Override public void setClientLastBuildGeneration(int value) { clientLastBuildGeneration = value; }
    @Override public float getClientBuildPlacedAtAge() { return clientBuildPlacedAtAge; }
    @Override public void setClientBuildPlacedAtAge(float value) { clientBuildPlacedAtAge = value; }
}

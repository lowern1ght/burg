package org.lowern1ght.burg.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

import java.util.Map;

/**
 * A town resident: a vanilla {@link Villager} that belongs to a town.
 *
 * <p><b>Why a Villager subclass and not another {@code PathfinderMob}.</b> Everything the
 * mod wants a citizen to do, vanilla already does and the mod does not: wake at dawn, walk
 * to a workstation it claimed through the POI system, work it, harvest and REPLANT a crop,
 * carry the produce, hand food to a neighbour who has none, go to a bed it owns at dusk,
 * panic when attacked, breed when fed and housed. That is the whole of "жители работают,
 * добывают ресурсы, развиваются сами", and it arrives by extending the class rather than
 * by being written. Inheriting also means a citizen IS a villager to every other system —
 * iron golems defend it, zombies convert it, raids target it.
 *
 * <p>Deliberately no brain overrides in this first cut. The vanilla schedule is the
 * feature; anything added here should be a new {@code Activity} beside it, not a
 * replacement for it.
 *
 * <p><b>What this class adds.</b> Town membership, so a citizen can be counted, fed,
 * conscripted and mourned; and the {@link TownNpc} contract, so it renders on the mod's own
 * villager-shaped rig — which unlike vanilla's {@code VillagerModel} carries arm poses and
 * an armour layer. That is what lets a citizen hold the tool of its trade today and lets a
 * conscript wear iron later without a second model.
 *
 * <p>Population today is an integer summed off placed buildings
 * ({@code Town.getTotalResidents}), and production is a timer that adds items to an
 * abstract store. This class is the first half of replacing both: a resident that exists.
 * Nothing is wired to the town's economy yet, on purpose — one live citizen that behaves
 * correctly is worth more than a population that half-behaves.
 */
public class Citizen extends Villager implements TownNpc {

    /**
     * Clothing per profession. Vanilla draws its own profession overlay on its own model;
     * ours is a separate texture on the shared rig, so the cast stays one rig deep.
     * A profession with no entry here simply goes unclothed over its skin.
     */
    private static final Map<String, String> CLOTHES = Map.of(
        "farmer", "farmer_clothes",
        "mason", "mason_clothes",
        "weaponsmith", "smith_clothes",
        "toolsmith", "smith_clothes",
        "armorer", "smith_clothes",
        "fletcher", "forester_clothes"
    );

    /**
     * How many base skins exist. Vanilla varies a villager by its biome TYPE — seven skins
     * under one profession overlay — and ours had exactly one, so every citizen was the same
     * person in different clothes. Synced rather than derived on the client so a chief or a
     * scripted NPC can be given a chosen face later instead of whatever its UUID hashes to.
     */
    public static final int SKIN_VARIANTS = 6;

    // -1, not 0, for "no face chosen yet". Using 0 as the sentinel meant the first skin
    // could never be pinned: a chief deliberately given face 0 would have it overwritten by
    // whatever its UUID hashed to on the next load.
    private static final int UNASSIGNED = -1;

    private static final EntityDataAccessor<Integer> DATA_SKIN =
        SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.INT);

    /** Anchor of the town this citizen belongs to; saved so it can self-validate on load. */
    private BlockPos townAnchorPos = null;
    private boolean anchorValidated = false;
    private boolean identified = false;

    // Client-side animation cursor for the shared rig. Never synced, never saved.
    private int clientLastBuildGeneration = -1;
    private float clientBuildPlacedAtAge = -1000f;

    public Citizen(EntityType<? extends Citizen> type, Level level) {
        super(type, level);
        // A resident is a resident of somewhere. Without this Minecraft removes them when
        // no player is near, which for a mob the town keeps a UUID for is a silent leak.
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, UNASSIGNED);
    }

    /**
     * Name and face, both derived from the UUID, assigned on the first server tick.
     *
     * <p>Not in the constructor: the entity has no UUID there — Minecraft assigns it while
     * adding the entity to the level — so a name taken in the constructor is a name taken
     * from a zero id, and every citizen in the world would be called the same thing.
     *
     * <p>Not in a spawn hook either. {@code onAddedToWorld} is a loader extension the common
     * module cannot see, and {@code finalizeSpawn} only fires for spawners and spawn eggs, not
     * for {@code addFreshEntity}. The first tick is the one place that is both portable and
     * guaranteed to have run.
     */
    private void identify() {
        // The name is NOT set here any more, and nothing in the mod calls setCustomName. A
        // custom name is the name TAG — a thing the player owns — so storing identity in it
        // meant one anvil could overwrite who somebody was, permanently and invisibly. The
        // name now lives under the tag: see Citizens.nameOf and the renderer that draws it.
        if (entityData.get(DATA_SKIN) == UNASSIGNED) {
            entityData.set(DATA_SKIN, CitizenNames.skinVariant(getUUID(), SKIN_VARIANTS));
        }
    }

    public int getSkinVariant() {
        int v = entityData.get(DATA_SKIN);
        // The client can see this before the server's first tick has chosen a face.
        return v == UNASSIGNED ? 0 : Math.floorMod(v, SKIN_VARIANTS);
    }

    public void setSkinVariant(int variant) {
        entityData.set(DATA_SKIN, Math.floorMod(variant, SKIN_VARIANTS));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !identified) {
            identified = true;
            identify();
        }
        // Same self-validation the builder does: a citizen whose town is gone is a stray
        // mob with a saved anchor, and it will otherwise wander a dead plot forever.
        if (!level().isClientSide && !anchorValidated && townAnchorPos != null
                && level() instanceof ServerLevel sl) {
            anchorValidated = true;
            Town town = LevelTowns.get(sl).getTownAt(townAnchorPos).orElse(null);
            if (town == null) discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (townAnchorPos != null) tag.put("TownAnchorPos", Constants.writeBlockPos(townAnchorPos));
        tag.putInt("SkinVariant", entityData.get(DATA_SKIN));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TownAnchorPos")) {
            townAnchorPos = Constants.readBlockPos(tag, "TownAnchorPos");
        }
        if (tag.contains("SkinVariant")) entityData.set(DATA_SKIN, tag.getInt("SkinVariant"));
    }

    public void setTownAnchorPos(BlockPos pos) { this.townAnchorPos = pos; }
    public BlockPos getTownAnchorPos() { return townAnchorPos; }

    /**
     * A citizen's child is a citizen, of the same town.
     *
     * <p>Vanilla's {@code Villager.getBreedOffspring} hands back a plain
     * {@code EntityType.VILLAGER}, so left alone a town that bred would slowly fill with
     * ordinary villagers that belong to nobody, are counted by nothing, and cannot be
     * conscripted or mourned. The population would dilute itself the longer the town
     * prospered, which is the opposite of the intended reading.
     *
     * <p>Race is deliberately inherited rather than rolled: a child is of its parents' people.
     * That is what makes the biome-races meaningful when they arrive — a desert people in a
     * plains town stays a desert people, and mixing has to be a decision somewhere rather
     * than a side effect of breeding.
     */
    @Override
    public Villager getBreedOffspring(ServerLevel level, net.minecraft.world.entity.AgeableMob mate) {
        Citizen child = org.lowern1ght.burg.registry.EntityRegistry.CITIZEN.create(level);
        if (child == null) return super.getBreedOffspring(level, mate);
        child.finalizeSpawn(level, level.getCurrentDifficultyAt(child.blockPosition()),
            net.minecraft.world.entity.MobSpawnType.BREEDING, null);
        child.setTownAnchorPos(townAnchorPos);
        // The parents' type, not the biome's: the child is of their people.
        child.setVillagerData(child.getVillagerData().setType(getVillagerData().getType()));
        return child;
    }

    /**
     * Give this citizen a trade. Vanilla takes it from here: the profession decides which
     * workstation block it will look for through the POI system, and the schedule decides
     * when it walks there. No behaviour is written for the job itself.
     */
    public void setProfession(VillagerProfession profession) {
        setVillagerData(getVillagerData().setProfession(profession));
    }

    // --- TownNpc: the shared rig ---

    /** Vanilla's own tell for an unemployed villager, and true for the same reason. */
    @Override
    public boolean isCrossingArms() {
        return getVillagerData().getProfession() == VillagerProfession.NONE;
    }

    @Override
    public boolean isReading() { return false; }

    /** A citizen does not build, so nothing ever bumps this and the swing never fires. */
    @Override
    public int getBuildGeneration() { return 0; }

    @Override
    public ResourceLocation clothesTexture() {
        String key = CLOTHES.get(professionName());
        return key == null ? null
            : ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID,
                "textures/entity/npc/" + key + ".png");
    }

    private String professionName() {
        // The registry name, not toString(): a profession's own name field is what vanilla
        // keys its textures and POI lookups off.
        return getVillagerData().getProfession().name();
    }

    @Override public int getClientLastBuildGeneration() { return clientLastBuildGeneration; }
    @Override public void setClientLastBuildGeneration(int value) { clientLastBuildGeneration = value; }
    @Override public float getClientBuildPlacedAtAge() { return clientBuildPlacedAtAge; }
    @Override public void setClientBuildPlacedAtAge(float value) { clientBuildPlacedAtAge = value; }
}

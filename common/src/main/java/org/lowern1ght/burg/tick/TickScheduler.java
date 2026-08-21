package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.lowern1ght.burg.behavior.BehaviorEngine;
import org.lowern1ght.burg.behavior.war.RaidManager;
import org.lowern1ght.burg.network.NetworkHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.ActiveBuildState;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.datapack.QuestDataHandler;
import org.lowern1ght.burg.town.Quest;
import org.lowern1ght.burg.town.QuestDef;
import org.lowern1ght.burg.town.QuestManager;
import org.lowern1ght.burg.town.TownInventory;
import org.lowern1ght.burg.town.Town;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TickScheduler {

    // Set true after BehaviorEngine.register(...) has been called with a real Town.
    // The first level to provide a Town wins the slot; subsequent levels don't overwrite it.
    // Multi-world setups (rare; not currently supported) would need per-world state here.
    private static boolean engineWired = false;

    // Called from OuatForge via TickEvent.ServerTickEvent (Phase.END)
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelTowns levelTowns = LevelTowns.get(level);
            long gameTime = level.getGameTime();

            for (Map.Entry<Long, Town> townEntry : levelTowns.getAllTownEntries()) {
                Town town = townEntry.getValue();
                long anchorKey = townEntry.getKey();

                // One-time wiring of the behavior engine's BuildExecutor seam. The first
                // Town to appear wins; a second level's town does not overwrite the first.
                // BuildExecutor is registered with a concrete Town because Town implements
                // the interface directly.
                if (!engineWired) {
                    BehaviorEngine.register(town, BehaviorEngine.getNpcSupplier());
                    engineWired = true;
                    org.slf4j.LoggerFactory.getLogger(TickScheduler.class)
                        .info("[OUAT-BEHAVIOR] BuildExecutor wired to first town (anchor={})",
                            BlockPos.of(anchorKey));
                }

                ProductionManager.tick(town, level, gameTime, anchorKey);
                FoodManager.tick(town, level, gameTime, anchorKey);
                Settlers.tick(town, level, gameTime, BlockPos.of(anchorKey));
                Embodiment.tick(level, town, BlockPos.of(anchorKey), gameTime);
                Homes.tick(level, town, gameTime);
                tickQuests(town, level, gameTime, anchorKey);
                // Raid-cadence wire-up: gate via RaidManager.tick(previousFire, gameTime)
                // and stamp town.setLastRaidFireTick(gameTime) on fire so the next call
                // reads the fresh anchor. See tickRaids — extracted as a static helper so
                // :neoforge:test can exercise the wire-up without a MinecraftServer.
                if (tickRaids(town, gameTime)) {
                    LevelTowns.get(level).markDirty();
                }
                EraManager.tick(town, level, gameTime, anchorKey);
            }

            // Behavior engine fires after the per-town updates so the existing pipeline has
            // finished its bookkeeping (NPC spawning, queue resync) before the engine reads
            // the live state. Opt-in by intent enqueueing: with no intents, the engine is a
            // no-op.
            BehaviorEngine.INSTANCE.onServerTick(level, gameTime);

            // Spawn builders for each slot up to targetBuilderCount, once a player is nearby.
            if (gameTime % 20 == 0) {
                for (Map.Entry<Long, Town> entry : levelTowns.getAllTownEntries()) {
                    Town town = entry.getValue();
                    BlockPos anchorPos = BlockPos.of(entry.getKey());

                    boolean playerNearby = level.players().stream().anyMatch(p ->
                        p.distanceToSqr(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5) < 128.0 * 128.0);
                    if (!playerNearby) continue;

                    boolean dirty = false;
                    List<UUID> ids = town.getBuilderNpcIds();
                    for (int slot = 0; slot < town.getTargetBuilderCount(); slot++) {
                        UUID slotId = slot < ids.size() ? ids.get(slot) : null;
                        net.minecraft.world.entity.Entity existing = slotId != null ? level.getEntity(slotId) : null;
                        if (existing != null) continue;

                        // NPC not found in loaded entities. Check if the NPC's chunk is simply unloaded
                        // before spawning a replacement -- the builder is immortal so absence = chunk not loaded.
                        ActiveBuildState buildState = town.getActiveBuild(slot);
                        BlockPos checkPos = buildState != null ? buildState.placementPos() : anchorPos;
                        if (!areChunksLoaded(level, checkPos)) continue;

                        // All 9 chunks around the expected position are loaded but NPC is still missing:
                        // coherence issue (e.g. entity deleted externally). Spawn a replacement.
                        // Release any queue claims the dead builder held so the new one can resume.
                        if (slotId != null) town.releaseAllClaimsForBuilder(slotId);
                        Npc builder = EntityRegistry.NPC.create(level);
                        if (builder == null) continue;

                        builder.setPersistenceRequired();
                        builder.setTownAnchorPos(anchorPos);

                        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            anchorPos.getX(), anchorPos.getZ());
                        builder.moveTo(anchorPos.getX() + 0.5, surfaceY + 1.0, anchorPos.getZ() + 0.5);

                        if (level.addFreshEntity(builder)) {
                            town.setBuilderNpcIdAtSlot(slot, builder.getUUID());
                            dirty = true;
                        }
                    }
                    if (dirty) levelTowns.markDirty();
                }
            }
        }
    }

    // Returns true if all 9 chunks in the 3x3 grid around the given position are loaded.
    // Used to distinguish "NPC in unloaded chunk" from "NPC genuinely missing".
    private static boolean areChunksLoaded(ServerLevel level, BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.isLoaded(new BlockPos(
                        (center.x + dx) * 16, pos.getY(), (center.z + dz) * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void tickQuests(Town town, ServerLevel level, long gameTime, long anchorKey) {
        BlockPos anchorPos = BlockPos.of(anchorKey);
        boolean changed = false;
        TownInventory inventory = town.getTownInventory();
        Map<String, Long> lastCompleted = town.getQuestDefLastCompleted();

        for (QuestDef def : QuestDataHandler.getAll()) {
            if (QuestManager.isAlreadyActive(def, town.getActiveQuests())) continue;

            if ("TASK".equals(def.type())) {
                long lastTime = lastCompleted.getOrDefault(def.id(), 0L);
                if (gameTime - lastTime < def.refreshIntervalTicks()) continue;
            }

            if (!prerequisitesMet(def.prerequisites(), town, inventory)) continue;

            Quest q = QuestManager.buildFromDef(def);
            town.addQuest(q);
            changed = true;
        }

        if (changed) {
            LevelTowns.get(level).markDirty();
            NetworkHelper.pushQuestUpdateToWatchers(level, town, anchorPos);
        }
    }

    /**
     * Cooldown-gated raid tick. Returns true iff a raid may fire at {@code gameTime},
     * and stamps {@link Town#setLastRaidFireTick(long)} to {@code gameTime} so the
     * next call's previousFire is the firing gameTime.
     *
     * <p>Extracted as a package-private static helper so {@code :neoforge:test} can
     * exercise the wire-up without spinning up a {@code MinecraftServer}. The
     * caller ({@link #tick(MinecraftServer)}) wraps the boolean with a
     * {@link LevelTowns#markDirty()} so the fire-tick stamp persists on the next
     * chunk save.
     *
     * <p>The cooldown gate lives on {@link RaidManager#tick(long, long)} and reads
     * {@link org.lowern1ght.burg.people.RaidConfig#current()} for the cooldown —
     * the live reader {@link org.lowern1ght.burg.infrastructure.config.BurgConfig#refreshRaidConfig()}
     * pushes into on mod-bus init and on every config reload. The Cloth
     * {@code raidCooldownSeconds} knob therefore reaches the gate with no further
     * wiring: a config-screen edit takes effect on the very next raid-cadence
     * decision, no world reload.
     *
     * <p>{@code previousFire == 0L} is the additive default for a town whose first
     * raid has not yet fired; the gate sees {@code gameTime >= cooldownTicks()} from
     * that starting point, so the first raid fires at the cooldown boundary rather
     * than at {@code gameTime=0}. The cooldown counts from town registration, not
     * from world load — worlds saved before this carve load with the additive
     * default and the first post-load fire is the same boundary from world load,
     * not from when the town would have last fired (the cooldown has been earned,
     * not reset).
     */
    static boolean tickRaids(Town town, long gameTime) {
        long previousFire = town.getLastRaidFireTick();
        if (RaidManager.tick(previousFire, gameTime)) {
            town.setLastRaidFireTick(gameTime);
            return true;
        }
        return false;
    }

    private static boolean prerequisitesMet(QuestDef.Prerequisites prereqs, Town town, TownInventory inventory) {
        if (prereqs == null) return true;
        int era = town.getCurrentEra();
        if (era < prereqs.minEra() || era > prereqs.maxEra()) return false;
        int residents = town.getActiveResidents();
        if (residents < prereqs.minResidents() || residents > prereqs.maxResidents()) return false;
        if (!prereqs.requiredOrientations().isEmpty()) {
            String orientation = town.getOrDeriveOrientation();
            if (prereqs.requiredOrientations().stream().noneMatch(o -> o.equals(orientation))) return false;
        }
        for (String defId : prereqs.requiredBuildings()) {
            boolean found = town.getBuildings().stream().anyMatch(b -> b.getDefId().equals(defId));
            if (!found) return false;
        }
        for (QuestDef.StockCondition sc : prereqs.stockConditions()) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(sc.item()));
            int stock = inventory.getStock(item);
            if (stock < sc.min() || stock > sc.max()) return false;
        }
        return true;
    }
}

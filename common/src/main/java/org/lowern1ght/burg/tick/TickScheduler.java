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
                // Structural-flags SoT wire-up seams — both helpers are now no-op
                // stubs (the synthetic first-increment writes that previously flipped
                // {@link Town#structuralFlags()} from NONE to non-NONE on every
                // tick have been removed; see their javadoc for the rationale).
                // The call sites stay so the seam the (future) production zoning
                // layer / road planner wire into is already in place — the next
                // carve just replaces the helper bodies with planner-driven output.
                if (tickZoning(town, gameTime)) {
                    LevelTowns.get(level).markDirty();
                }
                if (tickRoadPlans(town, gameTime)) {
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

    /**
     * ADR-0029 — pure-logic quest tick. Iterates {@link QuestDataHandler},
     * checks presence via the defId-keyed {@link Town#findQuestDef} port
     * (through {@link QuestManager#isAlreadyActive}), respects the TASK
     * refresh-interval cooldown, and verifies {@link QuestDef#prerequisites()}
     * before spawning a fresh {@link Quest}. Returns true iff at least one
     * quest was spawned on this call.
     *
     * <p>Extracted as a package-private static helper so {@code :neoforge:test}
     * can drive the wire-up without spinning up a {@code MinecraftServer}.
     * The MC-typed orchestrator below calls this helper and only handles the
     * side effects (dirty mark + watcher push) when this helper reports a
     * change.
     */
    static boolean tickQuests(Town town, long gameTime) {
        boolean changed = false;
        TownInventory inventory = town.getTownInventory();
        Map<String, Long> lastCompleted = town.getQuestDefLastCompleted();

        for (QuestDef def : QuestDataHandler.getAll()) {
            if (QuestManager.isAlreadyActive(town, def.id())) continue;

            if ("TASK".equals(def.type())) {
                long lastTime = lastCompleted.getOrDefault(def.id(), 0L);
                if (gameTime - lastTime < def.refreshIntervalTicks()) continue;
            }

            if (!prerequisitesMet(def.prerequisites(), town, inventory)) continue;

            Quest q = QuestManager.buildFromDef(def);
            town.addQuest(q);
            changed = true;
        }

        return changed;
    }

    private static void tickQuests(Town town, ServerLevel level, long gameTime, long anchorKey) {
        BlockPos anchorPos = BlockPos.of(anchorKey);
        if (tickQuests(town, gameTime)) {
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

    /**
     * Structural-flags wire-up — no-op stub for the zoning seam.
     *
     * <p>The earlier PR #56 implementation wrote the first zoning increment
     * (a synthetic {@code addZoning(CORE, 1)}) the moment the tick path
     * ran, so {@link Town#structuralFlags()} flipped from {@code NONE} to
     * non-{@code NONE} for every town that ticked — even ones whose
     * (future) production zoning layer had no real work to do. That
     * synthetic write made the hub-mode gate's structural triple fire
     * spuriously on every save. The helper now returns {@code false} on
     * every call and never mutates the SoT; the structural gate stays on
     * the {@link org.lowern1ght.burg.domain.settlement.StructuralFlags#NONE}
     * floor until the (future) production zoning layer calls
     * {@link Town#addZoning(org.lowern1ght.burg.town.Town.Zone, int)} on
     * the planning path.
     *
     * <p>The method signature is preserved (package-private, static,
     * {@code boolean}, {@code (Town, long)}) so the seam the planner / future
     * zoning layer wire into is already in place — the next carve just has
     * to replace the body with real output. The cheap {@code :common:test}
     * signature pin ({@link TickSchedulerStructuralWireTest}) still pins the
     * helper's shape; the no-op behaviour is pinned by
     * {@code :neoforge:test}'s {@code TickSchedulerStructuralFlagsPostTickNoneTest}.
     *
     * <p><b>Real-planner status (carved by this PR, end-state doc).</b>
     * No zoning planner class exists in the codebase yet (a glob over
     * any path matching {@code *zoning&#47;*Planner*.java} returns no hits
     * as of PR #71). The only production code that <i>decides</i> a zone
     * today is {@link Town#zoneOf(net.minecraft.core.BlockPos)}, which is a
     * position lookup, not a planning step — it does not produce cells to
     * commit and therefore has no reason to call {@link Town#addZoning}.
     * The {@code addZoning} seam therefore sits open by design: the helper's
     * caller path (line 76 of {@link #tick}) feeds every town per tick,
     * the production zoning layer (an act-4 / act-5 carve) will take it
     * over, and this helper then either disappears or becomes the
     * rate-limited dispatch wrapper the future seam describes.
     *
     * <p>TODO(act5): real zoning layer (see <code>docs/01-vision/VISION.md</code>
     * §"the hub is a window") writes {@code Town.addZoning(zone, cells)}
     * from its planning path; this helper then either disappears or becomes
     * the rate-limited dispatch wrapper the future seam describes.
     */
    static boolean tickZoning(Town town, long gameTime) {
        // No-op: the synthetic write has been removed. The structural SoT
        // stays empty until the production zoning layer runs.
        return false;
    }

    /**
     * Structural-flags wire-up — no-op stub for the road-segment seam.
     *
     * <p>The earlier PR #56 implementation appended a synthetic one-cell
     * segment at {@link BlockPos#ZERO} on every town's first tick, so
     * {@link Town#structuralFlags()} flipped on the {@code road_laid} leg
     * for every town that ticked — even ones whose (future) production
     * road planner had no real work to do. That synthetic write made the
     * hub-mode gate's structural triple fire spuriously on every save. The
     * helper now returns {@code false} on every call and never mutates the
     * SoT; the structural gate stays on the
     * {@link org.lowern1ght.burg.domain.settlement.StructuralFlags#NONE}
     * floor until the (future) production road planner calls
     * {@link Town#addRoadSegment(org.lowern1ght.burg.behavior.road.RoadSegment)}
     * from the {@code RoadBuilder.planTasks} commit path.
     *
     * <p>The method signature is preserved (package-private, static,
     * {@code boolean}, {@code (Town, long)}) so the seam the road planner
     * wires into is already in place — the next carve just has to replace
     * the body with the planner's real output. The cheap {@code :common:test}
     * signature pin ({@link TickSchedulerStructuralWireTest}) still pins the
     * helper's shape; the no-op behaviour is pinned by
     * {@code :neoforge:test}'s {@code TickSchedulerStructuralFlagsPostTickNoneTest}.
     *
     * <p><b>Real-planner status (carved by this PR, end-state doc).</b>
     * The road planner <i>does</i> exist: <code>org.lowern1ght.burg.behavior.road.RoadBuilder</code>
     * (with the public method
     * {@code RoadBuilder.planTasks(ExpandIntent, Town, ServerLevel)}) computes
     * real A* (Dijkstra) routes through {@link org.lowern1ght.burg.behavior.road.RoadPlanner}
     * and returns them as {@link org.lowern1ght.burg.behavior.road.RoadSegment}
     * instances. <b>The seam is not wired yet</b>: no production caller
     * routes an {@link org.lowern1ght.burg.behavior.intent.ExpandIntent} into
     * {@code planTasks} (only {@link org.lowern1ght.burg.gametest.PathLayerGameTest}
     * does so today), and {@code planTasks} itself does not call
     * {@link Town#addRoadSegment(org.lowern1ght.burg.behavior.road.RoadSegment)}
     * — that single call at the end of the planner's commit path is the only
     * plumbing left to flip the {@code road_laid} leg via the real planner.
     * This carve leaves the helper as a no-op rather than wire that call
     * directly: there is no production driver feeding {@code planTasks}
     * today, so flipping the call would record synthetic {@link BlockPos#ZERO}
     * segments on every tick (the same mistake the PR #56 stub made).
     *
     * <p>TODO(act5): the production caller that owns the act-4 transition
     * (the strict reading of ruling 3 — supply steers what gets built —
     * owns the ExpandIntent ramp, see <code>docs/01-vision/VISION.md</code>
     * §"the hub is a window") fires an {@code ExpandIntent} per decided
     * route; that intent flows into
     * {@code RoadBuilder.planTasks(...).get(0)} and the resulting
     * {@code RoadSegment} lands via {@link Town#addRoadSegment(RoadSegment)}.
     * This helper then either disappears or becomes the rate-limited
     * dispatch wrapper the future seam describes.
     *
     * <p>Companion pin (no MinecraftServer required): {@code PlannerPopulationSeamTest}
     * in {@code :common:test} bundles the seam view — Town mutators + the
     * TickScheduler helpers + the {@code RoadBuilder} FQCN. That file is
     * the seam's <em>landing pad</em>: a next carve flips its assertions
     * when the production caller is in place.
     */
    static boolean tickRoadPlans(Town town, long gameTime) {
        // No-op: the synthetic write has been removed. The structural SoT
        // stays empty until the production road planner commits.
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

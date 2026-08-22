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
import org.lowern1ght.burg.behavior.road.RoadPlanSource;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TickScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TickScheduler.class);

    // Set true after BehaviorEngine.register(...) has been called with a real Town.
    // The first level to provide a Town wins the slot; subsequent levels don't overwrite it.
    // Multi-world setups (rare; not currently supported) would need per-world state here.
    private static boolean engineWired = false;

    // The road-planner source the {@link #tickRoadPlans(Town, long)} helper delegates
    // to. Default null = {@link RoadPlanSource#NONE} (empty list, no SoT mutation),
    // preserving the pre-carve no-op stub behaviour until the production caller
    // (the act-4 transition owner) wires its planning path in. See
    // {@link RoadPlanSource} for the contract.
    //
    // Volatile because production tick threads and bare-JVM / :neoforge:test set-up
    // can race; a stale read of the source pointer is recoverable on the next tick.
    private static volatile RoadPlanSource roadPlanSource = null;

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
     * Road-segment wire-up. Calls the installed {@link RoadPlanSource}
     * (if any), routes the returned {@link RoadSegment}s through
     * {@link Town#addRoadSegment(RoadSegment)}, and returns {@code true}
     * iff at least one segment landed on the SoT.
     *
     * <p><b>Wire path.</b> {@link TickScheduler#tick(MinecraftServer)} →
     * {@code tickRoadPlans(town, gameTime)} →
     * {@code roadPlanSource.planFor(town, gameTime)} →
     * {@link Town#addRoadSegment(RoadSegment)} (one per non-null returned
     * segment). The helper's boolean return is wrapped by the caller with
     * {@code LevelTowns.markDirty()} so the SoT append persists on the
     * next chunk save — same dirty-mark contract as {@link #tickRaids} and
     * {@link #tickQuests}.
     *
     * <p><b>Default (no source installed).</b> {@link #roadPlanSource} is
     * {@code null} by default, which the helper resolves to
     * {@link RoadPlanSource#NONE} (empty list, no SoT mutation, returns
     * {@code false}). This preserves the pre-carve no-op stub behaviour
     * — the structural gate stays on the
     * {@link org.lowern1ght.burg.domain.settlement.StructuralFlags#NONE}
     * floor until the production caller (the act-4 transition owner)
     * installs a source. No production caller exists yet (a glob over
     * any path matching {@code *road/*Plan*.java} other than this seam
     * still returns no hits as of this PR), so the seam's default is
     * load-bearing: it keeps the structural gate honest until the
     * production caller lands.
     *
     * <p><b>Production caller (future).</b> The act-4 transition owner
     * (the strict reading of ruling 3 — supply steers what gets built —
     * owns the {@code ExpandIntent} ramp, see
     * <code>docs/01-vision/VISION.md</code> §"the hub is a window") fires
     * an {@code ExpandIntent} per decided route. That intent flows into
     * {@code RoadBuilder.planTasks(...)}; the resulting {@link RoadTask}'s
     * {@link RoadTask#segment()} lands on the SoT via
     * {@link Town#addRoadSegment(RoadSegment)}. The production caller
     * wraps that flow in a {@link RoadPlanSource} and installs it via
     * {@link #setRoadPlanSource(RoadPlanSource)} during mod-bus init
     * (one-time, like {@link BehaviorEngine#register}).
     *
     * <p><b>Failure safety.</b> The {@code planFor} call is wrapped in
     * {@code try/catch (Throwable)} so a buggy or throwing source cannot
     * break the tick loop. The exception is logged via the
     * {@link #LOGGER}, the helper returns {@code false}, the caller's
     * {@code markDirty} branch is skipped, and the next tick retries with
     * whatever state the source is in by then. The structural SoT is the
     * most load-bearing invariant in the act-4 gate, so a partial / buggy
     * source degrades to no-op rather than tearing the gate open on a
     * stale write.
     *
     * <p><b>Pins.</b> The cheap {@code :common:test} signature pin
     * ({@link TickSchedulerStructuralWireTest}) still pins the helper's
     * shape. The bare-JVM wire pin
     * ({@link TickSchedulerRoadPlanWireTest}) exercises the source-driven
     * path with a fake source (one segment → SoT size 1; empty list →
     * {@code NONE} flag). The MC gametest
     * ({@code RoadPlanTickGameTest}) runs the wire on a real MC server
     * with a real {@code RoadBuilder} + {@code ExpandIntent}, end-to-end.
     */
    static boolean tickRoadPlans(Town town, long gameTime) {
        RoadPlanSource source = roadPlanSource;
        if (source == null) source = RoadPlanSource.NONE;
        try {
            List<RoadSegment> segments = source.planFor(town, gameTime);
            if (segments == null || segments.isEmpty()) {
                return false;
            }
            boolean changed = false;
            for (RoadSegment seg : segments) {
                // Town.addRoadSegment drops null silently at the edge (see its
                // javadoc), so the loop tolerates a null element without an
                // extra guard. The `changed` flag flips for any non-null
                // segment that lands — addRoadSegment's drop-on-null is what
                // determines whether `changed` reflects what actually landed.
                if (seg != null) {
                    town.addRoadSegment(seg);
                    changed = true;
                }
            }
            return changed;
        } catch (Throwable t) {
            // The structural SoT is the load-bearing invariant of the act-4
            // gate. A failing planner degrades to no-op (no SoT mutation, no
            // markDirty) rather than tearing the gate open or breaking the
            // tick loop. The next tick retries with whatever state the source
            // is in by then.
            LOGGER.error("[OUAT-TICK] tickRoadPlans failed for town; tick loop survives"
                + " (segments-in-flight were dropped)", t);
            return false;
        }
    }

    /**
     * Install (or reset, with {@code null}) the {@link RoadPlanSource} the
     * {@link #tickRoadPlans(Town, long)} helper delegates to. Package-private
     * on purpose: the production wire is one-time at mod-bus init (mirroring
     * the {@link BehaviorEngine#register} discipline); the bare-JVM and
     * {@code :neoforge:test} targets install fakes via this setter. A
     * {@code null} argument resets to the {@link RoadPlanSource#NONE} default
     * so a test that forgets to clean up does not leak a source into the
     * next test in the same JVM.
     *
     * <p>Volatile read in {@link #tickRoadPlans} makes the install visible
     * to the production tick thread without an explicit fence. The window
     * between "source set" and "next tick observes it" is acceptable: a
     * first tick that runs against the default {@link RoadPlanSource#NONE}
     * is a single missed no-op tick, not a correctness gap.
     */
    static void setRoadPlanSource(RoadPlanSource source) {
        roadPlanSource = source;
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

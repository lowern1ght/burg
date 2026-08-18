package org.lowern1ght.burg.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticRegistry;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticStatus;
import org.lowern1ght.burg.behavior.diplomacy.Relation;
import org.lowern1ght.burg.behavior.war.BattleContext;
import org.lowern1ght.burg.behavior.war.BattleState;
import org.lowern1ght.burg.behavior.war.BattleStateMachine;
import org.lowern1ght.burg.behavior.war.CasualtyModel;
import org.lowern1ght.burg.behavior.war.Squad;
import org.lowern1ght.burg.behavior.war.SquadGoal;
import org.lowern1ght.burg.integration.xaero.XaeroIntegration;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drives every active battle in one dimension.
 *
 * <p>The driver is the integration shim between {@link DiplomaticRegistry}
 * (which records which towns are at war) and the {@link BattleStateMachine}
 * (which decides what each squad does given its current state and the
 * world). On every server tick the driver:
 *
 * <ol>
 *   <li>Counts ticks ({@link #BATTLE_TICK_INTERVAL} = 20 ticks, i.e.
 *       once per second at 20 tps). Anything finer is wasted work for
 *       a state machine that reads squad average positions.</li>
 *   <li>Iterates {@link DiplomaticRegistry#allRelations()} and acts on
 *       every {@link DiplomaticStatus#AT_WAR} edge.</li>
 *   <li>Lazily spawns attack and defend squads for each war pair (the
 *       first slice ships stub squads with no members — real squad
 *       selection from the towns' role-assigned NPCs is a separate
 *       commit).</li>
 *   <li>Ticks the state machine for each squad and stores the result
 *       in {@link #squadStates}.</li>
 * </ol>
 *
 * <p><b>Persistence.</b> NONE. {@link #squadStates}, {@link #attackerSquads},
 * {@link #defenderSquads}, and {@link #casualties} are all in-memory
 * structures. Wars are short-lived (a few minutes of NPC-vs-NPC combat
 * at most), and the state machine is deterministic; a server reload
 * can re-run the last tick without surprising outcomes. This decision
 * is documented in {@link BattleStateMachine}'s class javadoc.
 *
 * <p><b>Targets.</b> The Town API has no {@code getAnchor()} — the
 * anchor position is tracked in {@link LevelTowns} keyed by long, not
 * on the Town itself. The driver looks up the anchor through
 * {@link LevelTowns#getAllTownEntries()} when a squad is first
 * spawned. If the town is not present in the level's registry (which
 * can happen in tests that construct a bare {@link Town} for a unit
 * test), the driver falls back to {@code new BlockPos(0, 64, 0)}.
 */
public final class BattleDriver {

    /** Tick the battle state machine once every N server ticks. At 20 tps, 20 = once per second. */
    private static final int BATTLE_TICK_INTERVAL = 20;

    /** Fallback target position when a Town has no anchor registered in the level. */
    private static final BlockPos FALLBACK_ANCHOR = new BlockPos(0, 64, 0);

    private final Map<UUID, BattleState> squadStates = new HashMap<>();
    private final Map<Town, Squad> attackerSquads = new HashMap<>();
    private final Map<Town, Squad> defenderSquads = new HashMap<>();
    private final CasualtyModel casualties = new CasualtyModel();
    private final BattleStateMachine sm = new BattleStateMachine();
    private int ticksSinceLastTick = 0;
    /**
     * Relations that were {@link DiplomaticStatus#AT_WAR} at the previous
     * {@code BATTLE_TICK_INTERVAL} tick. Diffed against the current set each
     * tick to detect new and ended wars. New wars fire
     * {@link XaeroIntegration#onWarStarted}; wars that are no longer
     * {@code AT_WAR} fire {@link XaeroIntegration#onWarEnded}.
     */
    private final Set<Relation> activeWars = new HashSet<>();

    public void onServerTick(ServerLevel level, long gameTick, DiplomaticRegistry registry) {
        if (level == null || registry == null) return;
        ticksSinceLastTick++;
        if (ticksSinceLastTick < BATTLE_TICK_INTERVAL) return;
        ticksSinceLastTick = 0;

        // 0) Diff the current AT_WAR set against the previous tick. The diff drives
        //    the soft-dep Xaero Minimap integration (and any other future lifecycle
        //    hook that wants to know "this war just started" / "this war just ended").
        //    We collect currentWars as a fresh Set so the diff is straightforward and
        //    no relation is double-counted.
        Set<Relation> currentWars = new HashSet<>();
        for (Relation rel : registry.allRelations()) {
            if (rel.status() == DiplomaticStatus.AT_WAR) currentWars.add(rel);
        }
        Set<Relation> newWars = new HashSet<>(currentWars);
        newWars.removeAll(activeWars);
        Set<Relation> endedWars = new HashSet<>(activeWars);
        endedWars.removeAll(currentWars);
        for (Relation w : newWars) {
            XaeroIntegration.onWarStarted(w.from(), w.to());
        }
        for (Relation w : endedWars) {
            XaeroIntegration.onWarEnded(w.from(), w.to());
        }
        activeWars.clear();
        activeWars.addAll(currentWars);

        for (Relation rel : currentWars) {
            Town attacker = rel.from();
            Town defender = rel.to();
            if (attacker == null || defender == null) continue;

            // Lazy-spawn paired squads for this war. The first slice ships
            // stub squads with no members — real squad selection (e.g. one
            // attack squad per town, sized by the assigned warrior roles)
            // is a separate concern.
            Squad att = attackerSquads.computeIfAbsent(attacker,
                k -> createAttackSquad(level, attacker, defender));
            Squad def = defenderSquads.computeIfAbsent(defender,
                k -> createDefendSquad(level, defender, attacker));

            BattleContext ctx = new BattleContext(level, gameTick,
                attacker, defender, att, def, casualties);

            BattleState attState = squadStates.getOrDefault(att.id(), BattleState.ADVANCING);
            squadStates.put(att.id(), sm.tick(att, attState, ctx));

            BattleState defState = squadStates.getOrDefault(def.id(), BattleState.ADVANCING);
            squadStates.put(def.id(), sm.tick(def, defState, ctx));
        }
    }

    private Squad createAttackSquad(ServerLevel level, Town attacker, Town defender) {
        BlockPos target = anchorFor(level, defender);
        return new Squad(UUID.randomUUID(),
            attacker.getName() + "-attack",
            List.of(),
            SquadGoal.ATTACK,
            target);
    }

    private Squad createDefendSquad(ServerLevel level, Town defender, Town attacker) {
        BlockPos target = anchorFor(level, defender);
        return new Squad(UUID.randomUUID(),
            defender.getName() + "-defend",
            List.of(),
            SquadGoal.DEFEND,
            target);
    }

    /**
     * Look up the anchor position of {@code town} in the level's
     * {@link LevelTowns}. Returns {@link #FALLBACK_ANCHOR} when the town
     * is not registered (a test environment with a bare {@link Town}, or
     * a town still being registered).
     */
    private static BlockPos anchorFor(ServerLevel level, Town town) {
        if (town == null) return FALLBACK_ANCHOR;
        for (Map.Entry<Long, Town> entry : LevelTowns.get(level).getAllTownEntries()) {
            if (entry.getValue() == town) {
                return BlockPos.of(entry.getKey());
            }
        }
        return FALLBACK_ANCHOR;
    }

    // --- test / diagnostic accessors ----------------------------------------------------

    public BattleState stateOf(UUID squadId) { return squadStates.get(squadId); }

    public Squad attackerSquadFor(Town town) { return attackerSquads.get(town); }

    public Squad defenderSquadFor(Town town) { return defenderSquads.get(town); }

    public CasualtyModel casualties() { return casualties; }

    public BattleStateMachine stateMachine() { return sm; }
}

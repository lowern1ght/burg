package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.BattleDriver;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticRegistry;
import org.lowern1ght.burg.behavior.war.BattleContext;
import org.lowern1ght.burg.behavior.war.BattleState;
import org.lowern1ght.burg.behavior.war.BattleStateMachine;
import org.lowern1ght.burg.behavior.war.CasualtyModel;
import org.lowern1ght.burg.behavior.war.Squad;
import org.lowern1ght.burg.behavior.war.SquadGoal;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

import java.util.List;
import java.util.UUID;

/**
 * GameTest coverage for Phase BEHAVIOR-8 (war foundation — BattleStateMachine).
 *
 * <p>Each test owns its own {@link Squad}, {@link CasualtyModel}, and
 * {@link BattleStateMachine} so cases cannot leak into one another.
 * The state-machine tests avoid the engine entirely — the machine is a
 * pure function, so the inputs are enough to verify the transitions.
 *
 * <p>NPCs are spawned via the same {@code EntityRegistry.NPC.create +
 * moveTo + addFreshEntity} pattern the other behavior tests use. The
 * {@code Squad} constructor requires a non-empty roster, so the
 * state-machine tests still need one stub NPC at a known position; the
 * arithmetic in {@link CasualtyModel#averageHealth} is what the
 * machine actually reads, and it does not depend on the NPC's wander
 * state.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class WarGameTest {

    // -----------------------------------------------------------------------------------
    // Squad
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void squad_averagePosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        Npc b = spawnNpcAt(level, new BlockPos(4, 0, 0));
        Npc c = spawnNpcAt(level, new BlockPos(8, 0, 0));

        Squad squad = new Squad(UUID.randomUUID(), "avg-test",
            List.of(a, b, c), SquadGoal.ATTACK, new BlockPos(0, 64, 0));

        BlockPos avg = squad.averagePosition();
        helper.assertTrue(avg.getX() == 4,
            "average X = (0+4+8)/3 = 4 (was " + avg.getX() + ")");
        helper.assertTrue(avg.getY() == 0,
            "average Y = 0 (was " + avg.getY() + ")");
        helper.assertTrue(avg.getZ() == 0,
            "average Z = 0 (was " + avg.getZ() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void squad_distanceToTargetSqrt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        Squad squad = new Squad(UUID.randomUUID(), "dist-test",
            List.of(a), SquadGoal.ATTACK, new BlockPos(3, 0, 4));

        // sqrt(3^2 + 0^2 + 4^2) = 5.0
        double dist = squad.distanceToTarget();
        helper.assertTrue(Math.abs(dist - 5.0) < 0.001,
            "distance from (0,0,0) to (3,0,4) = 5.0 (was " + dist + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // CasualtyModel
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void casualtyModel_markDead_isAliveReturnsFalse(GameTestHelper helper) {
        CasualtyModel cm = new CasualtyModel();
        UUID id = UUID.randomUUID();

        helper.assertTrue(cm.isAlive(id), "an unseen NPC is alive by default");

        cm.markDead(id);
        helper.assertTrue(!cm.isAlive(id),
            "isAlive returns false after markDead (was " + cm.isAlive(id) + ")");

        // A different UUID is still alive.
        helper.assertTrue(cm.isAlive(UUID.randomUUID()),
            "a different NPC is unaffected by another NPC's death");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void casualtyModel_averageHealth_decreases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CasualtyModel cm = new CasualtyModel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        Npc b = spawnNpcAt(level, new BlockPos(1, 0, 0));
        Npc c = spawnNpcAt(level, new BlockPos(2, 0, 0));
        Npc d = spawnNpcAt(level, new BlockPos(3, 0, 0));
        Squad squad = new Squad(UUID.randomUUID(), "health-test",
            List.of(a, b, c, d), SquadGoal.ATTACK, new BlockPos(0, 64, 0));

        float h = cm.averageHealth(squad);
        helper.assertTrue(Math.abs(h - 1.0f) < 0.001f,
            "all healthy -> 1.0 (was " + h + ")");

        cm.markDead(d.getUUID());
        h = cm.averageHealth(squad);
        helper.assertTrue(Math.abs(h - 0.75f) < 0.001f,
            "one dead of four -> 0.75 (was " + h + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void casualtyModel_averageHealth_injuryBuckets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CasualtyModel cm = new CasualtyModel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));   // healthy
        Npc b = spawnNpcAt(level, new BlockPos(1, 0, 0));   // light injury (sev 2)
        Npc c = spawnNpcAt(level, new BlockPos(2, 0, 0));   // mid injury (sev 4 => half)
        Npc d = spawnNpcAt(level, new BlockPos(3, 0, 0));   // severe injury (sev 7 => 0)
        Squad squad = new Squad(UUID.randomUUID(), "injury-bucket",
            List.of(a, b, c, d), SquadGoal.ATTACK, new BlockPos(0, 64, 0));

        cm.markInjured(b.getUUID(), 2);
        cm.markInjured(c.getUUID(), 4);
        cm.markInjured(d.getUUID(), 7);

        // 1.0 + 1.0 + 0.5 + 0.0 = 2.5 / 4 = 0.625
        float h = cm.averageHealth(squad);
        helper.assertTrue(Math.abs(h - 0.625f) < 0.001f,
            "bucket mix: 1+1+0.5+0 / 4 = 0.625 (was " + h + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // BattleStateMachine
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleStateMachine_advancingWithinRange_engages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        // Target 5 blocks away — well within ENGAGE_RANGE (10).
        Squad squad = new Squad(UUID.randomUUID(), "engage-test",
            List.of(a), SquadGoal.ATTACK, new BlockPos(5, 0, 0));

        BattleContext ctx = battleContext(level, squad);
        BattleStateMachine sm = new BattleStateMachine();

        BattleState next = sm.tick(squad, BattleState.ADVANCING, ctx);
        helper.assertTrue(next == BattleState.ENGAGING,
            "ADVANCING within 10 blocks -> ENGAGING (was " + next + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleStateMachine_engagingAtTarget_victorious(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        // On top of the target — within range AND at the target.
        Squad squad = new Squad(UUID.randomUUID(), "victory-test",
            List.of(a), SquadGoal.ATTACK, new BlockPos(0, 0, 0));

        BattleContext ctx = battleContext(level, squad);
        BattleStateMachine sm = new BattleStateMachine();

        BattleState next = sm.tick(squad, BattleState.ENGAGING, ctx);
        helper.assertTrue(next == BattleState.VICTORIOUS,
            "ENGAGING at target with full health -> VICTORIOUS (was " + next + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleStateMachine_lowHealth_retreats(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Four members: 2 dead, 2 with severity-4 injury (half-casualty).
        // Health = (0 + 0 + 0.5 + 0.5) / 4 = 0.25, which is <= 0.30 retreat but > 0.10 rout.
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        Npc b = spawnNpcAt(level, new BlockPos(1, 0, 0));
        Npc c = spawnNpcAt(level, new BlockPos(2, 0, 0));
        Npc d = spawnNpcAt(level, new BlockPos(3, 0, 0));
        Squad squad = new Squad(UUID.randomUUID(), "retreat-test",
            List.of(a, b, c, d), SquadGoal.ATTACK, new BlockPos(0, 0, 0));

        CasualtyModel cm = new CasualtyModel();
        cm.markDead(a.getUUID());
        cm.markDead(b.getUUID());
        cm.markInjured(c.getUUID(), 4);
        cm.markInjured(d.getUUID(), 4);

        BattleContext ctx = new BattleContext(level, 0,
            newTown("A"), newTown("B"), squad, squad, cm);

        BattleStateMachine sm = new BattleStateMachine();
        BattleState next = sm.tick(squad, BattleState.ADVANCING, ctx);
        helper.assertTrue(next == BattleState.RETREATING,
            "health 0.25 (> 0.10 rout, <= 0.30 retreat) -> RETREATING (was " + next + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleStateMachine_veryLowHealth_routs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        Squad squad = new Squad(UUID.randomUUID(), "rout-test",
            List.of(a), SquadGoal.ATTACK, new BlockPos(0, 0, 0));

        CasualtyModel cm = new CasualtyModel();
        // severity 7 = severe injury, health = 0.0.
        cm.markInjured(a.getUUID(), 7);
        BattleContext ctx = new BattleContext(level, 0,
            newTown("A"), newTown("B"), squad, squad, cm);

        BattleStateMachine sm = new BattleStateMachine();
        BattleState next = sm.tick(squad, BattleState.ADVANCING, ctx);
        helper.assertTrue(next == BattleState.ROUTED,
            "health 0.0 -> ROUTED (was " + next + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleStateMachine_routedIsTerminal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Npc a = spawnNpcAt(level, new BlockPos(0, 0, 0));
        Squad squad = new Squad(UUID.randomUUID(), "terminal-test",
            List.of(a), SquadGoal.ATTACK, new BlockPos(0, 0, 0));

        BattleContext ctx = battleContext(level, squad);
        BattleStateMachine sm = new BattleStateMachine();

        // ROUTED is terminal even when health is full and the squad is at the target.
        BattleState next = sm.tick(squad, BattleState.ROUTED, ctx);
        helper.assertTrue(next == BattleState.ROUTED,
            "ROUTED is terminal (was " + next + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // BattleDriver
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleDriver_activeWar_spawnsSquadsAndAdvancesState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Town attacker = newTown("A");
        Town defender = newTown("B");

        BlockPos atkAnchor = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos defAnchor = helper.absolutePos(new BlockPos(20, 1, 20));
        LevelTowns.get(level).registerTown(atkAnchor, attacker);
        LevelTowns.get(level).registerTown(defAnchor, defender);

        DiplomaticRegistry registry = new DiplomaticRegistry();
        registry.declareWar(attacker, defender);

        BattleDriver driver = new BattleDriver();

        // First battle tick fires at BATTLE_TICK_INTERVAL = 20 ticks. Run 25 to
        // be safely past the first tick boundary.
        for (int i = 0; i < 25; i++) {
            driver.onServerTick(level, i, registry);
        }

        Squad attSquad = driver.attackerSquadFor(attacker);
        Squad defSquad = driver.defenderSquadFor(defender);
        helper.assertTrue(attSquad != null, "attacker squad created for the war");
        helper.assertTrue(defSquad != null, "defender squad created for the war");

        // Empty squads have health 0, so the state machine routes them on
        // the first battle tick. Stub-squad routing is the expected
        // behaviour for the first slice -- real squad selection (warrior
        // roles) is a separate commit.
        helper.assertTrue(driver.stateOf(attSquad.id()) == BattleState.ROUTED,
            "attacker squad advanced to ROUTED (was "
                + driver.stateOf(attSquad.id()) + ")");
        helper.assertTrue(driver.stateOf(defSquad.id()) == BattleState.ROUTED,
            "defender squad advanced to ROUTED (was "
                + driver.stateOf(defSquad.id()) + ")");

        // The squads' target positions are the registered anchors, not the
        // fallback. (This is the round-trip we care about: the driver
        // asks LevelTowns for the anchor and gets a real position back.)
        helper.assertTrue(attSquad.targetPosition().equals(defAnchor),
            "attack squad target = defender anchor (was " + attSquad.targetPosition() + ")");
        helper.assertTrue(defSquad.targetPosition().equals(defAnchor),
            "defend squad target = defender anchor (was " + defSquad.targetPosition() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void battleDriver_noWar_skipsSquads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DiplomaticRegistry registry = new DiplomaticRegistry();
        BattleDriver driver = new BattleDriver();

        for (int i = 0; i < 25; i++) {
            driver.onServerTick(level, i, registry);
        }

        // No AT_WAR relations -> no squads created.
        helper.assertTrue(driver.casualties().deadMembers().isEmpty(),
            "no casualties when no war is active");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    /**
     * Spawns a single NPC at the exact block position {@code pos}. The Npc's
     * block position after spawn is the integer floor of {@code (pos.x + 0.5,
     * pos.y, pos.z + 0.5)}, so the spawn places an NPC at block coords
     * {@code pos} itself.
     */
    private static Npc spawnNpcAt(ServerLevel level, BlockPos pos) {
        Npc npc = EntityRegistry.NPC.create(level);
        npc.setPersistenceRequired();
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(npc);
        return npc;
    }

    private static Town newTown(String name) {
        Town t = new Town();
        t.setName(name);
        return t;
    }

    /** A full-health {@link BattleContext} for a single squad at the supplied level. */
    private static BattleContext battleContext(ServerLevel level, Squad squad) {
        Town a = newTown("A");
        Town b = newTown("B");
        return new BattleContext(level, 0, a, b, squad, squad, new CasualtyModel());
    }
}

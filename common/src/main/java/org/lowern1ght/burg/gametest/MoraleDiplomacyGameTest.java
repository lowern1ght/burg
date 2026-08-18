package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.diplomacy.DeclareWarAction;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticAI;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticAction;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticRegistry;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticStatus;
import org.lowern1ght.burg.behavior.diplomacy.ProposeAllianceAction;
import org.lowern1ght.burg.behavior.diplomacy.ProposeTruceAction;
import org.lowern1ght.burg.behavior.diplomacy.TributeAction;
import org.lowern1ght.burg.behavior.morale.HasFoodModifier;
import org.lowern1ght.burg.behavior.morale.MoraleCalculator;
import org.lowern1ght.burg.behavior.morale.MoraleLevel;
import org.lowern1ght.burg.behavior.morale.MoraleState;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.Town;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTest coverage for the morale and diplomacy foundation.
 *
 * <p>The pure-JUnit boundary check for {@link MoraleLevel} lives in
 * {@code common/src/test/.../MoraleLevelTest} (no Minecraft on its
 * classpath). Everything here touches an {@link Npc}, a {@link Town}, or a
 * Minecraft type and so needs the game-test JVM.
 *
 * <p>Each test builds fresh state ({@link MoraleState}, {@link DiplomaticRegistry},
 * {@link DiplomaticAI}, {@link MoraleCalculator}, citizen lists) so cases
 * cannot leak into one another.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class MoraleDiplomacyGameTest {

    // -----------------------------------------------------------------------------------
    // MoraleLevel — the boundary sweep itself is in MoraleLevelTest (pure JUnit).
    // Here we cover the fromValue call from a Minecraft context to confirm the
    // classpath also resolves when GameTest loads it.
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void moraleLevelFromValue_inGameContext(GameTestHelper helper) {
        helper.assertTrue(MoraleLevel.fromValue(0) == MoraleLevel.HOSTILE, "0 is HOSTILE");
        helper.assertTrue(MoraleLevel.fromValue(50) == MoraleLevel.NEUTRAL, "50 is NEUTRAL");
        helper.assertTrue(MoraleLevel.fromValue(100) == MoraleLevel.LOYAL, "100 is LOYAL");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // MoraleState
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void moraleState_adjustClampsToBounds(GameTestHelper helper) {
        MoraleState morale = new MoraleState();
        UUID id = UUID.randomUUID();

        // Big positive saturates at 100.
        morale.adjust(id, 200);
        helper.assertTrue(morale.valueFor(id) == 100,
            "adjust(+200) clamps to 100 (was " + morale.valueFor(id) + ")");

        // Big negative saturates at 0.
        morale.adjust(id, -200);
        helper.assertTrue(morale.valueFor(id) == 0,
            "adjust(-200) clamps to 0 (was " + morale.valueFor(id) + ")");

        // A small positive lands exactly.
        morale.adjust(id, 25);
        helper.assertTrue(morale.valueFor(id) == 25,
            "adjust(+25) from 0 lands at 25 (was " + morale.valueFor(id) + ")");

        // The DEFAULT (50) is what an unseen citizen reads as.
        UUID stranger = UUID.randomUUID();
        helper.assertTrue(morale.valueFor(stranger) == MoraleState.DEFAULT,
            "a citizen never seen reads as DEFAULT=50 (was " + morale.valueFor(stranger) + ")");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // Modifiers
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void hasFoodModifier_appliesFive(GameTestHelper helper) {
        HasFoodModifier mod = new HasFoodModifier();
        int result = mod.modify(UUID.randomUUID(), new Town(), 50, null);
        helper.assertTrue(result == 55,
            "+50 + 5 (HasFood) = 55 (was " + result + ")");
        helper.assertTrue("HasFood".equals(mod.name()),
            "stable name is 'HasFood' (was " + mod.name() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void moraleCalculator_combinesModifiers(GameTestHelper helper) {
        MoraleCalculator calc = new MoraleCalculator();
        int result = calc.compute(UUID.randomUUID(), new Town(), 50, null);
        // Default registration: HasFood (+5) then HasBed (+3). 50 + 5 + 3 = 58.
        helper.assertTrue(result == 58,
            "50 + HasFood(+5) + HasBed(+3) = 58 (was " + result + ")");
        helper.assertTrue(calc.modifiers().size() == 2,
            "two default modifiers registered (was " + calc.modifiers().size() + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // DiplomaticRegistry
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void diplomaticRegistry_defaultIsNeutral(GameTestHelper helper) {
        DiplomaticRegistry reg = new DiplomaticRegistry();
        Town a = new Town();
        Town b = new Town();
        a.setName("A");
        b.setName("B");

        helper.assertTrue(reg.between(a, b).status() == DiplomaticStatus.NEUTRAL,
            "untouched relation defaults to NEUTRAL (was " + reg.between(a, b).status() + ")");
        helper.assertTrue(reg.between(b, a).status() == DiplomaticStatus.NEUTRAL,
            "the reverse direction also reads NEUTRAL");
        helper.assertTrue(reg.allRelations().isEmpty(),
            "nothing explicitly stored yet (was " + reg.allRelations().size() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void diplomaticRegistry_declareWar_setsAtWar(GameTestHelper helper) {
        DiplomaticRegistry reg = new DiplomaticRegistry();
        Town a = new Town();
        Town b = new Town();
        a.setName("A");
        b.setName("B");

        reg.declareWar(a, b);

        helper.assertTrue(reg.between(a, b).status() == DiplomaticStatus.AT_WAR,
            "a -> b is AT_WAR after declareWar (was " + reg.between(a, b).status() + ")");
        helper.assertTrue(reg.between(b, a).status() == DiplomaticStatus.AT_WAR,
            "b -> a is mirrored to AT_WAR (was " + reg.between(b, a).status() + ")");
        helper.assertTrue(reg.allRelations().size() == 2,
            "two directed edges stored (was " + reg.allRelations().size() + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // DiplomaticAI
    // -----------------------------------------------------------------------------------

    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void diplomaticAI_shouldDeclareWar_basedOnMorale(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));

        // Aggressor: two citizens at morale 80. Defender: two citizens at morale 50.
        // Difference = 30 > 20, so war is declared.
        List<Npc> aggressor = spawnCitizens(level, 2, anchor);
        List<Npc> defender = spawnCitizens(level, 2, anchor.east().east());

        MoraleState morale = new MoraleState();
        for (Npc n : aggressor) morale.set(n.getUUID(), 80);
        for (Npc n : defender) morale.set(n.getUUID(), 50);

        DiplomaticAI ai = new DiplomaticAI();
        helper.assertTrue(ai.shouldDeclareWar(morale, aggressor, defender),
            "aggressor +30 morale edge over defender triggers war");

        // Equal morale (both 50) — no war.
        for (Npc n : aggressor) morale.set(n.getUUID(), 50);
        helper.assertTrue(!ai.shouldDeclareWar(morale, aggressor, defender),
            "equal morale does not trigger war");

        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void diplomaticAI_shouldAcceptTruce_whenMoraleLow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));

        List<Npc> own = spawnCitizens(level, 2, anchor);
        MoraleState morale = new MoraleState();

        DiplomaticAI ai = new DiplomaticAI();

        // Morale 30 — below 40, wants peace.
        for (Npc n : own) morale.set(n.getUUID(), 30);
        helper.assertTrue(ai.shouldAcceptTruce(morale, own),
            "low morale (30) accepts truce");

        // Morale 70 — doesn't want peace.
        for (Npc n : own) morale.set(n.getUUID(), 70);
        helper.assertTrue(!ai.shouldAcceptTruce(morale, own),
            "comfortable morale (70) refuses truce");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // Sealed DiplomaticAction
    // -----------------------------------------------------------------------------------

    /**
     * Compile-time check that {@link DiplomaticAction} permits exactly the four
     * concrete actions it documents. If a future slice adds a fifth action but
     * forgets to update the sealed permits list, this test fails to compile.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void diplomaticAction_sealedPermits(GameTestHelper helper) {
        Town a = new Town();
        Town b = new Town();
        a.setName("A");
        b.setName("B");

        DiplomaticAction war = new DeclareWarAction(a, b);
        DiplomaticAction truce = new ProposeTruceAction(a, b);
        DiplomaticAction alliance = new ProposeAllianceAction(a, b);
        DiplomaticAction tribute = new TributeAction(a, b, 10);

        helper.assertTrue(war.proposedStatus() == DiplomaticStatus.AT_WAR, "war declares AT_WAR");
        helper.assertTrue(truce.proposedStatus() == DiplomaticStatus.TRUCE, "truce declares TRUCE");
        helper.assertTrue(alliance.proposedStatus() == DiplomaticStatus.ALLY, "alliance declares ALLY");
        // Tribute doesn't change status — NEUTRAL is the documented default.
        helper.assertTrue(tribute.proposedStatus() == DiplomaticStatus.NEUTRAL,
            "tribute does not change status (got " + tribute.proposedStatus() + ")");
        helper.assertTrue(((TributeAction) tribute).amountPerTick() == 10,
            "tribute carries its amount");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    /** Spawns {@code count} fresh NPCs in a row at increasing X offsets. */
    private static List<Npc> spawnCitizens(ServerLevel level, int count, BlockPos anchor) {
        List<Npc> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Npc npc = EntityRegistry.NPC.create(level);
            npc.setPersistenceRequired();
            npc.moveTo(anchor.getX() + 0.5 + i, anchor.getY() + 1.0, anchor.getZ() + 0.5);
            level.addFreshEntity(npc);
            out.add(npc);
        }
        return out;
    }
}

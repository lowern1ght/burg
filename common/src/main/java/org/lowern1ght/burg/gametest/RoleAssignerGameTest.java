package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.behavior.role.RoleAssigner;
import org.lowern1ght.burg.behavior.role.RoleAssignerConfig;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.Town;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTest coverage for {@link RoleAssigner} and {@code Town.roleOf(UUID)}.
 *
 * <p>The role assigner is pure logic — given a citizen list and a quota it produces a
 * deterministic role map — so most of its tests do not need the live Minecraft world. They
 * still need {@link Npc} for the UUIDs, and spawning an {@code Npc} requires a level, hence
 * the {@code GameTest}. The Town backward-compat test stands alone: it constructs an empty
 * town, mutates the legacy {@code builders} list directly, and exercises {@link
 * Town#roleOf(UUID)}.
 *
 * <p>Each test passes its own citizens and a fresh {@link RoleAssigner}, so tests do not
 * share state.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class RoleAssignerGameTest {

    // -----------------------------------------------------------------------------------
    // RoleAssigner
    // -----------------------------------------------------------------------------------

    /**
     * Five citizens, defaults (maxBuilders=2, maxRoadBuilders=1). The assigner fills the two
     * builder slots, hands the next citizen the road-builder slot (priority order: BUILDER
     * &gt; ROAD_BUILDER), and leaves the remaining two as IDLE because no other quota is open.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void roleAssigner_assignsBuildersUpToQuota(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RoleAssigner assigner = new RoleAssigner();
        List<Npc> citizens = spawnCitizens(level, 5, helper.absolutePos(new BlockPos(0, 1, 0)));

        assigner.update(new Town(), citizens, RoleAssignerConfig.defaults());

        long builders = countRoles(assigner, citizens, CitizenRole.BUILDER);
        long roadBuilders = countRoles(assigner, citizens, CitizenRole.ROAD_BUILDER);
        long idle = countRoles(assigner, citizens, CitizenRole.IDLE);
        helper.assertTrue(builders == 2,
            "exactly 2 citizens assigned BUILDER under defaults (got " + builders + ")");
        helper.assertTrue(roadBuilders == 1,
            "the next citizen picks up ROAD_BUILDER (got " + roadBuilders + ")");
        helper.assertTrue(idle == 2,
            "the remaining 2 citizens are IDLE (got " + idle + ")");
        helper.succeed();
    }

    /**
     * Three citizens, defaults (maxBuilders=2, maxRoadBuilders=1). Two builders fill first,
     * the third falls through to road builder. With a larger citizen set and a higher
     * road-builder quota, BUILDER would still dominate and ROAD_BUILDER would only kick in
     * once the builder quota is full.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void roleAssigner_prioritizesBuildersOverRoadBuilders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RoleAssigner assigner = new RoleAssigner();
        List<Npc> citizens = spawnCitizens(level, 3, helper.absolutePos(new BlockPos(0, 1, 0)));

        assigner.update(new Town(), citizens, RoleAssignerConfig.defaults());

        long builders = countRoles(assigner, citizens, CitizenRole.BUILDER);
        long roadBuilders = countRoles(assigner, citizens, CitizenRole.ROAD_BUILDER);
        helper.assertTrue(builders == 2,
            "2 builders assigned first (got " + builders + ")");
        helper.assertTrue(roadBuilders == 1,
            "the third citizen falls through to ROAD_BUILDER (got " + roadBuilders + ")");
        helper.succeed();
    }

    /**
     * Calling {@code update} twice with the same input must not change the role map. This
     * is the contract that makes the engine's 5-second tick safe.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void roleAssigner_idempotent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RoleAssigner assigner = new RoleAssigner();
        List<Npc> citizens = spawnCitizens(level, 4, helper.absolutePos(new BlockPos(0, 1, 0)));

        assigner.update(new Town(), citizens, RoleAssignerConfig.defaults());
        // Snapshot the role map after the first pass.
        List<CitizenRole> snapshot = new ArrayList<>();
        for (Npc c : citizens) snapshot.add(assigner.currentRole(c.getUUID()));

        assigner.update(new Town(), citizens, RoleAssignerConfig.defaults());

        for (int i = 0; i < citizens.size(); i++) {
            helper.assertTrue(assigner.currentRole(citizens.get(i).getUUID()) == snapshot.get(i),
                "citizen " + i + " kept its role across the second update");
        }
        helper.succeed();
    }

    /**
     * A manual override sticks across the next {@code update}. The assigner must not silently
     * re-classify a GUARD because the builder quota has room.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void roleAssigner_assignManually_overridesDefault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RoleAssigner assigner = new RoleAssigner();
        List<Npc> citizens = spawnCitizens(level, 3, helper.absolutePos(new BlockPos(0, 1, 0)));

        UUID guarded = citizens.get(0).getUUID();
        assigner.assignManually(guarded, CitizenRole.GUARD);

        assigner.update(new Town(), citizens, RoleAssignerConfig.defaults());

        helper.assertTrue(assigner.currentRole(guarded) == CitizenRole.GUARD,
            "the manually-assigned GUARD survives update (got " + assigner.currentRole(guarded) + ")");
        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // Town.roleOf backward compat
    // -----------------------------------------------------------------------------------

    /**
     * A citizen in the legacy {@code builders} list reads as BUILDER via {@link
     * Town#roleOf(UUID)}; a citizen not on the list reads as IDLE. The legacy field is
     * untouched (save-compat).
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void townRoleOf_backwardCompat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 1, 0));
        List<Npc> citizens = spawnCitizens(level, 2, anchor);

        Town town = new Town();
        town.setBuilderNpcIdAtSlot(0, citizens.get(0).getUUID());
        // citizens.get(1) is intentionally NOT in the builders list.

        helper.assertTrue(town.roleOf(citizens.get(0).getUUID()) == CitizenRole.BUILDER,
            "a citizen in the legacy builders list reads as BUILDER (got "
                + town.roleOf(citizens.get(0).getUUID()) + ")");
        helper.assertTrue(town.roleOf(citizens.get(1).getUUID()) == CitizenRole.IDLE,
            "a citizen not in the legacy builders list reads as IDLE (got "
                + town.roleOf(citizens.get(1).getUUID()) + ")");
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

    private static long countRoles(RoleAssigner assigner, List<Npc> citizens, CitizenRole role) {
        long count = 0;
        for (Npc c : citizens) {
            if (assigner.currentRole(c.getUUID()) == role) count++;
        }
        return count;
    }

    /**
     * Unused: kept to satisfy the same "compile-time guard" pattern the rest of the test
     * suite uses — if the Entity class ever drops {@code getUUID}, this catches it.
     */
    @SuppressWarnings("unused")
    private static void assertNpcIdentity(Entity e) {
        if (!(e instanceof Npc n)) throw new AssertionError("not an Npc: " + e);
        UUID id = n.getUUID();
        if (id == null) throw new AssertionError("Npc has no UUID");
    }
}

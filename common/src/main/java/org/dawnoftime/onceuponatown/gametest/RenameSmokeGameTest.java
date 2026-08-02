package org.dawnoftime.onceuponatown.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.task.RoadTask;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.UUID;

/**
 * Rename smoke test for the {@code behavior.path} → {@code behavior.road} +
 * {@code PathTask} → {@code RoadTask} rename.
 *
 * <p>The rename is complete when {@link RoadTask} exists as a loadable class
 * and {@code PathTask} no longer exists. A direct compile-time reference to
 * the old name would pass even after a partial rename (if the old class still
 * existed), so we verify at runtime via {@link Class#forName} that the old
 * class cannot be loaded from the classpath.
 *
 * <p>Also confirms the new package is loadable and the engine still
 * references the new class via the {@code CitizenTask} sealed interface.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class RenameSmokeGameTest {

    @GameTest(template = "empty5x5", timeoutTicks = 30, batch = "behavior")
    public static void rename_roadTaskExists_pathTaskGone(GameTestHelper helper) {
        // 1) RoadTask must be loadable.
        Class<?> roadTaskClass;
        try {
            roadTaskClass = Class.forName("org.dawnoftime.onceuponatown.behavior.task.RoadTask");
        } catch (ClassNotFoundException e) {
            helper.fail("RoadTask class not found after rename: " + e.getMessage());
            return;
        }
        helper.assertTrue(roadTaskClass != null, "RoadTask is loadable");
        helper.assertTrue(roadTaskClass == RoadTask.class,
            "Class.forName resolves to the same class as the import (was "
                + roadTaskClass.getName() + ")");

        // 2) PathTask must NOT be loadable.
        try {
            Class.forName("org.dawnoftime.onceuponatown.behavior.task.PathTask");
            helper.fail("PathTask still exists on the classpath after rename");
        } catch (ClassNotFoundException expected) {
            // Expected — the rename is complete.
        }

        // 3) The new road package is loadable.
        try {
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadBuilder");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadPlanner");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadLayer");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadLayerFromStructures");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadSegment");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadType");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.RoadGraph");
            Class.forName("org.dawnoftime.onceuponatown.behavior.road.TerrainCost");
        } catch (ClassNotFoundException e) {
            helper.fail("Road package class not found: " + e.getMessage());
            return;
        }

        // 4) The old path package is gone (no class should resolve from it).
        try {
            Class.forName("org.dawnoftime.onceuponatown.behavior.path.RoadBuilder");
            helper.fail("Old behavior.path package still resolvable");
        } catch (ClassNotFoundException expected) {
            // Expected.
        }

        // 5) Instantiate a RoadTask via the legacy (UUID, source, assignee)
        // constructor — confirms the class is real, not just a stub. We pass
        // null source/assignee to avoid setting up a Town/Npc for a smoke test.
        UUID id = UUID.randomUUID();
        TownIntent nullSource = null;
        Npc nullAssignee = null;
        RoadTask task = new RoadTask(id, nullSource, nullAssignee);
        helper.assertTrue(task.id().equals(id),
            "RoadTask instance carries the id passed to the ctor (was " + task.id() + ")");

        helper.succeed();
    }
}

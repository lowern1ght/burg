package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sealed {@link ConstructionIntent} union — the two permitted shapes
 * ({@link ConstructionIntent.NewBuild} and {@link ConstructionIntent.Upgrade})
 * and the Minecraft-free discipline both share. {@code ConstructionQueueTest}
 * exercises the union at the queue boundary; this file pins the standalone
 * record contract the {@code Town} facade inherits.
 */
class ConstructionIntentTest {

    @Test
    @DisplayName("NewBuild exposes entryId + buildingDefId and no world coordinate")
    void newBuildShape() {
        ConstructionIntent intent = new ConstructionIntent.NewBuild(7L, "burg:oak_log");

        assertAll(
            () -> assertEquals(7L, intent.entryId()),
            () -> assertEquals("burg:oak_log", intent.buildingDefId()),
            () -> assertTrue(intent instanceof ConstructionIntent.NewBuild)
        );
    }

    @Test
    @DisplayName("Upgrade exposes entryId + buildingDefId + worldPosKey + fromLevel")
    void upgradeShape() {
        long packedPos = 1234567890123L;
        ConstructionIntent intent = new ConstructionIntent.Upgrade(
            7L, "burg:smithy", Long.toString(packedPos), 2);

        assertAll(
            () -> assertEquals(7L, intent.entryId()),
            () -> assertEquals("burg:smithy", intent.buildingDefId()),
            () -> assertEquals(Long.toString(packedPos),
                ((ConstructionIntent.Upgrade) intent).worldPosKey(),
                "worldPosKey is the stringified form of BlockPos.asLong()"),
            () -> assertEquals(2, ((ConstructionIntent.Upgrade) intent).fromLevel()),
            () -> assertTrue(intent instanceof ConstructionIntent.Upgrade)
        );
    }

    @Test
    @DisplayName("the two permitted shapes are distinct values")
    void shapesAreDistinct() {
        ConstructionIntent nb = new ConstructionIntent.NewBuild(1L, "burg:oak_log");
        ConstructionIntent up = new ConstructionIntent.Upgrade(
            1L, "burg:oak_log", "0", 0);

        // Same entryId + buildingDefId, different shape — not equal.
        assertNotEquals(nb, up,
            "NewBuild and Upgrade are different records even with overlapping fields");
    }

    @Test
    @DisplayName("two NewBuilds with the same fields are equal; a field flip is not")
    void newBuildEquality() {
        ConstructionIntent a = new ConstructionIntent.NewBuild(1L, "burg:oak_log");
        ConstructionIntent b = new ConstructionIntent.NewBuild(1L, "burg:oak_log");

        assertAll(
            () -> assertEquals(a, b),
            () -> assertNotEquals(a, new ConstructionIntent.NewBuild(2L, "burg:oak_log"),
                "different entryId — different ref"),
            () -> assertNotEquals(a, new ConstructionIntent.NewBuild(1L, "burg:smithy"),
                "different buildingDefId — different ref")
        );
    }

    @Test
    @DisplayName("two Upgrades with the same fields are equal; a field flip is not")
    void upgradeEquality() {
        ConstructionIntent a = new ConstructionIntent.Upgrade(1L, "burg:smithy", "42", 1);
        ConstructionIntent b = new ConstructionIntent.Upgrade(1L, "burg:smithy", "42", 1);

        assertAll(
            () -> assertEquals(a, b),
            () -> assertNotEquals(a, new ConstructionIntent.Upgrade(1L, "burg:smithy", "99", 1),
                "different worldPosKey — different ref"),
            () -> assertNotEquals(a, new ConstructionIntent.Upgrade(1L, "burg:smithy", "42", 2),
                "different fromLevel — different ref")
        );
    }

    @Test
    @DisplayName("NewBuild rejects a null buildingDefId")
    void newBuildRejectsNullBuildingDefId() {
        assertThrows(NullPointerException.class,
            () -> new ConstructionIntent.NewBuild(1L, null));
    }

    @Test
    @DisplayName("Upgrade rejects a null buildingDefId or a null worldPosKey")
    void upgradeRejectsNulls() {
        assertAll(
            () -> assertThrows(NullPointerException.class,
                () -> new ConstructionIntent.Upgrade(1L, null, "0", 0),
                "Upgrade requires buildingDefId"),
            () -> assertThrows(NullPointerException.class,
                () -> new ConstructionIntent.Upgrade(1L, "burg:smithy", null, 0),
                "Upgrade requires worldPosKey")
        );
    }

    @Test
    @DisplayName("Upgrade accepts a negative or zero fromLevel — it is data, not a gate")
    void upgradeAcceptsAnyFromLevel() {
        // fromLevel is data the Town facade reads at conversion time; the
        // domain shape does not gate it. Pin the contract here so a future
        // tightening is a deliberate ADR rather than a silent constraint.
        ConstructionIntent zero = new ConstructionIntent.Upgrade(1L, "burg:smithy", "0", 0);
        ConstructionIntent negative = new ConstructionIntent.Upgrade(1L, "burg:smithy", "0", -3);

        assertAll(
            () -> assertEquals(0, ((ConstructionIntent.Upgrade) zero).fromLevel()),
            () -> assertEquals(-3, ((ConstructionIntent.Upgrade) negative).fromLevel())
        );
    }
}

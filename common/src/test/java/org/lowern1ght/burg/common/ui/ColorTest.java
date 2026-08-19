package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 32-bit ARGB colour — value-type contract: factories, channel extraction,
 * lerp behaviour at the boundaries. No {@code net.minecraft} import — pure
 * POJO test of a value type the engine and the adapter both use.
 */
class ColorTest {

    @Test
    @DisplayName("rgb factory builds an opaque colour")
    void rgbIsOpaque() {
        Color c = Color.rgb(10, 20, 30);
        assertAll(
            () -> assertEquals(255, c.alpha()),
            () -> assertEquals(10, c.red()),
            () -> assertEquals(20, c.green()),
            () -> assertEquals(30, c.blue())
        );
    }

    @Test
    @DisplayName("rgba factory packs all four channels")
    void rgba() {
        Color c = Color.rgba(10, 20, 30, 200);
        assertEquals(200, c.alpha());
        assertEquals(10, c.red());
    }

    @Test
    @DisplayName("factories clamp channels into [0, 255]")
    void channelClamping() {
        assertAll(
            () -> assertEquals(0, Color.rgb(-5, 100, 100).red(), "negative R clamps to 0"),
            () -> assertEquals(255, Color.rgb(300, 100, 100).red(), "R > 255 clamps to 255"),
            () -> assertEquals(0, Color.rgba(0, 0, 0, -1).alpha(), "negative alpha clamps"),
            () -> assertEquals(255, Color.rgba(0, 0, 0, 999).alpha(), "alpha > 255 clamps")
        );
    }

    @Test
    @DisplayName("BLACK, WHITE, TRANSPARENT sentinels")
    void sentinels() {
        assertEquals(0xFF000000, Color.BLACK.argb());
        assertEquals(0xFFFFFFFF, Color.WHITE.argb());
        assertEquals(0x00000000, Color.TRANSPARENT.argb());
    }

    @Test
    @DisplayName("lerp at t = 0 returns self")
    void lerpAtZero() {
        Color c = Color.rgb(50, 100, 150);
        assertEquals(c, c.lerp(Color.WHITE, 0f));
    }

    @Test
    @DisplayName("lerp at t = 1 returns other")
    void lerpAtOne() {
        Color c = Color.rgb(50, 100, 150);
        assertEquals(Color.WHITE, c.lerp(Color.WHITE, 1f));
    }

    @Test
    @DisplayName("lerp at t = 0.5 splits the channels")
    void lerpAtHalf() {
        Color a = Color.BLACK;
        Color b = Color.WHITE;
        Color mid = a.lerp(b, 0.5f);
        assertAll(
            () -> assertEquals(255, mid.alpha(), "alpha stays full"),
            () -> assertEquals(128, mid.red(), "red splits"),
            () -> assertEquals(128, mid.green()),
            () -> assertEquals(128, mid.blue())
        );
    }

    @Test
    @DisplayName("lerp clamps t into [0, 1]")
    void lerpClampT() {
        Color c = Color.rgb(50, 100, 150);
        assertAll(
            () -> assertEquals(c, c.lerp(Color.WHITE, -1f), "negative t clamps to 0"),
            () -> assertEquals(Color.WHITE, c.lerp(Color.WHITE, 2f), "t > 1 clamps to 1")
        );
    }

    @Test
    @DisplayName("equality follows the ARGB int")
    void equality() {
        assertEquals(Color.rgb(1, 2, 3), Color.rgb(1, 2, 3));
        assertEquals(Color.rgb(1, 2, 3).hashCode(), Color.rgb(1, 2, 3).hashCode());
        assertNotEquals(Color.rgb(1, 2, 3), Color.rgb(1, 2, 4));
    }
}
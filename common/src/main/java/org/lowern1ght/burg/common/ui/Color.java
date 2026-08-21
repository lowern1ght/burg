package org.lowern1ght.burg.common.ui;

import java.util.Objects;

/**
 * A 32-bit ARGB colour — the engine's bare-JVM pixel value. The four bytes
 * pack as {@code 0xAARRGGBB} so {@link #argb} can be passed straight to
 * Minecraft's {@code GuiGraphics.fill(...)} through the adapter.
 *
 * @param argb the packed 32-bit ARGB value (alpha in the high byte)
 *
 * <p>No {@code net.minecraft} import. {@link Color} is a value type the
 * UI tests can construct and assert against on a bare JVM (ADR-0022).
 *
 * <p>{@link #rgb(int, int, int)} and {@link #rgba(int, int, int, int)}
 * are the two factories — they clamp each channel into {@code [0, 255]}.
 * {@link #lerp(Color, float)} linearly interpolates each channel for
 * hover / focus / disabled tints; {@code t} is clamped into {@code [0, 1]}.
 */
public record Color(int argb) {

    public Color {
        // record; the int is the value.
    }

    /** Opaque black. */
    public static final Color BLACK = new Color(0xFF000000);
    /** Opaque white. */
    public static final Color WHITE = new Color(0xFFFFFFFF);
    /** Fully transparent — a no-draw sentinel. */
    public static final Color TRANSPARENT = new Color(0x00000000);

    /** Builds an opaque RGB color with {@code a = 255}. Channels clamp to {@code [0, 255]}. */
    public static Color rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    /**
     * Builds an ARGB color. Each channel clamps into {@code [0, 255]}. The
     * alpha channel accepts 0..255 directly (the user gives an unsigned
     * value, the constructor packs it).
     */
    public static Color rgba(int r, int g, int b, int a) {
        int ar = clamp8(r);
        int ag = clamp8(g);
        int ab = clamp8(b);
        int aa = clamp8(a);
        return new Color((aa << 24) | (ar << 16) | (ag << 8) | ab);
    }

    /** Linear interpolation toward {@code other} by {@code t} (clamped to {@code [0, 1]}). */
    public Color lerp(Color other, float t) {
        Objects.requireNonNull(other, "other");
        float tt = clamp01(t);
        int a = Math.round(lerpChannel(alpha(), other.alpha(), tt));
        int r = Math.round(lerpChannel(red(),   other.red(),   tt));
        int g = Math.round(lerpChannel(green(), other.green(), tt));
        int b = Math.round(lerpChannel(blue(),  other.blue(),  tt));
        return new Color((a << 24) | (r << 16) | (g << 8) | b);
    }

    private static float lerpChannel(int from, int to, float t) {
        return from + (to - from) * t;
    }

    /** Alpha channel ({@code 0..255}). */
    public int alpha() { return (argb >>> 24) & 0xFF; }
    /** Red channel ({@code 0..255}). */
    public int red()   { return (argb >> 16) & 0xFF; }
    /** Green channel ({@code 0..255}). */
    public int green() { return (argb >> 8) & 0xFF; }
    /** Blue channel ({@code 0..255}). */
    public int blue()  { return argb & 0xFF; }

    private static int clamp8(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
package org.lowern1ght.burg.common.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * The draw surface handed to every {@link Widget#draw(DrawContext)} call.
 * A POJO holding the current clip rectangle, the cursor position in
 * GUI-space, and the methods widgets use to render primitives.
 *
 * <p>No {@code net.minecraft} import. The {@link DrawContext} is a plain
 * value object on the bare JVM; the {@code McDrawContext} subclass lives in
 * the {@code neoforge} module and translates every primitive into a
 * Minecraft {@code GuiGraphics} call. The engine never sees Minecraft.
 *
 * <p>Clipping is a stack: {@link #pushClip(Rect)} intersects the new clip
 * with the current one and pushes the result; {@link #popClip()} restores
 * the previous clip. Empty clips short-circuit every draw call so a widget
 * that finds itself entirely outside the visible region does no work.
 *
 * <p>The cursor ({@link #mouseX}, {@link #mouseY}) is in GUI-space relative
 * to the {@link Root} that owns this context — not in screen-space. The
 * adapter computes the offset once when the context is constructed.
 */
public class DrawContext {

    private Rect clip;
    private final Deque<Rect> clipStack = new ArrayDeque<>();
    private final int mouseX;
    private final int mouseY;

    /**
     * Constructs a context with the given full-canvas clip and cursor.
     * Use the {@link #pushClip(Rect)} / {@link #popClip()} pair to scope
     * drawing to a sub-rectangle.
     */
    public DrawContext(int width, int height, int mouseX, int mouseY) {
        this.clip = new Rect(0, 0, Math.max(0, width), Math.max(0, height));
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** The current clip rectangle — readonly. Updated by {@link #pushClip}/{@link #popClip}. */
    public Rect clip() {
        return clip;
    }

    /** Cursor X in GUI-space (relative to the root). */
    public int mouseX() {
        return mouseX;
    }

    /** Cursor Y in GUI-space (relative to the root). */
    public int mouseY() {
        return mouseY;
    }

    /** Cursor as a {@link Point} in GUI-space. */
    public Point mouse() {
        return new Point(mouseX, mouseY);
    }

    /**
     * Pushes a sub-rectangle clip: the new clip is the intersection of the
     * current clip and {@code rect}. Empty rectangles are pushed as-is so
     * a later {@link #popClip()} restores the parent clip correctly.
     */
    public void pushClip(Rect rect) {
        Objects.requireNonNull(rect, "rect");
        clipStack.push(clip);
        clip = clip.intersection(rect);
    }

    /** Pops the most recent clip, restoring the previous one. */
    public void popClip() {
        if (clipStack.isEmpty()) {
            throw new IllegalStateException("DrawContext.popClip: stack underflow");
        }
        clip = clipStack.pop();
    }

    /**
     * Draws a filled rectangle. The default implementation is a no-op —
     * the {@code neoforge} adapter overrides it to call
     * {@code GuiGraphics.fill(...)}. The engine itself never paints; it
     * just forwards. The no-op default keeps the engine unit-testable on
     * a bare JVM without an {@code IMcDrawBackend} seam.
     */
    public void drawRect(int x, int y, int w, int h, Color color) {
        Objects.requireNonNull(color, "color");
        // no-op in the base class; the Minecraft adapter overrides this.
    }

    /**
     * Draws a single line of text at {@code (x, y)} in GUI-space. The base
     * implementation is a no-op; the {@code neoforge} adapter overrides
     * with a real {@code Font} call. Text is a {@link String} literal — the
     * engine never sees Minecraft's {@code Component} (ADR-0022 §"Three
     * rules" — engine is Minecraft-free).
     */
    public void drawText(String text, int x, int y, TextStyle style) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
        // no-op in the base class; the Minecraft adapter overrides this.
    }
}
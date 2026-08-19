package org.lowern1ght.burg.client.ui;

import org.lowern1ght.burg.common.ui.Point;
import org.lowern1ght.burg.common.ui.UiEvent;

/**
 * Translates Minecraft's input callbacks into engine {@link UiEvent}s.
 * Static methods only — no state, no allocations. The screen's
 * {@code mouseX/mouseY} are passed in GUI-space (already adjusted for
 * the screen's {@code leftPos/topPos}); the adapter subtracts the
 * origin so the engine sees a {@code (0, 0)}-rooted coordinate space.
 *
 * <p>Modifiers come from GLFW — bit {@code 1} = shift, bit {@code 2} =
 * ctrl, bit {@code 4} = alt, bit {@code 8} = super. The adapter passes
 * the bit field through verbatim; widgets that care interpret it
 * themselves.
 */
public final class McInputAdapter {

    private McInputAdapter() {
        // static helpers only
    }

    /**
     * Builds a {@link UiEvent.MouseMoved} for the cursor at {@code (guiX, guiY)}.
     */
    public static UiEvent.MouseMoved mouseMoved(int guiX, int guiY) {
        return new UiEvent.MouseMoved(guiX, guiY);
    }

    /**
     * Builds a {@link UiEvent.MouseDown} for a button press at {@code (guiX, guiY)}.
     * {@code button} matches Minecraft's convention: {@code 0} = left,
     * {@code 1} = right, {@code 2} = middle.
     */
    public static UiEvent.MouseDown mouseDown(int guiX, int guiY, int button) {
        return new UiEvent.MouseDown(guiX, guiY, button);
    }

    /**
     * Builds a {@link UiEvent.MouseUp} for a button release at {@code (guiX, guiY)}.
     */
    public static UiEvent.MouseUp mouseUp(int guiX, int guiY, int button) {
        return new UiEvent.MouseUp(guiX, guiY, button);
    }

    /**
     * Builds a {@link UiEvent.KeyDown} from the engine's wire-format.
     * The screen passes the platform key code, scan code, and the GLFW
     * modifiers bit field.
     */
    public static UiEvent.KeyDown keyDown(int keyCode, int scanCode, int modifiers) {
        return new UiEvent.KeyDown(keyCode, scanCode, modifiers);
    }

    /** Builds a {@link UiEvent.KeyUp} from the engine's wire-format. */
    public static UiEvent.KeyUp keyUp(int keyCode, int scanCode, int modifiers) {
        return new UiEvent.KeyUp(keyCode, scanCode, modifiers);
    }

    /** Builds a {@link UiEvent.CharTyped} for a typed UTF-16 code unit. */
    public static UiEvent.CharTyped charTyped(char ch) {
        return new UiEvent.CharTyped(ch);
    }

    /** Builds a {@link UiEvent.Scroll} from the mouse-wheel delta. */
    public static UiEvent.Scroll scroll(double deltaX, double deltaY) {
        return new UiEvent.Scroll(deltaX, deltaY);
    }

    /** Builds a {@link UiEvent.Resize} when the screen's size changes. */
    public static UiEvent.Resize resize(int width, int height) {
        return new UiEvent.Resize(width, height);
    }

    /**
     * Convenience: a fully-formed {@code mouseX} / {@code mouseY} pair
     * already offset against the parent's GUI-space origin. Returns
     * a {@link Point} with the engine's coordinate convention so the
     * caller can drop it into a hit-test.
     */
    public static Point at(int guiX, int guiY) {
        return new Point(guiX, guiY);
    }
}
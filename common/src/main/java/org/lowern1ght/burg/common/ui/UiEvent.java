package org.lowern1ght.burg.common.ui;

import java.util.Objects;

/**
 * A UI event — anything the engine's widgets need to react to. Sealed so
 * every {@code switch} on the root dispatcher is exhaustive; the engine's
 * event vocabulary is the union of cases declared here.
 *
 * <p>No {@code net.minecraft} import. The events are translated by the
 * neoforge adapter from Minecraft's mouse / keyboard / scroll callbacks
 * (see {@code McInputAdapter}). Mouse coordinates are in GUI-space — the
 * adapter normalises screen-pixel offsets to the {@link Root}'s bounds
 * so the engine never sees Minecraft pixel coordinates.
 *
 * <p>The cases split along the three input axes the engine handles today:
 *
 * <ul>
 *   <li><b>Mouse:</b> {@link MouseMoved}, {@link MouseDown}, {@link MouseUp},
 *       {@link Scroll}. Button numbers match the Minecraft convention:
 *       {@code 0} = left, {@code 1} = right, {@code 2} = middle.</li>
 *   <li><b>Keyboard:</b> {@link KeyDown}, {@link KeyUp}, {@link CharTyped}.
 *       Key / scan codes are the platform integers the adapter passes through;
 *       {@code modifiers} is the GLFW-style bit field
 *       ({@code 1 = shift}, {@code 2 = ctrl}, {@code 4 = alt}).</li>
 *   <li><b>Lifecycle:</b> {@link Resize} when the parent's bounds change.</li>
 * </ul>
 */
public sealed interface UiEvent
    permits UiEvent.MouseMoved, UiEvent.MouseDown, UiEvent.MouseUp,
            UiEvent.KeyDown, UiEvent.KeyUp, UiEvent.CharTyped,
            UiEvent.Scroll, UiEvent.Resize {

    /** Mouse cursor moved to {@code (x, y)} in GUI-space. */
    record MouseMoved(int x, int y) implements UiEvent {}

    /** Mouse button {@code button} went down at {@code (x, y)}. */
    record MouseDown(int x, int y, int button) implements UiEvent {}

    /** Mouse button {@code button} went up at {@code (x, y)}. */
    record MouseUp(int x, int y, int button) implements UiEvent {}

    /**
     * Keyboard key {@code keyCode} / {@code scanCode} went down. {@code modifiers}
     * is the GLFW-style bit field; the engine does not interpret it — widgets
     * that care (e.g. ctrl+click) read it themselves.
     */
    record KeyDown(int keyCode, int scanCode, int modifiers) implements UiEvent {}

    /** Keyboard key released. Same encoding as {@link KeyDown}. */
    record KeyUp(int keyCode, int scanCode, int modifiers) implements UiEvent {}

    /** A UTF-16 code unit was typed. The engine never sees raw key codes for typing. */
    record CharTyped(char ch) implements UiEvent {}

    /** A scroll-wheel event. {@code deltaY > 0} means "scrolled up". */
    record Scroll(double deltaX, double deltaY) implements UiEvent {}

    /** The parent's size changed to {@code (w, h)}. */
    record Resize(int w, int h) implements UiEvent {}

    /**
     * Returns {@code true} iff this event targets {@code point} — the cursor
     * is over {@code point} when the event fired. Used by the dispatcher to
     * decide whether to forward a key / scroll event to a focused widget
     * that does not have the cursor over it.
     */
    default boolean isAt(Point point) {
        Objects.requireNonNull(point, "point");
        return switch (this) {
            case MouseMoved(int x, int y) -> x == point.x() && y == point.y();
            case MouseDown(int x, int y, int button) -> x == point.x() && y == point.y();
            case MouseUp(int x, int y, int button) -> x == point.x() && y == point.y();
            case Scroll ignored -> true;       // wheel is positional; the engine never gates on it
            case KeyDown ignored -> true;      // keyboard goes to the focused widget regardless
            case KeyUp ignored -> true;
            case CharTyped ignored -> true;
            case Resize ignored -> false;
        };
    }
}
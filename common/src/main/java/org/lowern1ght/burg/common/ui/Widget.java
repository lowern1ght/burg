package org.lowern1ght.burg.common.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The engine's widget base class. Every UI element — labels, panels,
 * containers, the {@link Root} — extends {@code Widget}. The engine is
 * immediate-mode: a widget draws itself when handed a {@link DrawContext};
 * no retained scene graph, no internal layout solver, no reified widget tree
 * on the Minecraft side.
 *
 * <p>No {@code net.minecraft} import. The whole {@code common.ui} package
 * is deliberately Minecraft-free so the engine can be unit-tested on a
 * bare JVM (ADR-0022).
 *
 * <p>Lifecycle state — the four booleans — is owned by the {@link Root},
 * not by individual widgets. {@link Root#dispatch(UiEvent)} updates them on
 * each event; widgets read them in {@link #draw(DrawContext)} to render
 * hover / focus / disabled tints. The protected {@code onEntered} /
 * {@code onExited} / {@code onFocusGained} / {@code onFocusLost} hooks are
 * the canonical way for a widget to react to a state transition: override
 * them, don't read the booleans from inside an event handler.
 *
 * <p>Bounds are mutable on the widget — a {@link Container} lays out its
 * children each frame. The {@link Rect} value is replaced, not mutated.
 *
 * <p>Visibility and enabled-ness are first-class: an invisible widget
 * skips draw AND hit-test; a disabled widget draws (greyed) but skips
 * hit-test.
 */
public abstract class Widget {

    /** The widget's bounding box in parent space. Set by {@link Container#layout} or the root. */
    protected Rect bounds;

    /** True while the cursor is over this widget. Set by the root on each mouse move. */
    public boolean hovered = false;

    /** True while this widget holds keyboard focus. Set by the root on each key down. */
    public boolean focused = false;

    /** When false, the widget skips draw AND hit-test. */
    public boolean visible = true;

    /** When false, the widget draws (greyed) but skips hit-test. */
    public boolean enabled = true;

    protected Widget(Rect bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    protected Widget() {
        this(Rect.EMPTY);
    }

    /** Returns the widget's current bounding box. */
    public Rect bounds() {
        return bounds;
    }

    /** Replaces the bounding box. Layout calls this on each frame. */
    public void setBounds(Rect bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /**
     * Lays out this widget against the parent's width / height. The default
     * implementation simply sets the bounds; {@link Container} overrides it
     * to position its children.
     */
    public void layout(int width, int height) {
        setBounds(new Rect(0, 0, Math.max(0, width), Math.max(0, height)));
    }

    /** Returns true iff the widget covers {@code point} in its parent's space. */
    public boolean hitTest(Point point) {
        Objects.requireNonNull(point, "point");
        return visible && enabled && !bounds.isEmpty() && bounds.contains(point);
    }

    /**
     * Draws the widget. The default implementation is a no-op; concrete
     * widgets override to push a clip, draw their background, draw children.
     * The {@link DrawContext}'s current clip already reflects the widget's
     * parent's sub-rectangle; this method is responsible for adding its
     * own sub-clip if it wants one.
     */
    public abstract void draw(DrawContext ctx);

    /**
     * Handles an event. Returns {@code true} iff the event was consumed and
     * should not propagate to ancestors. The default implementation returns
     * {@code false}; concrete widgets override to react to clicks, key
     * presses, etc.
     */
    public boolean handle(UiEvent event) {
        Objects.requireNonNull(event, "event");
        return false;
    }

    // ----- state-transition hooks (overridable) -----

    /** Called when {@link #hovered} transitions from {@code false} to {@code true}. */
    protected void onEntered() {}

    /** Called when {@link #hovered} transitions from {@code true} to {@code false}. */
    protected void onExited() {}

    /** Called when {@link #focused} transitions from {@code false} to {@code true}. */
    protected void onFocusGained() {}

    /** Called when {@link #focused} transitions from {@code true} to {@code false}. */
    protected void onFocusLost() {}

    // ----- package-private setters used by the root dispatcher -----

    void setHovered(boolean hovered) {
        if (this.hovered == hovered) return;
        this.hovered = hovered;
        if (hovered) onEntered(); else onExited();
    }

    void setFocused(boolean focused) {
        if (this.focused == focused) return;
        this.focused = focused;
        if (focused) onFocusGained(); else onFocusLost();
    }

    /**
     * Helper that walks a tree of widgets in DFS order, calling
     * {@code visitor#visit} on each. The {@link Root#dispatch} uses this to
     * push / pop hovered and focused state without recursing by hand.
     * Both {@link Container} and {@link Root} contribute their children
     * to the walk.
     */
    static void walk(Widget w, WidgetVisitor visitor) {
        Objects.requireNonNull(w, "w");
        Objects.requireNonNull(visitor, "visitor");
        Deque<Widget> stack = new ArrayDeque<>();
        stack.push(w);
        while (!stack.isEmpty()) {
            Widget current = stack.pop();
            visitor.visit(current);
            List<Widget> kids = childrenOf(current);
            for (int i = kids.size() - 1; i >= 0; i--) {
                stack.push(kids.get(i));
            }
        }
    }

    private static List<Widget> childrenOf(Widget w) {
        if (w instanceof Container container) return container.children();
        if (w instanceof Root root) return root.children();
        return List.of();
    }

    /** One-arg visitor used by {@link #walk}. */
    @FunctionalInterface
    interface WidgetVisitor {
        void visit(Widget widget);
    }
}
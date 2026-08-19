package org.lowern1ght.burg.common.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The top-level widget — every screen has exactly one. The {@link Root}
 * owns the child list, lays children out against the screen bounds, draws
 * them, and dispatches events.
 *
 * <p>No {@code net.minecraft} import. {@link Root} is a bare-JVM widget the
 * adapter can construct inside {@code McDrawContext} without touching the
 * domain types — and it can run the engine's tests on a bare JVM.
 *
 * <p>Hit-test walks children in reverse-add order: the most recently added
 * child gets the event first. The first child whose {@link Widget#hitTest}
 * returns {@code true} wins. Keyboard events go to the focused widget —
 * focus is acquired by a {@link UiEvent.MouseDown} landing on a focusable
 * child (every widget that overrides {@link Widget#handle} is focusable in
 * the abstract; widgets that want a hover-only behaviour simply don't
 * consume the {@link UiEvent.MouseDown}).
 *
 * <p>Layout is a single call: {@link #layout(int, int)} resizes the root
 * and each direct child. Nested containers lay themselves out recursively
 * (see {@link Container#layout}). A screen typically calls
 * {@code root.layout(width, height)} once per resize.
 */
public final class Root extends Widget {

    private final List<Widget> children = new ArrayList<>();

    /** Currently focused widget, or {@code null} when no child has focus. */
    private Widget focused = null;

    public Root() {
        super(Rect.EMPTY);
    }

    /**
     * Lays out the root + direct children. A {@link Container} child gets a
     * recursive layout call with the root's full size; a leaf widget keeps
     * whatever bounds it was given when added (a leaf widget's size is its
     * own concern — a label knows its own text width, a panel knows its
     * own background size). Leaves whose bounds are still {@link Rect#EMPTY}
     * take the root's full size, which is the "auto-fill" default.
     *
     * <p>A typical screen calls {@code layout} once at init and again on each
     * {@link UiEvent.Resize}.
     */
    @Override
    public void layout(int width, int height) {
        setBounds(new Rect(0, 0, Math.max(0, width), Math.max(0, height)));
        for (Widget child : children) {
            if (child instanceof Container containerChild) {
                containerChild.layout(bounds.w(), bounds.h());
            } else if (child.bounds.isEmpty()) {
                child.setBounds(bounds);
            }
        }
    }

    /** Appends a widget to the top-level child list. */
    public void add(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        children.add(widget);
    }

    /** Removes a widget from the top-level child list. */
    public void remove(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        children.remove(widget);
        if (focused == widget) {
            focused.setFocused(false);
            focused = null;
        }
    }

    /** Read-only view of the children. */
    public List<Widget> children() {
        return List.copyOf(children);
    }

    /** Draws every visible child in add-order. */
    @Override
    public void draw(DrawContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        for (Widget child : children) {
            if (!child.visible) continue;
            child.draw(ctx);
        }
    }

    /**
     * Dispatches an event to the focused child (for keyboard / scroll) and
     * to the child under the cursor (for mouse events). Hit-test walks the
     * tree in reverse-add order: the topmost child wins.
     */
    public void dispatch(UiEvent event) {
        Objects.requireNonNull(event, "event");
        // update hovered state on every event with a cursor position
        Point cursor = cursorOf(event);
        if (cursor != null) {
            updateHovered(cursor);
        }

        if (event instanceof UiEvent.MouseDown mouseDown) {
            Widget hit = findHit(new Point(mouseDown.x(), mouseDown.y()));
            if (hit != null) {
                if (focused != null && focused != hit) {
                    focused.setFocused(false);
                }
                focused = hit;
                hit.setFocused(true);
                if (hit.handle(event)) return;
            }
            return;
        }

        if (event instanceof UiEvent.Resize resize) {
            layout(resize.w(), resize.h());
            return;
        }

        // Keyboard / scroll / mouse-up: send to the focused widget if any.
        if (focused != null && focused.handle(event)) return;
    }

    /**
     * Returns the deepest visible / enabled child whose bounds contain
     * {@code point}, or {@code null} if no child covers the point. Walks
     * children in reverse-add order — the topmost wins.
     */
    public Widget findHit(Point point) {
        Objects.requireNonNull(point, "point");
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget candidate = findHitIn(children.get(i), point);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private Widget findHitIn(Widget widget, Point point) {
        if (!widget.visible || !widget.enabled) return null;
        if (!widget.bounds.contains(point)) return null;
        if (widget instanceof Container container) {
            for (int i = container.children().size() - 1; i >= 0; i--) {
                Widget childHit = findHitIn(container.children().get(i), point);
                if (childHit != null) return childHit;
            }
        }
        return widget;
    }

    private void updateHovered(Point cursor) {
        Widget topHit = findHit(cursor);
        Widget.walk(this, w -> w.setHovered(w == topHit));
    }

    private static Point cursorOf(UiEvent event) {
        return switch (event) {
            case UiEvent.MouseMoved(int x, int y) -> new Point(x, y);
            case UiEvent.MouseDown(int x, int y, int b) -> new Point(x, y);
            case UiEvent.MouseUp(int x, int y, int b) -> new Point(x, y);
            case UiEvent.Scroll ignored -> null;
            default -> null;
        };
    }
}
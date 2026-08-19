package org.lowern1ght.burg.common.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A widget that owns a list of children + a layout direction.
 *
 * <p>Three layout directions:
 *
 * <ul>
 *   <li>{@link Direction#HORIZONTAL} — pack children left-to-right with
 *       {@link #spacing} pixels between them. Each child takes its
 *       preferred size; leftover space is dropped.</li>
 *   <li>{@link Direction#VERTICAL} — pack top-to-bottom, same spacing
 *       rule.</li>
 *   <li>{@link Direction#OVERLAY} — every child occupies the full container
 *       bounds; later children draw on top. Useful for stacked panels,
 *       hover overlays, etc.</li>
 * </ul>
 *
 * <p>Containers do not implement a preferred-size protocol — the engine
 * is intentionally tiny. A child that wants its own size should override
 * its {@link Widget#bounds} after {@link #layout(int, int)} runs, or be a
 * leaf widget whose {@link Widget#bounds} is set explicitly before
 * {@link Root#layout(int, int)}.
 *
 * <p>Hit-test recurses: a container reports itself as the hit only when no
 * child absorbs the point. Events flow down the tree through
 * {@link Root#dispatch}; containers themselves don't consume events unless
 * an explicit override says so.
 */
public class Container extends Widget {

    /** Layout direction. */
    public enum Direction {
        HORIZONTAL, VERTICAL, OVERLAY
    }

    private final List<Widget> children = new ArrayList<>();
    private final Direction direction;
    private int spacing = 0;

    public Container(Rect bounds, Direction direction) {
        super(bounds);
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public Container(Direction direction) {
        this(Rect.EMPTY, direction);
    }

    /** Returns the layout direction. */
    public Direction direction() {
        return direction;
    }

    /** Returns the pixel gap between consecutive children. */
    public int spacing() {
        return spacing;
    }

    /** Sets the pixel gap between consecutive children. */
    public void setSpacing(int spacing) {
        this.spacing = Math.max(0, spacing);
    }

    /** Appends a child. */
    public void add(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        children.add(widget);
    }

    /** Removes a child. */
    public void remove(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        children.remove(widget);
    }

    /** Read-only view of the children. */
    public List<Widget> children() {
        return List.copyOf(children);
    }

    /**
     * Lays out children along the configured direction with the configured
     * spacing. {@code width} / {@code height} are the container's own
     * dimensions, taken from the parent's call to
     * {@link Root#layout(int, int)}.
     */
    public void layout(int width, int height) {
        setBounds(new Rect(0, 0, Math.max(0, width), Math.max(0, height)));
        switch (direction) {
            case HORIZONTAL -> layoutHorizontal();
            case VERTICAL -> layoutVertical();
            case OVERLAY -> layoutOverlay();
        }
    }

    private void layoutHorizontal() {
        int cursorX = 0;
        int maxH = 0;
        for (Widget child : children) {
            int cw = Math.max(0, child.bounds().w());
            int ch = Math.max(0, child.bounds().h());
            child.setBounds(new Rect(cursorX, 0, cw, ch));
            cursorX += cw + spacing;
            if (ch > maxH) maxH = ch;
        }
        // Container grows to fit its children. The parent will replace these
        // bounds with its own layout call when the parent is HORIZONTAL itself;
        // for OVERLAY parents the container's bounds are the full parent.
    }

    private void layoutVertical() {
        int cursorY = 0;
        int maxW = 0;
        for (Widget child : children) {
            int cw = Math.max(0, child.bounds().w());
            int ch = Math.max(0, child.bounds().h());
            child.setBounds(new Rect(0, cursorY, cw, ch));
            cursorY += ch + spacing;
            if (cw > maxW) maxW = cw;
        }
    }

    private void layoutOverlay() {
        Rect parentBounds = bounds;
        for (Widget child : children) {
            child.setBounds(parentBounds);
        }
    }

    /** Draws each visible child in add-order; the container itself draws nothing. */
    @Override
    public void draw(DrawContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        for (Widget child : children) {
            if (!child.visible) continue;
            child.draw(ctx);
        }
    }
}
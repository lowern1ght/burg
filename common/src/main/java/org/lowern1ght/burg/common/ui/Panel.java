package org.lowern1ght.burg.common.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A widget that draws a filled rectangle (the "panel" background) and
 * optionally a 1-pixel border, then forwards drawing to its children.
 * Used as a background container behind text rows, hover overlays, and
 * the focused outline of a focusable widget.
 *
 * <p>No {@code net.minecraft} import. {@link Panel} is a POJO widget the
 * engine tests can construct on a bare JVM.
 *
 * <p>The panel itself does not consume events; children handle their own.
 * A panel pushed onto the tree as a hover highlight does not interfere
 * with hit-testing on the children because {@link Panel} inherits the
 * default {@link Widget#handle} that returns {@code false}.
 *
 * <p>Children are positioned in the same way a {@link Container} with
 * {@link Container.Direction#OVERLAY} positions them — every child takes
 * the full panel bounds. A panel with both a background {@link Label}
 * and a foreground widget is the common composition; the panel's layout
 * is "overlay all children over the panel rectangle".
 */
public class Panel extends Widget {

    private Color background;
    private Color border;
    private final List<Widget> children = new ArrayList<>();

    public Panel(Rect bounds, Color background, Color border) {
        super(bounds);
        this.background = Objects.requireNonNull(background, "background");
        this.border = Objects.requireNonNull(border, "border");
    }

    public Panel(Rect bounds, Color background) {
        this(bounds, background, Color.TRANSPARENT);
    }

    /** The fill colour. May be {@link Color#TRANSPARENT} for "no background". */
    public Color background() {
        return background;
    }

    /** The border colour. May be {@link Color#TRANSPARENT} for "no border". */
    public Color border() {
        return border;
    }

    /** Replaces the fill colour — used by hover / focus overlays. */
    public void setBackground(Color background) {
        this.background = Objects.requireNonNull(background, "background");
    }

    /** Replaces the border colour. */
    public void setBorder(Color border) {
        this.border = Objects.requireNonNull(border, "border");
    }

    /** Appends a child. The child takes the full panel bounds when drawn. */
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

    /** Lays every child over the panel's full bounds. */
    public void layout() {
        Rect b = bounds;
        for (Widget child : children) {
            child.setBounds(b);
        }
    }

    /** Draws the panel rectangle, the optional border, and every visible child. */
    @Override
    public void draw(DrawContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Rect b = bounds;
        if (b.isEmpty()) return;
        if (background.alpha() > 0) {
            ctx.drawRect(b.x(), b.y(), b.w(), b.h(), background);
        }
        if (border.alpha() > 0) {
            ctx.drawRect(b.x(), b.y(), b.w(), 1, border);
            ctx.drawRect(b.x(), b.y() + b.h() - 1, b.w(), 1, border);
            ctx.drawRect(b.x(), b.y(), 1, b.h(), border);
            ctx.drawRect(b.x() + b.w() - 1, b.y(), 1, b.h(), border);
        }
        for (Widget child : children) {
            if (!child.visible) continue;
            child.draw(ctx);
        }
    }
}
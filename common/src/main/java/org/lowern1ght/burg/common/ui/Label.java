package org.lowern1ght.burg.common.ui;

import java.util.Objects;

/**
 * A read-only text widget. Holds a {@link String} + a {@link TextStyle}
 * and draws the string at {@code (bounds.x, bounds.y)} when drawn.
 *
 * <p>No {@code net.minecraft} import. {@link Label} is a value-typed
 * widget the engine tests can construct on a bare JVM; the adapter
 * resolves the String to a Minecraft {@code Font} glyph run at draw time.
 *
 * <p>A {@code Label} does not consume events — {@link #handle(UiEvent)}
 * always returns {@code false}. It is hover-aware, however: the engine's
 * hit-test will set {@link Widget#hovered} when the cursor is over the
 * label, but the label's own draw routine ignores the hover state. Hover
 * is a visual affordance of {@link Panel} / {@code Container}, not of the
 * label itself.
 */
public class Label extends Widget {

    private String text;
    private TextStyle style;

    public Label(Rect bounds, String text, TextStyle style) {
        super(bounds);
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
    }

    public Label(Rect bounds, String text) {
        this(bounds, text, TextStyle.defaults());
    }

    /** Static factory used by callers that want the engine's neutral style. */
    public static Label withDefaultStyle(Rect bounds, String text) {
        return new Label(bounds, text, TextStyle.defaults());
    }

    /** Returns the rendered string. */
    public String text() {
        return text;
    }

    /** Replaces the rendered string. */
    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /** Returns the current text style. */
    public TextStyle style() {
        return style;
    }

    /** Replaces the text style. */
    public void setStyle(TextStyle style) {
        this.style = Objects.requireNonNull(style, "style");
    }

    /** Draws the label at its top-left. Empty / whitespace-only strings draw nothing. */
    @Override
    public void draw(DrawContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if (text.isEmpty()) return;
        Rect b = bounds;
        if (b.isEmpty()) return;
        ctx.drawText(text, b.x(), b.y(), style);
    }
}
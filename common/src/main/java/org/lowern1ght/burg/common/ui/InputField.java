package org.lowern1ght.burg.common.ui;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A bare-JVM focusable text input widget. The first interactive widget the
 * engine ships — typed digits append to the buffer in order, {@code Backspace}
 * removes the trailing char, and {@code Enter} parses the buffer to a
 * positive {@code int} and hands it to {@link #onSubmit}.
 *
 * <p>No {@code net.m} import. {@link InputField} is a POJO value type the
 * UI tests can construct and drive on a bare JVM; the {@code neoforge}
 * adapter forwards the Minecraft key / char callbacks into the engine's
 * {@link UiEvent} stream (see {@code McInputAdapter}) and the widget reads
 * the same {@link UiEvent.KeyDown} / {@link UiEvent.CharTyped} records
 * that the tests construct by hand.
 *
 * <p>The widget tracks focus itself rather than relying on the engine's
 * {@link Root} to do it. {@link #handle(UiEvent)} consumes a
 * {@link UiEvent.MouseDown} when the widget is hovered (set
 * {@code focused = true}) and clears focus when the click landed
 * outside — the canonical pattern from the spec. The engine's
 * {@link Root#dispatch(UiEvent)} already moves focus when a different
 * child absorbs the click, but does not clear focus on an empty-space
 * click; the widget handles that case in its own {@code handle} so the
 * field releases focus when the user clicks elsewhere on the screen.
 *
 * <p>Key codes are the GLFW integers that Minecraft's LWJGL adapter
 * passes through verbatim (see {@code McInputAdapter}): {@code 257} for
 * {@code Enter} and {@code 259} for {@code Backspace}. {@link
 * UiEvent.CharTyped} carries the typed UTF-16 code unit — the widget
 * appends {@code '0'..'9'} and silently ignores every other char
 * (letters, punctuation, whitespace, control codes).
 *
 * <p>Style is engine-neutral. The {@link #idleStyle} draws when the
 * field is unfocused; the {@link #focusedStyle} takes over the moment
 * focus arrives. The cursor bar is a single 2-pixel-tall rectangle at
 * the end of the buffer text, drawn only when {@code focused == true}.
 * Empty bounds short-circuit the draw routine so a field that has not
 * been laid out yet draws nothing — the same pattern {@link Label} uses.
 */
public final class InputField extends Widget {

    /** The GLFW {@code Enter} key code, passed through by the LWJGL adapter. */
    public static final int ENTER_KEY = 257;

    /** The GLFW {@code Backspace} key code, passed through by the LWJGL adapter. */
    public static final int BACKSPACE_KEY = 259;

    /** Default ceiling on the buffer length — wide enough for a Minecraft stack (64). */
    public static final int DEFAULT_MAX_LENGTH = 16;

    /** Placeholder shown when the buffer is empty. */
    public static final String PLACEHOLDER = "[amount]";

    /** Height of the cursor bar in pixels. */
    static final int CURSOR_HEIGHT = 2;

    /**
     * Package-private so the unit test can inject non-digit content
     * ({@code "-"}, {@code "1a"}) and exercise the submission path. The
     * production API is {@link #value()} / {@link #handle(UiEvent)} —
     * {@link UiEvent.CharTyped} only appends digits, so the buffer
     * cannot contain non-digit characters through normal use.
     */
    final StringBuilder buffer = new StringBuilder();
    private String label;
    private final TextStyle idleStyle;
    private final TextStyle focusedStyle;
    private final int maxLength;

    /**
     * Handler invoked when the user presses {@link #ENTER_KEY} and the
     * buffer parses to a positive {@code int}. The argument is the parsed
     * quantity; the screen wires this to its packet dispatch. {@code null}
     * is allowed — Enter becomes a no-op rather than throwing.
     */
    public Consumer<Integer> onSubmit;

    public InputField(
        Rect bounds,
        String label,
        int maxLength,
        TextStyle idleStyle,
        TextStyle focusedStyle
    ) {
        super(bounds);
        this.label = Objects.requireNonNull(label, "label");
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive, got " + maxLength);
        }
        this.maxLength = maxLength;
        this.idleStyle = Objects.requireNonNull(idleStyle, "idleStyle");
        this.focusedStyle = Objects.requireNonNull(focusedStyle, "focusedStyle");
    }

    /**
     * Convenience constructor — bounds at {@code (0, 0, width, height)},
     * default {@code maxLength = 16}, engine-neutral styles
     * (transparent fill + black text). Used by the screen's layout —
     * a typical call site wants a known footprint and the engine
     * defaults; the lower-level constructor exists for tests.
     */
    public InputField(String label, int width, int height) {
        this(
            new Rect(0, 0, width, height),
            label,
            DEFAULT_MAX_LENGTH,
            TextStyle.defaults(),
            TextStyle.defaults()
        );
    }

    /** The current text the user has typed. Empty string when nothing has been entered. */
    public String value() {
        return buffer.toString();
    }

    /** Empties the buffer. Does not touch focus — the user can keep typing after a clear. */
    public void clear() {
        buffer.setLength(0);
    }

    /** Returns the label rendered above the input row. */
    public String label() {
        return label;
    }

    /** Replaces the label rendered above the input row. */
    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /** Returns the ceiling on the buffer length. */
    public int maxLength() {
        return maxLength;
    }

    /** The style used when {@link #focused} is {@code false}. */
    public TextStyle idleStyle() {
        return idleStyle;
    }

    /** The style used when {@link #focused} is {@code true}. */
    public TextStyle focusedStyle() {
        return focusedStyle;
    }

    /**
     * Asks the engine to focus this field. The widget toggles its own
     * {@link #focused} flag and fires {@code onFocusGained}; the caller
     * (the {@code neoforge} adapter or the screen) is responsible for
     * telling the engine's {@link Root} to forward subsequent keyboard
     * events here. Callers that go through the engine's
     * {@link Root#dispatch(UiEvent)} dispatch path get this for free
     * (a {@link UiEvent.MouseDown} lands on the field, the engine
     * focuses it). This method exists so a screen can move focus
     * programmatically — e.g. after a click on a gap row.
     */
    public void requestFocus() {
        setFocused(true);
    }

    @Override
    public void draw(DrawContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Rect b = bounds;
        if (b.isEmpty()) return;

        TextStyle bodyStyle = focused ? focusedStyle : idleStyle;
        ctx.drawText(label, b.x(), b.y(), bodyStyle);

        String displayed = buffer.length() == 0 ? PLACEHOLDER : buffer.toString();
        ctx.drawText(displayed, b.x(), b.y() + ROW_HEIGHT, bodyStyle);

        if (focused && buffer.length() > 0) {
            int cursorX = b.x() + CHAR_WIDTH * buffer.length();
            int cursorY = b.y() + ROW_HEIGHT;
            ctx.drawRect(cursorX, cursorY, 1, CURSOR_HEIGHT, bodyStyle.text());
        }
    }

    @Override
    public boolean handle(UiEvent event) {
        Objects.requireNonNull(event, "event");

        if (event instanceof UiEvent.MouseDown) {
            setFocused(hovered);
            return true;
        }

        if (!focused) {
            return false;
        }

        if (event instanceof UiEvent.KeyDown keyDown) {
            if (keyDown.keyCode() == BACKSPACE_KEY) {
                if (buffer.length() > 0) {
                    buffer.deleteCharAt(buffer.length() - 1);
                }
                return true;
            }
            if (keyDown.keyCode() == ENTER_KEY) {
                submitIfValid();
                return true;
            }
            return false;
        }

        if (event instanceof UiEvent.CharTyped charTyped) {
            char ch = charTyped.ch();
            if (ch >= '0' && ch <= '9' && buffer.length() < maxLength) {
                buffer.append(ch);
                return true;
            }
            return false;
        }

        return false;
    }

    private void submitIfValid() {
        if (buffer.length() == 0) return;
        int parsed;
        try {
            parsed = Integer.parseInt(buffer.toString());
        } catch (NumberFormatException notAnInt) {
            return;
        }
        if (parsed <= 0) return;
        if (onSubmit == null) return;
        onSubmit.accept(parsed);
    }

    /** Width of one glyph in engine pixels — the {@code label} / buffer share it. */
    static final int CHAR_WIDTH = 6;

    /** Pixel offset from the top of the bounds to the body row. */
    static final int ROW_HEIGHT = 12;
}
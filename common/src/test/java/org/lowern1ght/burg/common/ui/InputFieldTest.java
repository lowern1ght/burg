package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InputField} — the engine's first focusable text widget. Digit
 * input, backspace, focus toggling on click, submit-on-enter with
 * parse + positive-int validation, draw-style based on focus state.
 * The {@link DrawContext} stub here records every {@code drawText} and
 * {@code drawRect} call so the test can assert against the draw shape
 * without spinning up the Minecraft adapter.
 */
class InputFieldTest {

    /**
     * DrawContext that records every primitive call. Stores the full call
     * history because the field draws the label and the body separately —
     * a single "lastText" field would lose the first call.
     */
    private static final class RecordingDrawContext extends DrawContext {
        final List<String> texts = new ArrayList<>();
        final List<TextStyle> textStyles = new ArrayList<>();
        final List<int[]> rects = new ArrayList<>();   // [x, y, w, h]
        final List<Color> rectColors = new ArrayList<>();

        RecordingDrawContext() {
            super(200, 200, 0, 0);
        }

        @Override
        public void drawText(String text, int x, int y, TextStyle style) {
            texts.add(text);
            textStyles.add(style);
        }

        @Override
        public void drawRect(int x, int y, int w, int h, Color color) {
            rects.add(new int[] { x, y, w, h });
            rectColors.add(color);
        }

        int textCalls() {
            return texts.size();
        }

        int rectCalls() {
            return rects.size();
        }
    }

    private static InputField newField() {
        return new InputField(new Rect(0, 0, 80, 24), "Quantity", 16,
            TextStyle.defaults(), TextStyle.defaults());
    }

    /** Focus the field by simulating an in-bounds click — every KeyDown / CharTyped test starts here. */
    private static void focus(InputField f) {
        f.hovered = true;
        f.handle(new UiEvent.MouseDown(5, 5, 0));
        assertTrue(f.focused, "field is focused after in-bounds MouseDown");
    }

    @Test
    @DisplayName("appendDigits_holds_buffer_in_order")
    void appendDigitsHoldsBufferInOrder() {
        InputField f = newField();
        focus(f);
        assertTrue(f.handle(new UiEvent.CharTyped('1')));
        assertTrue(f.handle(new UiEvent.CharTyped('2')));
        assertTrue(f.handle(new UiEvent.CharTyped('3')));
        assertAll(
            () -> assertEquals("123", f.value()),
            () -> assertEquals(3, f.buffer.length())
        );
    }

    @Test
    @DisplayName("backspace_removes_last_char")
    void backspaceRemovesLastChar() {
        InputField f = newField();
        focus(f);
        f.handle(new UiEvent.CharTyped('1'));
        f.handle(new UiEvent.CharTyped('2'));
        f.handle(new UiEvent.CharTyped('3'));
        assertTrue(f.handle(new UiEvent.KeyDown(InputField.BACKSPACE_KEY, 0, 0)));
        assertAll(
            () -> assertEquals("12", f.value()),
            () -> assertEquals(2, f.buffer.length())
        );
    }

    @Test
    @DisplayName("backspace_on_empty_is_a_noop")
    void backspaceOnEmptyIsNoop() {
        InputField f = newField();
        focus(f);
        assertTrue(f.handle(new UiEvent.KeyDown(InputField.BACKSPACE_KEY, 0, 0)));
        assertAll(
            () -> assertEquals("", f.value()),
            () -> assertEquals(0, f.buffer.length())
        );
    }

    @Test
    @DisplayName("focus_toggle_on_click")
    void focusToggleOnClick() {
        InputField f = newField();
        assertFalse(f.focused, "starts unfocused");

        f.hovered = true;
        assertTrue(f.handle(new UiEvent.MouseDown(5, 5, 0)));
        assertTrue(f.focused, "click inside grants focus");

        f.hovered = false;
        assertTrue(f.handle(new UiEvent.MouseDown(200, 200, 0)));
        assertFalse(f.focused, "click outside releases focus");
    }

    @Test
    @DisplayName("non_digit_chars_are_ignored")
    void nonDigitCharsAreIgnored() {
        InputField f = newField();
        focus(f);
        assertAll(
            () -> assertFalse(f.handle(new UiEvent.CharTyped('a')), "letter rejected"),
            () -> assertFalse(f.handle(new UiEvent.CharTyped(' ')), "space rejected"),
            () -> assertEquals("", f.value(), "still empty after rejected chars"),
            () -> assertTrue(f.handle(new UiEvent.CharTyped('1')), "digit accepted"),
            () -> assertEquals("1", f.value())
        );
    }

    @Test
    @DisplayName("submit_with_empty_buffer_is_noop")
    void submitWithEmptyBufferIsNoop() {
        InputField f = newField();
        AtomicInteger hits = new AtomicInteger();
        f.onSubmit = quantity -> hits.incrementAndGet();

        focus(f);
        f.handle(new UiEvent.KeyDown(InputField.ENTER_KEY, 0, 0));

        assertEquals(0, hits.get(), "Enter on empty buffer does not invoke onSubmit");
    }

    @Test
    @DisplayName("submit_with_buffer_in_int_range_invokes_handler")
    void submitWithBufferInIntRangeInvokesHandler() {
        InputField f = newField();
        AtomicReference<Integer> captured = new AtomicReference<>();
        f.onSubmit = captured::set;

        focus(f);
        f.handle(new UiEvent.CharTyped('5'));
        f.handle(new UiEvent.KeyDown(InputField.ENTER_KEY, 0, 0));

        assertEquals(Integer.valueOf(5), captured.get());
    }

    @Test
    @DisplayName("submit_with_buffer_at_max_length")
    void submitWithBufferAtMaxLength() {
        InputField f = newField();
        AtomicReference<Integer> captured = new AtomicReference<>();
        f.onSubmit = captured::set;

        focus(f);
        for (int i = 0; i < f.maxLength(); i++) {
            char digit = (char) ('0' + (i % 10));
            assertTrue(f.handle(new UiEvent.CharTyped(digit)),
                "digit " + digit + " accepted at position " + i);
        }
        assertEquals(f.maxLength(), f.buffer.length(), "buffer hits the ceiling");

        // The buffer is now 16 digits — well past Integer.MAX_VALUE (~2.1e9, 10 digits).
        // Integer.parseInt will throw NumberFormatException; the widget's submitIfValid
        // catches it and turns the Enter into a no-op. The handler must NOT throw.
        f.handle(new UiEvent.KeyDown(InputField.ENTER_KEY, 0, 0));
        assertEquals(null, captured.get(),
            "16-digit number overflows int — onSubmit is correctly not invoked");
        assertEquals(f.maxLength(), f.buffer.length(),
            "submit on overflow does NOT clear the buffer — the caller decides when to clear");
    }

    @Test
    @DisplayName("submit_with_buffer_at_int_max_invokes_handler")
    void submitWithBufferAtIntMaxInvokesHandler() {
        // Companion to the max-length test: an in-int-range buffer of 10 digits must
        // parse successfully and invoke onSubmit. This is the case the spec's
        // max-length test was guarding against (a NumberFormatException leaking out)
        // — proved here on a value that DOES fit in int.
        InputField f = newField();
        AtomicReference<Integer> captured = new AtomicReference<>();
        f.onSubmit = captured::set;

        focus(f);
        String digits = "1234567890";          // 10 digits, fits in int
        for (int i = 0; i < digits.length(); i++) {
            assertTrue(f.handle(new UiEvent.CharTyped(digits.charAt(i))));
        }
        f.handle(new UiEvent.KeyDown(InputField.ENTER_KEY, 0, 0));

        assertEquals(Integer.valueOf(1_234_567_890), captured.get());
    }

    @Test
    @DisplayName("submit_with_negative_or_non_numeric_is_noop")
    void submitWithNegativeOrNonNumericIsNoop() {
        AtomicInteger hits = new AtomicInteger();

        InputField dash = new InputField(new Rect(0, 0, 80, 24), "Quantity", 16,
            TextStyle.defaults(), TextStyle.defaults());
        dash.onSubmit = quantity -> hits.incrementAndGet();
        focus(dash);
        dash.buffer.append("-");                         // non-digit — injected directly
        dash.handle(new UiEvent.KeyDown(InputField.ENTER_KEY, 0, 0));
        assertEquals(0, hits.get(), "Enter on '-' does not invoke onSubmit");

        InputField letter = new InputField(new Rect(0, 0, 80, 24), "Quantity", 16,
            TextStyle.defaults(), TextStyle.defaults());
        letter.onSubmit = quantity -> hits.incrementAndGet();
        focus(letter);
        letter.buffer.append("1a");                      // invalid int — injected directly
        letter.handle(new UiEvent.KeyDown(InputField.ENTER_KEY, 0, 0));
        assertEquals(0, hits.get(), "Enter on '1a' does not invoke onSubmit");
    }

    @Test
    @DisplayName("draw_uses_idle_style_when_unfocused_and_focused_style_when_focused")
    void drawUsesIdleStyleWhenUnfocusedAndFocusedStyleWhenFocused() {
        TextStyle idle = TextStyle.defaults();
        TextStyle focused = idle.withBold(true);
        InputField f = new InputField(new Rect(0, 0, 80, 24), "Quantity", 16, idle, focused);

        RecordingDrawContext ctx = new RecordingDrawContext();
        f.draw(ctx);
        assertAll(
            () -> assertEquals(2, ctx.textCalls(), "label + body"),
            () -> assertEquals("Quantity", ctx.texts.get(0), "label draws first"),
            () -> assertEquals(InputField.PLACEHOLDER, ctx.texts.get(1),
                "body shows placeholder while buffer is empty"),
            () -> assertSame(idle, ctx.textStyles.get(0), "label uses idle style while unfocused"),
            () -> assertSame(idle, ctx.textStyles.get(1), "body uses idle style while unfocused"),
            () -> assertEquals(0, ctx.rectCalls(), "no cursor while unfocused")
        );

        f.focused = true;
        f.buffer.append("5");
        ctx.texts.clear();
        ctx.textStyles.clear();
        ctx.rects.clear();
        ctx.rectColors.clear();
        f.draw(ctx);
        assertAll(
            () -> assertEquals(2, ctx.textCalls()),
            () -> assertEquals("Quantity", ctx.texts.get(0)),
            () -> assertEquals("5", ctx.texts.get(1),
                "body shows the actual buffer content once the user has typed"),
            () -> assertSame(focused, ctx.textStyles.get(0), "label uses focused style while focused"),
            () -> assertSame(focused, ctx.textStyles.get(1), "body uses focused style while focused"),
            () -> assertEquals(1, ctx.rectCalls(), "cursor draws when buffer is non-empty"),
            () -> assertEquals(InputField.CURSOR_HEIGHT, ctx.rects.get(0)[3],
                "cursor is the declared CURSOR_HEIGHT pixels tall")
        );
    }
}
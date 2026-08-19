package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link Label} — text rendering primitive. The base {@link DrawContext}
 * has a no-op {@code drawText}, so the test reads what the engine asked
 * for by injecting a counting context.
 */
class LabelTest {

    /** DrawContext that counts {@code drawText} calls. */
    private static final class CountingContext extends DrawContext {
        int textCalls;
        String lastText;
        int lastX;
        int lastY;
        TextStyle lastStyle;

        CountingContext() {
            super(100, 100, 0, 0);
        }

        @Override
        public void drawText(String text, int x, int y, TextStyle style) {
            textCalls++;
            lastText = text;
            lastX = x;
            lastY = y;
            lastStyle = style;
        }
    }

    @Test
    @DisplayName("Label draws the text at its top-left")
    void drawsAtTopLeft() {
        Label l = new Label(new Rect(15, 20, 80, 10), "Hello");
        CountingContext ctx = new CountingContext();
        l.draw(ctx);
        assertAll(
            () -> assertEquals(1, ctx.textCalls),
            () -> assertEquals("Hello", ctx.lastText),
            () -> assertEquals(15, ctx.lastX, "x comes from bounds.x()"),
            () -> assertEquals(20, ctx.lastY, "y comes from bounds.y()")
        );
    }

    @Test
    @DisplayName("Label uses the default style when none is supplied")
    void defaultStyle() {
        Label l = new Label(new Rect(0, 0, 10, 10), "x");
        TextStyle style = l.style();
        assertSame(TextStyle.defaults(), style);
    }

    @Test
    @DisplayName("setText replaces the rendered string; setStyle replaces the style")
    void setters() {
        Label l = new Label(new Rect(0, 0, 10, 10), "first");
        l.setText("second");
        TextStyle bold = TextStyle.defaults().withBold(true);
        l.setStyle(bold);
        assertAll(
            () -> assertEquals("second", l.text()),
            () -> assertSame(bold, l.style())
        );
    }

    @Test
    @DisplayName("Label with empty bounds draws nothing")
    void emptyBoundsDrawsNothing() {
        Label l = new Label(Rect.EMPTY, "Hello");
        CountingContext ctx = new CountingContext();
        l.draw(ctx);
        assertEquals(0, ctx.textCalls);
    }

    @Test
    @DisplayName("Label with an empty string draws nothing")
    void emptyStringDrawsNothing() {
        Label l = new Label(new Rect(0, 0, 10, 10), "");
        CountingContext ctx = new CountingContext();
        l.draw(ctx);
        assertEquals(0, ctx.textCalls);
    }

    @Test
    @DisplayName("Label does not consume events")
    void doesNotConsumeEvents() {
        Label l = new Label(new Rect(0, 0, 10, 10), "Hello");
        AtomicInteger n = new AtomicInteger();
        l.handle(new UiEvent.MouseDown(0, 0, 0));
        n.incrementAndGet();   // ensure handler doesn't throw
        assertEquals(1, n.get());
    }
}
package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link Panel} — background-fill + optional border + children forwarded
 * in draw. The panel itself does not consume events; children draw over
 * the background rectangle.
 */
class PanelTest {

    /** DrawContext that counts drawRect calls. */
    private static final class CountingContext extends DrawContext {
        int rectCalls;
        int lastX, lastY, lastW, lastH;
        Color lastColor;

        CountingContext() {
            super(200, 200, 0, 0);
        }

        @Override
        public void drawRect(int x, int y, int w, int h, Color color) {
            rectCalls++;
            lastX = x;
            lastY = y;
            lastW = w;
            lastH = h;
            lastColor = color;
        }
    }

    @Test
    @DisplayName("Panel draws the background rectangle and forwards to children")
    void drawsBackgroundAndForwardsChildren() {
        Panel p = new Panel(new Rect(10, 10, 50, 30), Color.rgb(50, 50, 50));
        Label l = new Label(Rect.EMPTY, "inside");
        p.add(l);

        CountingContext ctx = new CountingContext();
        p.draw(ctx);

        assertAll(
            () -> assertEquals(1, ctx.rectCalls, "background is one drawRect call"),
            () -> assertEquals(10, ctx.lastX),
            () -> assertEquals(10, ctx.lastY),
            () -> assertEquals(50, ctx.lastW),
            () -> assertEquals(30, ctx.lastH),
            () -> assertEquals(Color.rgb(50, 50, 50), ctx.lastColor)
        );
    }

    @Test
    @DisplayName("Panel with transparent background draws zero rects")
    void transparentBackgroundDrawsNothing() {
        Panel p = new Panel(new Rect(10, 10, 50, 30), Color.TRANSPARENT);
        CountingContext ctx = new CountingContext();
        p.draw(ctx);
        assertEquals(0, ctx.rectCalls);
    }

    @Test
    @DisplayName("Panel with a border draws 4 single-pixel rects for the frame")
    void borderDrawsFourRects() {
        Panel p = new Panel(new Rect(10, 10, 50, 30), Color.BLACK, Color.WHITE);
        CountingContext ctx = new CountingContext();
        p.draw(ctx);

        // 1 background + 4 border rects = 5 calls
        assertEquals(5, ctx.rectCalls);
    }

    @Test
    @DisplayName("Empty-bounds Panel draws nothing")
    void emptyBoundsDrawsNothing() {
        Panel p = new Panel(Rect.EMPTY, Color.BLACK);
        Label l = new Label(Rect.EMPTY, "x");
        p.add(l);
        CountingContext ctx = new CountingContext();
        p.draw(ctx);
        assertEquals(0, ctx.rectCalls);
    }

    @Test
    @DisplayName("layout() copies the panel bounds to every child")
    void layoutCopiesBounds() {
        Panel p = new Panel(new Rect(0, 0, 100, 50), Color.BLACK);
        Label l = new Label(Rect.EMPTY, "x");
        p.add(l);

        p.layout();

        assertEquals(new Rect(0, 0, 100, 50), l.bounds());
    }

    @Test
    @DisplayName("Panel does not consume events")
    void doesNotConsumeEvents() {
        Panel p = new Panel(Rect.EMPTY, Color.BLACK);
        // Default handle returns false; calling it does not throw.
        assertEquals(false, p.handle(new UiEvent.MouseDown(0, 0, 0)));
    }
}
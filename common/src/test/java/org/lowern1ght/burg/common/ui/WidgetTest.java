package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract widget base class — hit-test propagation, state-transition
 * hooks, default {@code handle} returns false. The engine has no other
 * shape; this is what every leaf and container verifies its contract
 * against.
 */
class WidgetTest {

    /** Concrete test widget that records hook calls. */
    private static final class HookWidget extends Widget {
        int enteredCount;
        int exitedCount;
        int focusGainedCount;
        int focusLostCount;

        HookWidget(Rect bounds) {
            super(bounds);
        }

        @Override
        public void draw(DrawContext ctx) {
            // no-op
        }

        @Override
        protected void onEntered() {
            enteredCount++;
        }

        @Override
        protected void onExited() {
            exitedCount++;
        }

        @Override
        protected void onFocusGained() {
            focusGainedCount++;
        }

        @Override
        protected void onFocusLost() {
            focusLostCount++;
        }
    }

    /** A widget that consumes every event. */
    private static final class GreedyWidget extends Widget {
        int consumeCount;

        GreedyWidget(Rect bounds) {
            super(bounds);
        }

        @Override
        public void draw(DrawContext ctx) {
        }

        @Override
        public boolean handle(UiEvent event) {
            consumeCount++;
            return true;
        }
    }

    @Test
    @DisplayName("default handle returns false")
    void defaultHandleReturnsFalse() {
        Widget w = new HookWidget(new Rect(0, 0, 10, 10));
        assertFalse(w.handle(new UiEvent.MouseDown(5, 5, 0)));
        assertFalse(w.handle(new UiEvent.KeyDown(0, 0, 0)));
    }

    @Test
    @DisplayName("hit-test respects visible + enabled + bounds")
    void hitTestRespectsState() {
        Widget w = new HookWidget(new Rect(10, 10, 20, 20));
        assertAll(
            () -> assertTrue(w.hitTest(new Point(15, 15)), "inside bounds is a hit"),
            () -> assertFalse(w.hitTest(new Point(5, 5)), "outside bounds is a miss"),
            () -> assertFalse(w.hitTest(new Point(10, 30)), "outside bounds is a miss"),
            () -> assertFalse(w.hitTest(new Point(30, 15)), "outside bounds is a miss")
        );

        w.visible = false;
        assertFalse(w.hitTest(new Point(15, 15)), "invisible widgets skip hit-test");

        w.visible = true;
        w.enabled = false;
        assertFalse(w.hitTest(new Point(15, 15)), "disabled widgets skip hit-test");
    }

    @Test
    @DisplayName("hit-test on an empty bounds always misses")
    void hitTestEmptyBounds() {
        Widget w = new HookWidget(Rect.EMPTY);
        assertFalse(w.hitTest(new Point(0, 0)));
    }

    @Test
    @DisplayName("setHovered fires onEntered exactly once on a true edge")
    void setHoveredFiresOnce() {
        HookWidget w = new HookWidget(new Rect(0, 0, 10, 10));
        w.setHovered(true);
        w.setHovered(true);
        assertEquals(1, w.enteredCount, "true→true is a no-op");
        assertTrue(w.hovered);
    }

    @Test
    @DisplayName("setHovered(false) fires onExited when previously hovered")
    void setHoveredClearsHooks() {
        HookWidget w = new HookWidget(new Rect(0, 0, 10, 10));
        w.setHovered(true);
        w.setHovered(false);
        w.setHovered(false);
        assertAll(
            () -> assertEquals(1, w.enteredCount),
            () -> assertEquals(1, w.exitedCount, "false→false is a no-op"),
            () -> assertFalse(w.hovered)
        );
    }

    @Test
    @DisplayName("setFocused fires onFocusGained / onFocusLost at the right edges")
    void setFocusedFiresHooks() {
        HookWidget w = new HookWidget(new Rect(0, 0, 10, 10));
        w.setFocused(true);
        w.setFocused(true);
        w.setFocused(false);
        assertAll(
            () -> assertEquals(1, w.focusGainedCount, "true→true is a no-op"),
            () -> assertEquals(1, w.focusLostCount, "the single false edge fires onFocusLost"),
            () -> assertFalse(w.focused)
        );
    }

    @Test
    @DisplayName("GreedyWidget consumes an event the engine dispatches")
    void greedyConsumes() {
        GreedyWidget g = new GreedyWidget(new Rect(0, 0, 10, 10));
        Root root = new Root();
        root.add(g);
        root.layout(100, 100);
        root.dispatch(new UiEvent.MouseDown(5, 5, 0));
        root.dispatch(new UiEvent.MouseDown(5, 5, 0));
        assertEquals(2, g.consumeCount);
    }

    @Test
    @DisplayName("the Walk visitor visits the root exactly once")
    void walkVisitsOnce() {
        AtomicInteger count = new AtomicInteger();
        Root root = new Root();
        Widget.walk(root, w -> count.incrementAndGet());
        assertEquals(1, count.get());
    }
}
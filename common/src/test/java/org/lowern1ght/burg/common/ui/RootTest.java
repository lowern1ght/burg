package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Root} — multi-widget event dispatch, focus hand-off on
 * mouse-down, hover propagation, recursive hit-test across nested
 * containers.
 */
class RootTest {

    /** A widget that records every event the engine dispatches to it. */
    private static final class Recorder extends Widget {
        final List<UiEvent> received = new ArrayList<>();

        Recorder(Rect bounds) {
            super(bounds);
        }

        @Override
        public void draw(DrawContext ctx) {
        }

        @Override
        public boolean handle(UiEvent event) {
            received.add(event);
            return false;
        }
    }

    /** A widget that consumes only MouseDown events. */
    private static final class GreedyDown extends Widget {
        int downCount;

        GreedyDown(Rect bounds) {
            super(bounds);
        }

        @Override
        public void draw(DrawContext ctx) {
        }

        @Override
        public boolean handle(UiEvent event) {
            if (event instanceof UiEvent.MouseDown) {
                downCount++;
                return true;
            }
            return false;
        }
    }

    @Test
    @DisplayName("layout assigns each direct child the root bounds")
    void layoutAssignsBounds() {
        Root root = new Root();
        Recorder r = new Recorder(Rect.EMPTY);
        root.add(r);

        root.layout(200, 150);

        assertAll(
            () -> assertEquals(new Rect(0, 0, 200, 150), root.bounds()),
            () -> assertEquals(new Rect(0, 0, 200, 150), r.bounds(), "direct child takes root bounds")
        );
    }

    @Test
    @DisplayName("mouse-down inside a child grants focus; mouse-down outside clears it")
    void focusHandOff() {
        Root root = new Root();
        Recorder inside = new Recorder(new Rect(10, 10, 30, 30));
        Recorder outside = new Recorder(new Rect(100, 100, 30, 30));
        root.add(inside);
        root.add(outside);
        root.layout(200, 200);

        root.dispatch(new UiEvent.MouseDown(15, 15, 0));
        assertSame(inside, inside.focused ? inside : inside, "inside is now focused");
        assertTrue(inside.focused);
        assertTrue(!outside.focused);

        root.dispatch(new UiEvent.MouseDown(115, 115, 0));
        assertTrue(outside.focused, "outside is now focused");
        assertTrue(!inside.focused, "inside lost focus");
    }

    @Test
    @DisplayName("Resize re-layouts the root and re-bounds its children")
    void resizeRelayouts() {
        Root root = new Root();
        Recorder r = new Recorder(Rect.EMPTY);
        root.add(r);
        root.layout(100, 100);

        root.dispatch(new UiEvent.Resize(200, 250));

        assertEquals(new Rect(0, 0, 200, 250), root.bounds());
        // A leaf widget whose bounds were empty takes the root's size on the
        // first layout, then keeps its now-non-empty bounds on subsequent
        // layouts. This matches the "auto-fill once, then size to content"
        // convention — a leaf that wants to track the parent should sit
        // inside an OVERLAY container.
        assertEquals(new Rect(0, 0, 100, 100), r.bounds());
    }

    @Test
    @DisplayName("hover propagates: only the deepest hit becomes hovered")
    void hoverPropagation() {
        Root root = new Root();
        Container inner = new Container(new Rect(50, 50, 50, 50), Container.Direction.OVERLAY);
        Recorder leaf = new Recorder(new Rect(50, 50, 50, 50));
        root.add(inner);
        inner.add(leaf);
        root.layout(200, 200);

        root.dispatch(new UiEvent.MouseMoved(75, 75));
        assertAll(
            () -> assertTrue(leaf.hovered, "leaf is the deepest hit"),
            () -> assertTrue(!inner.hovered, "container is NOT hovered (leaf absorbed the event)")
        );
    }

    @Test
    @DisplayName("hover clears when the cursor moves off all children")
    void hoverClearsWhenCursorLeaves() {
        Root root = new Root();
        Recorder r = new Recorder(new Rect(0, 0, 10, 10));
        root.add(r);
        root.layout(100, 100);

        root.dispatch(new UiEvent.MouseMoved(5, 5));
        assertTrue(r.hovered);

        root.dispatch(new UiEvent.MouseMoved(50, 50));
        assertTrue(!r.hovered, "leaving the child clears the hovered state");
    }

    @Test
    @DisplayName("hit-test returns null when no child covers the point")
    void findHitOutsideAnyChild() {
        Root root = new Root();
        Recorder r = new Recorder(new Rect(0, 0, 10, 10));
        root.add(r);
        root.layout(100, 100);

        assertNull(root.findHit(new Point(50, 50)));
    }

    @Test
    @DisplayName("hit-test prefers the most recently added child on overlap")
    void hitTestReverseAddOrder() {
        Root root = new Root();
        Recorder older = new Recorder(new Rect(0, 0, 50, 50));
        Recorder newer = new Recorder(new Rect(0, 0, 50, 50));
        root.add(older);
        root.add(newer);
        root.layout(100, 100);

        assertSame(newer, root.findHit(new Point(25, 25)), "newer wins on overlap");
    }

    @Test
    @DisplayName("greedy widget consumes the mouse-down; the engine still tracks focus")
    void greedyWidgetConsumes() {
        Root root = new Root();
        GreedyDown g = new GreedyDown(new Rect(0, 0, 20, 20));
        root.add(g);
        root.layout(100, 100);

        root.dispatch(new UiEvent.MouseDown(5, 5, 0));
        root.dispatch(new UiEvent.MouseDown(5, 5, 0));
        assertEquals(2, g.downCount);
        assertTrue(g.focused);
    }

    @Test
    @DisplayName("removing the focused widget clears focus")
    void removeFocused() {
        Root root = new Root();
        Recorder r = new Recorder(new Rect(0, 0, 10, 10));
        root.add(r);
        root.layout(100, 100);

        root.dispatch(new UiEvent.MouseDown(5, 5, 0));
        assertTrue(r.focused);

        root.remove(r);
        assertTrue(!r.focused);
    }

    @Test
    @DisplayName("findHit recurses into nested containers")
    void hitTestNestedContainer() {
        Root root = new Root();
        Container outer = new Container(new Rect(0, 0, 100, 100), Container.Direction.OVERLAY);
        Recorder leaf = new Recorder(new Rect(10, 10, 20, 20));
        root.add(outer);
        outer.add(leaf);
        root.layout(200, 200);

        Widget hit = root.findHit(new Point(15, 15));
        assertNotNull(hit);
        assertSame(leaf, hit);
    }

    @Test
    @DisplayName("children() is an unmodifiable snapshot")
    void childrenSnapshot() {
        Root root = new Root();
        Recorder r = new Recorder(Rect.EMPTY);
        root.add(r);

        List<Widget> snap = root.children();
        assertEquals(1, snap.size());
        try {
            snap.add(new Recorder(Rect.EMPTY));
            assert false : "should have thrown";
        } catch (UnsupportedOperationException expected) {
            // ok — unmodifiable
        }
    }

    @Test
    @DisplayName("keyboard / scroll events flow to the focused widget")
    void keyboardGoesToFocusedWidget() {
        Root root = new Root();
        Recorder r = new Recorder(new Rect(0, 0, 10, 10));
        root.add(r);
        root.layout(100, 100);

        root.dispatch(new UiEvent.MouseDown(5, 5, 0));    // focus the recorder AND forward the click
        root.dispatch(new UiEvent.KeyDown(0, 0, 0));      // keyboard goes to focused
        root.dispatch(new UiEvent.Scroll(0, 1.0));        // scroll goes to focused

        // Three events total: the focusing MouseDown + the post-focus KeyDown
        // and Scroll. The dispatcher forwards a focusing MouseDown to the
        // newly-focused widget so click handlers fire alongside the focus gain.
        assertEquals(3, r.received.size());
        assertTrue(r.received.get(0) instanceof UiEvent.MouseDown);
        assertTrue(r.received.get(1) instanceof UiEvent.KeyDown);
        assertTrue(r.received.get(2) instanceof UiEvent.Scroll);
    }

    @Test
    @DisplayName("a counter widget is drawn once per draw() call on the root")
    void drawCallsEveryChildOnce() {
        Root root = new Root();
        AtomicInteger draws = new AtomicInteger();
        Widget counter = new Widget(Rect.EMPTY) {
                @Override
                public void draw(DrawContext ctx) {
                    draws.incrementAndGet();
                }
            };
        root.add(counter);
        root.layout(50, 50);

        root.draw(new DrawContext(50, 50, 0, 0));
        assertEquals(1, draws.get());

        root.draw(new DrawContext(50, 50, 0, 0));
        assertEquals(2, draws.get());
    }
}
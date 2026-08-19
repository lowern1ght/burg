package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Container} — three layout directions (HORIZONTAL, VERTICAL,
 * OVERLAY) and recursive hit-testing. The container's own draw routine
 * is a no-op; children draw themselves.
 */
class ContainerTest {

    /** Stub widget that records draw calls. */
    private static final class StubWidget extends Widget {
        int drawCount;

        StubWidget(Rect bounds) {
            super(bounds);
        }

        @Override
        public void draw(DrawContext ctx) {
            drawCount++;
        }
    }

    @Test
    @DisplayName("HORIZONTAL packs children left-to-right with spacing")
    void horizontalLayout() {
        Container c = new Container(new Rect(0, 0, 100, 50), Container.Direction.HORIZONTAL);
        c.setSpacing(2);

        StubWidget a = new StubWidget(new Rect(0, 0, 10, 20));
        StubWidget b = new StubWidget(new Rect(0, 0, 10, 20));
        StubWidget d = new StubWidget(new Rect(0, 0, 10, 20));
        c.add(a);
        c.add(b);
        c.add(d);

        c.layout(100, 50);

        assertAll(
            () -> assertEquals(new Rect(0, 0, 10, 20), a.bounds(), "first child at x=0"),
            () -> assertEquals(new Rect(12, 0, 10, 20), b.bounds(), "second child after 10 + 2 spacing"),
            () -> assertEquals(new Rect(24, 0, 10, 20), d.bounds(), "third child after 10 + 2 + 10 + 2")
        );
    }

    @Test
    @DisplayName("VERTICAL packs children top-to-bottom with spacing")
    void verticalLayout() {
        Container c = new Container(new Rect(0, 0, 50, 100), Container.Direction.VERTICAL);
        c.setSpacing(3);

        StubWidget a = new StubWidget(new Rect(0, 0, 20, 10));
        StubWidget b = new StubWidget(new Rect(0, 0, 20, 10));
        StubWidget d = new StubWidget(new Rect(0, 0, 20, 10));
        c.add(a);
        c.add(b);
        c.add(d);

        c.layout(50, 100);

        assertAll(
            () -> assertEquals(new Rect(0, 0, 20, 10), a.bounds(), "first child at y=0"),
            () -> assertEquals(new Rect(0, 13, 20, 10), b.bounds(), "second child after 10 + 3 spacing"),
            () -> assertEquals(new Rect(0, 26, 20, 10), d.bounds(), "third child after 10 + 3 + 10 + 3")
        );
    }

    @Test
    @DisplayName("OVERLAY stacks every child on the container's bounds")
    void overlayLayout() {
        Container c = new Container(new Rect(0, 0, 50, 30), Container.Direction.OVERLAY);

        StubWidget a = new StubWidget(new Rect(0, 0, 5, 5));
        StubWidget b = new StubWidget(new Rect(0, 0, 100, 100));
        c.add(a);
        c.add(b);

        c.layout(50, 30);

        assertAll(
            () -> assertEquals(new Rect(0, 0, 50, 30), a.bounds(), "overlay child takes full bounds"),
            () -> assertEquals(new Rect(0, 0, 50, 30), b.bounds(), "overlay child takes full bounds")
        );
    }

    @Test
    @DisplayName("draw forwards to every visible child in add-order")
    void drawForwardsToChildren() {
        Container c = new Container(Rect.EMPTY, Container.Direction.VERTICAL);
        StubWidget a = new StubWidget(Rect.EMPTY);
        StubWidget b = new StubWidget(Rect.EMPTY);
        c.add(a);
        c.add(b);

        DrawContext ctx = new DrawContext(100, 100, 0, 0);
        c.draw(ctx);

        assertEquals(1, a.drawCount);
        assertEquals(1, b.drawCount);
    }

    @Test
    @DisplayName("invisible children are skipped by draw")
    void invisibleChildrenAreSkipped() {
        Container c = new Container(Rect.EMPTY, Container.Direction.VERTICAL);
        StubWidget a = new StubWidget(Rect.EMPTY);
        StubWidget b = new StubWidget(Rect.EMPTY);
        a.visible = false;
        c.add(a);
        c.add(b);

        c.draw(new DrawContext(100, 100, 0, 0));

        assertEquals(0, a.drawCount);
        assertEquals(1, b.drawCount);
    }

    @Test
    @DisplayName("negative spacing is clamped to zero")
    void negativeSpacingClamps() {
        Container c = new Container(new Rect(0, 0, 100, 100), Container.Direction.HORIZONTAL);
        c.setSpacing(-7);
        assertEquals(0, c.spacing());
    }

    @Test
    @DisplayName("remove() drops a child; the children() view reflects the change")
    void removeChild() {
        Container c = new Container(Rect.EMPTY, Container.Direction.VERTICAL);
        StubWidget a = new StubWidget(Rect.EMPTY);
        StubWidget b = new StubWidget(Rect.EMPTY);
        c.add(a);
        c.add(b);

        assertEquals(2, c.children().size());
        c.remove(a);
        assertEquals(1, c.children().size());
        assertSame(b, c.children().get(0));
    }

    @Test
    @DisplayName("add rejects null children")
    void nullChildRejected() {
        Container c = new Container(Rect.EMPTY, Container.Direction.VERTICAL);
        try {
            c.add(null);
            org.junit.jupiter.api.Assertions.fail("should have thrown");
        } catch (NullPointerException expected) {
            // expected
        }
    }
}
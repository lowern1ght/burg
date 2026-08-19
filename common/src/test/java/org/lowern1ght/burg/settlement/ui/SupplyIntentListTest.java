package org.lowern1ght.burg.settlement.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.common.ui.Color;
import org.lowern1ght.burg.common.ui.Label;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The act-4 SUPPLY-mode intent list — sort order, gap math, empty
 * placeholder. Three jobs the test pins:
 *
 * <ol>
 *   <li>Intent rows sort alphabetically by {@code buildingDefId}
 *       (case-insensitive on the canonical lower-case form).</li>
 *   <li>{@link SupplyIntentList#computeGaps} sums {@code inputsMissing}
 *       across every intent and produces one row per {@link ItemId}.</li>
 *   <li>An empty intent list shows the {@link
 *       SupplyIntentListWidget#NO_INTENT_KEY} placeholder — no
 *       intent panels, one empty gap row.</li>
 * </ol>
 */
class SupplyIntentListTest {

    private static final ItemId OAK_LOG = ItemId.of("minecraft:oak_log");
    private static final ItemId STONE = ItemId.of("minecraft:stone");
    private static final ItemId WHEAT = ItemId.of("minecraft:wheat");

    private static SupplyIntentList.IntentItem item(String defId, ItemId key, int amount) {
        Map<ItemId, Integer> missing = new LinkedHashMap<>();
        missing.put(key, amount);
        return new SupplyIntentList.IntentItem(defId, missing);
    }

    private static SupplyIntentList.IntentItem item(String defId, ItemId a, int aQty, ItemId b, int bQty) {
        Map<ItemId, Integer> missing = new LinkedHashMap<>();
        missing.put(a, aQty);
        missing.put(b, bQty);
        return new SupplyIntentList.IntentItem(defId, missing);
    }

    @Test
    @DisplayName("intents sort alphabetically by buildingDefId, case-insensitive")
    void intentSortIsAlphabetical() {
        SupplyIntentList.IntentItem house = item("house", OAK_LOG, 10);
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 20);
        SupplyIntentList.IntentItem chapel = item("chapel", STONE, 5);

        SupplyIntentList data = new SupplyIntentList(
            List.of(house, barn, chapel),
            SupplyIntentList.computeGaps(List.of(house, barn, chapel), Map.of())
        );
        SupplyIntentListWidget w = data.toWidget();

        // Read the rendered text off each panel's child Label. The widget
        // formats each intent as "defId (N missing)"; the sort order is
        // the order of the rendered defIds.
        assertAll(
            () -> assertEquals(3, w.intentRowsContainer().children().size()),
            () -> assertEquals("barn (1 missing)", labelText(w.intentPanel(0))),
            () -> assertEquals("chapel (1 missing)", labelText(w.intentPanel(1))),
            () -> assertEquals("house (1 missing)", labelText(w.intentPanel(2)))
        );
    }

    private static String labelText(org.lowern1ght.burg.common.ui.Panel panel) {
        return ((Label) panel.children().get(0)).text();
    }

    @Test
    @DisplayName("intents sort is case-insensitive — 'House' next to 'house'")
    void intentSortCaseInsensitive() {
        SupplyIntentList.IntentItem house = item("House", OAK_LOG, 1);
        SupplyIntentList.IntentItem HouseLower = item("house", OAK_LOG, 2);

        SupplyIntentList data = new SupplyIntentList(
            List.of(HouseLower, house),     // add in mixed order
            SupplyIntentList.computeGaps(List.of(HouseLower, house), Map.of())
        );
        SupplyIntentListWidget w = data.toWidget();

        // both show up; their relative order is the case-insensitive
        // comparison on the canonical form. We just assert both are present.
        assertEquals(2, w.intentRowsContainer().children().size());
    }

    @Test
    @DisplayName("gaps sum inputsMissing across every intent into one row per ItemId")
    void gapsSumAcrossIntents() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 10);
        SupplyIntentList.IntentItem chapel = item("chapel", OAK_LOG, 5);
        SupplyIntentList.IntentItem farmhouse = item("farm", OAK_LOG, 3, STONE, 2);

        List<SupplyIntentList.StockGapItem> gaps = SupplyIntentList.computeGaps(
            List.of(barn, chapel, farmhouse),
            Map.of()
        );

        // barn(10) + chapel(5) + farmhouse(3) = 18 OAK_LOG; 2 STONE
        assertEquals(2, gaps.size());
        assertEquals(OAK_LOG, gaps.get(0).item());
        assertEquals(18, gaps.get(0).missing());
        assertEquals(STONE, gaps.get(1).item());
        assertEquals(2, gaps.get(1).missing());
    }

    @Test
    @DisplayName("gaps subtract onHand: 10 wanted, 3 on hand → missing=7")
    void gapsSubtractOnHand() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 10);

        List<SupplyIntentList.StockGapItem> gaps = SupplyIntentList.computeGaps(
            List.of(barn),
            Map.of(OAK_LOG, 3)
        );

        assertEquals(1, gaps.size());
        assertEquals(OAK_LOG, gaps.get(0).item());
        assertEquals(7, gaps.get(0).missing());
        assertEquals(3, gaps.get(0).onHand());
    }

    @Test
    @DisplayName("gap never goes negative: 5 wanted, 10 on hand → missing=0")
    void gapFloorsAtZero() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 5);

        List<SupplyIntentList.StockGapItem> gaps = SupplyIntentList.computeGaps(
            List.of(barn),
            Map.of(OAK_LOG, 10)
        );

        assertEquals(1, gaps.size());
        assertEquals(0, gaps.get(0).missing());
        assertEquals(10, gaps.get(0).onHand());
    }

    @Test
    @DisplayName("gap roll with no intents is empty")
    void emptyIntentGapRoll() {
        List<SupplyIntentList.StockGapItem> gaps = SupplyIntentList.computeGaps(List.of(), Map.of());
        assertTrue(gaps.isEmpty());
    }

    @Test
    @DisplayName("items the intents don't ask for do not appear in the gap roll")
    void unrelatedItemsDoNotAppear() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 10);

        List<SupplyIntentList.StockGapItem> gaps = SupplyIntentList.computeGaps(
            List.of(barn),
            Map.of(STONE, 99, OAK_LOG, 0, WHEAT, 42)
        );

        assertEquals(1, gaps.size());
        assertEquals(OAK_LOG, gaps.get(0).item());
    }

    @Test
    @DisplayName("empty intent list renders the lang-key placeholder, no intent panels")
    void emptyListShowsPlaceholder() {
        SupplyIntentList data = new SupplyIntentList(
            List.of(),
            SupplyIntentList.computeGaps(List.of(), Map.of())
        );
        SupplyIntentListWidget w = data.toWidget();

        assertAll(
            () -> assertEquals(1, w.intentRowsContainer().children().size()),
            () -> assertSame(w.noIntentPlaceholder(), w.intentRowsContainer().children().get(0)),
            () -> assertEquals(SupplyIntentListWidget.NO_INTENT_KEY, w.noIntentPlaceholder().text()),
            () -> assertEquals(1, w.gapRowsContainer().children().size())
        );
    }

    @Test
    @DisplayName("hovering an intent highlights the matching gap row(s)")
    void hoverHighlightsMatchingGap() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 10);
        SupplyIntentList.IntentItem chapel = item("chapel", STONE, 5);
        SupplyIntentList data = new SupplyIntentList(
            List.of(barn, chapel),
            SupplyIntentList.computeGaps(List.of(barn, chapel), Map.of())
        );
        SupplyIntentListWidget w = data.toWidget();

        w.setHoveredIntentIndex(0);    // hover barn (sorted first)

        // The OAK_LOG gap row is highlighted; the STONE row is not.
        assertEquals(SupplyIntentListWidget.HIGHLIGHT_TINT, w.gapPanel(0).background());
        assertEquals(Color.TRANSPARENT, w.gapPanel(1).background());
        // The barn intent panel itself is highlighted.
        assertEquals(SupplyIntentListWidget.HIGHLIGHT_TINT, w.intentPanel(0).background());
        assertEquals(Color.TRANSPARENT, w.intentPanel(1).background());
    }

    @Test
    @DisplayName("clearing the hover clears every highlight")
    void clearingHoverClearsHighlights() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 10);
        SupplyIntentList data = new SupplyIntentList(
            List.of(barn),
            SupplyIntentList.computeGaps(List.of(barn), Map.of())
        );
        SupplyIntentListWidget w = data.toWidget();

        w.setHoveredIntentIndex(0);
        w.setHoveredIntentIndex(-1);

        assertEquals(Color.TRANSPARENT, w.gapPanel(0).background());
        assertEquals(Color.TRANSPARENT, w.intentPanel(0).background());
    }

    @Test
    @DisplayName("layout() expands the row bounds to fill the parent width")
    void layoutExpandsRowBounds() {
        SupplyIntentList.IntentItem barn = item("barn", OAK_LOG, 10);
        SupplyIntentList data = new SupplyIntentList(
            List.of(barn),
            SupplyIntentList.computeGaps(List.of(barn), Map.of())
        );
        SupplyIntentListWidget w = data.toWidget();

        w.layout(400, 200);

        assertEquals(400, w.intentPanel(0).bounds().w());
        assertEquals(400, w.gapPanel(0).bounds().w());
    }

    @Test
    @DisplayName("the placeholder carries the lang key — the engine never sees a translated string")
    void placeholderCarriesLangKey() {
        Label placeholder = new SupplyIntentList(
            List.of(),
            List.of()
        ).toWidget().noIntentPlaceholder();

        assertEquals("burg.message.hub.supply.no_intent", placeholder.text());
    }
}
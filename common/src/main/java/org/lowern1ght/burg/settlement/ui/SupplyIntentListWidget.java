package org.lowern1ght.burg.settlement.ui;

import org.lowern1ght.burg.common.ui.Color;
import org.lowern1ght.burg.common.ui.Container;
import org.lowern1ght.burg.common.ui.DrawContext;
import org.lowern1ght.burg.common.ui.Label;
import org.lowern1ght.burg.common.ui.Panel;
import org.lowern1ght.burg.common.ui.Rect;
import org.lowern1ght.burg.common.ui.Widget;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/**
 * The act-4 SUPPLY-mode intent list widget — the first widget the new
 * {@code common.ui} engine renders. Two zones:
 *
 * <ul>
 *   <li><b>Intent rows</b> — one row per pending construction intent,
 *       alphabetically sorted by {@code buildingDefId}. Hovering a row
 *       highlights the matching gap row(s) below.</li>
 *   <li><b>Stock-gap rows</b> — the union of inputs missing across all
 *       intents, summed by {@link ItemId}, sorted by item id. Each row
 *       carries {@code itemId, missing, onHand}.</li>
 * </ul>
 *
 * <p>Empty intent list shows a single placeholder line whose text is
 * the literal lang key {@link #NO_INTENT_KEY}; the Minecraft adapter
 * resolves it at draw time, the engine never sees a translated string.
 *
 * <p>No {@code net.minecraft} import. The widget is built from {@link
 * Container}, {@link Panel}, {@link Label} and {@link Widget} — all
 * bare-JVM types — and reads {@link ItemId} + the {@link SupplyIntentList}
 * data record.
 */
public final class SupplyIntentListWidget extends Widget {

    /** Lang key the placeholder label carries when there is no intent. */
    public static final String NO_INTENT_KEY = "burg.message.hub.supply.no_intent";

    /** Pixel gap between consecutive rows in the same section. */
    static final int ROW_GAP = 2;
    /** Pixel gap between the intent list and the gap roll. */
    static final int SECTION_GAP = 8;
    /** Pixel height of one row. */
    static final int ROW_HEIGHT = 12;

    /** The hover tint — pale gold, alpha 80. */
    public static final Color HIGHLIGHT_TINT = Color.rgba(255, 220, 100, 80);

    private final Container root;
    private final Container intentRows;
    private final Container gapRows;
    private final Label noIntentLabel;
    private final List<Panel> intentPanels = new ArrayList<>();
    private final List<Panel> gapPanels = new ArrayList<>();
    private SupplyIntentList data;
    private int hoveredIntentIndex = -1;

    public SupplyIntentListWidget(SupplyIntentList data) {
        super(Rect.EMPTY);
        this.data = Objects.requireNonNull(data, "data");

        this.root = new Container(Container.Direction.VERTICAL);
        this.root.setSpacing(SECTION_GAP);

        this.intentRows = new Container(Container.Direction.VERTICAL);
        this.intentRows.setSpacing(ROW_GAP);
        this.root.add(intentRows);

        this.gapRows = new Container(Container.Direction.VERTICAL);
        this.gapRows.setSpacing(ROW_GAP);
        this.root.add(gapRows);

        this.noIntentLabel = new Label(Rect.EMPTY, NO_INTENT_KEY);
        this.intentRows.add(noIntentLabel);

        rebuild();
    }

    /** Replaces the data and rebuilds the inner widget tree. */
    public void setData(SupplyIntentList data) {
        this.data = Objects.requireNonNull(data, "data");
        rebuild();
    }

    /** Returns the data the widget currently renders. */
    public SupplyIntentList data() {
        return data;
    }

    /** Returns the row index currently being hovered, or {@code -1}. */
    public int hoveredIntentIndex() {
        return hoveredIntentIndex;
    }

    /** Returns the panel backing the intent row at {@code index}. */
    public Panel intentPanel(int index) {
        return intentPanels.get(index);
    }

    /** Returns the panel backing the gap row at {@code index}. */
    public Panel gapPanel(int index) {
        return gapPanels.get(index);
    }

    /** Returns the placeholder label the widget shows when intent is empty. */
    public Label noIntentPlaceholder() {
        return noIntentLabel;
    }

    /** Returns the container the intent rows live in. */
    public Container intentRowsContainer() {
        return intentRows;
    }

    /** Returns the container the gap rows live in. */
    public Container gapRowsContainer() {
        return gapRows;
    }

    /** Returns the inner vertical container, for the test harness. */
    public Container rootContainer() {
        return root;
    }

    private void rebuild() {
        intentRows.children().forEach(intentRows::remove);
        gapRows.children().forEach(gapRows::remove);
        intentPanels.clear();
        gapPanels.clear();

        List<SupplyIntentList.IntentItem> sortedItems = sortedIntents(data.items());
        List<SupplyIntentList.StockGapItem> sortedGaps = sortedGaps(data.gaps());

        if (sortedItems.isEmpty()) {
            intentRows.add(noIntentLabel);
            gapRows.add(new Label(Rect.EMPTY, ""));
        } else {
            intentRows.remove(noIntentLabel);
            for (SupplyIntentList.IntentItem item : sortedItems) {
                Panel panel = new Panel(
                    new Rect(0, 0, 1, ROW_HEIGHT),
                    Color.TRANSPARENT
                );
                panel.add(new Label(new Rect(2, 0, 1, ROW_HEIGHT), formatIntent(item)));
                intentPanels.add(panel);
                intentRows.add(panel);
            }
            for (SupplyIntentList.StockGapItem gap : sortedGaps) {
                Panel panel = new Panel(
                    new Rect(0, 0, 1, ROW_HEIGHT),
                    Color.TRANSPARENT
                );
                panel.add(new Label(new Rect(2, 0, 1, ROW_HEIGHT), formatGap(gap)));
                gapPanels.add(panel);
                gapRows.add(panel);
            }
        }
    }

    /** Lays out the widget's container tree against the parent bounds. */
    @Override
    public void layout(int width, int height) {
        setBounds(new Rect(0, 0, Math.max(0, width), Math.max(0, height)));
        root.layout(width, height);
        // Re-place each intent/gap row at the parent width so the panel
        // backgrounds cover the whole row, not the 1-pixel sentinel.
        expandRowBounds(intentPanels, width);
        expandRowBounds(gapPanels, width);
    }

    private static void expandRowBounds(List<Panel> panels, int width) {
        for (Panel p : panels) {
            Rect b = p.bounds();
            p.setBounds(new Rect(b.x(), b.y(), width, Math.max(ROW_HEIGHT, b.h())));
            for (Widget child : p.children()) {
                if (child instanceof Label) {
                    child.setBounds(new Rect(2, child.bounds().y(), width - 2, ROW_HEIGHT));
                }
            }
        }
    }

    /** Draws the widget — the container forwards to every row panel. */
    @Override
    public void draw(DrawContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        root.draw(ctx);
    }

    /**
     * Sets the hovered intent by 0-based index. {@code -1} clears the
     * hover. The matching gap row(s) are highlighted via their panel
     * background colour; the intent row itself is also tinted.
     */
    public void setHoveredIntentIndex(int index) {
        if (index == hoveredIntentIndex) return;
        hoveredIntentIndex = index;
        List<SupplyIntentList.IntentItem> sortedItems = sortedIntents(data.items());
        Map<ItemId, Integer> hoveredMissing = (index >= 0 && index < sortedItems.size())
            ? sortedItems.get(index).inputsMissing()
            : Collections.emptyMap();
        for (int i = 0; i < gapPanels.size(); i++) {
            Panel p = gapPanels.get(i);
            SupplyIntentList.StockGapItem gap = data.gaps().get(i);
            boolean matches = hoveredMissing.containsKey(gap.item());
            p.setBackground(matches ? HIGHLIGHT_TINT : Color.TRANSPARENT);
        }
        for (int i = 0; i < intentPanels.size(); i++) {
            Panel p = intentPanels.get(i);
            p.setBackground(i == index ? HIGHLIGHT_TINT : Color.TRANSPARENT);
        }
    }

    private static String formatIntent(SupplyIntentList.IntentItem item) {
        return item.buildingDefId() + " (" + item.inputsMissing().size() + " missing)";
    }

    private static String formatGap(SupplyIntentList.StockGapItem gap) {
        return gap.item().value() + " missing=" + gap.missing() + " onHand=" + gap.onHand();
    }

    private static List<SupplyIntentList.IntentItem> sortedIntents(List<SupplyIntentList.IntentItem> source) {
        List<SupplyIntentList.IntentItem> copy = new ArrayList<>(source);
        copy.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
            a.buildingDefId().toLowerCase(Locale.ROOT),
            b.buildingDefId().toLowerCase(Locale.ROOT)
        ));
        return copy;
    }

    private static List<SupplyIntentList.StockGapItem> sortedGaps(List<SupplyIntentList.StockGapItem> source) {
        List<SupplyIntentList.StockGapItem> copy = new ArrayList<>(source);
        copy.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
            a.item().value(),
            b.item().value()
        ));
        return copy;
    }
}
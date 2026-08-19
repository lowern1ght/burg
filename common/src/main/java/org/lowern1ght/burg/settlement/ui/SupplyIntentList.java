package org.lowern1ght.burg.settlement.ui;

import org.lowern1ght.burg.common.ui.Color;
import org.lowern1ght.burg.common.ui.Container;
import org.lowern1ght.burg.common.ui.DrawContext;
import org.lowern1ght.burg.common.ui.Label;
import org.lowern1ght.burg.common.ui.Panel;
import org.lowern1ght.burg.common.ui.Rect;
import org.lowern1ght.burg.common.ui.TextStyle;
import org.lowern1ght.burg.common.ui.Widget;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The act-4 SUPPLY-mode intent list — domain value type + the {@link
 * Widget} that renders it. The value type carries two parallel rolls:
 * the pending construction intents and the stock-gap roll that aggregates
 * what the town is short of. The widget renders them top-to-bottom.
 *
 * <p>No Minecraft imports. The whole class lives on a bare JVM and the
 * engine renders it without ever touching a Minecraft client. The
 * {@code TownHubScreenV2} (in {@code neoforge/.../client/gui/}) is the
 * only place that bridges to a {@code GuiGraphics}.
 *
 * <p>Sorting is case-insensitive on the canonical lower-case form of
 * the {@code buildingDefId} / {@code ItemId} string — so {@code "House"}
 * and {@code "house"} sort next to each other. The intent rows are
 * alphabetical by defId; the gap rows are alphabetical by item id.
 *
 * <p>Hovering an intent row highlights the matching gap row(s) — every
 * gap whose {@link ItemId} appears in the hovered intent's
 * {@code inputsMissing} map. The highlight uses a fixed {@link
 * Color#lerp}-compatible tint; the Minecraft adapter can swap it for a
 * sprite in its draw override.
 */
public record SupplyIntentList(List<IntentItem> items, List<StockGapItem> gaps) {

    /**
     * One row of the intent list. {@code inputsMissing} is sparse —
     * items the intent does not need are absent, not zero.
     */
    public record IntentItem(String buildingDefId, Map<ItemId, Integer> inputsMissing) {
        public IntentItem {
            buildingDefId = Objects.requireNonNull(buildingDefId, "buildingDefId");
            inputsMissing = Map.copyOf(Objects.requireNonNull(inputsMissing, "inputsMissing"));
        }
    }

    /**
     * One row of the gap roll. {@code missing} is how many of {@code item}
     * the town is short of to satisfy every intent that needs it;
     * {@code onHand} is how many it already holds.
     */
    public record StockGapItem(ItemId item, int missing, int onHand) {
        public StockGapItem {
            Objects.requireNonNull(item, "item");
        }
    }

    public SupplyIntentList {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        gaps = List.copyOf(Objects.requireNonNull(gaps, "gaps"));
    }

    /**
     * Builds the canonical gap roll from a list of intents and the
     * town's on-hand counts. Items the intents don't ask for do not
     * appear in the roll; an intent item the town already holds at or
     * above its requested amount reads as {@code missing = 0}.
     */
    public static List<StockGapItem> computeGaps(List<IntentItem> intents, Map<ItemId, Integer> onHand) {
        java.util.Map<ItemId, Integer> sums = new java.util.LinkedHashMap<>();
        for (IntentItem intent : intents) {
            for (Map.Entry<ItemId, Integer> e : intent.inputsMissing().entrySet()) {
                sums.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        List<StockGapItem> gaps = new ArrayList<>(sums.size());
        for (Map.Entry<ItemId, Integer> e : sums.entrySet()) {
            int have = onHand.getOrDefault(e.getKey(), 0);
            int want = e.getValue();
            int missing = Math.max(0, want - have);
            gaps.add(new StockGapItem(e.getKey(), missing, have));
        }
        return gaps;
    }

    /**
     * Returns a new widget that renders this data. The widget holds a
     * snapshot of the data; call {@link SupplyIntentListWidget#setData}
     * with a fresh value to refresh.
     */
    public SupplyIntentListWidget toWidget() {
        return new SupplyIntentListWidget(this);
    }
}
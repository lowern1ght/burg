package org.lowern1ght.burg.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lowern1ght.burg.client.ui.McDrawContext;
import org.lowern1ght.burg.client.ui.McInputAdapter;
import org.lowern1ght.burg.common.ui.InputField;
import org.lowern1ght.burg.common.ui.Label;
import org.lowern1ght.burg.common.ui.Rect;
import org.lowern1ght.burg.common.ui.Root;
import org.lowern1ght.burg.common.ui.TextStyle;
import org.lowern1ght.burg.common.ui.UiEvent;
import org.lowern1ght.burg.common.ui.Widget;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.network.NetworkHelper;
import org.lowern1ght.burg.settlement.ui.SupplyIntentList;
import org.lowern1ght.burg.settlement.ui.SupplyIntentListWidget;

import java.util.List;

/**
 * The act-4 SUPPLY-mode town hub screen — the FIRST screen that runs on
 * the new {@code common.ui} engine. Today the screen renders ONLY the
 * SUPPLY-mode intent list (ADR-0022); the CONSTRUCTION-mode screen
 * (the legacy {@code TownHubScreen}) stays untouched until the act-4
 * follow-up PR.
 *
 * <p>This screen is NOT registered against a {@link net.minecraft.world.inventory.MenuType}
 * yet — mod-loading wiring is a follow-up. The class compiles and
 * can be instantiated in-process so the engine's
 * {@code SupplyIntentListWidget} renders on a fake {@link Root} and the
 * test can wire it up.
 *
 * <p>The screen extends {@link Screen} (not {@link
 * net.minecraft.client.gui.screens.inventory.AbstractContainerScreen}
 * because the act-4 hub has no inventory; it is a read-only window.
 * {@link #render(GuiGraphics, int, int, float)} builds the
 * {@link McDrawContext}, lays the {@link Root} out against the screen's
 * bounds, and dispatches a {@link UiEvent.Resize} so any container that
 * wants the new size can react.
 *
 * <p>Mouse / keyboard / scroll callbacks translate to {@link UiEvent}s
 * via {@link McInputAdapter} and dispatch into the engine. The engine
 * owns focus, hit-test, and hover state; the screen just forwards.
 */
public class TownHubScreenV2 extends Screen {

    /** Bottom strip reserved for the supply-quantity input field — pixels. */
    static final int FIELD_STRIP_HEIGHT = 30;

    /** Horizontal margin around the input field — pixels. */
    static final int FIELD_MARGIN_X = 8;

    private final Root root;
    private final SupplyIntentListWidget intentList;
    /**
     * Quantity input — the field the user types into before pressing
     * {@code Enter} to fire a supply packet. Lives at the bottom of the
     * screen, below the intent list.
     */
    private final InputField quantityField;
    /**
     * Item the user has selected by clicking a gap row. {@link ItemId#EMPTY}
     * means no row has been clicked yet — the submit handler treats that as
     * a no-op so the user can't submit a packet for an unselected item.
     */
    private ItemId pendingSupplyItem = ItemId.EMPTY;
    /**
     * Anchor position of the town the screen was opened against. Carried
     * by the screen so the click handler can route a {@code C2SSupplyStockPacket}
     * back to the same town. {@code null} for legacy callers that still
     * construct the screen with {@link #withEmptyIntent()}.
     */
    private final BlockPos anchorPos;
    /**
     * Status-bar widget that reflects the most recent apply result.
     * Empty text until the user fires one packet; the text is set by
     * {@link #submitQuantity(int)} on a successful apply and cleared
     * on a no-op (no selection / no anchor / non-positive quantity).
     * Lives at the top of the bottom strip, above the input field, so
     * the user's eye travels up to read it after pressing Enter.
     */
    private final Label statusLabel;
    /**
     * Last applied (itemId, quantity) — exposed for the test harness so
     * a unit test can assert the label got set without needing a full
     * Minecraft draw cycle. {@code null} until the first successful
     * apply.
     */
    private AppliedSupply lastApplied = null;

    /** Mutable record of one successful supply packet — what the status bar reads. */
    public record AppliedSupply(ItemId itemId, int quantity) {}

    public TownHubScreenV2(SupplyIntentList data, BlockPos anchorPos) {
        super(Component.translatable("burg.hub.supply.title"));
        this.root = new Root();
        this.intentList = data.toWidget();
        this.quantityField = new InputField("Quantity", 80, 20);
        this.statusLabel = new Label(new Rect(0, 0, 0, 0), "",
            TextStyle.defaults());
        this.quantityField.onSubmit = quantity -> submitQuantity(quantity);
        this.root.add(intentList);
        this.root.add(statusLabel);
        this.root.add(quantityField);
        this.anchorPos = anchorPos;
    }

    /**
     * Legacy constructor — kept so existing tests / mock harnesses that
     * construct the screen with no anchor still compile. The click handler
     * is a no-op when the anchor is unset.
     */
    public TownHubScreenV2(SupplyIntentList data) {
        this(data, null);
    }

    /**
     * Builds a screen with an empty placeholder list, no anchor.
     * The act-4 follow-up PR will replace this with a snapshot that
     * carries the anchor.
     */
    public static TownHubScreenV2 withEmptyIntent() {
        return new TownHubScreenV2(new SupplyIntentList(
            List.of(),
            SupplyIntentList.computeGaps(List.of(), java.util.Map.of())
        ));
    }

    /**
     * Builds a screen anchored to {@code anchorPos} with an empty
     * placeholder list. The {@code OuatForgeClient#onClientTick} poll
     * uses this factory so the click handler can route a
     * {@code C2SSupplyStockPacket} back to the right town.
     */
    public static TownHubScreenV2 withAnchor(BlockPos anchorPos) {
        return new TownHubScreenV2(new SupplyIntentList(
            List.of(),
            SupplyIntentList.computeGaps(List.of(), java.util.Map.of())
        ), anchorPos);
    }

    /** Returns the engine root — test harness + future layout hooks. */
    public Root root() {
        return root;
    }

    /** Returns the intent-list widget — test harness. */
    public SupplyIntentListWidget intentList() {
        return intentList;
    }

    /** Returns the quantity input field — test harness. */
    public InputField quantityField() {
        return quantityField;
    }

    /** Returns the item the user has selected by clicking a gap row — test harness. */
    public ItemId pendingSupplyItem() {
        return pendingSupplyItem;
    }

    /** Returns the most recent successful apply — test harness. {@code null} until the first submit. */
    public AppliedSupply lastApplied() {
        return lastApplied;
    }

    /** Returns the status-bar widget — test harness. */
    public Label statusLabel() {
        return statusLabel;
    }

    @Override
    protected void init() {
        super.init();
        // The Root fills the screen; the intent list draws inside it.
        root.layout(this.width, this.height);
        // The intent list takes the top of the screen; the input field
        // sits in a fixed-height strip at the bottom. We set both bounds
        // explicitly because the intent list is a leaf widget whose
        // bounds the Root filled to the full screen on layout(), and the
        // input field has its default (0, 0, 80, 20) bounds from the
        // convenience constructor.
        int fieldWidth = Math.min(80, this.width - 2 * FIELD_MARGIN_X);
        intentList.setBounds(new Rect(
            0, 0, this.width, Math.max(0, this.height - FIELD_STRIP_HEIGHT)
        ));
        intentList.layout(this.width, Math.max(0, this.height - FIELD_STRIP_HEIGHT));
        // The status bar sits in the same bottom strip as the input
        // field, just above it. Width matches the input field so the
        // text wraps inside the strip; height is one row.
        int statusStripTop = this.height - FIELD_STRIP_HEIGHT - 14;
        statusLabel.setBounds(new Rect(
            FIELD_MARGIN_X,
            Math.max(0, statusStripTop),
            fieldWidth,
            12
        ));
        quantityField.setBounds(new Rect(
            FIELD_MARGIN_X,
            this.height - FIELD_STRIP_HEIGHT + 4,
            fieldWidth,
            20
        ));
    }

@Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Build the per-frame draw context. originX/originY = 0 here
        // because Screen renders in GUI-space relative to the screen's
        // own top-left, not relative to a parent panel.
        McDrawContext ctx = new McDrawContext(
            guiGraphics,
            this.font,
            0,
            0,
            this.width,
            this.height,
            mouseX,
            mouseY
        );
        intentList.draw(ctx);
        statusLabel.draw(ctx);
        quantityField.draw(ctx);
    }

    // ---- Input forwarding ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        root.dispatch(McInputAdapter.mouseDown((int) mouseX, (int) mouseY, button));
        dispatchSupplyClick();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Selects the first positive gap on every screen click — the
     * placeholder behaviour was "fire a packet on click", now it's
     * "remember which gap was clicked and focus the quantity field".
     * The actual packet is sent when the user types a number and presses
     * {@code Enter} in {@link #quantityField}. {@link #pendingSupplyItem}
     * tracks the selection; {@link ItemId#EMPTY} is the "nothing picked"
     * sentinel so {@link #submitQuantity(int)} can no-op safely.
     */
    private void dispatchSupplyClick() {
        if (anchorPos == null) return;
        for (SupplyIntentList.StockGapItem gap : intentList.data().gaps()) {
            if (gap.missing() > 0) {
                pendingSupplyItem = gap.item();
                quantityField.requestFocus();
                return;
            }
        }
    }

    /**
     * Fires one {@code C2SSupplyStockPacket} when the user has typed a
     * positive integer and pressed {@code Enter}. Guards: a quantity of
     * zero or less is ignored (the {@link InputField} already filtered
     * that out at parse time, but the screen re-checks defensively); a
     * missing selection ({@link #pendingSupplyItem} == {@link ItemId#EMPTY})
     * is ignored so the user can't fire a packet for an unselected item;
     * a missing anchor (legacy construction path) is ignored.
     *
     * <p>On a successful submit the status-bar {@link #statusLabel} is
     * updated to read {@code "last supplied: <itemId> ×<qty>"} so the user
     * sees confirmation without waiting for the server's stock-update
     * round-trip. The label is the single source of truth on the client
     * for "what did I just fire?"; the server's stock-update packet
     * overwrites the reserve on the next render but doesn't reach the
     * intent-list gap until then.
     */
    private void submitQuantity(int quantity) {
        if (quantity <= 0) return;
        if (pendingSupplyItem == ItemId.EMPTY) return;
        if (anchorPos == null) return;
        NetworkHelper.sendSupplyStockPacket.send(
            anchorPos,
            pendingSupplyItem.value(),
            quantity
        );
        lastApplied = new AppliedSupply(pendingSupplyItem, quantity);
        statusLabel.setText("last supplied: " + pendingSupplyItem.value() + " x" + quantity);
        quantityField.clear();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        root.dispatch(McInputAdapter.mouseUp((int) mouseX, (int) mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        root.dispatch(McInputAdapter.mouseMoved((int) mouseX, (int) mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        root.dispatch(McInputAdapter.scroll(deltaX, deltaY));
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        root.dispatch(McInputAdapter.keyDown(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        root.dispatch(McInputAdapter.keyUp(keyCode, scanCode, modifiers));
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        root.dispatch(McInputAdapter.charTyped(ch));
        return super.charTyped(ch, modifiers);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        root.dispatch(new UiEvent.Resize(width, height));
    }

    /**
     * Returns the focused widget — exposed for the test harness.
     */
    public Widget focusedWidget() {
        // Focus is owned by the engine; the Root keeps it on the
        // last-clicked child. Without exposing it directly, the test
        // can read {@code intentList.hovered}.
        return null;
    }
}
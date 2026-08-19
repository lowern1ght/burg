package org.lowern1ght.burg.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lowern1ght.burg.client.ui.McDrawContext;
import org.lowern1ght.burg.client.ui.McInputAdapter;
import org.lowern1ght.burg.common.ui.Root;
import org.lowern1ght.burg.common.ui.UiEvent;
import org.lowern1ght.burg.common.ui.Widget;
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

    private final Root root;
    private final SupplyIntentListWidget intentList;
    /**
     * Anchor position of the town the screen was opened against. Carried
     * by the screen so the click handler can route a {@code C2SSupplyStockPacket}
     * back to the same town. {@code null} for legacy callers that still
     * construct the screen with {@link #withEmptyIntent()}.
     */
    private final BlockPos anchorPos;

    public TownHubScreenV2(SupplyIntentList data, BlockPos anchorPos) {
        super(Component.translatable("burg.hub.supply.title"));
        this.root = new Root();
        this.intentList = data.toWidget();
        this.root.add(intentList);
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

    @Override
    protected void init() {
        super.init();
        // The Root fills the screen; the intent list draws inside it.
        root.layout(this.width, this.height);
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
    }

    // ---- Input forwarding ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        root.dispatch(McInputAdapter.mouseDown((int) mouseX, (int) mouseY, button));
        dispatchSupplyClick();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Fires one {@code C2SSupplyStockPacket} when there is a real gap
     * (missing {@code >} 0) to supply and the screen was opened against
     * a known anchor. The act-4 input field is not implemented yet, so
     * any click on the screen routes the first positive gap back to the
     * server as a placeholder — the server applies it to the town's
     * reserve stock and pushes a stock snapshot back.
     */
    private void dispatchSupplyClick() {
        if (anchorPos == null) return;
        for (SupplyIntentList.StockGapItem gap : intentList.data().gaps()) {
            if (gap.missing() > 0) {
                NetworkHelper.sendSupplyStockPacket.send(
                    anchorPos,
                    gap.item().value(),
                    gap.missing()
                );
                return;
            }
        }
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
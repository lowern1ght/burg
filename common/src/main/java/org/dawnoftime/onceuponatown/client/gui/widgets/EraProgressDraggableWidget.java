package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EraProgressDraggableWidget extends DraggableWidget {

    public static final int WIDGET_W         = 240;
    private static final int WIDGET_W_SINGLE = 180;

    private static final int COLOR_MET          = 0xFF55CC55;
    private static final int COLOR_UNMET        = 0xFF555555;
    private static final int COLOR_TEXT         = 0xFFCCCCCC;
    private static final int COLOR_DIM          = 0xFF888888;
    private static final int COLOR_HEADER       = 0xFF888888;
    private static final int COLOR_CARD_BG      = 0xFF1A1A1A;
    private static final int COLOR_CARD_SEL     = 0xFF223322;
    private static final int COLOR_CARD_BORDER  = 0xFF55AA55;
    private static final int CARD_PADDING       = 5;
    private static final int BTN_H              = 12;

    public record CostRow(String itemId, int amount, int have) {}
    public record ReqBuildRow(String defId, int count, int have) {}
    public record SimpleCost(String itemId, int amount) {}
    public record SimpleReq(String defId, int count) {}

    public record UnlockEntry(
        String defId, String iconItem, String category,
        List<SimpleCost> cost, int requiredResidents,
        List<SimpleReq> requiredBuildings, boolean hasProduction
    ) {
        public String buildingName() { return formatBuildingId(defId); }
    }

    public record EraPathOption(
        String id,
        String orientationLabel,
        String iconItem,
        boolean prereqsMet,
        int requiredWeight,
        int currentWeight,
        int maxWeight,
        boolean weightMet,
        List<CostRow> resourceCost,
        int requiredResidents,
        int activeResidents,
        boolean residentsMet,
        List<ReqBuildRow> requiredBuildings,
        List<UnlockEntry> unlocked
    ) {}

    private int currentEra;
    private List<EraPathOption> pathOptions = new ArrayList<>();
    private String selectedPathId = null;
    private final Consumer<String> onAdvance;

    private int contentHeight = 0;
    private final List<CardBounds> cardBounds = new ArrayList<>();
    private final List<int[]> selectBtnBounds = new ArrayList<>(); // {x, y, w, h, cardIdx}

    private record CardBounds(int x, int y, int w, int h, int cardIndex) {}

    private static int computeWidgetW(List<EraPathOption> options) {
        return options.size() == 1 ? WIDGET_W_SINGLE : WIDGET_W;
    }

    public EraProgressDraggableWidget(int x, int y, int freeZoneMaxX, int screenH,
                                       int currentEra, List<EraPathOption> pathOptions,
                                       Consumer<String> onAdvance) {
        super(x, y, computeWidgetW(pathOptions), TITLE_BAR_H + computeContentH(pathOptions), freeZoneMaxX, screenH);
        this.currentEra = currentEra;
        this.pathOptions = new ArrayList<>(pathOptions);
        this.onAdvance = onAdvance;
        this.contentHeight = computeContentH(pathOptions);
    }

    public void updateData(int currentEra, List<EraPathOption> pathOptions) {
        this.currentEra = currentEra;
        this.pathOptions = new ArrayList<>(pathOptions);
        this.contentHeight = computeContentH(pathOptions);
        this.width = computeWidgetW(pathOptions);
        setHeight(TITLE_BAR_H + this.contentHeight);
        if (selectedPathId != null && pathOptions.stream().noneMatch(p -> p.id().equals(selectedPathId))) {
            selectedPathId = null;
        }
    }

    // -------------------------------------------------------------------------
    // Layout computation (single source of truth for card height)
    // -------------------------------------------------------------------------

    private static int computeUnifiedCardH(EraPathOption opt) {
        int h = CARD_PADDING;
        h += 10 + 5 + 4; // icon+name row, gap, pre-requirements gap
        h += 8 + 2;      // "Requirements:" label + gap
        h += 11;         // weight row
        h += opt.resourceCost().size() * 11;
        if (opt.requiredResidents() > 0) h += 11;
        h += opt.requiredBuildings().size() * 11;
        h += 5 + BTN_H;  // gap + Select button
        h += CARD_PADDING;
        return h;
    }

    private static int computeContentH(List<EraPathOption> options) {
        if (options.isEmpty()) return 30;
        int maxCardH = options.stream().mapToInt(EraProgressDraggableWidget::computeUnifiedCardH).max().orElse(60);
        return 4 + maxCardH + 8;
    }

    // -------------------------------------------------------------------------
    // Titlebar extras: "Advance Era" button
    // -------------------------------------------------------------------------

    @Override
    protected void renderTitleBarExtras(GuiGraphics g, int mouseX, int mouseY) {
        if (pathOptions.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        String btnText = "Advance Era";
        int btnW = font.width(btnText) + 6;
        int btnX = closeBtnX() - 2 - btnW;
        boolean canAdvance = getActivePrereqsMet();
        boolean hover = canAdvance && mouseX >= btnX && mouseX < btnX + btnW
            && mouseY >= y && mouseY < y + TITLE_BAR_H;
        int bgColor = canAdvance ? (hover ? 0xFF338833 : 0xFF225522) : 0xFF222222;
        g.fill(btnX, y + 1, btnX + btnW, y + TITLE_BAR_H - 1, bgColor);
        g.drawString(font, btnText, btnX + 3, y + 2, canAdvance ? 0xFF88FF88 : 0xFF555555, false);
    }

    @Override
    protected boolean onTitleBarClick(double mouseX, double mouseY) {
        if (pathOptions.isEmpty()) return false;
        var font = Minecraft.getInstance().font;
        String btnText = "Advance Era";
        int btnW = font.width(btnText) + 6;
        int btnX = closeBtnX() - 2 - btnW;
        if (mouseX >= btnX && mouseX < btnX + btnW
                && mouseY >= y && mouseY < y + TITLE_BAR_H) {
            if (getActivePrereqsMet()) {
                if (pathOptions.size() == 1) {
                    onAdvance.accept(pathOptions.get(0).id());
                } else if (selectedPathId != null) {
                    onAdvance.accept(selectedPathId);
                }
            }
            return true;
        }
        return false;
    }

    private boolean getActivePrereqsMet() {
        if (pathOptions.size() == 1) return pathOptions.get(0).prereqsMet();
        if (selectedPathId != null) {
            return pathOptions.stream()
                .filter(p -> p.id().equals(selectedPathId))
                .findFirst()
                .map(EraPathOption::prereqsMet)
                .orElse(false);
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Content rendering (unified layout for 1 or N cards)
    // -------------------------------------------------------------------------

    @Override
    protected String getTitle() { return "Era Progress"; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                  int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        g.fill(cx, cy, cx + cw, cy + ch, 0xF5111111);

        cardBounds.clear();
        selectBtnBounds.clear();

        if (pathOptions.isEmpty()) {
            g.drawString(font, "No transitions available", cx + 4, cy + 10, COLOR_DIM, false);
            return;
        }

        int maxCardH = pathOptions.stream().mapToInt(EraProgressDraggableWidget::computeUnifiedCardH).max().orElse(60);
        boolean multiPath = pathOptions.size() > 1;

        int cardCount = pathOptions.size();
        int gapBetween = 6;
        int totalGaps = (cardCount - 1) * gapBetween + 8;
        int cardW = (cw - totalGaps) / cardCount;

        for (int ci = 0; ci < cardCount; ci++) {
            EraPathOption opt = pathOptions.get(ci);
            int cardX = cx + 4 + ci * (cardW + gapBetween);
            int cardY = cy + 4;
            boolean selected = opt.id().equals(selectedPathId);
            renderCard(g, font, cardX, cardY, cardW, maxCardH, mx, my, opt, ci, selected, multiPath);
        }
    }

    private void renderCard(GuiGraphics g, net.minecraft.client.gui.Font font,
                             int cx, int cy, int cw, int fixedH,
                             int mx, int my, EraPathOption opt, int cardIdx,
                             boolean selected, boolean selectable) {
        boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + fixedH;
        g.fill(cx, cy, cx + cw, cy + fixedH, selected ? COLOR_CARD_SEL : COLOR_CARD_BG);
        if (selected || hover) {
            g.fill(cx,           cy,              cx + cw,     cy + 1,           COLOR_CARD_BORDER);
            g.fill(cx,           cy + fixedH - 1, cx + cw,     cy + fixedH,      COLOR_CARD_BORDER);
            g.fill(cx,           cy,              cx + 1,      cy + fixedH,      COLOR_CARD_BORDER);
            g.fill(cx + cw - 1, cy,               cx + cw,     cy + fixedH,      COLOR_CARD_BORDER);
        }

        cardBounds.add(new CardBounds(cx, cy, cw, fixedH, cardIdx));

        int rowY = cy + CARD_PADDING;

        renderItemIcon(g, opt.iconItem(), cx + CARD_PADDING, rowY);
        g.drawString(font, opt.orientationLabel(), cx + CARD_PADDING + 18, rowY + 3, 0xFFEEEEEE, false);
        rowY += 10 + 5 + 4;

        g.drawString(font, "Requirements:", cx + CARD_PADDING, rowY, COLOR_HEADER, false);
        rowY += 8 + 2;

        renderCondRow(g, font, cx + CARD_PADDING, rowY,
            opt.currentWeight() + "/" + opt.maxWeight() + " weight", opt.weightMet());
        rowY += 11;

        for (CostRow cr : opt.resourceCost()) {
            renderCondRow(g, font, cx + CARD_PADDING, rowY,
                cr.amount() + "x " + formatItemId(cr.itemId()), cr.have() >= cr.amount());
            rowY += 11;
        }

        if (opt.requiredResidents() > 0) {
            renderCondRow(g, font, cx + CARD_PADDING, rowY,
                opt.requiredResidents() + " residents", opt.residentsMet());
            rowY += 11;
        }

        for (ReqBuildRow rb : opt.requiredBuildings()) {
            renderCondRow(g, font, cx + CARD_PADDING, rowY,
                rb.count() + "x " + formatBuildingId(rb.defId()), rb.have() >= rb.count());
            rowY += 11;
        }

        // Select button anchored to the bottom of fixedH
        int btnY  = cy + fixedH - CARD_PADDING - BTN_H;
        int btnW  = cw - CARD_PADDING * 2;
        int btnX  = cx + CARD_PADDING;
        boolean isSelected = opt.id().equals(selectedPathId);
        boolean btnHover   = selectable && mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + BTN_H;

        int btnColor;
        if (!selectable) {
            btnColor = 0xFF222222;
        } else if (isSelected) {
            btnColor = btnHover ? 0xFF55AA55 : 0xFF336633;
        } else {
            btnColor = btnHover ? 0xFF555555 : 0xFF333333;
        }

        g.fill(btnX, btnY, btnX + btnW, btnY + BTN_H, btnColor);
        String btnText      = (!selectable || isSelected) ? "Selected" : "Select";
        int    btnTextColor = (!selectable || isSelected) ? 0xFF88FF88 : 0xFFCCCCCC;
        if (!selectable) btnTextColor = 0xFF444444;
        g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, btnY + 2, btnTextColor, false);
        selectBtnBounds.add(new int[]{ btnX, btnY, btnW, BTN_H, cardIdx });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void renderCondRow(GuiGraphics g, net.minecraft.client.gui.Font font,
                                       int x, int y, String text, boolean met) {
        g.fill(x, y + 2, x + 4, y + 6, met ? COLOR_MET : COLOR_UNMET);
        g.drawString(font, text, x + 7, y, met ? COLOR_TEXT : COLOR_DIM, false);
    }

    private static void renderItemIcon(GuiGraphics g, String iconItemId, int x, int y) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(iconItemId));
            if (item != net.minecraft.world.item.Items.AIR) {
                g.renderFakeItem(new ItemStack(item), x, y);
            }
        } catch (Exception ignored) {}
    }


    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return isMouseOver(mouseX, mouseY);

        if (pathOptions.size() > 1) {
            for (int[] bb : selectBtnBounds) {
                if (mouseX >= bb[0] && mouseX < bb[0] + bb[2]
                        && mouseY >= bb[1] && mouseY < bb[1] + bb[3]) {
                    int cardIdx = bb[4];
                    if (cardIdx < pathOptions.size()) {
                        selectedPathId = pathOptions.get(cardIdx).id();
                    }
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    private static String formatItemId(String itemId) {
        String name = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String formatBuildingId(String id) { return formatItemId(id); }
}

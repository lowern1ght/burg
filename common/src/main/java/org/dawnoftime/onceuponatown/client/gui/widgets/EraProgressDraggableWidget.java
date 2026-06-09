package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EraProgressDraggableWidget extends DraggableWidget {

    public static final int WIDGET_W = 240;

    private static final int COLOR_MET      = 0xFF55CC55;
    private static final int COLOR_UNMET    = 0xFF555555;
    private static final int COLOR_TEXT     = 0xFFCCCCCC;
    private static final int COLOR_DIM      = 0xFF888888;
    private static final int COLOR_HEADER   = 0xFF888888;
    private static final int COLOR_CARD_BG  = 0xFF1A1A1A;
    private static final int COLOR_CARD_SEL = 0xFF223322;
    private static final int COLOR_CARD_BORDER = 0xFF55AA55;
    private static final int CARD_PADDING   = 5;
    private static final int ITEM_ICON_SIZE = 16;
    private static final int UNLOCK_CELL    = 16;

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
        String displayName,
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
    private final List<int[]> iconBounds = new ArrayList<>();       // {x, y, cardIdx, iconIdx}
    private final List<int[]> advanceBtnBounds = new ArrayList<>(); // {x, y, w, h, cardIdx}

    private record CardBounds(int x, int y, int w, int h, int cardIndex) {}

    public EraProgressDraggableWidget(int x, int y, int freeZoneMaxX, int screenH,
                                       int currentEra, List<EraPathOption> pathOptions,
                                       Consumer<String> onAdvance) {
        super(x, y, WIDGET_W, TITLE_BAR_H + computeContentH(pathOptions), freeZoneMaxX, screenH);
        this.currentEra = currentEra;
        this.pathOptions = new ArrayList<>(pathOptions);
        this.onAdvance = onAdvance;
        this.contentHeight = computeContentH(pathOptions);
    }

    public void updateData(int currentEra, List<EraPathOption> pathOptions) {
        this.currentEra = currentEra;
        this.pathOptions = new ArrayList<>(pathOptions);
        this.contentHeight = computeContentH(pathOptions);
        setHeight(TITLE_BAR_H + this.contentHeight);
        if (selectedPathId != null && pathOptions.stream().noneMatch(p -> p.id().equals(selectedPathId))) {
            selectedPathId = null;
        }
    }

    // -------------------------------------------------------------------------
    // Layout computation
    // -------------------------------------------------------------------------

    private static int computeContentH(List<EraPathOption> options) {
        if (options.isEmpty()) return 30;
        if (options.size() > 1) {
            int cardH = computeCardH(options.stream().mapToInt(o -> o.unlocked().size()).max().orElse(0));
            return 14 + cardH + 8;
        }
        EraPathOption opt = options.get(0);
        int h = 16; // subtitle line
        h += 12;    // displayName
        h += 10;    // "Requirements" label
        h += countPrereqRows(opt) * 10;
        if (!opt.unlocked().isEmpty()) {
            h += 4 + 10 + UNLOCK_CELL + 4;
        }
        h += 8; // bottom padding
        return h;
    }

    private static int countPrereqRows(EraPathOption opt) {
        int count = 1; // weight
        count += opt.resourceCost().size();
        if (opt.requiredResidents() > 0) count++;
        count += opt.requiredBuildings().size();
        return count;
    }

    private static int computeCardH(int maxUnlockedIcons) {
        int h = CARD_PADDING;
        h += 10; // display name
        h += 5;
        h += 8;  // "Requirements:" label
        h += 10; // weight row
        h += 3 * 10; // up to 3 cost rows
        h += 10; // residents row
        if (maxUnlockedIcons > 0) {
            h += 5 + 8 + UNLOCK_CELL + 4;
        }
        h += 5 + 14; // SELECT button
        h += CARD_PADDING;
        return h;
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
            return true; // consume click even if not ready
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
    // Content rendering
    // -------------------------------------------------------------------------

    @Override
    protected String getTitle() { return "Era Progress"; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                  int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        g.fill(cx, cy, cx + cw, cy + ch, 0xF5111111);

        cardBounds.clear();
        iconBounds.clear();
        advanceBtnBounds.clear();

        if (pathOptions.isEmpty()) {
            g.drawString(font, "No transitions available", cx + 4, cy + 10, COLOR_DIM, false);
            return;
        }

        String subtitle = "Era " + currentEra + " -> Era " + (currentEra + 1);
        g.drawString(font, subtitle, cx + 4, cy + 4, COLOR_HEADER, false);

        if (pathOptions.size() > 1) {
            renderForkLayout(g, font, cx, cy + 16, cw, mx, my);
        } else {
            renderSingleLayout(g, font, cx, cy + 16, cw, mx, my, pathOptions.get(0));
        }

        // Unlock cell hover popup rendered on top of everything
        renderHoveredUnlockPopup(g, font, mx, my);
    }

    private void renderSingleLayout(GuiGraphics g, net.minecraft.client.gui.Font font,
                                     int cx, int cy, int cw, int mx, int my, EraPathOption opt) {
        int y = cy;

        g.drawString(font, opt.displayName(), cx + 4, y, 0xFFEEEEEE, false);
        y += 12;

        g.drawString(font, "Requirements", cx + 4, y, COLOR_HEADER, false);
        y += 10;

        renderCondRow(g, font, cx + 4, y, "Weight: " + opt.currentWeight() + "/" + opt.maxWeight(), opt.weightMet());
        y += 10;

        for (CostRow cr : opt.resourceCost()) {
            boolean met = cr.have() >= cr.amount();
            renderCondRow(g, font, cx + 4, y, formatItemId(cr.itemId()) + ": " + cr.have() + "/" + cr.amount(), met);
            y += 10;
        }

        if (opt.requiredResidents() > 0) {
            renderCondRow(g, font, cx + 4, y,
                "Residents: " + opt.activeResidents() + "/" + opt.requiredResidents(), opt.residentsMet());
            y += 10;
        }

        List<UnlockEntry> unlocked = opt.unlocked();
        if (!unlocked.isEmpty()) {
            y += 4;
            g.drawString(font, "Unlocks", cx + 4, y, COLOR_HEADER, false);
            y += 10;
            int iconX = cx + 4;
            for (int i = 0; i < unlocked.size(); i++) {
                renderUnlockCell(g, unlocked.get(i), iconX, y);
                iconBounds.add(new int[]{ iconX, y, 0, i });
                iconX += UNLOCK_CELL + 3;
            }
        }
    }

    private void renderForkLayout(GuiGraphics g, net.minecraft.client.gui.Font font,
                                   int cx, int cy, int cw, int mx, int my) {
        g.drawString(font, "Choose your specialization:", cx + 4, cy, COLOR_DIM, false);

        int cardCount = pathOptions.size();
        int gapBetween = 6;
        int totalGaps = (cardCount - 1) * gapBetween + 8;
        int cardW = (cw - totalGaps) / cardCount;

        for (int ci = 0; ci < cardCount; ci++) {
            EraPathOption opt = pathOptions.get(ci);
            int cardX = cx + 4 + ci * (cardW + gapBetween);
            int cardY = cy + 12;
            boolean selected = opt.id().equals(selectedPathId);
            renderCard(g, font, cardX, cardY, cardW, mx, my, opt, ci, selected);
        }
    }

    private void renderCard(GuiGraphics g, net.minecraft.client.gui.Font font,
                             int cx, int cy, int cw, int mx, int my,
                             EraPathOption opt, int cardIdx, boolean selected) {
        int cardH = CARD_PADDING;
        cardH += 10 + 5;
        cardH += 8 + 10;
        cardH += opt.resourceCost().size() * 10;
        if (opt.requiredResidents() > 0) cardH += 10;
        cardH += opt.requiredBuildings().size() * 10;
        if (!opt.unlocked().isEmpty()) cardH += 5 + 8 + UNLOCK_CELL + 4;
        cardH += 5 + 14 + CARD_PADDING;

        boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + cardH;
        g.fill(cx, cy, cx + cw, cy + cardH, selected ? COLOR_CARD_SEL : COLOR_CARD_BG);
        if (selected || hover) {
            g.fill(cx,           cy,            cx + cw,     cy + 1,          COLOR_CARD_BORDER);
            g.fill(cx,           cy + cardH - 1, cx + cw,    cy + cardH,      COLOR_CARD_BORDER);
            g.fill(cx,           cy,            cx + 1,      cy + cardH,      COLOR_CARD_BORDER);
            g.fill(cx + cw - 1, cy,             cx + cw,     cy + cardH,      COLOR_CARD_BORDER);
        }

        cardBounds.add(new CardBounds(cx, cy, cw, cardH, cardIdx));

        int y = cy + CARD_PADDING;

        renderItemIcon(g, opt.iconItem(), cx + CARD_PADDING, y);
        g.drawString(font, opt.displayName(), cx + CARD_PADDING + 18, y + 3, 0xFFEEEEEE, false);
        y += 10 + 5;

        g.drawString(font, "Requirements:", cx + CARD_PADDING, y, COLOR_HEADER, false);
        y += 8;

        renderCondRow(g, font, cx + CARD_PADDING, y,
            opt.currentWeight() + "/" + opt.maxWeight() + " weight", opt.weightMet());
        y += 10;

        for (CostRow cr : opt.resourceCost()) {
            renderCondRow(g, font, cx + CARD_PADDING, y,
                cr.amount() + "x " + formatItemId(cr.itemId()), cr.have() >= cr.amount());
            y += 10;
        }

        if (opt.requiredResidents() > 0) {
            renderCondRow(g, font, cx + CARD_PADDING, y,
                opt.requiredResidents() + " residents", opt.residentsMet());
            y += 10;
        }

        for (ReqBuildRow rb : opt.requiredBuildings()) {
            renderCondRow(g, font, cx + CARD_PADDING, y,
                rb.count() + "x " + formatBuildingId(rb.defId()), rb.have() >= rb.count());
            y += 10;
        }

        List<UnlockEntry> unlocked = opt.unlocked();
        if (!unlocked.isEmpty()) {
            y += 5;
            g.drawString(font, "Unlocks:", cx + CARD_PADDING, y, COLOR_HEADER, false);
            y += 8;
            int iconX = cx + CARD_PADDING;
            for (int ii = 0; ii < unlocked.size(); ii++) {
                renderUnlockCell(g, unlocked.get(ii), iconX, y);
                iconBounds.add(new int[]{ iconX, y, cardIdx, ii });
                iconX += UNLOCK_CELL + 3;
            }
            y += UNLOCK_CELL + 4;
        }

        y += 5;
        int btnW = cw - CARD_PADDING * 2;
        int btnH = 12;
        int btnX = cx + CARD_PADDING;
        boolean btnHover = mx >= btnX && mx < btnX + btnW && my >= y && my < y + btnH;
        boolean isSelected = opt.id().equals(selectedPathId);
        int btnColor = isSelected
            ? (btnHover ? 0xFF55AA55 : 0xFF336633)
            : (btnHover ? 0xFF555555 : 0xFF333333);
        g.fill(btnX, y, btnX + btnW, y + btnH, btnColor);
        String btnText = isSelected ? "Selected" : "Select";
        int btnTextColor = isSelected ? 0xFF88FF88 : 0xFFCCCCCC;
        g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, y + 2, btnTextColor, false);
        advanceBtnBounds.add(new int[]{ btnX, y, btnW, btnH, cardIdx });
    }

    // -------------------------------------------------------------------------
    // Unlock cell rendering
    // -------------------------------------------------------------------------

    private static void renderUnlockCell(GuiGraphics g, UnlockEntry entry, int x, int y) {
        g.fill(x, y, x + UNLOCK_CELL, y + UNLOCK_CELL, categoryColor(entry.category()));
        renderItemIcon(g, entry.iconItem(), x, y);
    }

    private void renderHoveredUnlockPopup(GuiGraphics g, net.minecraft.client.gui.Font font, int mx, int my) {
        for (int[] ib : iconBounds) {
            int ix = ib[0], iy = ib[1];
            if (mx >= ix && mx < ix + UNLOCK_CELL && my >= iy && my < iy + UNLOCK_CELL) {
                int cardIdx = ib[2], iconIdx = ib[3];
                List<UnlockEntry> unlocked = cardIdx < pathOptions.size()
                    ? pathOptions.get(cardIdx).unlocked()
                    : List.of();
                if (iconIdx < unlocked.size()) {
                    g.renderComponentTooltip(font, buildUnlockTooltip(unlocked.get(iconIdx)), mx, my);
                }
                return;
            }
        }
    }

    private static List<Component> buildUnlockTooltip(UnlockEntry e) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(e.buildingName()).withStyle(s -> s.withBold(true)));
        lines.add(Component.literal("Category: " + formatBuildingId(e.category()))
            .withStyle(s -> s.withColor(0xAAAAAA)));

        if (!e.cost().isEmpty()) {
            lines.add(Component.literal("Construction cost:").withStyle(s -> s.withColor(0xCCCCCC)));
            for (SimpleCost c : e.cost()) {
                String name = c.itemId().contains(":")
                    ? c.itemId().substring(c.itemId().indexOf(':') + 1) : c.itemId();
                lines.add(Component.literal("  " + c.amount() + "x " + formatItemId(name))
                    .withStyle(s -> s.withColor(0xCCCCCC)));
            }
        } else {
            lines.add(Component.literal("Free").withStyle(s -> s.withColor(0x55FF55)));
        }

        if (e.requiredResidents() > 0 || !e.requiredBuildings().isEmpty()) {
            lines.add(Component.literal("Requires:").withStyle(s -> s.withColor(0xCCCCCC)));
            if (e.requiredResidents() > 0) {
                lines.add(Component.literal("  " + e.requiredResidents() + " active residents")
                    .withStyle(s -> s.withColor(0xAAAAAA)));
            }
            for (SimpleReq r : e.requiredBuildings()) {
                lines.add(Component.literal("  " + r.count() + "x " + formatBuildingId(r.defId()))
                    .withStyle(s -> s.withColor(0xAAAAAA)));
            }
        }

        if (e.hasProduction()) {
            lines.add(Component.literal("Right-click: View production").withStyle(s -> s.withColor(0x888888)));
        }

        return lines;
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

    private static int categoryColor(String category) {
        return switch (category) {
            case "town_center" -> 0xFFFFAA00;
            case "jobs"        -> 0xFFCC3333;
            case "gardens"     -> 0xFF33AA33;
            case "natural"     -> 0xFF666666;
            case "buildings"   -> 0xFF885533;
            default            -> 0xFF444444;
        };
    }

    public List<Component> getHoveredTooltip(double mx, double my) {
        // Advance button hover with unmet prereqs (single-path mode)
        if (pathOptions.size() == 1 && !pathOptions.get(0).prereqsMet()) {
            for (int[] bb : advanceBtnBounds) {
                if (mx >= bb[0] && mx < bb[0] + bb[2] && my >= bb[1] && my < bb[1] + bb[3]) {
                    return buildUnmetTooltip(pathOptions.get(0));
                }
            }
        }
        return List.of();
    }

    private static List<Component> buildUnmetTooltip(EraPathOption opt) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Requirements not met:").withStyle(s -> s.withColor(0xFFFF5555)));
        if (!opt.weightMet()) {
            lines.add(Component.literal("  Weight: " + opt.currentWeight() + "/" + opt.requiredWeight() + " min")
                .withStyle(s -> s.withColor(0xFFAAAAAA)));
        }
        for (CostRow cr : opt.resourceCost()) {
            if (cr.have() < cr.amount()) {
                lines.add(Component.literal("  " + formatItemId(cr.itemId()) + ": " + cr.have() + "/" + cr.amount())
                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
            }
        }
        if (opt.requiredResidents() > 0 && !opt.residentsMet()) {
            lines.add(Component.literal("  Residents: " + opt.activeResidents() + "/" + opt.requiredResidents())
                .withStyle(s -> s.withColor(0xFFAAAAAA)));
        }
        return lines;
    }

    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return isMouseOver(mouseX, mouseY);

        for (int[] bb : advanceBtnBounds) {
            if (mouseX >= bb[0] && mouseX < bb[0] + bb[2]
                    && mouseY >= bb[1] && mouseY < bb[1] + bb[3]) {
                int cardIdx = bb[4];
                if (cardIdx < pathOptions.size() && pathOptions.size() > 1) {
                    // Fork mode: SELECT button only selects the path
                    selectedPathId = pathOptions.get(cardIdx).id();
                }
                return true;
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

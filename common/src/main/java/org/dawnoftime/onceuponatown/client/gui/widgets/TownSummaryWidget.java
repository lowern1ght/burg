package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.dawnoftime.onceuponatown.town.TownLogEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TownSummaryWidget extends DraggableWidget {

    public static final int WIDGET_W  = 175;
    public static final int VISIBLE_H = 180;

    private static final int HEADER_H    = 12;
    private static final int ITEM_H      = 16;
    private static final int PADDING     = 3;
    private static final int SCROLLBAR_W = 6;
    private static final int TOGGLE_BTN_W = 10;
    private static final int TOGGLE_BTN_GAP = 2;

    private static final int COLOR_HEADER_BG   = 0xF52A2A2A;
    private static final int COLOR_SECTION_TEXT = 0xFFAAAAFF;
    private static final int COLOR_ORIENT_TEXT  = 0xFFEECC66;
    private static final int COLOR_CONTENT_BG   = 0xF51A1A1A;

    private static final String[] TOGGLE_LABELS = {"I", "P", "T", "L"};

    // Toggle block visibility (session-only, reset to true on load)
    static boolean blockInfoVisible           = true;
    static boolean blockProductionVisible     = true;
    static boolean blockTransformationVisible = true;
    static boolean blockLogVisible            = false;

    // Activity log entries: newest first (index 0 = most recent)
    private final ArrayDeque<TownLogEntry> logEntries = new ArrayDeque<>();

    private record Row(ItemStack icon, Component text, boolean isSection) {}

    private final List<Row> rows = new ArrayList<>();
    private int scrollPx = 0;
    private int totalH   = 0;

    // Scrollbar drag state
    private boolean draggingScrollbar = false;
    private double  dragStartMouseY   = 0;
    private int     dragStartScrollPx = 0;

    private CompoundTag cachedMapData;
    private CompoundTag cachedSummaryData;

    public TownSummaryWidget(CompoundTag mapData, CompoundTag summaryData,
                              int x, int y, int freeZoneMaxX, int screenH) {
        super(x, y, WIDGET_W, TITLE_BAR_H + VISIBLE_H, freeZoneMaxX, screenH);
        this.cachedMapData = mapData;
        this.cachedSummaryData = summaryData;
        buildRows(mapData, summaryData);
    }

    public void updateCitizenData(int totalResidents, int activeResidents, int totalFoodDemand,
                                   int totalHerd, int activeHerd) {
        cachedSummaryData.putInt("TotalResidents", totalResidents);
        cachedSummaryData.putInt("ActiveResidents", activeResidents);
        cachedSummaryData.putInt("TotalFoodDemand", totalFoodDemand);
        cachedSummaryData.putInt("TotalHerd", totalHerd);
        cachedSummaryData.putInt("ActiveHerd", activeHerd);
        rows.clear();
        buildRows(cachedMapData, cachedSummaryData);
    }

    // -------------------------------------------------------------------------
    // Title bar toggle buttons (I / P / T)
    // -------------------------------------------------------------------------

    private int toggleBtnX(int index) {
        // 0=I, 1=P, 2=T, 3=L -- placed left of the X button with 2px gaps
        return closeBtnX() - (4 - index) * (TOGGLE_BTN_W + TOGGLE_BTN_GAP);
    }

    @Override
    protected void renderTitleBarExtras(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        boolean[] states = {blockInfoVisible, blockProductionVisible, blockTransformationVisible, blockLogVisible};
        for (int i = 0; i < 4; i++) {
            int bx     = toggleBtnX(i);
            boolean hover   = mouseX >= bx && mouseX < bx + TOGGLE_BTN_W
                           && mouseY >= y  && mouseY < y + TITLE_BAR_H;
            boolean active  = states[i];
            int bg = active ? (hover ? 0xFF2A5A2A : 0xFF224422)
                            : (hover ? 0xFF444444 : 0xFF333333);
            int fg = active ? 0xFFAAFFAA : 0xFF888888;
            g.fill(bx, y + 1, bx + TOGGLE_BTN_W, y + TITLE_BAR_H - 1, bg);
            g.drawString(font, TOGGLE_LABELS[i], bx + 2, y + 2, fg, false);
        }
    }

    @Override
    protected boolean onTitleBarClick(double mouseX, double mouseY) {
        for (int i = 0; i < 4; i++) {
            int bx = toggleBtnX(i);
            if (mouseX >= bx && mouseX < bx + TOGGLE_BTN_W
                    && mouseY >= y && mouseY < y + TITLE_BAR_H) {
                switch (i) {
                    case 0 -> blockInfoVisible           = !blockInfoVisible;
                    case 1 -> blockProductionVisible     = !blockProductionVisible;
                    case 2 -> blockTransformationVisible = !blockTransformationVisible;
                    case 3 -> blockLogVisible            = !blockLogVisible;
                }
                rows.clear();
                buildRows(cachedMapData, cachedSummaryData);
                scrollPx = 0;
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Log entry management
    // -------------------------------------------------------------------------

    public void appendLogEntry(TownLogEntry entry) {
        logEntries.addFirst(entry);
        if (logEntries.size() > 20) logEntries.removeLast();
        if (blockLogVisible) {
            rows.clear();
            buildRows(cachedMapData, cachedSummaryData);
        }
    }

    // Loads the initial log snapshot (oldest to newest order) received from the hub packet.
    public void loadInitialLog(List<TownLogEntry> entries) {
        logEntries.clear();
        // entries is oldest-first from the server; insert newest-first into the deque
        for (int i = entries.size() - 1; i >= 0; i--) {
            logEntries.addLast(entries.get(i));
        }
        if (blockLogVisible) {
            rows.clear();
            buildRows(cachedMapData, cachedSummaryData);
        }
    }

    private static int logColor(TownLogEntry.TownLogType type) {
        return switch (type) {
            case BUILD_START, UPGRADE_START -> 0xFFAAAAFF;
            case BUILD_DONE, UPGRADE_DONE   -> 0xFF55FF55;
            case FOOD_CONSUMED              -> 0xFFDDDDDD;
            case VILLAGE_FULL               -> 0xFFFF5555;
        };
    }

    private Component logEntryToComponent(TownLogEntry entry) {
        String param = entry.param();
        String resolved = param.isEmpty() ? "" : Component.translatable("onceuponatown.building." + param).getString();
        String text = switch (entry.type()) {
            case BUILD_START    -> "Builder: starting " + resolved;
            case BUILD_DONE     -> "Builder: " + resolved + " built";
            case UPGRADE_START  -> "Builder: upgrading " + resolved;
            case UPGRADE_DONE   -> "Builder: " + resolved + " upgraded";
            case FOOD_CONSUMED  -> "Village consumed " + param + " food units";
            case VILLAGE_FULL   -> "Village: no space left to expand";
        };
        int color = logColor(entry.type());
        return Component.literal(text).withStyle(s -> s.withColor(color));
    }

    // -------------------------------------------------------------------------
    // Row building
    // -------------------------------------------------------------------------

    private void buildRows(CompoundTag mapData, CompoundTag summaryData) {
        if (blockLogVisible) {
            if (logEntries.isEmpty()) {
                rows.add(new Row(null, Component.literal("No activity yet.").withStyle(s -> s.withColor(0xFF888888)), false));
            } else {
                for (TownLogEntry e : logEntries) {
                    rows.add(new Row(null, logEntryToComponent(e), false));
                }
            }
            totalH = PADDING;
            for (Row r : rows) totalH += r.isSection() ? HEADER_H : ITEM_H;
            totalH += PADDING;
            return;
        }

        String orientation  = summaryData.getString("Orientation");
        int totalResidents  = summaryData.getInt("TotalResidents");
        int activeResidents = summaryData.getInt("ActiveResidents");
        int totalFoodDemand = summaryData.getInt("TotalFoodDemand");
        int totalHerd       = summaryData.getInt("TotalHerd");
        int activeHerd      = summaryData.getInt("ActiveHerd");

        if (blockInfoVisible) {
            rows.add(new Row(null,
                Component.literal("Orientation: " + capitalize(orientation))
                    .withStyle(s -> s.withColor(COLOR_ORIENT_TEXT)),
                false));

            if (totalResidents > 0) {
                int resColor = activeResidents >= totalResidents ? 0xFF55FF55
                             : activeResidents * 2 >= totalResidents ? 0xFFFFAA00
                             : 0xFFFF5555;
                Item egg = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:villager_spawn_egg"));
                MutableComponent txt = Component.literal("Residents: ")
                    .withStyle(s -> s.withColor(0xFFCCCCCC))
                    .append(Component.literal(String.valueOf(activeResidents)).withStyle(s -> s.withColor(resColor)))
                    .append(Component.literal(" / " + totalResidents).withStyle(s -> s.withColor(0xFFCCCCCC)));
                rows.add(new Row(new ItemStack(egg), txt, false));
            }

            if (totalHerd > 0) {
                int herdColor = activeHerd >= totalHerd ? 0xFF55FF55
                              : activeHerd * 2 >= totalHerd ? 0xFFFFAA00
                              : 0xFFFF5555;
                Item pig = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:pig_spawn_egg"));
                MutableComponent txt = Component.literal("Herd: ")
                    .withStyle(s -> s.withColor(0xFFCCCCCC))
                    .append(Component.literal(String.valueOf(activeHerd)).withStyle(s -> s.withColor(herdColor)))
                    .append(Component.literal(" / " + totalHerd).withStyle(s -> s.withColor(0xFFCCCCCC)));
                rows.add(new Row(new ItemStack(pig), txt, false));
            }

            if (totalFoodDemand > 0) {
                rows.add(new Row(new ItemStack(Items.BREAD),
                    Component.literal(totalFoodDemand + " food units / day").withStyle(s -> s.withColor(0xFFDDDDDD)),
                    false));
            }
        }

        Map<String, int[]> prodData   = new LinkedHashMap<>();
        Map<String, int[]> transforms = new LinkedHashMap<>();

        ListTag elements = mapData.getList("Elements", Tag.TAG_COMPOUND);
        for (Tag rawEl : elements) {
            CompoundTag el = (CompoundTag) rawEl;
            if (el.getByte("Category") != 2) continue;

            if (blockProductionVisible) {
                for (Tag rawP : el.getList("Production", Tag.TAG_COMPOUND)) {
                    CompoundTag pt = (CompoundTag) rawP;
                    String itemId  = pt.getString("Item");
                    int amount     = pt.getInt("Amount");
                    int ticks      = pt.getInt("EveryTicks");
                    prodData.merge(itemId, new int[]{amount, ticks},
                        (a, b) -> new int[]{a[0] + b[0], a[1]});
                }
            }

            if (blockTransformationVisible) {
                for (Tag rawT : el.getList("Transforms", Tag.TAG_COMPOUND)) {
                    CompoundTag tt = (CompoundTag) rawT;
                    String outId   = tt.getString("OutputItem");
                    if (!transforms.containsKey(outId)) {
                        transforms.put(outId, new int[]{tt.getInt("OutputAmount"), tt.getInt("EveryTicks")});
                    }
                }
            }
        }

        if (!prodData.isEmpty()) {
            rows.add(new Row(null, Component.literal("Production").withStyle(s -> s.withColor(COLOR_SECTION_TEXT)), true));
            for (Map.Entry<String, int[]> e : prodData.entrySet()) {
                Item item    = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int amount   = e.getValue()[0];
                int seconds  = e.getValue()[1] / 20;
                rows.add(new Row(new ItemStack(item),
                    Component.literal("x" + amount + " " + shortId(e.getKey()) + " / " + seconds + "s")
                        .withStyle(s -> s.withColor(0xFFDDDDDD)),
                    false));
            }
        }

        if (!transforms.isEmpty()) {
            rows.add(new Row(null, Component.literal("Transformations").withStyle(s -> s.withColor(COLOR_SECTION_TEXT)), true));
            for (Map.Entry<String, int[]> e : transforms.entrySet()) {
                Item item   = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int seconds = e.getValue()[1] / 20;
                rows.add(new Row(new ItemStack(item),
                    Component.literal("x" + e.getValue()[0] + " " + shortId(e.getKey()) + " / " + seconds + "s")
                        .withStyle(s -> s.withColor(0xFFDDDDDD)),
                    false));
            }
        }

        totalH = PADDING;
        for (Row r : rows) totalH += r.isSection() ? HEADER_H : ITEM_H;
        totalH += PADDING;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    protected String getTitle() { return "Summary"; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                  int mx, int my, float delta) {
        g.fill(cx, cy, cx + cw, cy + ch, COLOR_CONTENT_BG);

        boolean hasScroll = totalH > ch;
        int contentW = hasScroll ? cw - SCROLLBAR_W : cw;

        g.enableScissor(cx, cy, cx + contentW, cy + ch);

        var font = Minecraft.getInstance().font;
        int rowY = cy + PADDING - scrollPx;

        for (Row row : rows) {
            int rh = row.isSection() ? HEADER_H : ITEM_H;
            if (rowY + rh > cy && rowY < cy + ch) {
                if (row.isSection()) {
                    g.fill(cx, rowY, cx + contentW, rowY + rh, COLOR_HEADER_BG);
                    g.drawString(font, row.text(), cx + 4, rowY + 2, 0xFFFFFFFF, false);
                } else if (row.icon() != null && !row.icon().isEmpty()) {
                    g.renderFakeItem(row.icon(), cx + 2, rowY);
                    g.drawString(font, row.text(), cx + 20, rowY + 4, 0xFFFFFFFF, false);
                } else {
                    g.drawString(font, row.text(), cx + 4, rowY + 2, 0xFFFFFFFF, false);
                }
            }
            rowY += rh;
        }

        g.disableScissor();

        if (hasScroll) {
            int maxScroll = totalH - ch;
            int thumbH    = Math.max(12, ch * ch / totalH);
            int thumbY    = cy + (int)((long) scrollPx * (ch - thumbH) / maxScroll);
            int trackX    = cx + cw - SCROLLBAR_W;

            g.fill(trackX, cy, cx + cw, cy + ch, 0x33FFFFFF);

            boolean thumbHover = !draggingScrollbar
                && mx >= trackX && mx < cx + cw
                && my >= thumbY && my < thumbY + thumbH;
            int thumbColor = draggingScrollbar ? 0xFFFFFFFF
                           : thumbHover        ? 0xCCCCCCCC
                           :                    0x99AAAAAA;
            g.fill(trackX, thumbY, cx + cw, thumbY + thumbH, thumbColor);
        }
    }

    // -------------------------------------------------------------------------
    // Scrollbar interaction
    // -------------------------------------------------------------------------

    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && totalH > VISIBLE_H) {
            int cx = x;
            int cy = y + TITLE_BAR_H;
            int cw = width;
            int ch = VISIBLE_H;
            int maxScroll = totalH - ch;
            int thumbH    = Math.max(12, ch * ch / totalH);
            int thumbY    = cy + (int)((long) scrollPx * (ch - thumbH) / maxScroll);
            int trackX    = cx + cw - SCROLLBAR_W;

            if (mouseX >= trackX && mouseX < cx + cw
                    && mouseY >= thumbY && mouseY < thumbY + thumbH) {
                draggingScrollbar = true;
                dragStartMouseY   = mouseY;
                dragStartScrollPx = scrollPx;
                return true;
            }
        }
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean contentMouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (draggingScrollbar && button == 0) {
            int ch        = VISIBLE_H;
            int maxScroll = Math.max(0, totalH - ch);
            int thumbH    = Math.max(12, ch * ch / totalH);
            double trackRange = ch - thumbH;
            if (trackRange > 0) {
                double delta = mY - dragStartMouseY;
                scrollPx = (int) Math.max(0, Math.min(maxScroll,
                    dragStartScrollPx + delta * maxScroll / trackRange));
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean contentMouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean contentMouseScrolled(double mx, double my, double delta) {
        if (!isMouseOver(mx, my)) return false;
        int maxScroll = Math.max(0, totalH - VISIBLE_H);
        scrollPx = Math.max(0, Math.min(maxScroll, scrollPx - (int)(delta * 10)));
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String shortId(String resourceId) {
        if (resourceId == null || resourceId.isEmpty()) return "";
        int colon = resourceId.indexOf(':');
        String raw = colon >= 0 ? resourceId.substring(colon + 1) : resourceId;
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}

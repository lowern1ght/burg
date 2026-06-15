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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TownSummaryWidget extends DraggableWidget {

    public static final int WIDGET_W  = 175;
    public static final int VISIBLE_H = 180;

    private static final int HEADER_H  = 12;
    private static final int ITEM_H    = 16;
    private static final int PADDING   = 3;

    private static final int COLOR_HEADER_BG   = 0xF52A2A2A;
    private static final int COLOR_SECTION_TEXT = 0xFFAAAAFF;
    private static final int COLOR_ORIENT_TEXT  = 0xFFEECC66;
    private static final int COLOR_CONTENT_BG   = 0xF51A1A1A;

    private record Row(ItemStack icon, Component text, boolean isSection) {}

    private final List<Row> rows = new ArrayList<>();
    private int scrollPx  = 0;
    private int totalH    = 0;

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

    private void buildRows(CompoundTag mapData, CompoundTag summaryData) {
        String orientation     = summaryData.getString("Orientation");
        int totalResidents     = summaryData.getInt("TotalResidents");
        int activeResidents    = summaryData.getInt("ActiveResidents");
        int totalFoodDemand    = summaryData.getInt("TotalFoodDemand");
        int totalHerd          = summaryData.getInt("TotalHerd");
        int activeHerd         = summaryData.getInt("ActiveHerd");

        rows.add(new Row(null,
            Component.literal("Orientation: " + capitalize(orientation))
                .withStyle(s -> s.withColor(COLOR_ORIENT_TEXT)),
            false));

        // Residents row: active/total with color based on fed ratio
        if (totalResidents > 0) {
            int resColor;
            if (activeResidents >= totalResidents) {
                resColor = 0xFF55FF55;
            } else if (activeResidents * 2 >= totalResidents) {
                resColor = 0xFFFFAA00;
            } else {
                resColor = 0xFFFF5555;
            }
            Item villagerEgg = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:villager_spawn_egg"));
            MutableComponent resText = Component.literal("Residents: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(String.valueOf(activeResidents)).withStyle(s -> s.withColor(resColor)))
                .append(Component.literal(" / " + totalResidents).withStyle(s -> s.withColor(0xFFCCCCCC)));
            rows.add(new Row(new ItemStack(villagerEgg), resText, false));
        }

        // Herd row: between residents and food demand
        if (totalHerd > 0) {
            int herdColor;
            if (activeHerd >= totalHerd) {
                herdColor = 0xFF55FF55;
            } else if (activeHerd * 2 >= totalHerd) {
                herdColor = 0xFFFFAA00;
            } else {
                herdColor = 0xFFFF5555;
            }
            Item pigEgg = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft:pig_spawn_egg"));
            MutableComponent herdText = Component.literal("Herd: ")
                .withStyle(s -> s.withColor(0xFFCCCCCC))
                .append(Component.literal(String.valueOf(activeHerd)).withStyle(s -> s.withColor(herdColor)))
                .append(Component.literal(" / " + totalHerd).withStyle(s -> s.withColor(0xFFCCCCCC)));
            rows.add(new Row(new ItemStack(pigEgg), herdText, false));
        }

        // Food demand row directly after residents and herd
        if (totalFoodDemand > 0) {
            rows.add(new Row(new ItemStack(Items.BREAD),
                Component.literal(totalFoodDemand + " food units / day").withStyle(s -> s.withColor(0xFFDDDDDD)),
                false));
        }

        Map<String, int[]> prodData   = new LinkedHashMap<>(); // itemId -> [totalAmount, ticks]
        Map<String, int[]> transforms = new LinkedHashMap<>(); // outItemId -> [outAmount, ticks]

        ListTag elements = mapData.getList("Elements", Tag.TAG_COMPOUND);
        for (Tag rawEl : elements) {
            CompoundTag el = (CompoundTag) rawEl;
            byte elCategory = el.getByte("Category");
            if (elCategory != 2) continue;

            for (Tag rawP : el.getList("Production", Tag.TAG_COMPOUND)) {
                CompoundTag pt    = (CompoundTag) rawP;
                String itemId     = pt.getString("Item");
                int amount        = pt.getInt("Amount");
                int ticks         = pt.getInt("EveryTicks");
                prodData.merge(itemId, new int[]{amount, ticks},
                    (a, b) -> new int[]{a[0] + b[0], a[1]});
            }

            for (Tag rawT : el.getList("Transforms", Tag.TAG_COMPOUND)) {
                CompoundTag tt = (CompoundTag) rawT;
                String outId   = tt.getString("OutputItem");
                if (!transforms.containsKey(outId)) {
                    transforms.put(outId, new int[]{
                        tt.getInt("OutputAmount"),
                        tt.getInt("EveryTicks")
                    });
                }
            }
        }

        if (!prodData.isEmpty()) {
            rows.add(new Row(null, Component.literal("Production").withStyle(s -> s.withColor(COLOR_SECTION_TEXT)), true));
            for (Map.Entry<String, int[]> e : prodData.entrySet()) {
                Item item     = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int amount    = e.getValue()[0];
                int seconds   = e.getValue()[1] / 20;
                Component text = Component.literal("x" + amount + " " + shortId(e.getKey()) + " / " + seconds + "s")
                    .withStyle(s -> s.withColor(0xFFDDDDDD));
                rows.add(new Row(new ItemStack(item), text, false));
            }
        }

        if (!transforms.isEmpty()) {
            rows.add(new Row(null, Component.literal("Transformations").withStyle(s -> s.withColor(COLOR_SECTION_TEXT)), true));
            for (Map.Entry<String, int[]> e : transforms.entrySet()) {
                Item item   = BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));
                int seconds = e.getValue()[1] / 20;
                Component text = Component.literal("x" + e.getValue()[0] + " " + shortId(e.getKey()) + " / " + seconds + "s")
                    .withStyle(s -> s.withColor(0xFFDDDDDD));
                rows.add(new Row(new ItemStack(item), text, false));
            }
        }

        totalH = PADDING;
        for (Row r : rows) totalH += r.isSection() ? HEADER_H : ITEM_H;
        totalH += PADDING;
    }

    @Override
    protected String getTitle() { return "Summary"; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                  int mx, int my, float delta) {
        g.fill(cx, cy, cx + cw, cy + ch, COLOR_CONTENT_BG);

        g.enableScissor(cx, cy, cx + cw, cy + ch);

        var font = Minecraft.getInstance().font;
        int rowY = cy + PADDING - scrollPx;

        for (Row row : rows) {
            int rh = row.isSection() ? HEADER_H : ITEM_H;
            if (rowY + rh > cy && rowY < cy + ch) {
                if (row.isSection()) {
                    g.fill(cx, rowY, cx + cw, rowY + rh, COLOR_HEADER_BG);
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

        if (totalH > ch) {
            int maxScroll = totalH - ch;
            int barH = Math.max(8, ch * ch / totalH);
            int barY = cy + (int) ((long) scrollPx * (ch - barH) / maxScroll);
            g.fill(cx + cw - 3, cy, cx + cw, cy + ch, 0x33FFFFFF);
            g.fill(cx + cw - 3, barY, cx + cw, barY + barH, 0xAAFFFFFF);
        }
    }

    @Override
    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) {
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    protected boolean contentMouseScrolled(double mx, double my, double delta) {
        if (!isMouseOver(mx, my)) return false;
        int maxScroll = Math.max(0, totalH - VISIBLE_H);
        scrollPx = Math.max(0, Math.min(maxScroll, scrollPx - (int) (delta * 10)));
        return true;
    }

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

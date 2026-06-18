package org.dawnoftime.onceuponatown.client.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class QuestDraggableWidget extends DraggableWidget {

    public static final int WIDGET_W = 170;
    private static final int COND_H  = 20;
    private static final int BTN_H   = 12;
    private static final int PAD     = 3;
    private static final int SLOT_SIZE = 16;

    private final String questId;
    private String questType = "TASK";
    private String titleKey;
    private String descKey;
    private final List<ClientCondition> conditions = new ArrayList<>();
    private final Consumer<String> onClaim;
    // (conditionIndex, requestedAmount)
    private BiConsumer<Integer, Integer> onDeliver = (condIdx, amount) -> {};

    // Updated by recomputeHeight(); used by both renderContent and contentMouseClicked
    private int claimBtnY = 0;
    // Per-condition slot Y positions (absolute screen coords), updated each frame
    private final List<Integer> condSlotYs = new ArrayList<>();

    private Runnable moveCallback;

    private record ClientCondition(String type, String itemId, int required, int received) {
        boolean isMet() {
            if ("DELIVERY".equals(type)) return received >= required;
            return true;
        }
        int remaining() { return Math.max(0, required - received); }
        ItemStack asStack() {
            if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return ItemStack.EMPTY;
            var item = BuiltInRegistries.ITEM.get(rl);
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
    }

    public QuestDraggableWidget(CompoundTag tag, Consumer<String> onClaim, int x, int y,
                                int freeZoneMaxX, int screenHeight) {
        super(x, y, WIDGET_W, TITLE_BAR_H + PAD + 9 + PAD + BTN_H + PAD, freeZoneMaxX, screenHeight);
        this.clampPosition = false;
        this.questId  = tag.getString("QuestId");
        this.onClaim  = onClaim;
        loadFromTag(tag);
        this.showCloseButton = "NOTE".equals(questType);
        recomputeHeight();
    }

    // Called on each hub refresh to update live condition progress.
    public void update(CompoundTag tag) {
        loadFromTag(tag);
        recomputeHeight();
    }

    public String getQuestId() { return questId; }

    public void setMoveCallback(Runnable cb) { this.moveCallback = cb; }

    // (conditionIndex, requestedAmount) -> send to server
    public void setDeliveryCallback(BiConsumer<Integer, Integer> cb) { this.onDeliver = cb; }

    @Override
    protected void onMoved() {
        if (moveCallback != null) moveCallback.run();
    }

    @Override
    protected void onClose() {
        if ("NOTE".equals(questType)) onClaim.accept(questId);
        super.onClose();
    }

    // -------------------------------------------------------------------------
    // Dynamic height
    // -------------------------------------------------------------------------

    private void recomputeHeight() {
        var font = Minecraft.getInstance().font;
        if (font == null) return;
        List<?> descLines = font.split(Component.translatable(descKey), WIDGET_W - 6);
        int actualDescH = descLines.size() * 9;
        int newHeight;
        if ("NOTE".equals(questType)) {
            newHeight = TITLE_BAR_H + PAD + actualDescH + PAD;
        } else {
            newHeight = TITLE_BAR_H + PAD + actualDescH + PAD + conditions.size() * COND_H + PAD + BTN_H + PAD;
            claimBtnY = y + TITLE_BAR_H + PAD + actualDescH + PAD + conditions.size() * COND_H + PAD;
        }
        setHeight(newHeight);
    }

    // -------------------------------------------------------------------------
    // DraggableWidget contract
    // -------------------------------------------------------------------------

    @Override
    protected String getTitle() {
        String prefix = "NOTE".equals(questType) ? "[i] " : "[!] ";
        return prefix + Component.translatable(titleKey).getString();
    }

    @Override
    protected int getTitleBarColor()  { return "NOTE".equals(questType) ? 0xFF0E2B50 : 0xFF6A3A12; }
    @Override
    protected int getTitleTextColor() { return "NOTE".equals(questType) ? 0xFF88CCFF : 0xFFFFEE88; }

    @Override
    protected void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                 int mx, int my, float delta) {
        boolean isNote = "NOTE".equals(questType);
        int bgColor     = isNote ? 0xEE071420 : 0xEE2A1A0A;
        int borderColor = isNote ? 0xFF3366BB : 0xFFAA7744;
        int textColor   = isNote ? 0xFFCCEEFF : 0xFFDDCCAA;

        g.fill(cx, cy, cx + cw, cy + ch, bgColor);
        g.fill(cx,          cy, cx + 1,      cy + ch, borderColor);
        g.fill(cx + cw - 1, cy, cx + cw,     cy + ch, borderColor);
        g.fill(cx,    cy + ch - 1, cx + cw, cy + ch, borderColor);

        var font = Minecraft.getInstance().font;
        int lineY = cy + PAD;

        var descLines = font.split(Component.translatable(descKey), cw - 6);
        for (var line : descLines) {
            g.drawString(font, line, cx + 3, lineY, textColor, false);
            lineY += 9;
        }
        lineY += PAD;

        if (isNote) return;

        // Condition rows
        boolean allMet = true;
        condSlotYs.clear();
        for (int i = 0; i < conditions.size(); i++) {
            ClientCondition cond = conditions.get(i);
            boolean met = cond.isMet();
            if (!met) allMet = false;

            int slotX = cx + 3;
            int slotY = lineY + 2;
            condSlotYs.add(slotY);

            if ("DELIVERY".equals(cond.type())) {
                // Slot border
                int slotBorderColor = met ? 0xFF335533 : 0xFF555555;
                g.fill(slotX - 1, slotY - 1, slotX + SLOT_SIZE + 1, slotY + SLOT_SIZE + 1, slotBorderColor);
                int slotBg = met ? 0xFF1A331A : 0xFF1A1A1A;
                g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, slotBg);

                // Slot hover highlight
                if (!met && mx >= slotX && mx < slotX + SLOT_SIZE && my >= slotY && my < slotY + SLOT_SIZE) {
                    g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x40FFFFFF);
                }

                // Item icon
                ItemStack stack = cond.asStack();
                if (!stack.isEmpty()) {
                    if (met) {
                        g.renderFakeItem(stack, slotX, slotY);
                        g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0x88000000);
                    } else {
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1f, 1f, 1f, 0.5f);
                        g.renderFakeItem(stack, slotX, slotY);
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                        RenderSystem.disableBlend();
                    }
                }

                // Progress text and item name to the right of the slot
                String itemName = formatId(cond.itemId().contains(":")
                    ? cond.itemId().substring(cond.itemId().indexOf(':') + 1)
                    : cond.itemId());
                int color = met ? 0xFF55FF55 : 0xFFAAAAAA;
                g.drawString(font, itemName, cx + 3 + SLOT_SIZE + 3, slotY, color, false);
                g.drawString(font,
                    cond.received() + " / " + cond.required(),
                    cx + 3 + SLOT_SIZE + 3, slotY + 9, color, false);
            } else {
                // Non-DELIVERY condition: simple text row
                condSlotYs.set(condSlotYs.size() - 1, -1);
                int color = met ? 0xFF55FF55 : 0xFFAAAAAA;
                String check = met ? "[x]" : "[ ]";
                g.drawString(font, check + " " + cond.type(), cx + 3, lineY + 6, color, false);
            }

            lineY += COND_H;
        }
        lineY += PAD;

        claimBtnY = lineY;

        // CLAIM button
        int btnW = cw - 6;
        boolean btnHover = mx >= cx + 3 && mx < cx + 3 + btnW && my >= lineY && my < lineY + BTN_H;
        int btnColor = allMet
            ? (btnHover ? 0xFF55BB55 : 0xFF338833)
            : 0xFF444444;
        g.fill(cx + 3, lineY, cx + 3 + btnW, lineY + BTN_H, btnColor);
        String btnText = Component.translatable("onceuponatown.quest.claim").getString();
        g.drawString(font, btnText, cx + 3 + (btnW - font.width(btnText)) / 2, lineY + 2,
            0xFFFFFFFF, false);
    }

    @Override
    protected boolean contentMouseClicked(double mx, double my, int button) {
        if (button != 0) return isMouseOver(mx, my);

        // Check delivery slot clicks
        for (int i = 0; i < conditions.size() && i < condSlotYs.size(); i++) {
            int slotY = condSlotYs.get(i);
            if (slotY < 0) continue;
            ClientCondition cond = conditions.get(i);
            if (!"DELIVERY".equals(cond.type()) || cond.isMet()) continue;

            int slotX = x + 3;
            boolean inSlot = mx >= slotX && mx < slotX + SLOT_SIZE
                          && my >= slotY && my < slotY + SLOT_SIZE;
            if (!inSlot) continue;

            var mc = Minecraft.getInstance();
            if (mc.player == null) return true;

            int remaining = cond.remaining();
            if (remaining <= 0) return true;

            if (Screen.hasShiftDown()) {
                // Shift-click: take all needed from inventory + cursor
                onDeliver.accept(i, remaining);
            } else {
                // Left-click: take from cursor if item matches
                ItemStack carried = mc.player.containerMenu.getCarried();
                ResourceLocation condItemRl = ResourceLocation.tryParse(cond.itemId());
                if (!carried.isEmpty() && condItemRl != null) {
                    var expectedItem = BuiltInRegistries.ITEM.get(condItemRl);
                    if (expectedItem != Items.AIR && carried.getItem() == expectedItem) {
                        int toSend = Math.min(carried.getCount(), remaining);
                        onDeliver.accept(i, toSend);
                    }
                }
            }
            return true;
        }

        // CLAIM button
        int btnW = width - 6;
        if (mx >= x + 3 && mx < x + 3 + btnW && my >= claimBtnY && my < claimBtnY + BTN_H) {
            boolean allMet = conditions.stream().allMatch(ClientCondition::isMet);
            if (allMet) onClaim.accept(questId);
            return true;
        }
        return isMouseOver(mx, my);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void loadFromTag(CompoundTag tag) {
        titleKey = tag.getString("TitleKey");
        descKey  = tag.getString("DescKey");
        String rawType = tag.getString("QuestType");
        questType = rawType.isEmpty() ? "TASK" : rawType;
        conditions.clear();
        tag.getList("Conditions", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag ct = (CompoundTag) t;
            conditions.add(new ClientCondition(
                ct.getString("Type"), ct.getString("Item"),
                ct.getInt("Required"), ct.getInt("Received")
            ));
        });
    }

    private static String formatId(String id) {
        if (id == null || id.isEmpty()) return id;
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            if (!part.isEmpty()) sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}

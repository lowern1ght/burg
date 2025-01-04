package org.dawnoftime.onceuponatown.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.menu.TradeMenu;
import org.dawnoftime.onceuponatown.network.C2SSelectTradePacket;
import org.dawnoftime.onceuponatown.trade.MerchantDeal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.dawnoftime.onceuponatown.client.screen.BuyScreen.DealButton.HEIGHT;
import static org.dawnoftime.onceuponatown.client.screen.BuyScreen.DealButton.WIDTH;


public class TradeScreen extends NpcBaseScreen<TradeMenu> {
    private static final ResourceLocation TEXTURE = Ouat.modResource("textures/gui/trade_screen.png");
    private static final int MAIN_BLIT_WIDTH = 281;
    private static final int MAIN_BLIT_HEIGHT = 166;
    private static final int SCROLLER_HEIGHT = 27;
    private static final int SCROLLER_WIDTH = 6;
    private static final int SCROLLER_ENABLED_OFFSET_X = 54;
    private static final int SCROLLER_ENABLED_OFFSET_Y = 166;
    private static final int SCROLLER_DISABLED_OFFSET_X = 60;
    private static final int SCROLLER_DISABLED_OFFSET_Y = 166;
    private static final int OFFERS_GRID_X = 8;
    private static final int OFFERS_GRID_Y = 38;
    private static final int SCROLLER_X = 101;
    private static final int SCROLL_BAR_TOP = 38;
    private static final int SCROLL_BAR_BOTTOM = 93;
    private static final int SCROLL_BAR_HEIGHT = 139;
    private static final int OFFERS_GRID_ROWS = 6;
    private static final int OFFERS_GRID_COLUMNS = 5;
    public static final int OFFERS_GRID_CAPACITY = OFFERS_GRID_ROWS * OFFERS_GRID_COLUMNS;
    private final DealButton[] dealButtons = new DealButton[OFFERS_GRID_CAPACITY];
    private int activeDeal = -1;
    private int scrollOff;
    private boolean isDragging;
    private boolean isSelling;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Component.literal("Trade"), NpcTab.BUY);
        imageWidth = 281;
        imageHeight = 193;
        npc = menu.getNpcInteraction().getNpc();
    }

    protected void init() {
        leftPos = ((this.width - MAIN_BLIT_WIDTH) / 2) + 8; // Replaces super.init();
        topPos = ((this.height - MAIN_BLIT_HEIGHT) / 2); // Replaces super.init();
        createDealButtons();
        addRenderableWidget(new ReleaseFocusButton.Builder(Component.literal("Buy"), (button -> {
            isSelling = !isSelling;
            button.setMessage(Component.literal(isSelling ? "Sell" : "Buy"));

        })).bounds(this.leftPos + 7, this.topPos + 17, 92, 16).build());
    }

    private void createDealButtons() {
        int x, startX = this.leftPos + OFFERS_GRID_X;
        int y = this.topPos + OFFERS_GRID_Y;
        int buttonIndex = 0;
        for (int i = 0; i < OFFERS_GRID_ROWS; ++i) {
            x = startX;
            for (int j = 0; j < OFFERS_GRID_COLUMNS; ++j) {
                this.dealButtons[buttonIndex] = this.addRenderableWidget(new DealButton(x, y, buttonIndex, (pressedButton) -> {
                    if (pressedButton instanceof DealButton button) {
                        this.activeDeal = button.getIndex() + (this.scrollOff * OFFERS_GRID_COLUMNS);
                        menu.selectDeal(activeDeal);
                        Ouat.CLIENT.sendToServer(new C2SSelectTradePacket(this.activeDeal));
                    }
                }));
                ++buttonIndex;
                x += WIDTH;
            }
            y += HEIGHT;
        }
    }

    private int dealsAmount() {
        return this.menu.getDeals().size();
    }

    private boolean canScroll() {
        return dealsAmount() > OFFERS_GRID_CAPACITY;
    }

    private int availableScrolls() {
        return Mth.ceil((((double) this.menu.getDeals().size() - (double) OFFERS_GRID_CAPACITY) / OFFERS_GRID_ROWS));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.canScroll()) {
            this.scrollOff = Mth.clamp((int) ((double) this.scrollOff - delta), 0, availableScrolls());
        }
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        if (this.canScroll()
                && mouseX > (double) (leftPos + SCROLLER_X) && mouseX < (double) (leftPos + SCROLLER_X + SCROLLER_WIDTH)
                && mouseY > (double) (topPos + SCROLL_BAR_TOP) && mouseY <= (double) (topPos + SCROLL_BAR_TOP + SCROLL_BAR_HEIGHT + 1)) {
            this.isDragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            int j = this.topPos + SCROLL_BAR_TOP;
            int k = j + SCROLL_BAR_HEIGHT;
            int l = availableScrolls();
            float f = ((float) mouseY - (float) j - 13.5F) / ((float) (k - j) - 27.0F);
            f = f * (float) l + 0.5F;
            this.scrollOff = Mth.clamp((int) f, 0, l);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
    }

    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(graphics);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, MAIN_BLIT_WIDTH, MAIN_BLIT_HEIGHT, this.imageWidth, this.imageHeight);
        renderTabs(graphics);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        //graphics.drawString(this.font, "Cost", 133, 26, 4210752, false);
        //graphics.drawString(this.font, "Result", 227, 22, 4210752, false);
    }

    private void renderScroller(GuiGraphics graphics) {
        int i = 1 + availableScrolls();
        if (i > 1) {
            int j = SCROLL_BAR_HEIGHT - (SCROLLER_HEIGHT + (i - 1) * SCROLL_BAR_HEIGHT / i);
            int k = 1 + j / i + SCROLL_BAR_HEIGHT / i;
            int scrollerOffset = Math.min(SCROLL_BAR_BOTTOM, this.scrollOff * k);
            if (this.scrollOff == i - 1) {
                scrollerOffset = SCROLL_BAR_BOTTOM;
            }
            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP + scrollerOffset, SCROLLER_ENABLED_OFFSET_X, SCROLLER_ENABLED_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, imageWidth, imageHeight);
        } else {
            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP, SCROLLER_DISABLED_OFFSET_X, SCROLLER_DISABLED_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, imageWidth, imageHeight);
        }

    }

    private void renderSoldItemDecorations(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.drawString(font, String.valueOf(stack.getCount()), x + 20 - 3 - font.width(String.valueOf(stack.getCount())), y + 6 + 3, 16777215, true);
            graphics.pose().popPose();
        }
    }

    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderScroller(graphics);

        for (DealButton button : dealButtons) {
            button.visible = (button.index + this.scrollOff * OFFERS_GRID_COLUMNS) < dealsAmount();
        }
        List<MerchantDeal> deals = this.menu.getDeals();
        if (!deals.isEmpty()) {
            int startX = leftPos + OFFERS_GRID_X + 1;
            int x = startX;
            int y = topPos + OFFERS_GRID_Y + 2;

            int index = 0;
            for (MerchantDeal deal : deals) {
                if (!this.canScroll() || index >= (this.scrollOff * OFFERS_GRID_COLUMNS) && index < OFFERS_GRID_CAPACITY + (this.scrollOff * OFFERS_GRID_COLUMNS)) {
                    ItemStack result = deal.getResult();
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0F, 0.0F, 100.0F);
                    graphics.renderFakeItem(result, x, y);
                    renderSoldItemDecorations(graphics, this.font, result, x, y);
                    graphics.pose().popPose();
                    x += WIDTH;
                    if (x == startX + (WIDTH * OFFERS_GRID_COLUMNS)) {
                        x = startX;
                        y += HEIGHT;
                    }
                }
                ++index;
            }
            RenderSystem.enableDepthTest();
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    class DealButton extends Button {
        public static final int WIDTH = 18;
        public static final int HEIGHT = 20;
        public static final int UNSEL_OFFSET_X = 0;
        public static final int UNSEL_OFFSET_Y = 166;
        public static final int SEL_OFFSET_X = 18;
        public static final int SEL_OFFSET_Y = 166;
        public static final int HOVERED_OFFSET_X = 36;
        public static final int HOVERED_OFFSET_Y = 166;
        final int index;

        public DealButton(int x, int y, int index, OnPress onPress) {
            super(x, y, WIDTH, HEIGHT, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
            this.index = index;
            this.visible = false;
        }

        public int getIndex() {
            return this.index;
        }

        public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (this.visible) {
                this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
                int offsetX;
                int offsetY;
                if (this.isHovered && (this.index + TradeScreen.this.scrollOff * OFFERS_GRID_COLUMNS) != TradeScreen.this.activeDeal) {
                    offsetX = HOVERED_OFFSET_X;
                    offsetY = HOVERED_OFFSET_Y;
                } else if ((this.index + TradeScreen.this.scrollOff * OFFERS_GRID_COLUMNS) == TradeScreen.this.activeDeal) {
                    offsetX = SEL_OFFSET_X;
                    offsetY = SEL_OFFSET_Y;

                } else {
                    offsetX = UNSEL_OFFSET_X;
                    offsetY = UNSEL_OFFSET_Y;
                }
                graphics.blit(TEXTURE, getX(), getY(), offsetX, offsetY, this.width, this.height, TradeScreen.this.imageWidth, TradeScreen.this.imageHeight);
                if (isHoveredOrFocused()) {
                    renderToolTip(graphics, mouseX, mouseY);
                }
            }
        }

        public void renderToolTip(GuiGraphics graphics, int mouseX, int mouseY) {
            if (this.isHovered && TradeScreen.this.menu.getDeals().size() > this.index + (TradeScreen.this.scrollOff * OFFERS_GRID_COLUMNS)) {
                ItemStack result = TradeScreen.this.menu.getDeals().get(this.index + (TradeScreen.this.scrollOff * OFFERS_GRID_COLUMNS)).getResult();
                ItemStack stackA = TradeScreen.this.menu.getDeals().get(this.index + (TradeScreen.this.scrollOff * OFFERS_GRID_COLUMNS)).getRequiredA();
                ItemStack stackB = TradeScreen.this.menu.getDeals().get(this.index + (TradeScreen.this.scrollOff * OFFERS_GRID_COLUMNS)).getRequiredB();
                List<Component> text = getTooltipFromItem(TradeScreen.this.minecraft, result);
                Ouat.COMMON.renderTooltip(graphics, TradeScreen.this.font, text, new TradeItemTooltip(stackA, stackB, ItemStack.EMPTY), result, mouseX, mouseY);
            }
        }
    }
}

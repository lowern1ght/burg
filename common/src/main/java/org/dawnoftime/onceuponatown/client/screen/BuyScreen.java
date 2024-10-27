package org.dawnoftime.onceuponatown.client.screen;

import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.menu.BuyMenu;
import org.dawnoftime.onceuponatown.network.C2SSelectBuyDealPacket;
import org.dawnoftime.onceuponatown.platform.Platform;
import org.dawnoftime.onceuponatown.util.OuatUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.dawnoftime.onceuponatown.client.screen.BuyScreen.DealButton.DEAL_BUTTON_HEIGHT;
import static org.dawnoftime.onceuponatown.client.screen.BuyScreen.DealButton.DEAL_BUTTON_WIDTH;

public class BuyScreen extends CitizenBaseScreen<BuyMenu> {
    private static final ResourceLocation TEXTURE = OuatUtils.createOuatResource("textures/gui/buy_screen.png");
    private static final int BUY_SCREEN_TEXTURE_WIDTH = 289;//299;
    private static final int BUY_SCREEN_TEXTURE_HEIGHT = 166;//174;
    // Texture size and offset in file
    private static final int SCROLLER_HEIGHT = 27;
    private static final int SCROLLER_WIDTH = 6;
    private static final int SCROLLER_OFFSET_X = 287;
    private static final int SCROLLER_OFFSET_Y = 60;
    private static final int SCROLLER_AVAIL_OFFSET_X = 281;
    private static final int SCROLLER_AVAIL_OFFSET_Y = 60;
    // Positions of elements in gui
    private static final int GRID_X = 8;
    private static final int GRID_Y = 26;
    private static final int SCROLLER_X = 101;
    private static final int SCROLL_BAR_TOP_Y = 26;
    private static final int SCROLL_BAR_HEIGHT = 139;
    private static final int SCROLL_BAR_BOTTOM_Y = 99;
    private Component citizenInfo = Component.literal("Armorer");
    private Component citizenSentence;
    private static final int GRID_COLUMNS = 5;
    private static final int GRID_ROWS = 7;
    public static final int NUMBER_OF_DEAL_BUTTONS = GRID_ROWS * GRID_COLUMNS;
    private final DealButton[] dealButtons = new DealButton[NUMBER_OF_DEAL_BUTTONS];
    private int selectedDealIndex = -1;
    private int scrollOff;
    private boolean isDragging;

    public BuyScreen(BuyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CitizenTab.BUY);
        this.imageWidth = 289;
        this.imageHeight = 193  ;
        this.inventoryLabelX = 113;
        this.inventoryLabelY = 80;
        this.titleLabelX = 44;
        this.titleLabelY = 5;
        this.citizen = menu.getCitizen().getCitizen();
    }

    protected void init() {
        //super.init();
        this.leftPos = ((this.width - BUY_SCREEN_TEXTURE_WIDTH) / 2) + 8;

        //this.topPos = this.getMinecraft().options.guiScale().get() < 6 ? ((this.height - BUY_SCREEN_TEXTURE_HEIGHT) / 2) : ((this.height - BUY_SCREEN_TEXTURE_HEIGHT) / 2) + 20;
        this.topPos = ((this.height - BUY_SCREEN_TEXTURE_HEIGHT) / 2);
        createButtons();
        String buy = "Buy";
        String sell = "Sell";
        this.addRenderableWidget(new Button.Builder(Component.literal(buy), (pButton -> {})).bounds(this.leftPos + 7, this.topPos + 4, this.font.width(buy)+10, 12).build());
        this.addRenderableWidget(new Button.Builder(Component.literal(sell), (pButton -> {})).bounds(this.leftPos + 37, this.topPos + 4, this.font.width(sell)+10, 12).build());
    }

    private void createButtons() {
        int x, startX = this.leftPos + GRID_X;
        int y = this.topPos + GRID_Y;
        int buttonIndex = 0;
        for (int i = 0; i < GRID_ROWS; ++i) {
            x = startX;
            for(int j = 0; j < GRID_COLUMNS; ++j) {
                this.dealButtons[buttonIndex] = this.addRenderableWidget(new DealButton(x, y, buttonIndex, (pressedButton) -> {
                    if (pressedButton instanceof DealButton button) {
                        this.selectedDealIndex = button.getIndex() + (this.scrollOff * GRID_COLUMNS);
                        this.postButtonClick();
                    }
                }));
                ++buttonIndex;
                x += DEAL_BUTTON_WIDTH;
            }
            y += DEAL_BUTTON_HEIGHT;
        }
    }

    private void postButtonClick() {
        this.menu.setSelectedDeal(this.selectedDealIndex);
        this.menu.tryMoveItems(this.selectedDealIndex);
        Platform.PLATFORM.sendToServer(new C2SSelectBuyDealPacket(this.selectedDealIndex));
    }

    private int nbOfDeals() {
        return this.menu.getDeals().size();
    }

    private boolean canScroll() {
        return nbOfDeals() > NUMBER_OF_DEAL_BUTTONS;
    }

    private int nbOfTimesCanScrollDown() {
        return Mth.ceil((((double)this.menu.getDeals().size() - (double)NUMBER_OF_DEAL_BUTTONS) / 6));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.canScroll()) {
            this.scrollOff = Mth.clamp((int)((double)this.scrollOff - delta), 0, nbOfTimesCanScrollDown());
        }
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.isDragging = false;
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;
        if (this.canScroll() && mouseX > (double)(i + 119) && mouseX < (double)(i + 119 + 6) && mouseY > (double)(j + 24) && mouseY <= (double)(j + 24 + 139 + 1)) {
            this.isDragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            int j = this.topPos + 24;
            int k = j + SCROLL_BAR_HEIGHT;
            int l = nbOfTimesCanScrollDown();
            float f = ((float)mouseY - (float)j - 13.5F) / ((float)(k - j) - 27.0F);
            f = f * (float)l + 0.5F;
            this.scrollOff = Mth.clamp((int)f, 0, l);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
    }

    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(graphics);
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.pose().translate(0.0F, 0.0F, 100.0F);
        //graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT, this.imageWidth, this.imageHeight);
        renderTabs(graphics);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX-1, this.inventoryLabelY- 8, 4210752, false);
        //graphics.drawString(this.font, title, titleLabelX, titleLabelY, 4210752, false);
        //graphics.drawString(this.font, title, 132, titleLabelY, 4210752, false);
        //graphics.drawString(this.font, "Buy", 46, 14, 4210752, false);
        //graphics.drawString(this.font, "Cost", 128, 32, 4210752, false);
        //graphics.drawString(this.font, "Get", 234, 30, 4210752, false);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        //this.renderScroller(graphics);

        for (DealButton button : dealButtons) {
            button.visible = false;//(button.index + this.scrollOff * GRID_COLUMNS) < nbOfDeals();
        }

        /*
        List<BuyDeal> deals = this.menu.getDeals();
        if (!deals.isEmpty()) {
            int startX  = leftPos + GRID_X + 1;
            int x = startX;
            int y = topPos + GRID_Y + 1 + 1;

            int index = 0;
            for(BuyDeal deal : deals) {
                if (!this.canScroll() || index >= (this.scrollOff * GRID_COLUMNS) && index < NUMBER_OF_DEAL_BUTTONS + (this.scrollOff * GRID_COLUMNS)) {
                    ItemStack result = deal.getResult();
                    graphics.pose().pushPose();
                    graphics.pose().translate(0.0F, 0.0F, 100.0F);
                    graphics.renderFakeItem(result, x, y);
                    renderSoldItemDecorations(graphics, this.font, result, x, y);
                    graphics.pose().popPose();

                    x += DEAL_BUTTON_WIDTH;
                    if (x == startX + (DEAL_BUTTON_WIDTH * GRID_COLUMNS)) {
                        x = startX;
                        y += DEAL_BUTTON_HEIGHT;
                    }
                }
                ++index;
            }
            RenderSystem.enableDepthTest();
        }
        renderTooltip(graphics, mouseX, mouseY);

         */
    }

    private void renderScroller(GuiGraphics graphics) {
        int i = 1 + nbOfTimesCanScrollDown();
        if (i > 1) {
            int j = SCROLL_BAR_HEIGHT - (SCROLLER_HEIGHT + (i - 1) * SCROLL_BAR_HEIGHT / i);
            int k = 1 + j / i + SCROLL_BAR_HEIGHT / i;
            int scrollerOffset = Math.min(SCROLL_BAR_BOTTOM_Y, this.scrollOff * k);
            if (this.scrollOff == i - 1) {
                scrollerOffset = SCROLL_BAR_BOTTOM_Y;
            }

            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP_Y + scrollerOffset, 0, SCROLLER_AVAIL_OFFSET_X, SCROLLER_AVAIL_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
        } else {
            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP_Y, 0, SCROLLER_OFFSET_X, SCROLLER_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, BUY_SCREEN_TEXTURE_WIDTH, BUY_SCREEN_TEXTURE_HEIGHT);
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

    class DealButton extends Button {
        public static final int DEAL_BUTTON_WIDTH = 18;
        public static final int DEAL_BUTTON_HEIGHT = 20;
        public static final int DEAL_BUTTON_OFFSET_X = 281;
        public static final int DEAL_BUTTON_OFFSET_Y = 0;
        public static final int DEAL_BUTTON_SEL_OFFSET_X = 281;
        public static final int DEAL_BUTTON_SEL_OFFSET_Y = 20;
        public static final int DEAL_BUTTON_HOVER_OFFSET_X = 281;
        public static final int DEAL_BUTTON_HOVER_OFFSET_Y = 40;
        final int index;

        public DealButton(int x, int y, int index, OnPress onPress) {
            super(x, y, DEAL_BUTTON_WIDTH, DEAL_BUTTON_HEIGHT, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
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
                if (this.isHovered && (this.index + BuyScreen.this.scrollOff * GRID_COLUMNS) != BuyScreen.this.selectedDealIndex) {
                    offsetX = DEAL_BUTTON_HOVER_OFFSET_X;
                    offsetY = DEAL_BUTTON_HOVER_OFFSET_Y;
                } else if ((this.index + BuyScreen.this.scrollOff * GRID_COLUMNS) == BuyScreen.this.selectedDealIndex) {
                    offsetX = DEAL_BUTTON_SEL_OFFSET_X;
                    offsetY = DEAL_BUTTON_SEL_OFFSET_Y;

                } else {
                    offsetX = DEAL_BUTTON_OFFSET_X;
                    offsetY = DEAL_BUTTON_OFFSET_Y;
                }

                graphics.blit(TEXTURE, getX(), getY(), offsetX, offsetY, this.width, this.height, BuyScreen.BUY_SCREEN_TEXTURE_WIDTH, BuyScreen.BUY_SCREEN_TEXTURE_HEIGHT);
                if (isHoveredOrFocused()) {
                    renderToolTip(graphics,mouseX,mouseY);
                }
            }

        }

        public void renderToolTip(GuiGraphics graphics, int mouseX, int mouseY) {
            if (this.isHovered && BuyScreen.this.menu.getDeals().size() > this.index + (BuyScreen.this.scrollOff * GRID_COLUMNS)) {
                ItemStack result = BuyScreen.this.menu.getDeals().get(this.index + (BuyScreen.this.scrollOff * GRID_COLUMNS)).getResult();
                ItemStack stackA = BuyScreen.this.menu.getDeals().get(this.index + (BuyScreen.this.scrollOff * GRID_COLUMNS)).getInputA();
                ItemStack stackB = BuyScreen.this.menu.getDeals().get(this.index + (BuyScreen.this.scrollOff * GRID_COLUMNS)).getInputB();
                ItemStack stackC = BuyScreen.this.menu.getDeals().get(this.index + (BuyScreen.this.scrollOff * GRID_COLUMNS)).getInputC();
                List<Component> text = getTooltipFromItem(BuyScreen.this.minecraft, result);
                Platform.PLATFORM.renderTooltip(graphics, BuyScreen.this.font, text, new TradeItemTooltip(stackA, stackB, stackC), result, mouseX, mouseY);
            }
        }
    }
}

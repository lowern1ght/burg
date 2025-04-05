package org.dawnoftime.onceuponatown.client.gui.npc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.menu.TradeMenu;
import org.dawnoftime.onceuponatown.network.inventory.C2STradePacket;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TradeScreen extends NpcBaseScreen<TradeMenu> {
    private static final ResourceLocation TEXTURE = Ouat.modResource("textures/gui/trade_screen.png");
    private static final int MAIN_BLIT_WIDTH = 281;
    private static final int MAIN_BLIT_HEIGHT = 166;
    private static final int XP_BAR_EMPTY_OFFSET_X = 66;
    private static final int XP_BAR_EMPTY_OFFSET_Y = 171;
    private static final int XP_BAR_FULL_OFFSET_X = 66;
    private static final int XP_BAR_FULL_OFFSET_Y = 176;
    private static final int XP_BAR_WIDTH = 102;
    private static final int XP_BAR_HEIGHT = 5;
    private static final int XP_BAR_X = 138;
    private static final int XP_BAR_Y = 18;
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
    private Button buyButton;
    private Button sellButton;
    private int scrollOff;
    private boolean isDragging;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, Ouat.translatable("trade"), menu.getNpc().getNpc(), TabType.TRADE);
        imageWidth = 281;
        imageHeight = 193;
    }

    @Override
    protected void init() {
        leftPos = (width - MAIN_BLIT_WIDTH) / 2;
        topPos = (height - MAIN_BLIT_HEIGHT) / 2;
        createBuyButton();
        buyButton.active = false;
        addRenderableWidget(buyButton);
        createSellButton();
        addRenderableWidget(sellButton);
        createOfferButtons();
    }

    private void createOfferButtons() {
        int index = 0;
        for (int i = 0; i < OFFERS_GRID_ROWS; ++i) {
            for (int j = 0; j < OFFERS_GRID_COLUMNS; ++j) {
                addRenderableWidget(new OfferButton(leftPos + OFFERS_GRID_X + j * OfferButton.WIDTH,
                    topPos + OFFERS_GRID_Y + i * OfferButton.HEIGHT, index));
                ++index;
            }
        }
    }

    private void createBuyButton() {
        buyButton = new Button.Builder(Ouat.translatable("buy"), pressed -> {
            pressed.active = false;
            sellButton.active = true;
            switchTradeMode();
        }
        ).bounds(leftPos + 7, topPos + 20, 45, 16).build();
    }

    private void createSellButton() {
        sellButton = new Button.Builder(Ouat.translatable("sell"), pressed -> {
            pressed.active = false;
            buyButton.active = true;
            switchTradeMode();
        }
        ).bounds(leftPos + 54, topPos + 20, 45, 16).build();
    }

    private void switchTradeMode() {
        scrollOff = 0;
        menu.selectOffer(-1, true);
        Ouat.CLIENT.sendToServer(new C2STradePacket(-1, true));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, MAIN_BLIT_WIDTH, MAIN_BLIT_HEIGHT, imageWidth, imageHeight);
        renderTabs(graphics);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderXpBar(graphics);
        renderScroller(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderXpBar(GuiGraphics graphics) {
        graphics.blit(TEXTURE, leftPos + XP_BAR_X, topPos + XP_BAR_Y, XP_BAR_EMPTY_OFFSET_X, XP_BAR_EMPTY_OFFSET_Y, XP_BAR_WIDTH, XP_BAR_HEIGHT, imageWidth, imageHeight);
    }

    private void renderScroller(GuiGraphics graphics) {
        int i = 1 + maxScrolls();
        if (i > 1) {
            int j = SCROLL_BAR_HEIGHT - (SCROLLER_HEIGHT + (i - 1) * SCROLL_BAR_HEIGHT / i);
            int k = 1 + j / i + SCROLL_BAR_HEIGHT / i;
            int scrollerOffset = Math.min(SCROLL_BAR_BOTTOM, scrollOff * k);
            if (scrollOff == i - 1) {
                scrollerOffset = SCROLL_BAR_BOTTOM;
            }
            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP + scrollerOffset, SCROLLER_ENABLED_OFFSET_X, SCROLLER_ENABLED_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, imageWidth, imageHeight);
        } else {
            graphics.blit(TEXTURE, leftPos + SCROLLER_X, topPos + SCROLL_BAR_TOP, SCROLLER_DISABLED_OFFSET_X, SCROLLER_DISABLED_OFFSET_Y, SCROLLER_WIDTH, SCROLLER_HEIGHT, imageWidth, imageHeight);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.canScroll()) {
            this.scrollOff = Mth.clamp((int) ((double) scrollOff - delta), 0, maxScrolls());
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        isDragging = this.canScroll()
            && mouseX > (double) (leftPos + SCROLLER_X) && mouseX < (double) (leftPos + SCROLLER_X + SCROLLER_WIDTH)
            && mouseY > (double) (topPos + SCROLL_BAR_TOP) && mouseY <= (double) (topPos + SCROLL_BAR_TOP + SCROLL_BAR_HEIGHT + 1);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            int j = topPos + SCROLL_BAR_TOP;
            int k = j + SCROLL_BAR_HEIGHT;
            int l = maxScrolls();
            float f = ((float) mouseY - (float) j - 13.5F) / ((float) (k - j) - 27.0F);
            f = f * (float) l + 0.5F;
            scrollOff = Mth.clamp((int) f, 0, l);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
    }

    private boolean canScroll() {
        return menu.getOffers().size() > OFFERS_GRID_ROWS * OFFERS_GRID_COLUMNS;
    }

    private int maxScrolls() {
        return Mth.ceil((((double) menu.getOffers().size() - (double) OFFERS_GRID_ROWS * OFFERS_GRID_COLUMNS) / OFFERS_GRID_COLUMNS));
    }

    private class OfferButton extends Button {
        private static final int WIDTH = 18;
        private static final int HEIGHT = 20;
        private static final int UNSEL_OFFSET_X = 0;
        private static final int UNSEL_OFFSET_Y = 166;
        private static final int SEL_OFFSET_X = 18;
        private static final int SEL_OFFSET_Y = 166;
        private static final int HOVERED_OFFSET_X = 36;
        private static final int HOVERED_OFFSET_Y = 166;
        private final int index;

        public OfferButton(int x, int y, int index) {
            super(x, y, WIDTH, HEIGHT, CommonComponents.EMPTY, (pressed) -> {
                    int offer = ((OfferButton) pressed).index + (scrollOff * OFFERS_GRID_COLUMNS);
                    menu.selectOffer(offer, false);
                    Ouat.CLIENT.sendToServer(new C2STradePacket(offer, false));
                },
                DEFAULT_NARRATION);
            this.index = index;
            this.visible = false;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            visible = offerIndex() < menu.getOffers().size();
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            NpcOffer offer = menu.getOffers().get(offerIndex());
            ItemStack dealStack = offer.getTradeType().equals(NpcOffer.TradeType.BUY) ? offer.getResult() : offer.getInputA();
            if (!dealStack.isEmpty()) {
                graphics.renderFakeItem(dealStack, getX() + 1, getY() + 2);
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                int inStock = 4;
                graphics.drawString(font, String.valueOf(inStock), getX() + 18 - font.width(String.valueOf(inStock)), getY() + 11, 16777215, true);
                graphics.pose().popPose();
            }
            boolean activeOffer = offerIndex() == menu.getSelectedOffer();
            if (isHovered && !activeOffer) {
                graphics.blit(TEXTURE, getX(), getY(), HOVERED_OFFSET_X, HOVERED_OFFSET_Y, width, height, imageWidth, imageHeight);
            } else if (activeOffer) {
                graphics.blit(TEXTURE, getX(), getY(), SEL_OFFSET_X, SEL_OFFSET_Y, width, height, imageWidth, imageHeight);
            } else {
                graphics.blit(TEXTURE, getX(), getY(), UNSEL_OFFSET_X, UNSEL_OFFSET_Y, width, height, imageWidth, imageHeight);
            }
            if (isHovered()) {
                renderToolTip(graphics, mouseX, mouseY);
            }
        }

        public void renderToolTip(GuiGraphics graphics, int mouseX, int mouseY) {
            NpcOffer offer = menu.getOffers().get(offerIndex());
            ItemStack result = offer.getResult();
            ItemStack stackA = offer.getInputA();
            ItemStack stackB = offer.getInputB();
            ItemStack tooltipStack = offer.getTradeType() == NpcOffer.TradeType.BUY ? result : stackA;
            List<Component> text = getTooltipFromItem(minecraft, tooltipStack);
            Ouat.COMMON.renderTooltip(graphics, font, text, new TradeItemTooltip(stackA, stackB, result), tooltipStack, mouseX, mouseY);
        }

        private int offerIndex() {
            return index + scrollOff * OFFERS_GRID_COLUMNS;
        }
    }
}

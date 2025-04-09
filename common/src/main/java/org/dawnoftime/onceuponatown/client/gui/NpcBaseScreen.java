package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.menu.NpcBaseMenu;
import org.dawnoftime.onceuponatown.network.C2SChangeNpcTabPacket;

import java.util.ArrayList;
import java.util.List;

public abstract class NpcBaseScreen<T extends NpcBaseMenu> extends AbstractContainerScreen<T> {
    protected static final ResourceLocation BACKGROUND_TEXTURE = Ouat.modResource("textures/gui/npc_screen.png");
    protected static final ResourceLocation TABS_TEXTURE = Ouat.modResource("textures/gui/tabs/tabs.png");
    protected static final int BACKGROUND_WIDTH = 283;
    protected static final int BACKGROUND_HEIGHT = 175;
    private static final int TABS_TEXTURE_WIDTH = 35;
    private static final int TABS_TEXTURE_HEIGHT = 66;
    private static final int INACTIVE_TAB_TEXTURE_OFFSET_Y = 26;
    private static final int TAB_WIDTH = 32;
    private static final int TAB_HEIGHT = 26;
    private static final int ARROW_WIDTH = 11;
    private static final int ARROW_HEIGHT = 7;
    private static final int ARROW_UP_OFFSET_Y = 52;
    private static final int ARROW_DOWN_OFFSET_Y = 59;
    private static final int ARROW_X = -20;
    private static final int ARROW_UP_Y = 5;
    private static final int ARROW_DOWN_Y = 160;
    private final List<TabButton> tabs = new ArrayList<>();
    private static int tabScroll = 0;

    protected NpcBaseScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 581;
        imageHeight = 531;
        titleLabelX = 7;
        titleLabelY = 7;
        inventoryLabelX = 112;
        inventoryLabelY = 72;

        List<Tab> professionTabs = getProfessionTabs();
        int maxScrolls = professionTabs.size() - 5;
        if (tabScroll > maxScrolls) {
            tabScroll = 0;
        }
        int tabY = 18; // Start y
        for (int i = 0; i < professionTabs.size(); ++i) {
            Tab tab = professionTabs.get(i);
            tabs.add(new TabButton(tab, -TAB_WIDTH, tabY));
            if (tab == getTab() && tabScroll == 0) {
                tabScroll = Math.max(i - 4, 0);
            }
            tabY += TAB_HEIGHT + 1;
        }
        if (tabScroll > 0) {
            for (TabButton tabButton : tabs) {
                tabButton.y -= tabScroll * (TAB_HEIGHT + 1);
            }
        }
    }

    @Override
    protected void init() {
        leftPos = (width - BACKGROUND_WIDTH) / 2;
        topPos = (height - BACKGROUND_HEIGHT) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(graphics);
        graphics.blit(BACKGROUND_TEXTURE, leftPos, topPos, 0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, imageWidth, imageHeight);
        renderTabs(graphics);
    }

    protected void renderTabs(GuiGraphics graphics) {
        List<Tab> professionTabs = getProfessionTabs();
        if (professionTabs.size() > 5) {
            graphics.blit(TABS_TEXTURE, leftPos + ARROW_X, topPos + ARROW_UP_Y, 0, ARROW_UP_OFFSET_Y, ARROW_WIDTH, ARROW_HEIGHT, TABS_TEXTURE_WIDTH, TABS_TEXTURE_HEIGHT);
            graphics.blit(TABS_TEXTURE, leftPos + ARROW_X, topPos + ARROW_DOWN_Y, 0, ARROW_DOWN_OFFSET_Y, ARROW_WIDTH, ARROW_HEIGHT, TABS_TEXTURE_WIDTH, TABS_TEXTURE_HEIGHT);
        }

        for (int i = 0; i < Math.min(professionTabs.size(), 5); ++i) {
            TabButton tabButton = tabs.get(i + tabScroll);
            if (tabButton.tab == getTab()) {
                graphics.blit(TABS_TEXTURE, leftPos + tabButton.x, topPos + tabButton.y, 0, 0, TABS_TEXTURE_WIDTH, TAB_HEIGHT, TABS_TEXTURE_WIDTH, TABS_TEXTURE_HEIGHT);
                graphics.blit(tabButton.tab.iconTexture, leftPos + tabButton.x + 9, topPos + tabButton.y + 5, 0, 0, 16, 16, 16, 16);
            } else {
                graphics.blit(TABS_TEXTURE, leftPos + tabButton.x, topPos + tabButton.y, 0, INACTIVE_TAB_TEXTURE_OFFSET_Y, TABS_TEXTURE_WIDTH, TAB_HEIGHT, TABS_TEXTURE_WIDTH, TABS_TEXTURE_HEIGHT);
                graphics.blit(tabButton.tab.iconTexture, leftPos + tabButton.x + 10, topPos + tabButton.y + 5, 0, 0, 16, 16, 16, 16);
            }
        }
    }

    protected void renderInventory(GuiGraphics graphics) {
        graphics.drawString(font, playerInventoryTitle, leftPos + 114, topPos + 82, 4210752, false);
        graphics.blit(BACKGROUND_TEXTURE, leftPos + 113, topPos + 92, 0, 175, 162, 76, imageWidth, imageHeight);
    }

    protected void renderNpcDoll(GuiGraphics graphics, int mouseX, int mouseY, int dollX, int dollY, int dollScale) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + dollX, topPos + dollY, dollScale, leftPos + dollX - mouseX, topPos + dollY - mouseY, menu.getNpc().getNpc());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 4210752, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Tab> professionTabs = getProfessionTabs();
        if (mouseX >= leftPos + ARROW_X && mouseX <= leftPos + ARROW_X + ARROW_WIDTH && mouseY >= topPos + ARROW_UP_Y && mouseY <= topPos + ARROW_UP_Y + ARROW_HEIGHT) {
            if (tabScroll > 0) {
                tabScroll--;
                for (TabButton tabButton : tabs) {
                    tabButton.y += (TAB_HEIGHT + 1);
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.5F));
            }
        }
        if (mouseX >= leftPos + ARROW_X && mouseX <= leftPos + ARROW_X + ARROW_WIDTH && mouseY >= topPos + ARROW_DOWN_Y && mouseY <= topPos + ARROW_DOWN_Y + ARROW_HEIGHT) {
            if (tabScroll < (professionTabs.size() - 5)) {
                tabScroll++;
                for (TabButton tabButton : tabs) {
                    tabButton.y -= (TAB_HEIGHT + 1);
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.5F));
            }
        }
        for (int i = 0; i < Math.min(professionTabs.size(), 5); ++i) {
            TabButton tabButton = tabs.get(i + tabScroll);
            if ((tabButton.tab != getTab()) && (mouseX >= leftPos + tabButton.x) && (mouseX <= leftPos + tabButton.x + TAB_WIDTH) && (mouseY >= topPos + tabButton.y) && (mouseY <= topPos + tabButton.y + TAB_HEIGHT)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                Ouat.CLIENT.sendToServer(new C2SChangeNpcTabPacket(tabButton.tab.ordinal()));
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if ((mouseX >= leftPos - TAB_WIDTH) && (mouseX <= leftPos) && (mouseY >= topPos) && (mouseY <= topPos + BACKGROUND_HEIGHT)) {
            if (delta < 0 && tabScroll < (getProfessionTabs().size() - 5)) {
                tabScroll++;
                for (TabButton tabButton : tabs) {
                    tabButton.y -= (TAB_HEIGHT + 1);
                }
            }
            if (delta > 0 && tabScroll > 0) {
                tabScroll--;
                for (TabButton tabButton : tabs) {
                    tabButton.y += (TAB_HEIGHT + 1);
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private List<Tab> getProfessionTabs() {
        return List.of(Tab.TRADE, Tab.QUESTS, Tab.BUILDINGS, Tab.PROGRESSION, Tab.PROJECTS, Tab.DIPLOMACY);
    }

    protected abstract Tab getTab();

    public enum Tab {
        TRADE(Ouat.modResource("textures/item/emerald_pouch.png")),
        QUESTS(new ResourceLocation("textures/item/book.png")),
        BUILDINGS(Ouat.modResource("textures/item/town_scroll.png")),
        PROGRESSION(new ResourceLocation("textures/item/experience_bottle.png")),
        PROJECTS(new ResourceLocation("textures/item/wooden_shovel.png")),
        DIPLOMACY(new ResourceLocation("textures/item/iron_sword.png"));

        public final ResourceLocation iconTexture;

        Tab(ResourceLocation iconTexture) {
            this.iconTexture = iconTexture;
        }
    }

    private static class TabButton {
        Tab tab;
        int x;
        int y;

        TabButton(Tab tab, int x, int y) {
            this.tab = tab;
            this.x = x;
            this.y = y;
        }
    }
}

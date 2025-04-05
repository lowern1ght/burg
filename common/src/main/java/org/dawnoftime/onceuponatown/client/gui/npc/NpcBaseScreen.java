package org.dawnoftime.onceuponatown.client.gui.npc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.ArrayList;
import java.util.List;

public abstract class NpcBaseScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation TABS_TEXTURE = Ouat.modResource("textures/gui/tabs/tabs.png");
    private static final int TABS_TEXTURE_WIDTH = 35;
    private static final int TABS_TEXTURE_HEIGHT = 52;
    private static final int INACTIVE_TAB_TEXTURE_OFFSET_Y = 26;
    private static final int TAB_WIDTH = 32;
    private static final int TAB_HEIGHT = 26;
    private static final int NPC_DOLL_SCALE = 30;
    private static final int NPC_DOLL_X = -28;
    private static final int NPC_DOLL_Y = 15;
    private final List<Tab> tabs = new ArrayList<>();
    private final TabType activeTab;
    protected Npc npc;

    protected NpcBaseScreen(T menu, Inventory inventory, Component title, Npc npc, TabType activeTab) {
        super(menu, inventory, title);
        this.npc = npc;
        this.activeTab = activeTab;
        titleLabelX = 10;
        titleLabelY = 7;
        inventoryLabelX = 112;
        inventoryLabelY = 72;
        List<TabType> wantedTabs = List.of(TabType.TRADE, TabType.QUESTS);
        int tabY = 4;
        for (TabType wantedTab : wantedTabs) {
            tabs.add(new Tab(wantedTab, -TAB_WIDTH, tabY));
            tabY += TAB_HEIGHT + 1;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 4210752, false);
        graphics.drawString(font, title, titleLabelX, titleLabelY, 4210752, false);
        int professionWidth = font.width(Utils.capitalize(npc.getProfessionId()));
        graphics.drawString(font, Utils.capitalize(npc.getProfessionId()), imageWidth - professionWidth - titleLabelX, titleLabelY, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(graphics);
        if (npc != null) {
            renderNpcDoll(graphics, mouseX, mouseY);
            graphics.pose().translate(0.0F, 0.0F, 100.0F); // Hide Npc behind window
        }
    }

    private void renderNpcDoll(GuiGraphics graphics, int mouseX, int mouseY) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + imageWidth + NPC_DOLL_X, topPos + NPC_DOLL_Y, NPC_DOLL_SCALE, leftPos + imageWidth + NPC_DOLL_X - mouseX - 2, topPos - NPC_DOLL_Y - mouseY - 20, npc);
    }

    protected void renderTabs(GuiGraphics graphics) {
        for (Tab tab : tabs) {
            if (tab.tabType == activeTab) {
                graphics.blit(TABS_TEXTURE, leftPos + tab.x, topPos + tab.y, 0, 0, TABS_TEXTURE_WIDTH, TAB_HEIGHT, TABS_TEXTURE_WIDTH, TABS_TEXTURE_HEIGHT);
                graphics.blit(tab.tabType.iconTexture, leftPos + tab.x + 9, topPos + tab.y + 5, 0, 0, 16, 16, 16, 16);
            } else {
                graphics.blit(TABS_TEXTURE, leftPos + tab.x, topPos + tab.y, 0, INACTIVE_TAB_TEXTURE_OFFSET_Y, TABS_TEXTURE_WIDTH, TAB_HEIGHT, TABS_TEXTURE_WIDTH, TABS_TEXTURE_HEIGHT);
                graphics.blit(tab.tabType.iconTexture, leftPos + tab.x + 10, topPos + tab.y + 5, 0, 0, 16, 16, 16, 16);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Tab tab : tabs) {
            if ((tab.tabType != activeTab)
                && (mouseX >= leftPos + tab.x)
                && (mouseX <= leftPos + tab.x + TAB_WIDTH)
                && (mouseY >= topPos + tab.y)
                && (mouseY <= topPos + tab.y + TAB_HEIGHT)
            ) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                //Ouat.CLIENT.sendToServer(new C2SChangeNpcTabPacket(tab.tabType.ordinal()));
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private record Tab(TabType tabType, int x, int y) {
    }

    public enum TabType {
        TRADE(Ouat.modResource("textures/item/emerald_pouch.png")),
        QUESTS(new ResourceLocation("textures/item/book.png")); //"textures/gui/tabs/quests_icon.png"

        public final ResourceLocation iconTexture;

        TabType(ResourceLocation iconTexture) {
            this.iconTexture = iconTexture;
        }
    }
}

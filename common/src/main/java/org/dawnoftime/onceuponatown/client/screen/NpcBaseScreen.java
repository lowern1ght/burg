package org.dawnoftime.onceuponatown.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
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
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.C2SChangeNpcTabPacket;

import java.util.ArrayList;
import java.util.List;

public abstract class NpcBaseScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation EMPTY_TABS_TEXTURE = Ouat.modResource("textures/gui/tabs/empty_tabs.png");
    private static final ResourceLocation CITIZEN_HEADER_TEXTURE = Ouat.modResource("textures/gui/npc_header.png");
    private static final int NPC_HEADER_TEXTURE_WIDTH = 281;
    private static final int NPC_HEADER_TEXTURE_HEIGHT = 65;
    private static final int EMPTY_TABS_TEXTURE_WIDTH = 65;
    private static final int EMPTY_TABS_TEXTURE_HEIGHT = 52;
    private static final int ACTIVE_TAB_OFFSET_X = 30;
    private static final int ACTIVE_TAB_OFFSET_Y = 0;
    private static final int INACTIVE_TAB_OFFSET_X = 0;
    private static final int INACTIVE_TAB_OFFSET_Y = 0;
    private static final int ACTIVE_TAB_WIDTH = 35;
    private static final int ACTIVE_TAB_HEIGHT = 26;
    private static final int INACTIVE_TAB_WIDTH = 30;
    private static final int INACTIVE_TAB_HEIGHT = 26;
    private static final int CITIZEN_DOLL_X = 28;
    private static final int CITIZEN_DOLL_Y = 15;
    private static final int CITIZEN_DOLL_SCALE = 30;
    private static final int MAX_TABS = 10;
    private static final int[] TABS_X = {-32, -32, -32, -32, -32, 280, 280, 280, 280, 280};
    private static final int[] TABS_Y = {3, 30, 57, 84, 111, 3, 30, 57, 84, 111};
    private final List<DrawnTab> drawnTabs = new ArrayList<>();;
    private final NpcTab activeTab;
    protected Npc npc;

    public NpcBaseScreen(T menu, Inventory inventory, Component title, NpcTab activeTab) {
        super(menu, inventory, title);
        System.out.println(this.font);
        this.titleLabelY = 7;
        this.inventoryLabelX = 112;
        this.inventoryLabelY = 72;
        this.activeTab = activeTab;
        List<NpcTab> tabs = new ArrayList<>();
        tabs.add(NpcTab.TRADE);
        tabs.add(NpcTab.BUY);
        tabs.add(NpcTab.QUESTS);
        for (int i = 0; i < tabs.size(); ++i) {
            if (i >= MAX_TABS) {
                break;
            }
            this.drawnTabs.add(new DrawnTab(i, tabs.get(i), TABS_X[i], TABS_Y[i]));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
        int titleX = (leftPos + NPC_HEADER_TEXTURE_WIDTH) / 2;
        graphics.drawString(this.font, title, titleX, titleLabelY, 4210752, false);
    }

    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        String npcDescription = "Fermier";
        //int offset = Mth.clamp(NPC_HEADER_TEXTURE_WIDTH - (this.font.width(npcDescription) + 62), 3, 176);
        //graphics.blit(CITIZEN_HEADER_TEXTURE, this.leftPos + offset, this.topPos - 46, offset, 0, NPC_HEADER_TEXTURE_WIDTH - offset, NPC_HEADER_TEXTURE_HEIGHT, NPC_HEADER_TEXTURE_WIDTH, NPC_HEADER_TEXTURE_HEIGHT);
        //graphics.blit(CITIZEN_HEADER_TEXTURE, this.leftPos + offset - 2, this.topPos - 46, 0, 0, 6, NPC_HEADER_TEXTURE_HEIGHT, NPC_HEADER_TEXTURE_WIDTH, NPC_HEADER_TEXTURE_HEIGHT);
        graphics.blit(CITIZEN_HEADER_TEXTURE, this.leftPos, this.topPos - NPC_HEADER_TEXTURE_HEIGHT + 7, 0, 0, NPC_HEADER_TEXTURE_WIDTH, 48, NPC_HEADER_TEXTURE_WIDTH, NPC_HEADER_TEXTURE_HEIGHT);

        if (this.npc != null) {
            renderNpcDoll(graphics, mouseX, mouseY);
        }
        graphics.pose().translate(0,0,100);
        graphics.blit(CITIZEN_HEADER_TEXTURE, this.leftPos, this.topPos - NPC_HEADER_TEXTURE_HEIGHT + 7 + 48, 0, 48, NPC_HEADER_TEXTURE_WIDTH, 8, NPC_HEADER_TEXTURE_WIDTH, NPC_HEADER_TEXTURE_HEIGHT);
        graphics.drawString(this.font, Component.literal(npcDescription), leftPos + 54, topPos - 48, 4210752, false);
        graphics.blit(CITIZEN_HEADER_TEXTURE, this.leftPos + 54, this.topPos - 35, 0, 56, 9, 9, NPC_HEADER_TEXTURE_WIDTH, NPC_HEADER_TEXTURE_HEIGHT);
        graphics.drawString(this.font, Component.literal("10"), leftPos + 54 + 12, topPos - 34, 4210752, false);
        graphics.blit(CITIZEN_HEADER_TEXTURE, this.leftPos + 54, this.topPos - 22, 9, 56, 9, 9, NPC_HEADER_TEXTURE_WIDTH, NPC_HEADER_TEXTURE_HEIGHT);
        graphics.drawString(this.font, Component.literal("4"), leftPos + 54 + 12, topPos - 21, 4210752, false);
    }

    private void renderNpcDoll(GuiGraphics graphics, int mouseX, int mouseY) {
        float lookAtX = this.leftPos + 27 - mouseX;
        /*
            TODO: FIX THIS HORROR !!!
         */
        int b = switch (this.minecraft.options.guiScale().get()) {
                case 1 -> -450;
                case 2 -> -110;
                case 3 -> -10;
                case 4 -> 35;
                case 5 -> 45;
                case 6 -> 35;
                default -> 30;
            };
        int f = 30;
        float lookAtY = this.topPos + b - (float)Math.log((mouseY)) * f;
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,leftPos + CITIZEN_DOLL_X ,topPos + CITIZEN_DOLL_Y, CITIZEN_DOLL_SCALE, lookAtX, lookAtY, this.npc);
    }

    protected void renderTabs(GuiGraphics graphics) {
        this.drawnTabs.forEach((drawnTab -> renderTab(graphics, drawnTab.tab, drawnTab.x, drawnTab.y)));
    }

    private void renderTab(GuiGraphics graphics, NpcTab tab, int tabX, int tabY) {
        int inactiveTabFoldOffsetX = 2;
        if (this.activeTab == tab) {
            graphics.blit(EMPTY_TABS_TEXTURE, this.leftPos + tabX , this.topPos + tabY, ACTIVE_TAB_OFFSET_X, ACTIVE_TAB_OFFSET_Y, ACTIVE_TAB_WIDTH, ACTIVE_TAB_HEIGHT, EMPTY_TABS_TEXTURE_WIDTH, EMPTY_TABS_TEXTURE_HEIGHT);
            graphics.blit(tab.iconTexture, this.leftPos + tabX + tab.iconOffsetX - 1, this.topPos + tabY + tab.iconOffsetY, 0, 0, tab.iconWidth, tab.iconHeight, tab.iconWidth, tab.iconHeight);
        } else {
            graphics.blit(EMPTY_TABS_TEXTURE, this.leftPos + tabX + inactiveTabFoldOffsetX , this.topPos + tabY, INACTIVE_TAB_OFFSET_X, INACTIVE_TAB_OFFSET_Y, INACTIVE_TAB_WIDTH, INACTIVE_TAB_HEIGHT, EMPTY_TABS_TEXTURE_WIDTH, EMPTY_TABS_TEXTURE_HEIGHT);
            graphics.blit(tab.iconTexture, this.leftPos + tabX + tab.iconOffsetX, this.topPos + tabY + tab.iconOffsetY, 0, 0, tab.iconWidth, tab.iconHeight, tab.iconWidth, tab.iconHeight);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (DrawnTab drawnTab : this.drawnTabs) {
            if ((drawnTab.tab != this.activeTab)
            && (mouseX >= leftPos + drawnTab.x)
            && (mouseX <= leftPos + drawnTab.x + INACTIVE_TAB_WIDTH + 2)
            && (mouseY >= topPos + drawnTab.y)
            && (mouseY <= topPos+ drawnTab.y + INACTIVE_TAB_HEIGHT)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                Ouat.CLIENT.sendToServer(new C2SChangeNpcTabPacket(drawnTab.tab.ordinal()));
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private record DrawnTab(int index, NpcTab tab, int x, int y) {}

    public enum NpcTab {
        BUY(Ouat.modResource("textures/gui/tabs/buy_icon.png"), 14, 21, 11, 3),
        SELL(Ouat.modResource("textures/gui/tabs/sell_icon.png"), 14, 21, 11, 3),
        QUESTS(Ouat.modResource("textures/gui/tabs/quests_icon.png"),15 ,12 ,10 ,7),
        INFO(new ResourceLocation("textures/item/oak_sign.png"), 16, 16, 10, 5),
        TRADE(Ouat.modResource("textures/item/emerald_pouch_full.png"), 16, 16, 10, 6);

        public final ResourceLocation iconTexture;
        public final int iconWidth;
        public final int iconHeight;
        public final int iconOffsetX;
        public final int iconOffsetY;

        NpcTab(ResourceLocation iconTexture, int iconWidth, int iconHeight, int iconOffsetX, int iconOffsetY) {
            this.iconTexture = iconTexture;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
            this.iconOffsetX = iconOffsetX;
            this.iconOffsetY = iconOffsetY;
        }
    }
}

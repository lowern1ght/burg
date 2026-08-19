package org.lowern1ght.burg;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.DoubleListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lowern1ght.burg.client.TownHubClientState;
import org.lowern1ght.burg.client.gui.TownHubScreenV2;
import org.lowern1ght.burg.client.gui.tooltip.BuildingProductionTooltip;
import org.lowern1ght.burg.client.gui.tooltip.ClientBuildingProductionTooltip;
import org.lowern1ght.burg.client.gui.tooltip.ClientItemAndTitleTooltip;
import org.lowern1ght.burg.client.gui.tooltip.ItemAndTitleTooltip;
import org.lowern1ght.burg.client.model.NpcModel;
import org.lowern1ght.burg.client.renderer.CitizenRenderer;
import org.lowern1ght.burg.client.renderer.NpcRenderer;
import org.lowern1ght.burg.client.renderer.TownVillagerRenderer;
import org.lowern1ght.burg.entity.Citizen;
import org.lowern1ght.burg.client.screen.TownHubScreen;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.infrastructure.config.BurgConfig;
import org.lowern1ght.burg.network.C2SAdvanceEraPacket;
import org.lowern1ght.burg.network.C2SBuyPacket;
import org.lowern1ght.burg.network.C2SContributeQuestPacket;
import org.lowern1ght.burg.network.C2SDepositPacket;
import org.lowern1ght.burg.network.C2SQueueBuildingPacket;
import org.lowern1ght.burg.network.C2SRemoveQueuedBuildingPacket;
import org.lowern1ght.burg.network.C2SRequestStockPacket;
import org.lowern1ght.burg.network.C2SSupplyStockPacket;
import org.lowern1ght.burg.network.C2SToggleChatBroadcastPacket;
import org.lowern1ght.burg.network.C2SUpgradeBuildingPacket;
import org.lowern1ght.burg.network.NetworkHelper;
import org.lowern1ght.burg.people.GrowthMultiplier;
import org.lowern1ght.burg.screen.TownHubMenu;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class OuatForgeClient {

    // ADR-0021 — Cloth Config provides the user-facing config screen.
    // Registered at class-load time so the Mods → Burg → Config button works
    // from the very first launch, no need for the player to enable anything.
    // The screen itself is rebuilt every time it is opened (per Cloth's docs).
    //
    // ADR-0022 — register the SUPPLY-mode open-gateway tick listener on the
    // GAME bus (the mod-bus @EventBusSubscriber(Bus.MOD) on this class only
    // handles setup events; ClientTickEvent lives on the NeoForge.EVENT_BUS).
    static {
        ModLoadingContext.get().registerExtensionPoint(
            IConfigScreenFactory.class,
            () -> (container, parent) -> buildConfigScreen(parent)
        );
        NeoForge.EVENT_BUS.addListener(OuatForgeClient::onClientTick);
    }

    /**
     * Build the Cloth Config screen for the Burg configuration.
     *
     * <p>The screen reads the current value from {@link BurgConfig}'s spec,
     * shows it in a slider bounded by {@link GrowthMultiplier#MIN} /
     * {@link GrowthMultiplier#MAX}, and on save calls
     * {@link BurgConfig#refreshMultiplier()} (already wired to the
     * ModConfigEvent.Reloading listener in {@code OuatForge}) so the next
     * {@code DaySim.tickDay} sees the new value without a world reload.
     */
    private static Screen buildConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Burg"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("general"));

        DoubleListEntry entry = entryBuilder
            .startDoubleField(
                Component.literal("Villager Growth Multiplier"),
                BurgConfig.VILLAGER_GROWTH_MULTIPLIER.get())
            .setDefaultValue(GrowthMultiplier.DEFAULT_VALUE)
            .setMin(GrowthMultiplier.MIN)
            .setMax(GrowthMultiplier.MAX)
            .setTooltip(Component.literal(
                "Scales how quickly new villagers join established towns. 1.0 = default. Higher = faster growth."))
            .setSaveConsumer(BurgConfig::refreshMultiplier)
            .build();
        general.addEntry(entry);

        return builder.build();
    }

    /**
     * ADR-0022 — opens the act-4 SUPPLY-mode {@link TownHubScreenV2}
     * directly via {@link Minecraft#setScreen}. The V2 screen is not a
     * menu screen (no {@code AbstractContainerScreen}), so it cannot
     * ride the legacy {@code openMenu(be)} path; the server sends a
     * small {@code S2COpenTownHubV2Packet} gateway, the client-side
     * handler stashes the anchor in {@link TownHubClientState#openTownHubV2},
     * and the next tick {@link #pollOpenTownHubV2()} reads it.
     *
     * <p>Must be called on the client thread; the gateway handler runs
     * inside {@code enqueueWork} which is fine, and {@link #pollOpenTownHubV2()}
     * is wired to the client tick above so the actual
     * {@link Minecraft#setScreen} call always lands on the client tick.
     */
    private static void openTownHubV2(BlockPos anchorPos) {
        Minecraft.getInstance().setScreen(TownHubScreenV2.withAnchor(anchorPos));
    }

    /**
     * Polls {@link TownHubClientState#openTownHubV2} once per client tick.
     * Reads the one-shot flag, opens the V2 screen anchored at the same
     * town, and nulls the flag back so a stale value never fires twice.
     * The poll is a poll rather than a direct handler call because the
     * {@code S2COpenTownHubV2Packet.handle} runs on the network thread,
     * and {@link Minecraft#setScreen} must execute on the client thread.
     *
     * <p>Registered on the GAME bus via {@link NeoForge#EVENT_BUS#addListener}
     * in the static block — the class-level {@code @EventBusSubscriber(Bus.MOD)}
     * only handles setup events. Same pattern as
     * {@code OuatForge#onServerTick}.
     */
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (TownHubClientState.openTownHubV2 == null) return;
        BlockPos anchor = TownHubClientState.openTownHubV2;
        TownHubClientState.openTownHubV2 = null;
        openTownHubV2(anchor);
    }

    // EntityRenderersEvent fires during the initial resource reload, before FMLCommonSetupEvent sets
    // the common static fields -- use BuiltInRegistries directly.
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityType<Npc> npc = (EntityType<Npc>) BuiltInRegistries.ENTITY_TYPE
            .get(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "npc"));
        if (npc != null) {
            event.registerEntityRenderer(npc, NpcRenderer::new);
        }
        EntityType<Citizen> citizen = (EntityType<Citizen>) BuiltInRegistries.ENTITY_TYPE
            .get(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "citizen"));
        if (citizen != null) {
            event.registerEntityRenderer(citizen, CitizenRenderer::new);
        }
        // Overrides vanilla's own renderer for EVERY villager in the world. Supported by the
        // event, and deliberately not a mixin. TownVillagerRenderer hands anything that is not
        // a citizen to a real VillagerRenderer, so a village the player found looks untouched.
        event.registerEntityRenderer(EntityType.VILLAGER, TownVillagerRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NpcModel.LAYER_LOCATION, NpcModel::createBodyLayer);
        // AND NOTHING ELSE. Hair, beards and headwear used to register 42 baked cubes here; they
        // are paint on the `hat` cube now, and `hat` is part of the body layer above. See
        // NpcHairLayer for the measurement that retired the geometry — 31 of 31 reference skins
        // use the head's second layer — and note that this loop is also where the black screen
        // came from: it baked every location in an array whose absent variants were nulls.
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        MenuType<TownHubMenu> hub = (MenuType<TownHubMenu>) BuiltInRegistries.MENU
            .get(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "town_hub"));
        if (hub != null) {
            event.register(hub, TownHubScreen::new);
        }
    }

    // FMLClientSetupEvent fires after FMLCommonSetupEvent, so MenuRegistry.TOWN_HUB is set
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Block townAnchor = BuiltInRegistries.BLOCK
                .get(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "town_anchor"));
            if (townAnchor != null) {
                ItemBlockRenderTypes.setRenderLayer(townAnchor, RenderType.cutout());
            }

            // Wire C2C packet delegates: client → server via PacketDistributor.sendToServer.
            // Each delegate constructs the payload and sends it from the client connection.
            NetworkHelper.sendDepositPacket              = pos              -> PacketDistributor.sendToServer(new C2SDepositPacket(pos));
            NetworkHelper.sendQueueBuildingPacket        = (pos, defId)     -> PacketDistributor.sendToServer(new C2SQueueBuildingPacket(pos, defId));
            NetworkHelper.sendRemoveQueuedBuildingPacket = (pos, index)     -> PacketDistributor.sendToServer(new C2SRemoveQueuedBuildingPacket(pos, index));
            NetworkHelper.sendUpgradeBuildingPacket      = (pos, worldPos)  -> PacketDistributor.sendToServer(new C2SUpgradeBuildingPacket(pos, worldPos));
            NetworkHelper.sendAdvanceEraPacket           = (pos, pathId)    -> PacketDistributor.sendToServer(new C2SAdvanceEraPacket(pos, pathId));
            NetworkHelper.sendContributeQuestPacket      = (pos, questId)   -> PacketDistributor.sendToServer(new C2SContributeQuestPacket(pos, questId));
            NetworkHelper.sendRequestStockPacket         = pos              -> PacketDistributor.sendToServer(new C2SRequestStockPacket(pos));
            NetworkHelper.sendToggleChatBroadcastPacket  = pos              -> PacketDistributor.sendToServer(new C2SToggleChatBroadcastPacket(pos));
            NetworkHelper.sendBuyPacket                  = (pos, items)     -> PacketDistributor.sendToServer(new C2SBuyPacket(pos, items));
            // SUPPLY-mode TownHubScreenV2 wires (anchor, itemId, quantity) back
            // to the server so the player can refill the town's reserve.
            NetworkHelper.sendSupplyStockPacket          = (pos, itemId, qty) -> PacketDistributor.sendToServer(new C2SSupplyStockPacket(pos, itemId, qty));
        });
    }

    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ItemAndTitleTooltip.class, ClientItemAndTitleTooltip::new);
        event.register(BuildingProductionTooltip.class, ClientBuildingProductionTooltip::new);
    }
}

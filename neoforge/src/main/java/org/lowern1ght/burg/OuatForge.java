package org.lowern1ght.burg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.lowern1ght.burg.block.TownAnchorBlock;
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
import org.lowern1ght.burg.network.S2CBuildingDefsPacket;
import org.lowern1ght.burg.network.S2CBuildingListPacket;
import org.lowern1ght.burg.network.S2CCitizenUpdatePacket;
import org.lowern1ght.burg.network.S2CEraUpdatePacket;
import org.lowern1ght.burg.network.S2CLogEntryPacket;
import org.lowern1ght.burg.network.S2COpenTownHubV2Packet;
import org.lowern1ght.burg.network.S2CQuestUpdatePacket;
import org.lowern1ght.burg.network.S2CStockUpdatePacket;
import org.lowern1ght.burg.network.S2CTownHubPacket;
import org.lowern1ght.burg.network.S2CVillagerIdentityPacket;
import org.lowern1ght.burg.blockentity.TownAnchorBlockEntity;
import org.lowern1ght.burg.command.TownCommand;
import org.lowern1ght.burg.datapack.BuilderConfigDataHandler;
import org.lowern1ght.burg.datapack.BuildingDataHandler;
import org.lowern1ght.burg.datapack.BuildingListDataHandler;
import org.lowern1ght.burg.datapack.EraTransitionDataHandler;
import org.lowern1ght.burg.datapack.FoodListDataHandler;
import org.lowern1ght.burg.datapack.QuestDataHandler;
import org.lowern1ght.burg.datapack.SettlerJobsDataHandler;
import org.lowern1ght.burg.datapack.TradePriceDataHandler;
import org.lowern1ght.burg.entity.Citizen;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.entity.citizen.CitizenData;
import org.lowern1ght.burg.entity.citizen.Citizens;
import org.lowern1ght.burg.registry.AttachmentRegistry;
import org.lowern1ght.burg.registry.BlockEntityRegistry;
import org.lowern1ght.burg.registry.BlockRegistry;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.registry.ItemRegistry;
import org.lowern1ght.burg.registry.MenuRegistry;
import org.lowern1ght.burg.screen.TownHubMenu;
import org.lowern1ght.burg.tick.TickScheduler;
import org.lowern1ght.burg.town.TownIntegrity;

import java.util.function.BiFunction;

@Mod(Constants.MOD_ID)
public class OuatForge {

    // DeferredRegister-based registration. The common module's Ouat.init() also registers
    // the same blocks/items via direct Registry.register() for use by code that runs
    // before the mod-bus fires (e.g. data-pack reload). Both paths produce equivalent
    // values because the registry is a single identity map.
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Constants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(BuiltInRegistries.MENU, Constants.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Constants.MOD_ID);

    // Town membership on a vanilla villager. `serializable` rather than a Codec because
    // CitizenData already round-trips itself through CompoundTag and the codec form buys
    // nothing here. NOT `copyOnDeath` — a villager that dies is not replaced by a copy.
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<CitizenData>> CITIZEN_DATA_OBJ =
        ATTACHMENT_TYPES.register("citizen", () ->
            AttachmentType.<CompoundTag, CitizenData>serializable(CitizenData::new).build());

    private static final DeferredBlock<Block> TOWN_ANCHOR_OBJ =
        BLOCKS.register("town_anchor",
            () -> new TownAnchorBlock(TownAnchorBlock.defaultProperties()));

    // Block registry fires before block entity registry, so TOWN_ANCHOR_OBJ.get() is safe here
    private static final DeferredHolder<BlockEntityType<?>, ?> TOWN_ANCHOR_BE_OBJ =
        BLOCK_ENTITY_TYPES.register("town_anchor", () -> {
            BiFunction<BlockPos, BlockState, TownAnchorBlockEntity> factory = TownAnchorBlockEntity::new;
            return BlockEntityType.Builder.of(factory::apply, TOWN_ANCHOR_OBJ.get()).build(null);
        });

    private static final DeferredHolder<EntityType<?>, ?> NPC_OBJ =
        ENTITY_TYPES.register("npc", () ->
            EntityType.Builder.<Npc>of(Npc::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "npc").toString())
        );

    private static final DeferredHolder<EntityType<?>, ?> CITIZEN_OBJ =
        ENTITY_TYPES.register("citizen", () ->
            EntityType.Builder.<Citizen>of(Citizen::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "citizen").toString())
        );

    private static final DeferredHolder<MenuType<?>, ?> TOWN_HUB_OBJ =
        MENU_TYPES.register("town_hub",
            () -> IMenuTypeExtension.create((syncId, inv, buf) -> new TownHubMenu(syncId, inv)));

    private static final DeferredItem<Item> TOWN_ANCHOR_ITEM_OBJ =
        ITEMS.register("town_anchor",
            () -> new BlockItem(TOWN_ANCHOR_OBJ.get(), new Item.Properties()));

    public OuatForge() {
        IEventBus modBus = ModLoadingContext.get().getActiveContainer().getEventBus();

        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        ITEMS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onEntityAttributes);
        modBus.addListener(this::onRegisterPayloadHandlers);

        // ADR-0021 — register the user-facing config spec. Cloth Config ships
        // with the mod (declared `runtimeOnly` in build.gradle) and provides
        // the screen; the spec itself is NeoForge's `ModConfigSpec` so the
        // data is also reachable from the bare-JVM population simulation.
        ModLoadingContext.get().getActiveContainer().registerConfig(
            ModConfig.Type.COMMON, BurgConfig.SPEC, "burg-common.toml");

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onStartTracking);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(this::onModConfigReloading);
    }

    // Populate the common static fields after all DeferredRegister suppliers have run
    @SuppressWarnings("unchecked")
    private void onCommonSetup(FMLCommonSetupEvent event) {
        BlockRegistry.TOWN_ANCHOR = TOWN_ANCHOR_OBJ.get();
        BlockEntityRegistry.TOWN_ANCHOR = (BlockEntityType<TownAnchorBlockEntity>) TOWN_ANCHOR_BE_OBJ.get();
        EntityRegistry.NPC = (EntityType<Npc>) NPC_OBJ.get();
        EntityRegistry.CITIZEN = (EntityType<Citizen>) CITIZEN_OBJ.get();
        AttachmentRegistry.CITIZEN = CITIZEN_DATA_OBJ.get();
        MenuRegistry.TOWN_HUB = (MenuType<TownHubMenu>) TOWN_HUB_OBJ.get();
        ItemRegistry.TOWN_ANCHOR = TOWN_ANCHOR_ITEM_OBJ.get();

        // Wire S2C packet delegates: server → client via PacketDistributor.sendToPlayer.
        // Each delegate constructs the payload and sends it to the specific player.
        NetworkHelper.sendTownHubPacket       = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CTownHubPacket(data));
        NetworkHelper.sendBuildingDefsPacket  = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CBuildingDefsPacket(data));
        NetworkHelper.sendStockUpdatePacket   = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CStockUpdatePacket(data));
        NetworkHelper.sendBuildingListPacket  = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CBuildingListPacket(data));
        NetworkHelper.sendQuestUpdatePacket   = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CQuestUpdatePacket(data));
        NetworkHelper.sendEraUpdatePacket     = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CEraUpdatePacket(data));
        NetworkHelper.sendCitizenUpdatePacket = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CCitizenUpdatePacket(data));
        NetworkHelper.sendLogEntryPacket      = (player, data) -> PacketDistributor.sendToPlayer(player, new S2CLogEntryPacket(data));
        // ADR-0022 — gate to the SUPPLY-mode TownHubScreenV2. The server sends
        // this instead of the legacy sendTownHubPacket + openMenu(be) pair when
        // Town#hubMode() == SUPPLY. The V2 screen is not a menu screen (no
        // AbstractContainerScreen); the client opens it via
        // Minecraft.getInstance().setScreen(new TownHubScreenV2(...)).
        NetworkHelper.sendOpenTownHubV2Packet = (player, anchorPos) -> PacketDistributor.sendToPlayer(player, new S2COpenTownHubV2Packet(anchorPos));

        // Membership, the one fact about a citizen the client cannot derive from its UUID.
        NetworkHelper.broadcastVillagerIdentity = (villager, member) ->
            PacketDistributor.sendToPlayersTrackingEntity(villager,
                new S2CVillagerIdentityPacket(villager.getUUID(), member));
        NetworkHelper.sendVillagerIdentity = (to, villager, member) ->
            PacketDistributor.sendToPlayer(to, new S2CVillagerIdentityPacket(villager, member));

        // ADR-0021 — push the loaded config value into the bare-JVM
        // population simulation's `GrowthMultiplier` so the first tick
        // already sees the user's chosen value (or the default 1.0 on a
        // fresh install). The ModConfigEvent.Reloading handler below
        // keeps it in sync after every edit.
        BurgConfig.refreshMultiplier();
        BurgConfig.refreshBuildCadence();
        BurgConfig.refreshRaidConfig();
        BurgConfig.refreshBuildingOutputCap();
    }

    /**
     * Fires on {@code NeoForge.EVENT_BUS} (not the mod bus) every time the
     * common config reloads — either from disk at startup or after the user
     * saves in the Cloth screen. Pushes the new value into the bare-JVM
     * {@link org.lowern1ght.burg.people.GrowthMultiplier} so the next
     * {@code DaySim.tickDay} sees it.
     */
    private void onModConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != BurgConfig.SPEC) return;
        BurgConfig.refreshMultiplier();
        BurgConfig.refreshBuildCadence();
        BurgConfig.refreshRaidConfig();
        BurgConfig.refreshBuildingOutputCap();
    }

    /**
     * A player has come within range of an entity: if it is one of ours, say so.
     *
     * <p>Also where a citizen's town is re-checked, rather than on every tick. This is the
     * moment membership starts to matter — it is about to be rendered — it fires rarely, and
     * it costs nothing for the hundreds of ordinary villagers a world holds. The old
     * {@code Citizen} subclass did the same check in {@code tick()} and killed itself when the
     * town was gone; a plain villager is instead released, because deleting somebody else's
     * mob over our own stale bookkeeping is not ours to do.
     */
    private void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof Villager villager)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(villager.level() instanceof ServerLevel serverLevel)) return;
        // Only members are announced. A non-member is the client's default, so an ordinary
        // village generates no traffic at all.
        if (Citizens.validate(serverLevel, villager)) {
            NetworkHelper.sendVillagerIdentity.send(player, villager.getUUID(), true);
        }
    }

    // Registers all 17 CustomPacketPayload types with their StreamCodecs and handlers.
    // S2C packets use playToClient (server → client); C2S packets use playToServer (client → server).
    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID);

        // S2C (server → client) — 9 payloads carrying CompoundTag snapshots/deltas
        // + the ADR-0022 SUPPLY-mode open-gateway (no payload body for now; the
        // intent list is the act-4 follow-up PR).
        registrar.playToClient(S2CTownHubPacket.TYPE,        S2CTownHubPacket.STREAM_CODEC,        S2CTownHubPacket::handle);
        registrar.playToClient(S2COpenTownHubV2Packet.TYPE,  S2COpenTownHubV2Packet.STREAM_CODEC,  S2COpenTownHubV2Packet::handle);
        registrar.playToClient(S2CBuildingDefsPacket.TYPE,   S2CBuildingDefsPacket.STREAM_CODEC,   S2CBuildingDefsPacket::handle);
        registrar.playToClient(S2CStockUpdatePacket.TYPE,    S2CStockUpdatePacket.STREAM_CODEC,    S2CStockUpdatePacket::handle);
        registrar.playToClient(S2CBuildingListPacket.TYPE,   S2CBuildingListPacket.STREAM_CODEC,   S2CBuildingListPacket::handle);
        registrar.playToClient(S2CQuestUpdatePacket.TYPE,    S2CQuestUpdatePacket.STREAM_CODEC,    S2CQuestUpdatePacket::handle);
        registrar.playToClient(S2CEraUpdatePacket.TYPE,      S2CEraUpdatePacket.STREAM_CODEC,      S2CEraUpdatePacket::handle);
        registrar.playToClient(S2CCitizenUpdatePacket.TYPE,  S2CCitizenUpdatePacket.STREAM_CODEC,  S2CCitizenUpdatePacket::handle);
        registrar.playToClient(S2CLogEntryPacket.TYPE,       S2CLogEntryPacket.STREAM_CODEC,       S2CLogEntryPacket::handle);
        registrar.playToClient(S2CVillagerIdentityPacket.TYPE, S2CVillagerIdentityPacket.STREAM_CODEC, S2CVillagerIdentityPacket::handle);

        // C2S (client → server) — 9 payloads for player actions
        registrar.playToServer(C2SDepositPacket.TYPE,             C2SDepositPacket.STREAM_CODEC,             C2SDepositPacket::handle);
        registrar.playToServer(C2SQueueBuildingPacket.TYPE,       C2SQueueBuildingPacket.STREAM_CODEC,       C2SQueueBuildingPacket::handle);
        registrar.playToServer(C2SRemoveQueuedBuildingPacket.TYPE, C2SRemoveQueuedBuildingPacket.STREAM_CODEC, C2SRemoveQueuedBuildingPacket::handle);
        registrar.playToServer(C2SUpgradeBuildingPacket.TYPE,     C2SUpgradeBuildingPacket.STREAM_CODEC,     C2SUpgradeBuildingPacket::handle);
        registrar.playToServer(C2SAdvanceEraPacket.TYPE,          C2SAdvanceEraPacket.STREAM_CODEC,          C2SAdvanceEraPacket::handle);
        registrar.playToServer(C2SContributeQuestPacket.TYPE,     C2SContributeQuestPacket.STREAM_CODEC,     C2SContributeQuestPacket::handle);
        registrar.playToServer(C2SRequestStockPacket.TYPE,        C2SRequestStockPacket.STREAM_CODEC,        C2SRequestStockPacket::handle);
        registrar.playToServer(C2SToggleChatBroadcastPacket.TYPE, C2SToggleChatBroadcastPacket.STREAM_CODEC, C2SToggleChatBroadcastPacket::handle);
        registrar.playToServer(C2SBuyPacket.TYPE,                 C2SBuyPacket.STREAM_CODEC,                 C2SBuyPacket::handle);
        // ADR-0022 follow-up — SUPPLY-mode TownHubScreenV2 wires a single
        // (anchor, itemId, quantity) pair back to the server, the server
        // merges into the town's reserve stock and pushes a stock snapshot
        // to the watching players.
        registrar.playToServer(C2SSupplyStockPacket.TYPE,         C2SSupplyStockPacket.STREAM_CODEC,         C2SSupplyStockPacket::handle);
    }

    @SuppressWarnings("unchecked")
    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put((EntityType<Npc>) NPC_OBJ.get(), Npc.createAttributes().build());
        event.put((EntityType<Citizen>) CITIZEN_OBJ.get(), Citizen.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TownCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStarting(ServerStartingEvent event) {
        BuilderConfigDataHandler.reload(event.getServer());
        BuildingDataHandler.reload(event.getServer());
        BuildingListDataHandler.reload(event.getServer());
        EraTransitionDataHandler.reload(event.getServer());
        FoodListDataHandler.reload(event.getServer());
        QuestDataHandler.reload(event.getServer());
        SettlerJobsDataHandler.reload(event.getServer());
        TradePriceDataHandler.reload(event.getServer());
    }

    /**
     * Puts a town's anchor block back if it is missing from a chunk that just loaded.
     *
     * <p>Chunk load, and not server start, because it is the one moment the anchor is both
     * reachable and free to check — a startup sweep would have to force-load a chunk per town for
     * settlements nobody is near. It also catches damage done while the chunk was unloaded, which
     * is most of the ways an anchor can actually go: a {@code /fill} from across the map, a wither,
     * another mod's world edit.
     */
    /**
     * A villager that turns up inside one of our towns becomes one of ours.
     *
     * <p>Join and not spawn: join also fires when a villager is read back off disk as its chunk
     * loads, so a village that already exists retrofits itself as the player walks through it.
     * Wandering traders are not {@code Villager} and are left alone.
     */
    private void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        Citizens.autoEnlist(level, villager);
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        TownIntegrity.healAnchors(level, event.getChunk());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        TickScheduler.tick(ServerLifecycleHooks.getCurrentServer());
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Sync building upgrade definitions to the client once on login. The client
            // uses this data to render upgrade UI (costs, cadence, capacity deltas).
            CompoundTag defsData = BuildingDataHandler.buildDefsPacketData();
            NetworkHelper.sendBuildingDefsPacket.accept(player, defsData);
        }
    }
}

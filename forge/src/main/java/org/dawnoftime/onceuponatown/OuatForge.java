package org.dawnoftime.onceuponatown;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.dawnoftime.onceuponatown.block.TownAnchorBlock;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.command.TownCommand;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.item.TownScrollItem;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.network.S2CTownScrollScreenPacket;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;
import org.dawnoftime.onceuponatown.tick.TickScheduler;

import java.util.Optional;
import java.util.function.BiFunction;

@Mod(Constants.MOD_ID)
public class OuatForge {

    private static final String NET_PROTOCOL = "1";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(Constants.MOD_ID, "main"),
        () -> NET_PROTOCOL,
        NET_PROTOCOL::equals,
        NET_PROTOCOL::equals
    );

    // DeferredRegister is the only valid registration path in Forge - direct Registry.register() is blocked
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, Constants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Constants.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, Constants.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID);

    private static final RegistryObject<Block> TOWN_ANCHOR_OBJ =
        BLOCKS.register("town_anchor",
            () -> new TownAnchorBlock(TownAnchorBlock.defaultProperties()));

    // Block registry fires before block entity registry, so TOWN_ANCHOR_OBJ.get() is safe here
    private static final RegistryObject<BlockEntityType<?>> TOWN_ANCHOR_BE_OBJ =
        BLOCK_ENTITY_TYPES.register("town_anchor", () -> {
            BiFunction<BlockPos, BlockState, TownAnchorBlockEntity> factory = TownAnchorBlockEntity::new;
            return BlockEntityType.Builder.of(factory::apply, TOWN_ANCHOR_OBJ.get()).build(null);
        });

    private static final RegistryObject<EntityType<?>> NPC_OBJ =
        ENTITY_TYPES.register("npc", () ->
            EntityType.Builder.<Npc>of(Npc::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(10)
                .build(new ResourceLocation(Constants.MOD_ID, "npc").toString())
        );

    private static final RegistryObject<MenuType<?>> VILLAGE_CHEST_OBJ =
        MENU_TYPES.register("village_chest",
            () -> IForgeMenuType.create((syncId, inv, buf) -> new VillageChestMenu(syncId, inv)));

    private static final RegistryObject<Item> TOWN_SCROLL_OBJ =
        ITEMS.register("town_scroll",
            () -> new TownScrollItem(new Item.Properties().stacksTo(1)));

    public OuatForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        ITEMS.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onEntityAttributes);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    // Populate the common static fields after all DeferredRegister suppliers have run
    @SuppressWarnings("unchecked")
    private void onCommonSetup(FMLCommonSetupEvent event) {
        BlockRegistry.TOWN_ANCHOR = TOWN_ANCHOR_OBJ.get();
        BlockEntityRegistry.TOWN_ANCHOR = (BlockEntityType<TownAnchorBlockEntity>) TOWN_ANCHOR_BE_OBJ.get();
        EntityRegistry.NPC = (EntityType<Npc>) NPC_OBJ.get();
        MenuRegistry.VILLAGE_CHEST = (MenuType<VillageChestMenu>) VILLAGE_CHEST_OBJ.get();
        ItemRegistry.TOWN_SCROLL = TOWN_SCROLL_OBJ.get();

        CHANNEL.registerMessage(0,
            S2CTownScrollScreenPacket.class,
            S2CTownScrollScreenPacket::encode,
            S2CTownScrollScreenPacket::decode,
            (msg, ctx) -> {
                ctx.get().enqueueWork(() -> S2CTownScrollScreenPacket.Handler.handle(msg));
                ctx.get().setPacketHandled(true);
            },
            Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        NetworkHelper.sendTownScrollPacket = (player, data) ->
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CTownScrollScreenPacket(data));
    }

    @SuppressWarnings("unchecked")
    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put((EntityType<Npc>) NPC_OBJ.get(), Npc.createAttributes().build());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TownCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStarting(ServerStartingEvent event) {
        BuildingDataHandler.reload(event.getServer());
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TickScheduler.tick(ServerLifecycleHooks.getCurrentServer());
        }
    }
}

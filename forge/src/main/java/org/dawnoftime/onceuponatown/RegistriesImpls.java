package org.dawnoftime.onceuponatown;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.dawnoftime.onceuponatown.registry.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class RegistriesImpls {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static void init(IEventBus modEventBus) {
        // Create the registries.
        EntityRegistryImpl.REGISTRY = new EntityRegistryImpl();
        ItemRegistryImpl.REGISTRY = new ItemRegistryImpl();
        BlockRegistryImpl.REGISTRY = new BlockRegistryImpl();
        BlockEntityRegistryImpl.REGISTRY = new BlockEntityRegistryImpl();
        MenuRegistryImpl.REGISTRY = new MenuRegistryImpl();
        StructureTypeRegistryImpl.REGISTRY = new StructureTypeRegistryImpl();
        StructurePieceRegistryImpl.REGISTRY = new StructurePieceRegistryImpl();
        ScheduleRegistryImpl.REGISTRY = new ScheduleRegistryImpl();

        // Populates the registries and register their contents.
        EntityRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        ItemRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        BlockRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        BlockRegistryImpl.DEFERRED_ITEM_REGISTER.register(modEventBus);
        BlockEntityRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        MenuRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        StructureTypeRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        StructurePieceRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        ScheduleRegistryImpl.DEFERRED_REGISTER.register(modEventBus);

        //modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(EntityRegistry.REGISTRY.NPC.get(), Npc.createAttributes().build()));

        // Creative inventory init
        CREATIVE_MODE_TAB.register(modEventBus);
        CREATIVE_MODE_TAB.register(MOD_ID, () -> CreativeModeTab.builder()
                .title(Component.translatable("creative_mode_tab.onceuponatown.main_tab"))
                //.icon(() -> TAB_ICON.get().getDefaultInstance())
                //.displayItems((params, output) -> output.acceptAll(ForgeItemsRegistry.ITEMS_REGISTRY.getEntries().stream().filter(holder -> holder != TAB_ICON).map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList()))
            .icon(() -> new ItemStack(ItemRegistry.REGISTRY.TOWN_SCROLL.get()))
            .displayItems((params, output) -> {
                output.acceptAll(BlockRegistryImpl.DEFERRED_ITEM_REGISTER.getEntries().stream().map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList());
                output.acceptAll(ItemRegistryImpl.DEFERRED_REGISTER.getEntries().stream().map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList());
            })
            .build());
    }

    public static class EntityRegistryImpl extends EntityRegistry {
        public static final DeferredRegister<EntityType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

        @Override
        public <T extends Entity> Supplier<EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> builder) {
            return DEFERRED_REGISTER.register(name, () -> builder.get().build(name));
        }
    }

    public static class BlockRegistryImpl extends BlockRegistry {
        public static final DeferredRegister<Block> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
        public static final DeferredRegister<Item> DEFERRED_ITEM_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

        @Override
        public final <T extends Block, Y extends Item> Supplier<T> registerWithItem(String id, Supplier<T> block, Function<T, Y> item) {
            RegistryObject<T> registryBlock = DEFERRED_REGISTER.register(id, block);
            if(item != null) {
                DEFERRED_ITEM_REGISTER.register(id, () -> item.apply(registryBlock.get()));
            }
            return registryBlock;
        }
    }

    public static class ItemRegistryImpl extends ItemRegistry {
        public static final DeferredRegister<Item> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

        @Override
        public <T extends Item> Supplier<Item> register(String name, Supplier<T> itemSupplier) {
            return DEFERRED_REGISTER.register(name, itemSupplier);
        }

        @Override
        public <T extends Item> Supplier<Item> registerSpawnEgg(String name, Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor) {
            return register(name, () -> new ForgeSpawnEggItem(type, backgroundColor, highlightColor, new Item.Properties()));
        }
    }

    public static class BlockEntityRegistryImpl extends BlockEntityRegistry {
        public static final DeferredRegister<BlockEntityType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Ouat.MOD_ID);

        @Override
        public <T extends BlockEntity> Supplier<BlockEntityType<T>> register(String name, BiFunction<BlockPos, BlockState, T> factoryIn, Supplier<Block> validBlocksSupplier) {
            return DEFERRED_REGISTER.register(name, () -> BlockEntityType.Builder.of(factoryIn::apply, validBlocksSupplier.get()).build(null));
        }
    }

    public static class MenuRegistryImpl extends MenuRegistry {
        public static final DeferredRegister<MenuType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Ouat.MOD_ID);

        @Override
        public <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(String name, MenuTypeFactory<T> factory) {
            return DEFERRED_REGISTER.register(name, () -> IForgeMenuType.create((i, inventory, friendlyByteBuf) -> (T) factory.create(i, inventory, friendlyByteBuf)));
        }
    }

    public static class StructureTypeRegistryImpl extends StructureTypeRegistry {
        public static final DeferredRegister<StructureType<?>> DEFERRED_REGISTER = DeferredRegister.create(Registries.STRUCTURE_TYPE, Ouat.MOD_ID);

        @Override
        public <T extends Structure> Supplier<StructureType<T>> register(String name, Supplier<StructureType<T>> structureTypeSupplier) {
            return DEFERRED_REGISTER.register(name, structureTypeSupplier);
        }
    }

    public static class StructurePieceRegistryImpl extends StructurePieceRegistry {
        public static final DeferredRegister<StructurePieceType> DEFERRED_REGISTER = DeferredRegister.create(Registries.STRUCTURE_PIECE, Ouat.MOD_ID);

        @Override
        public Supplier<StructurePieceType> register(String name, Supplier<StructurePieceType> structurePieceTypeSupplier) {
            return DEFERRED_REGISTER.register(name, structurePieceTypeSupplier);
        }
    }

    public static class ScheduleRegistryImpl extends ScheduleRegistry {
        public static final DeferredRegister<Schedule> DEFERRED_REGISTER = DeferredRegister.create(Registries.SCHEDULE, Ouat.MOD_ID);

        @Override
        public Supplier<Schedule> register(String name, Supplier<Schedule> scheduleSupplier) {
            return DEFERRED_REGISTER.register(name, scheduleSupplier);
        }
    }

    public static class MemoryModuleTypeRegistryImpl extends MemoryModuleTypeRegistry {
        public static final DeferredRegister<MemoryModuleType<?>> DEFERRED_REGISTER = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, Ouat.MOD_ID);

        @Override
        public <U> Supplier<MemoryModuleType<U>> register(String name, Supplier<MemoryModuleType<U>> memoryModuleTypeSupplier) {
            return DEFERRED_REGISTER.register(name, memoryModuleTypeSupplier);
        }
    }
}

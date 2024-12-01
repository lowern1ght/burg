package org.dawnoftime.onceuponatown;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.*;

import java.util.function.Supplier;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class RegistriesImpls {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static void init(IEventBus modEventBus) {
        // Create the registries.
        EntityRegistryImpl.REGISTRY = new EntityRegistryImpl();
        ItemRegistryImpl.REGISTRY = new ItemRegistryImpl();
        MenuRegistryImpl.REGISTRY = new MenuRegistryImpl();
        StructureTypeRegistryImpl.REGISTRY = new StructureTypeRegistryImpl();
        StructurePieceRegistryImpl.REGISTRY = new StructurePieceRegistryImpl();

        // Populates the registries and register their contents.
        EntityRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        ItemRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        MenuRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        StructureTypeRegistryImpl.DEFERRED_REGISTER.register(modEventBus);
        StructurePieceRegistryImpl.DEFERRED_REGISTER.register(modEventBus);

        //modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(EntityRegistry.REGISTRY.NPC.get(), Npc.createAttributes().build()));

        // Creative inventory init
        CREATIVE_MODE_TAB.register(modEventBus);
        CREATIVE_MODE_TAB.register(MOD_ID, () -> CreativeModeTab.builder()
                .title(Component.literal(Ouat.MOD_NAME))
                //.icon(() -> TAB_ICON.get().getDefaultInstance())
                //.displayItems((params, output) -> output.acceptAll(ForgeItemsRegistry.ITEMS_REGISTRY.getEntries().stream().filter(holder -> holder != TAB_ICON).map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList()))
                .icon(() -> new ItemStack(ItemRegistry.REGISTRY.TOWN_MAP.get()))
                .displayItems((params, output) -> output.acceptAll(ItemRegistryImpl.DEFERRED_REGISTER.getEntries().stream().map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList()))
                .build());
    }

    public static class EntityRegistryImpl extends EntityRegistry {
        public static final DeferredRegister<EntityType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

        @Override
        public <T extends Entity> Supplier<EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> builder) {
            return DEFERRED_REGISTER.register(name, () -> builder.get().build(name));
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
}

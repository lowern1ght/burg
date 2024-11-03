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
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.registry.*;

import java.util.function.Supplier;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class RegistryImpls {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static void init(IEventBus modEventBus) {
        // Create the registries.
        ForgeEntitiesRegistry.ENTITY_REGISTRY = new ForgeEntitiesRegistry();
        ForgeItemsRegistry.ITEM_REGISTRY = new ForgeItemsRegistry();
        ForgeMenusRegistry.MENU_REGISTRY = new ForgeMenusRegistry();
        ForgeStructureTypesRegistry.STRUCTURE_TYPE_REGISTRY = new ForgeStructureTypesRegistry();
        ForgeStructurePiecesRegistry.STRUCTURE_PIECE_REGISTRY = new ForgeStructurePiecesRegistry();

        // Populates the registries and register their contents.
        ForgeEntitiesRegistry.REGISTRY.register(modEventBus);
        ForgeItemsRegistry.REGISTRY.register(modEventBus);
        ForgeMenusRegistry.REGISTRY.register(modEventBus);
        ForgeStructureTypesRegistry.REGISTRY.register(modEventBus);
        ForgeStructurePiecesRegistry.REGISTRY.register(modEventBus);

        modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(OuatEntitiesRegistry.ENTITY_REGISTRY.NPC.get(), Citizen.createAttributes().build()));

        // Creative inventory init
        CREATIVE_MODE_TAB.register(modEventBus);
        CREATIVE_MODE_TAB.register(MOD_ID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + MOD_ID))
                //.icon(() -> TAB_ICON.get().getDefaultInstance())
                //.displayItems((params, output) -> output.acceptAll(ForgeItemsRegistry.ITEMS_REGISTRY.getEntries().stream().filter(holder -> holder != TAB_ICON).map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList()))
                .icon(() -> new ItemStack(Items.EMERALD))
                .displayItems((params, output) -> output.acceptAll(ForgeItemsRegistry.REGISTRY.getEntries().stream().map((itemDeferredHolder) -> itemDeferredHolder.get().getDefaultInstance()).toList()))
                .build());
    }

    public static class ForgeEntitiesRegistry extends OuatEntitiesRegistry {
        public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
        @Override
        public <T extends Entity> Supplier<EntityType<T>> register(String name, Supplier<EntityType.Builder<T>> builder) {
            return REGISTRY.register(name, () -> builder.get().build(name));
        }
    }

    public static class ForgeItemsRegistry extends OuatItemsRegistry {
        public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

        @Override
        public <T extends Item> Supplier<Item> register(String name, Supplier<T> itemSupplier) {
            return REGISTRY.register(name, itemSupplier);
        }

        @Override
        public <T extends Item> Supplier<Item> registerSpawnEgg(String name, Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor) {
            return register(name, () -> new ForgeSpawnEggItem(type, backgroundColor, highlightColor, new Item.Properties()));
        }
    }

    public static class ForgeMenusRegistry extends OuatMenusRegistry{
        public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Ouat.MOD_ID);

        @Override
        public <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(String name, MenuTypeFactory<T> factory) {
            return REGISTRY.register(name, () -> IForgeMenuType.create((i, inventory, friendlyByteBuf) -> (T) factory.create(i, inventory, friendlyByteBuf)));
        }
    }

    public static class ForgeStructureTypesRegistry extends OuatStructureTypesRegistry {
        public static final DeferredRegister<StructureType<?>> REGISTRY = DeferredRegister.create(Registries.STRUCTURE_TYPE, Ouat.MOD_ID);

        @Override
        public <T extends Structure> Supplier<StructureType<T>> register(String name, Supplier<StructureType<T>> structureTypeSupplier) {
            return REGISTRY.register(name, structureTypeSupplier);
        }
    }

    public static class ForgeStructurePiecesRegistry extends OuatStructurePiecesRegistry{
        public static final DeferredRegister<StructurePieceType> REGISTRY = DeferredRegister.create(Registries.STRUCTURE_PIECE, Ouat.MOD_ID);

        @Override
        public Supplier<StructurePieceType> register(String name, Supplier<StructurePieceType> structurePieceTypeSupplier) {
            return REGISTRY.register(name, structurePieceTypeSupplier);
        }
    }
}

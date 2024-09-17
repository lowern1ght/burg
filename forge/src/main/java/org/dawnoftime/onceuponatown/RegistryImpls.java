package org.dawnoftime.onceuponatown;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.registry.OuatEntitiesRegistry;
import org.dawnoftime.onceuponatown.registry.OuatItemsRegistry;

import java.util.function.Supplier;

import static org.dawnoftime.onceuponatown.Constants.MOD_ID;

public class RegistryImpls {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static void init(IEventBus modEventBus) {
        // Create the registries.
        OuatEntitiesRegistry.INSTANCE = new ForgeEntitiesRegistry();
        ForgeItemsRegistry.INSTANCE = new ForgeItemsRegistry();

        // Populates the registries and register their contents.
        ForgeEntitiesRegistry.REGISTRY.register(modEventBus);
        ForgeItemsRegistry.REGISTRY.register(modEventBus);

        /*
        DoTBBlocksRegistry.INSTANCE = new ForgeBlocksRegistry();
        DoTBItemsRegistry.INSTANCE = new ForgeItemsRegistry();
        DoTBBlockEntitiesRegistry.INSTANCE = new ForgeBlockEntitiesRegistry();
        DoTBFeaturesRegistry.INSTANCE = new ForgeFeaturesRegistry();
        DoTBMenuTypesRegistry.INSTANCE = new ForgeMenuTypesRegistry();
        DoTBRecipeSerializersRegistry.INSTANCE = new ForgeRecipeSerializersRegistry();
        DoTBRecipeTypesRegistry.INSTANCE = new ForgeRecipeTypesRegistry();
        DoTBTags.INSTANCE = new ForgeTagsRegistry();
        DoTBCreativeModeTabsRegistry.INSTANCE = new ForgeCreativeModeTabsRegistry();


        ForgeBlocksRegistry.BLOCKS_REGISTRY.register(modEventBus);
        ForgeBlocksRegistry.BLOCK_ITEMS_REGISTRY.register(modEventBus);
        ForgeBlockEntitiesRegistry.BLOCK_ENTITY_TYPES_REGISTRY.register(modEventBus);
        ForgeFeaturesRegistry.FEATURES_REGISTRY.register(modEventBus);
        ForgeMenuTypesRegistry.MENU_TYPES_REGISTRY.register(modEventBus);
        ForgeRecipeSerializersRegistry.RECIPE_SERIALIZERS_REGISTRY.register(modEventBus);
        ForgeRecipeTypesRegistry.RECIPE_TYPES_REGISTRY.register(modEventBus);
        ForgeCreativeModeTabsRegistry.CREATIVE_MODE_TABS_REGISTRY.register(modEventBus);
         */

        modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(OuatEntitiesRegistry.INSTANCE.CITIZEN.get(), Citizen.createAttributes().build()));

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
}

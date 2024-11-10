package org.dawnoftime.onceuponatown;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Ouat.MOD_ID)
public class OuatForge {

    public OuatForge() {
        Ouat.COMMON.init();
        Ouat.CLIENT.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        RegistryImpls.init(modEventBus);
    }
}

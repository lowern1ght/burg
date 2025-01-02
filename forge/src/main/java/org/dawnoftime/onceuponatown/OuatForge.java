package org.dawnoftime.onceuponatown;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Ouat.MOD_ID)
public class OuatForge {
    public OuatForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        RegistriesImpls.init(modEventBus);
    }
}

package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.culture.CultureManager.CULTURE_JSON_FILE_NAME;

public class S2COpenCultureCCScreenPacket implements IOuatPacket {
    private static final ResourceLocation ID = modResource("s2c_open_culture_screen_cc");

    private final String cultureId;
    private S2COpenCultureCCScreenPacket(String cultureId){
        this.cultureId = cultureId;
    }

    public static S2COpenCultureCCScreenPacket create(String cultureId){
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder()
                    .toPath()
                    .resolve(MOD_ID)
                    .resolve(cultureId)
                    .resolve(CULTURE_JSON_FILE_NAME);
            String jsonContent = Files.readString(jsonPath);
            JsonObject jsonObject = JsonParser.parseString(jsonContent).getAsJsonObject();
        } catch (IOException ignored) {
            // An error occurred, we will create a new CULTURE_JSON_FILE_NAME
        }
        return new S2COpenCultureCCScreenPacket(cultureId);
    }

    public static S2COpenCultureCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        return new S2COpenCultureCCScreenPacket(cultureId);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2COpenCultureCCScreenPacket packet) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new CultureCCScreen(packet));
            });
        }
    }
}
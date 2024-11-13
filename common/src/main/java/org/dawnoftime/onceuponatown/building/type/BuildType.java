package org.dawnoftime.onceuponatown.building.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.Orientation;
import org.dawnoftime.onceuponatown.entity.NpcJob;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BuildType {
    public static final BuildType DEFAULT_TYPE = new BuildType("default");

    private final String name;
    private final int weight;
    private final HashMap<Orientation, Integer> researchGain = new HashMap<>();
    private final HashMap<ResourceLocation, Integer> production = new HashMap<>();
    private final HashMap<NpcJob, Integer> npcJobs = new HashMap<>();;
    private final HashMap<String, BuildVariant> variants = new HashMap<>();

    private BuildType(String buildTypeName) {
        this.name = buildTypeName;
        this.weight = 0;
    }

    public static @Nullable BuildType createFromJson(ResourceManager resourceManager, ResourceLocation buildResource, String cultureName){
        try {
            Resource resource = resourceManager.getResource(buildResource).orElseThrow();
            try (Reader reader = resource.openAsReader()) {
                String path = buildResource.getPath();
                String buildTypeName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                JsonObject buildTypeJson = GsonHelper.parse(reader);
                // TODO Parse the content of the json file of Build Types.
                return new BuildType(buildTypeName);
            }
        } catch (IOException | JsonParseException e) {
            Ouat.error("Could not read the Build Type json file : " + buildResource);
        }
        return null;
    }

    public HashMap<ResourceLocation, Integer> getProduction() {
        return this.production;
    }

    public String getName(){
        return this.name;
    }

    public void addVariant(BuildVariant variant){
        this.variants.put(variant.getName(), variant);
    }

    public int getVariantNumber(){
        return this.variants.size();
    }
/*
    // TODO Temporary code to generate the started pack for any culture.
    public static final int STARTER_PACK_SIZE = 21;
    public static final BuildType[] TEST_BUILDINGS = new BuildType[] {
            new BuildType(Ouat.createOuatResource("plains/bigchurch"), 19, 32),
            new BuildType(Ouat.createOuatResource("plains/bighouse"), 13, 11),
            new BuildType(Ouat.createOuatResource("plains/bighousefront"), 9, 8),
            new BuildType(Ouat.createOuatResource("plains/bighousestand"), 15, 10),
            new BuildType(Ouat.createOuatResource("plains/church"), 15, 24),
            new BuildType(Ouat.createOuatResource("plains/cowshed"), 16, 11),
            new BuildType(Ouat.createOuatResource("plains/doubledeckhouse"), 7, 15),
            new BuildType(Ouat.createOuatResource("plains/fountainplace"), 13, 13),
            new BuildType(Ouat.createOuatResource("plains/leathershop"), 14, 9),
            new BuildType(Ouat.createOuatResource("plains/littlefarm"), 9, 7),
            new BuildType(Ouat.createOuatResource("plains/lonegarden"), 14, 13),
            new BuildType(Ouat.createOuatResource("plains/loneresources"), 14, 11),
            new BuildType(Ouat.createOuatResource("plains/mediumhouse"), 11, 10),
            new BuildType(Ouat.createOuatResource("plains/merchantshop"), 15, 12),
            new BuildType(Ouat.createOuatResource("plains/smallfarm"), 17, 10),
            new BuildType(Ouat.createOuatResource("plains/smallhouse"), 8, 9),
            new BuildType(Ouat.createOuatResource("plains/smallhousefancy"), 14, 10),
            new BuildType(Ouat.createOuatResource("plains/smallhousegarden"), 14, 8),
            new BuildType(Ouat.createOuatResource("plains/smallmarket"), 25, 18),
            new BuildType(Ouat.createOuatResource("plains/soldierhouse"), 13, 13),
            new BuildType(Ouat.createOuatResource("plains/wildspot"), 10, 6)
    };

 */
}

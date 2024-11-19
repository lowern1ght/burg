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
import java.util.HashMap;

public class BuildingType extends BuildType{

    private final HashMap<Orientation, Integer> researchGain = new HashMap<>();
    private final HashMap<ResourceLocation, Integer> production = new HashMap<>();
    private final HashMap<NpcJob, Integer> npcJobs = new HashMap<>();

    protected BuildingType(String buildTypeName) {
        super(buildTypeName, 0);
        // TODO Load the weight
    }

    public static @Nullable BuildingType createFromJson(ResourceManager resourceManager, ResourceLocation buildResource, String cultureName){
        try {
            Resource resource = resourceManager.getResource(buildResource).orElseThrow();
            try (Reader reader = resource.openAsReader()) {
                String path = buildResource.getPath();
                String buildTypeName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                JsonObject buildTypeJson = GsonHelper.parse(reader);
                // TODO Parse the content of the json file of Build Types.
                return new BuildingType(buildTypeName);
            }
        } catch (IOException | JsonParseException e) {
            Ouat.error("Could not read the Build Type json file : " + buildResource);
        }
        return null;
    }

    public HashMap<ResourceLocation, Integer> getProduction() {
        return this.production;
    }
}

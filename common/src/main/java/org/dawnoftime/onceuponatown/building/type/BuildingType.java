package org.dawnoftime.onceuponatown.building.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.Orientation;
import org.dawnoftime.onceuponatown.entity.NpcJob;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

public class BuildingType extends BuildType{
    private final HashMap<Orientation, Integer> researchGain = new HashMap<>();
    private final HashMap<NpcJob, Integer> npcJobs = new HashMap<>();
    private final Item iconItem;

    protected BuildingType(String buildTypeName, Item item) {
        super(buildTypeName, 0);
        iconItem = item;
        // TODO Load the weight
    }

    public BuildVariant getRandomVariant(RandomSource rand){
        BuildVariant[] vars = this.getVariants().values().toArray(new BuildVariant[0]);
        return vars[rand.nextInt(vars.length)];
    }

    public static @Nullable BuildingType createFromJson(ResourceManager resourceManager, ResourceLocation buildResource, String cultureName){
        try {
            Resource resource = resourceManager.getResource(buildResource).orElseThrow();
            try (Reader reader = resource.openAsReader()) {
                String path = buildResource.getPath();
                String buildTypeName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                JsonObject buildTypeJson = GsonHelper.parse(reader);
                JsonElement iconItemJson = buildTypeJson.get("icon");
                Item iconItem;
                if (iconItemJson != null) {
                    iconItem = Ouat.COMMON.getItem(new ResourceLocation(iconItemJson.getAsString()));
                } else {
                    iconItem = Items.AIR;
                }
                // TODO Parse the content of the json file of Build Types.
                return new BuildingType(buildTypeName, iconItem);
            }
        } catch (IOException | JsonParseException e) {
            Ouat.error("Could not read the Build Type json file : " + buildResource);
        }
        return null;
    }

    public Item getIconItem() {
        return iconItem;
    }
}

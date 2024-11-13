package org.dawnoftime.onceuponatown.culture;

import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Triplet;

import java.util.*;

public class Culture {
    public static final Culture FAKE_PLAINS = new Culture("fake_plains", 3, 14, null);
    private String id;
    private List<Orientation> orientations;
    private final HashMap<String, BuildType> buildTypeMap = new HashMap<>();
    private int starterPackMinSize;
    private int starterPackMaxSize;
    // Starter pack candidate, building type id, min amount, max amount
    private Triplet<BuildType, Integer, Integer> starterPack;
    private List<Item> foods;
    private final List<Era> eras;

    Culture(String id, int starterPackMinSize, int starterPackMaxSize, List<Era> eras) {
        this.id = id;
        this.starterPackMinSize = starterPackMinSize;
        this.starterPackMaxSize = starterPackMaxSize;
        this.eras = eras;
    }

    public List<BuildType> getRandomStarterPack() {
        // TODO Replace this with datapack info later !
        /*
        List<TownGeneratorOld.BuildingInfo> availableBuildings = new LinkedList<>(Arrays.asList(TEST_BUILDINGS));
        List<TownGeneratorOld.BuildingInfo> starterPack = new ArrayList<>();
        for (int i = 0; i < STARTER_PACK_SIZE; ++i) {
            TownGeneratorOld.BuildingInfo building = availableBuildings.remove(Mth.nextInt(random, 0, availableBuildings.size() - 1));
            starterPack.add(building);
            if (availableBuildings.isEmpty()) {
                availableBuildings.addAll(Arrays.asList(TEST_BUILDINGS));
            }
        }
        return starterPack;

         */
        return new ArrayList<>();
    }

    public void addOrientation(Orientation orientation) {
        this.orientations.add(orientation);
    }

    public List<Era> getEras() {
        return this.eras;
    }

    public void addEra(Era era) {
        this.eras.add(era);
    }

    public void addBuildType(@NotNull BuildType type) {
        this.buildTypeMap.put(type.getName(), type);
    }

    public void addBuildVariant(@NotNull String buildTypeName, @NotNull BuildVariant variant){
        BuildType type = this.buildTypeMap.get(buildTypeName);
        if(type != null){
            type.addVariant(variant);
        }else{
            Ouat.error("Culture [%s]: Failed to register the build_variant '%s'. Its associated build_type '%s' is not defined for this culture.".formatted(this.id, variant.getName(), buildTypeName));
        }
    }

    public void dropBuildTypeWithoutVariant(){
        this.buildTypeMap.entrySet().removeIf(entry -> {
            if(entry.getValue().getVariantNumber() == 0){
                Ouat.error("Culture [%s]: Canceled the registration of the build_type '%s' as it doesn't have any build_variant.".formatted(this.id, entry.getValue().getName()));
                return true;
            }
            return false;
        });
    }

    public String getId() {
        return this.id;
    }

    public int getStarterPackMinSize() {
        return this.starterPackMinSize;
    }

    public int getStarterPackMaxSize() {
        return this.starterPackMaxSize;
    }

    public record Era(int order, int requiredXp, int buildingsWeight) {}
}

package org.dawnoftime.onceuponatown.building.type;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import oshi.util.tuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.dawnoftime.onceuponatown.town.generation.ProtoTown.RANDOM;

public class RoadType extends SliceBuildType {
    private final HashMap<String, BuildVariant> crossroadRightVariants = new HashMap<>();
    private final HashMap<String, BuildVariant> crossroadDoubleVariants = new HashMap<>();

    public RoadType(String roadTypeId, int weight, List<BuildLevel> levels, HashMap<String, Pair<BuildVariant, String>> variants, String cultureId) {
        super(roadTypeId, weight, levels, BuildingPurpose.INFRASTRUCTURE);
        variants.forEach((id, pair) -> addVariant(pair.getA(), pair.getB(), cultureId));
    }

    @Override
    protected void addVariant(BuildVariant variant, String shape, String cultureId) {
        try {
            if (this.getWidth() == 0) {
                this.width = variant.getDimensions().getX();
                this.patternLength = variant.getDimensions().getZ();
            } else {
                if (this.width != variant.getDimensions().getX() || this.patternLength != variant.getDimensions().getZ()) {
                    throw new CorruptedCultureException(cultureId, "Failed to register a build_variant. Every build_variant associated with '%s' must have the same width and length.".formatted(this.getId()));
                }
            }
            switch (SliceBuildShape.fromStringToRegister(cultureId, variant.getId(), shape)) {
                case FLAT -> super.addVariant(variant, shape, cultureId);
                case SLAB -> this.slabVariants.put(variant.getId(), variant);
                case STAIRS -> this.stairsVariants.put(variant.getId(), variant);
                case CROSSROAD_RIGHT -> this.crossroadRightVariants.put(variant.getId(), variant);
                case CROSSROAD_DOUBLE -> this.crossroadDoubleVariants.put(variant.getId(), variant);
            }
        } catch (CorruptedCultureException e) {
            Ouat.error(e.getMessage());
        }
    }

    @Override
    public BuildVariant getVariant(SliceBuildShape shape, String variantName) {
        return switch (shape) {
            case CROSSROAD_RIGHT, CROSSROAD_LEFT -> this.crossroadRightVariants.get(variantName);
            case CROSSROAD_DOUBLE -> this.crossroadDoubleVariants.get(variantName);
            default -> super.getVariant(shape, variantName);
        };
    }

    @Override
    public String getRandomVariantName(SliceBuildShape shape) {
        Set<String> keys;
        switch (shape) {
            case CROSSROAD_RIGHT, CROSSROAD_LEFT -> keys = this.crossroadRightVariants.keySet();
            case CROSSROAD_DOUBLE -> keys = this.crossroadDoubleVariants.keySet();
            default -> {
                return super.getRandomVariantName(shape);
            }
        }
        ;
        return new ArrayList<>(keys).get(RANDOM.nextInt(keys.size()));
    }

    @Override
    public boolean isValid(String cultureId) {
        if (this.crossroadRightVariants.isEmpty()) {
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'crossroad_right'.".formatted(this.getId()));
        }
        if (this.crossroadDoubleVariants.isEmpty()) {
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'crossroad_double'.".formatted(this.getId()));
        }
        return super.isValid(cultureId);
    }
}

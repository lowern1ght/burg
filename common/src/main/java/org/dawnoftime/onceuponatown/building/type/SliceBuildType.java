package org.dawnoftime.onceuponatown.building.type;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.dawnoftime.onceuponatown.town.generation.ProtoTown.RANDOM;

public class SliceBuildType extends BuildType {
    protected final HashMap<String, BuildVariant> slabVariants = new HashMap<>();
    protected final HashMap<String, BuildVariant> stairsVariants = new HashMap<>();
    protected int width;
    protected int patternLength;

    public SliceBuildType(String buildTypeName, int weight, List<BuildLevel> levels, BuildingPurpose purpose) {
        super(buildTypeName, weight, levels, purpose);
    }

    @Override
    protected void addVariant(BuildVariant variant, String shape, String cultureId) throws CorruptedCultureException {
        try {
            if (width == 0) {
                width = variant.getDimensions().getX();
                patternLength = variant.getDimensions().getZ();
            } else {
                if (width != variant.getDimensions().getX() || patternLength != variant.getDimensions().getZ()) {
                    throw new CorruptedCultureException(cultureId, "Failed to register a build variant. Every build variant of the build type '%s' must have the same width and length.".formatted(this.getId()));
                }
            }
            switch (SliceBuildShape.fromStringToRegister(cultureId, variant.getId(), shape)) {
                case FLAT -> super.addVariant(variant, shape, cultureId);
                case SLAB -> slabVariants.put(variant.getId(), variant);
                case STAIRS -> stairsVariants.put(variant.getId(), variant);
            }
        } catch (CorruptedCultureException cce) {
            // TODO no throw but there is a risk that all variants are wrong, leading to unusable build type
            Ouat.error(cce.getMessage());
        }
    }

    public BuildVariant getVariant(SliceBuildShape shape, String variantId) {
        return switch (shape) {
            case SLAB -> slabVariants.get(variantId);
            case STAIRS, STAIRS_INVERTED -> stairsVariants.get(variantId);
            default -> this.getBuildVariants().get(variantId);
        };
    }

    public String getRandomVariantName(SliceBuildShape shape) {
        Set<String> keys = switch (shape) {
            case SLAB -> slabVariants.keySet();
            case STAIRS, STAIRS_INVERTED -> stairsVariants.keySet();
            default -> this.getBuildVariants().keySet();
        };
        return new ArrayList<>(keys).get(RANDOM.nextInt(keys.size()));
    }

    @Override
    public boolean isValid(String cultureId) {
        if (this.getBuildVariants().isEmpty()) {
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'flat'.".formatted(this.getId()));
        }
        if (slabVariants.isEmpty()) {
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'slab'.".formatted(this.getId()));
        }
        if (stairsVariants.isEmpty()) {
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'stairs'.".formatted(this.getId()));
        }
        return true;
    }

    public enum SliceBuildShape {
        FLAT("flat", 0, false),
        SLAB("slab", 0, false),
        STAIRS("stairs", 1.0F, false),
        STAIRS_INVERTED("stairs_inverted", 1.0F, false),
        CROSSROAD_RIGHT("crossroad_right", 0, true),
        CROSSROAD_LEFT("crossroad_left", 0, true),
        CROSSROAD_DOUBLE("crossroad_double", 0, true);

        private final String shapeName;
        private final float slope;
        private final boolean locked;

        SliceBuildShape(String shapeName, float slope, boolean locked) {
            this.shapeName = shapeName;
            this.slope = slope;
            this.locked = locked;
        }

        public boolean isLocked() {
            return locked;
        }

        public int getYSize(int patternLength, int totalSizeY) {
            return getMaxYForSliceSchematic(0, patternLength, totalSizeY) + 1;
        }

        public int getMinYForSliceSchematic(int patternPos) {
            return (int) Math.floor(patternPos * slope);
        }

        public int getMaxYForSliceSchematic(int patternPos, int patternLength, int totalSizeY) {
            return totalSizeY - (int) Math.floor((patternLength - 1 - patternPos) * slope) - 1;
        }

        public static SliceBuildShape fromStringToRegister(String cultureId, String variantName, String shapeName) throws CorruptedCultureException {
            SliceBuildShape[] accepted = new SliceBuildShape[]{FLAT, SLAB, STAIRS, CROSSROAD_RIGHT, CROSSROAD_DOUBLE};
            for (SliceBuildShape shape : accepted) {
                if (shape.shapeName.equals(shapeName)) {
                    return shape;
                }
            }
            throw new CorruptedCultureException(cultureId, "Failed to register build variant '%s'. The accepted shapes are %s, and not '%s'.".formatted(variantName, accepted, shapeName));
        }
    }

    public int getWidth() {
        return width;
    }

    public int getPatternLength() {
        return patternLength;
    }
}

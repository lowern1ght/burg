package org.dawnoftime.onceuponatown;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.construction.EntityInfo;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class Utils {
    public static Town getNearestTown(ServerLevel level, BlockPos pos) {
        return getNearestTown(level, pos, Integer.MAX_VALUE);
    }

    public static Town getNearestTown(ServerLevel level, BlockPos pos, int maxDist) {
        if (maxDist <= 0) {
            return null;
        }
        Collection<Town> towns = LevelTowns.of(level).getAllTowns();
        if (towns.isEmpty()) {
            return null;
        }
        Town town = null;
        int dist;
        for (Town next : towns) {
            dist = (int)Math.sqrt(pos.distSqr(next.getCenter()));
            if (dist < maxDist) {
                town = next;
                maxDist = dist;
            }
        }
        return town;
    }

    /**
     * Rotate the given BlockPos as part of a building oriented to North.
     * @param pos BlockPos to rotate.
     * @param dir Direction the building is rotating into.
     * @param xSize Size X of the building when oriented North.
     * @param zSize Size Z of the building when oriented North.
     * @return A new instance of BlockPos at the correct position within the building size.
     */
    public static BlockPos rotateInBuild(BlockPos pos, Direction dir, int xSize, int zSize){
        return switch (dir){
            case WEST -> new BlockPos(zSize - pos.getZ(), pos.getY(), pos.getX());
            case SOUTH -> new BlockPos(xSize - pos.getX(), pos.getY(), zSize - pos.getZ());
            case EAST -> new BlockPos(pos.getZ(), pos.getY(), xSize - pos.getX());
            default -> pos;
        };
    }

    /**
     * Rotate the given BlockPos as part of a building oriented to North.
     * @param vec3 BlockPos to rotate.
     * @param dir Direction the building is rotating into.
     * @param xSize Size X of the building when oriented North.
     * @param zSize Size Z of the building when oriented North.
     * @return A new instance of BlockPos at the correct position within the building size.
     */
    public static Vec3 rotateInBuild(Vec3 vec3, Direction dir, int xSize, int zSize){
        return switch (dir){
            case WEST -> new Vec3(zSize - vec3.z(), vec3.y(), vec3.x());
            case SOUTH -> new Vec3(xSize - vec3.x(), vec3.y(), zSize - vec3.z());
            case EAST -> new Vec3(vec3.z(), vec3.y(), xSize - vec3.x());
            default -> vec3;
        };
    }

    // TODO Useful or to be deleted ?
    public static Vec3i getStructureDimensions(ResourceLocation path, ResourceManager resourceManager) {
        FileToIdConverter converter = new FileToIdConverter("structures", ".entityNbt");
        ResourceLocation resourceLocation = converter.idToFile(path);
        try (InputStream inputStream = resourceManager.open(resourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ListTag sizeTag = tag.getList("size", 3);
            return new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        } catch (FileNotFoundException fileNotFoundException) {
            LogUtils.getLogger().error("Structure not found {}", resourceLocation, fileNotFoundException);
            return null;
        } catch (Throwable throwable) {
            LogUtils.getLogger().error("Error loading structure {}", resourceLocation, throwable);
            return null;
        }
    }

    // TODO Useful or to be deleted ?
    public static List<EntityInfo> getStructureEntities(ResourceLocation path, ResourceManager resourceManager) {
        FileToIdConverter converter = new FileToIdConverter("structures", ".entityNbt");
        ResourceLocation resourceLocation = converter.idToFile(path);
        try (InputStream inputStream = resourceManager.open(resourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ListTag entitiesTag = tag.getList("entities", 10);
            List<EntityInfo> entityInfoList = new ArrayList<>();
            for(int i = 0; i < entitiesTag.size(); ++i) {
                CompoundTag entityTag = entitiesTag.getCompound(i);
                ListTag posTag = entityTag.getList("vec3", 6);
                Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
                ListTag blockPosTag = entityTag.getList("pos", 3);
                BlockPos blockPos = new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2));
                if (entityTag.contains("entityNbt")) {
                    CompoundTag entityNBT = entityTag.getCompound("entityNbt");
                    entityInfoList.add(new EntityInfo(pos, blockPos, entityNBT));
                }
            }
            return entityInfoList;
        } catch (FileNotFoundException fileNotFoundException) {
            LogUtils.getLogger().error("Structure not found {}", resourceLocation, fileNotFoundException);
            return null;
        } catch (Throwable throwable) {
            LogUtils.getLogger().error("Error loading structure {}", resourceLocation, throwable);
            return null;
        }
    }

    public static String blockPosToString(BlockPos pos){
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}

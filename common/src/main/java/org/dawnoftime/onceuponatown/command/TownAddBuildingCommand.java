package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Collection;

public class TownAddBuildingCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("town_add_building")
                .then(Commands.argument("build_type_name", StringArgumentType.string())
                        .executes(context -> addBuilding(context.getSource(), StringArgumentType.getString(context, "build_type_name"))));
    }

    private static int addBuilding(CommandSourceStack source, String buildTypeName) {
        // TODO Find a way to also place the blocks of the extended paths.
        Vec3 sourcePos = source.getPosition();
        LevelTowns manager = LevelTowns.of(source.getLevel());
        Collection<Town> towns = manager.getAllTowns();
        if (!towns.isEmpty()) {
            Town closestTown = null;
            double dist = -1;
            for (Town town : towns){
                double newDist = town.getCenter().distToCenterSqr(sourcePos);
                if(dist == -1 || newDist < dist){
                    dist = newDist;
                    closestTown = town;
                }
            }
            Town finalClosestTown = closestTown;
            BuildType type = finalClosestTown.getCulture().getBuildType(buildTypeName);
            if(type != null){
                if(type instanceof BuildingType buildingType){
                    Build build = finalClosestTown.addBuilding(buildingType);
                    if(build != null){
                        ServerLevel level = source.getLevel();
                        BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
                        SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
                        for(BlockInfo block: schema.getBlocks()){
                            cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                            level.setBlock(cursor.move(block.pos()), block.state(), 2);
                        }
                        source.sendSuccess(() -> Component.literal("A build from the build_type ")
                                .append(Component.literal(buildTypeName).withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" was successfully generated !")), false);
                    }else{
                        source.sendSuccess(() -> Component.literal("Could not manage to place the build."), false);
                    }
                }else{
                    source.sendFailure(Component.literal("The build_type must be a standard building."));
                }
            }else{
                source.sendFailure(Component.literal("This build_type doesn't exist in the closest town's culture."));
            }
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }
}

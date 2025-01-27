package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TownCommand {
    private static final String CLOSEST = "CLOSEST";
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TOWNS = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        LevelTowns.of(context.getSource().getLevel()).getAllTowns().forEach(town -> suggestions.add(town.getName()));
        suggestions.add(CLOSEST);
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BUILDING_TYPES = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        Town town = LevelTowns.of(context.getSource().getLevel()).getTown(getArg(context, "town"));
        if (town != null) {
            town.getCulture().getBuildingTypes().forEach(buildingType -> suggestions.add(buildingType.getId()));
        }
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BUILDINGS = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        Town town = LevelTowns.of(context.getSource().getLevel()).getTown(getArg(context, "town"));
        if (town != null) {
            town.getBuildings().forEach(building -> suggestions.add(building.toSafeString()));
        }
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("town")
            .then(Commands.literal("list")
                .executes(context -> listTowns(context.getSource()))
            )
            .then(Commands.literal("debug")
                .executes(context -> debugTown(context.getSource(), null))
                .then(Commands.argument("name", string())
                    .suggests(SUGGEST_TOWNS)
                    .executes(context -> debugTown(context.getSource(), getArg(context, "name")))
                )
            )
            .then(Commands.literal("spawn")
                .then(Commands.argument("culture", string())
                    .suggests(CultureCommand.SUGGEST_CULTURES)
                    .then(Commands.argument("name", string())
                        .executes(context -> spawnTown(context.getSource(), getArg(context, "culture"), getArg(context, "name")))
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("name", string())
                    .suggests(SUGGEST_TOWNS)
                    .then(Commands.argument("destroy", bool())
                        .executes(context -> deleteTown(context.getSource(), getArg(context, "name"), getBoolArg(context, "destroy")))
                    )
                )
            )
            .then(Commands.literal("building")
                .then(Commands.literal("list")
                    .executes(context -> listBuildings(context.getSource(), null))
                    .then(Commands.argument("town", string())
                        .suggests(SUGGEST_TOWNS)
                        .executes(context -> listBuildings(context.getSource(), getArg(context, "town")))
                    )
                )
                .then(Commands.literal("add")
                    .then(Commands.argument("town", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("building", string())
                            .suggests(SUGGEST_BUILDING_TYPES)
                            .executes(context -> addBuilding(context.getSource(), getArg(context, "town"), getArg(context, "building"), 1))
                            .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(context -> addBuilding(context.getSource(), getArg(context, "town"), getArg(context, "building"), IntegerArgumentType.getInteger(context, "level")))
                            )
                        )
                    )
                )
                .then(Commands.literal("upgrade")
                    .then(Commands.argument("town", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("building", string())
                            .suggests(SUGGEST_BUILDINGS)
                            .executes(context -> upgradeBuilding(context.getSource(), getArg(context, "town"), getArg(context, "building")))
                        )
                    )
                )
                .then(Commands.literal("delete")
                    .then(Commands.argument("town", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("building", string())
                            .suggests(SUGGEST_BUILDINGS)
                            .then(Commands.argument("destroy", bool())
                                .executes(context -> deleteBuilding(context.getSource(), getArg(context, "town"), getArg(context, "building"), getBoolArg(context, "destroy")))
                            )
                        )
                    )
                )
            );
    }

    private static int listTowns(CommandSourceStack source) {
        Collection<Town> towns = LevelTowns.of(source.getLevel()).getAllTowns();
        if (!towns.isEmpty()) {
            MutableComponent output = Component.empty()
                .append(Component.literal(towns.size() + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("town" + (towns.size() > 1 ? "s" : "" ) + " founded : "));
            towns.forEach(town -> output
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal(town.getName() + " ").withStyle(ChatFormatting.YELLOW))
                .append("at ")
                .append(Component.literal(town.getCenter().getX() + " " + town.getCenter().getY() + " " + town.getCenter().getZ())
                    .withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @p " + town.getCenter().getX() + " " + town.getCenter().getY() + " " + town.getCenter().getZ())))
                )
            );
            source.sendSuccess(() -> output, false);
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }

    private static int debugTown(CommandSourceStack source, String townName) {
        Vec3 sourcePos = source.getPosition();
        LevelTowns manager = LevelTowns.of(source.getLevel());
        Collection<Town> towns = manager.getAllTowns();
        if (!towns.isEmpty()) {
            Town closestTown = null;
            double dist = -1;
            for (Town town : towns) {
                double newDist = town.getCenter().distToCenterSqr(sourcePos);
                if (dist == -1 || newDist < dist) {
                    dist = newDist;
                    closestTown = town;
                }
            }
            Town finalClosestTown = closestTown;
            var townInfo = Component.literal("Check in the logs the description of the closest town ")
                .append(Component.literal(closestTown.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" found in "))
                .append((Component.literal(closestTown.getCenter().toShortString())).withStyle((style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + finalClosestTown.getCenter().getX() + " " + finalClosestTown.getCenter().getY() + " " + finalClosestTown.getCenter().getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport"))))));
            source.sendSuccess(() -> townInfo, false);
            closestTown.printDescription();
            closestTown.getBuilds().forEach((build -> source.getLevel().setBlock(build.getOriginPos(), Blocks.ORANGE_WOOL.defaultBlockState(), 2)));
            closestTown.getBuds().forEach((bud -> source.getLevel().setBlock(bud.getPosition(), Blocks.PURPLE_WOOL.defaultBlockState(), 2)));
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }

    private static int spawnTown(CommandSourceStack source, String cultureId, String townName) {
        Vec3 pos = source.getPosition();
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture != null) {
            ServerLevel level = source.getLevel();
            BlockPos posTown = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos((int) pos.x, (int) pos.y, (int) pos.z));
            Town town = LevelTowns.of(level).trySpawnTown(culture, posTown);
            if (town != null) {
                // Now we place the blocks.
                BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
                for (Build build : town.getBuilds()) {
                    SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
                    for (BlockInfo block : schema.getBlocks()) {
                        cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                        level.setBlock(cursor.move(block.pos()), block.state(), 2);
                    }
                    // TODO Do the same for the entities !
                }
                source.sendSuccess(() -> Component.literal("The town ")
                    .append(Component.literal(townName).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" was successfully generated !")), false);
            } else {
                source.sendSuccess(() -> Component.literal("The town ")
                    .append(Component.literal(townName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" couldn't not be spawned because there wasn't enough free space.")), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("The culture ")
                .append(Component.literal(cultureId).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" doesn't exist.")), false);
        }
        return 1;
    }

    private static int deleteTown(CommandSourceStack source, String townName, boolean destroy) {
        Town town = getTownOrClosest(source, townName);
        if ((destroy ? LevelTowns.of(source.getLevel()).deleteAndDemolishTown(town.getId()) : LevelTowns.of(source.getLevel()).deleteTown(town.getId()))) {
            source.sendSuccess(() -> Component.literal("Successfuly " + (destroy ? "demolished" : "deleted") + " town " + townName), false);
        } else {
            source.sendFailure(Component.literal("Failed to delete town"));
        }
        return 1;
    }

    private static Town getTownOrClosest(CommandSourceStack source, String townName) {
        return (townName == null || townName.equals(CLOSEST)) ?
            Utils.getNearestTown(source.getLevel(), new BlockPos((int) source.getPosition().x, (int) source.getPosition().y, (int) source.getPosition().z) ) :
            LevelTowns.of(source.getLevel()).getTown(townName);
    }

    private static int listBuildings(CommandSourceStack source, String townName) {
        List<String> buildings = new ArrayList<>();
        Town town = getTownOrClosest(source, townName);
        if (town != null) {
            town.getBuildings().forEach(building -> buildings.add(building.toSafeString()));
            MutableComponent output = Component.literal("Town")
                .append(Component.literal(" " + town.getName()).withStyle(ChatFormatting.YELLOW))
                .append(" has")
                .append(Component.literal(" " + buildings.size()).withStyle(ChatFormatting.GRAY))
                .append(" buildings :");
            buildings.forEach((building) -> output
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal(building).withStyle(ChatFormatting.DARK_AQUA)));
            source.sendSuccess(() -> output, false);
        } else {
            source.sendFailure(Component.literal("Town not founded"));
        }
        return 1;
    }

    private static int addBuilding(CommandSourceStack source, String townName, String buildTypeId, int level) {
        // TODO Find a way to also place the blocks of the extended paths.
        Vec3 sourcePos = source.getPosition();
        LevelTowns manager = LevelTowns.of(source.getLevel());
        Collection<Town> towns = manager.getAllTowns();
        if (!towns.isEmpty()) {
            Town closestTown = null;
            double dist = -1;
            for (Town town : towns) {
                double newDist = town.getCenter().distToCenterSqr(sourcePos);
                if (dist == -1 || newDist < dist) {
                    dist = newDist;
                    closestTown = town;
                }
            }
            Town finalClosestTown = closestTown;
            BuildType type = finalClosestTown.getCulture().getBuildType(buildTypeId);
            if (type != null) {
                if (type instanceof BuildingType buildingType) {
                    Build build = finalClosestTown.tryAddBuilding(buildingType, level);
                    if (build != null) {
                        ServerLevel serverLevel = source.getLevel();
                        BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
                        SchematicContent schema = build.getSchematicContent(serverLevel.getServer().getResourceManager());
                        for (BlockInfo block : schema.getBlocks()) {
                            cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                            serverLevel.setBlock(cursor.move(block.pos()), block.state(), 2);
                        }
                        source.sendSuccess(() -> Component.literal("A build from the build_type ")
                            .append(Component.literal(buildTypeId).withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(" was successfully generated !")), false);
                    } else {
                        source.sendSuccess(() -> Component.literal("Could not manage to place the build."), false);
                    }
                } else {
                    source.sendFailure(Component.literal("The build_type must be a standard building."));
                }
            } else {
                source.sendFailure(Component.literal("This build_type doesn't exist in the closest town's culture."));
            }
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }

    private static int upgradeBuilding(CommandSourceStack source, String townName, String buildingName) {
        return 1;
    }

    private static int deleteBuilding(CommandSourceStack source, String townName, String buildingName, boolean destroy) {
        return 1;
    }

    private static StringArgumentType string() {
        return StringArgumentType.string();
    }

    private static BoolArgumentType bool() {
        return BoolArgumentType.bool();
    }

    private static String getArg(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private static boolean getBoolArg(CommandContext<CommandSourceStack> context, String name) {
        return BoolArgumentType.getBool(context, name);
    }
}

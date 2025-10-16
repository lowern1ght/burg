package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.instance.Building;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TownCommand {
    private static final int CLOSEST_TOWN_MAX_SEARCH_DIST = 100;
    private static final String CLOSEST = "CLOSEST";
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TOWNS = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        LevelTowns.of(context.getSource().getLevel()).getAll().forEach(town -> suggestions.add(town.getFancyId()));
        suggestions.add(CLOSEST);
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BUILDING_TYPES = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        String townId = getString(context, "townid");
        Town town;
        if (townId.equals(CLOSEST)) {
            town = getClosestTown(context.getSource());
        } else {
            town = LevelTowns.of(context.getSource().getLevel()).getTownByFancyId(townId);
        }
        if (town != null) {
            town.getCulture().getBuildingTypes().forEach(buildingType -> suggestions.add(buildingType.getId()));
        }
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BUILDINGS = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        String townId = getString(context, "townid");
        Town town;
        if (townId.equals(CLOSEST)) {
            town = getClosestTown(context.getSource());
        } else {
            town = LevelTowns.of(context.getSource().getLevel()).getTownByFancyId(townId);
        }
        if (town != null) {
            town.getBuildings().forEach(building -> suggestions.add(building.toSafeString()));
        }
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROJECTS = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        String townId = getString(context, "townid");
        Town town;
        if (townId.equals(CLOSEST)) {
            town = getClosestTown(context.getSource());
        } else {
            town = LevelTowns.of(context.getSource().getLevel()).getTownByFancyId(townId);
        }
        if (town != null) {
            town.getProjects().forEach(project -> suggestions.add(project.toSafeString()));
        }
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("town")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
            .then(Commands.literal("list")
                .executes(context -> listTowns(context.getSource()))
            )
            .then(Commands.literal("debug")
                .then(Commands.argument("townid", string())
                    .suggests(SUGGEST_TOWNS)
                    .executes(context -> debugTown(context.getSource(), getString(context, "townid"), false))
                    .then(Commands.argument("placemarkerblocks", bool())
                        .executes(context -> debugTown(context.getSource(), getString(context, "townid"), getBool(context, "placemarkerblocks")))
                    )
                )
            )
            .then(Commands.literal("spawn")
                .then(Commands.argument("culture", string())
                    .suggests(CultureCommand.SUGGEST_CULTURES)
                    .then(Commands.argument("name", component())
                        .executes(context -> spawnTown(context.getSource(), getString(context, "culture"), getComponent(context, "name"), null))
                        .then(Commands.argument("position", BlockPosArgument.blockPos())
                            .executes(context -> spawnTown(context.getSource(), getString(context, "culture"), getComponent(context, "name"), BlockPosArgument.getBlockPos(context, "position")))
                        )
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("townid", string())
                    .suggests(SUGGEST_TOWNS)
                    .then(Commands.argument("demolish", bool())
                        .executes(context -> deleteTown(context.getSource(), getString(context, "townid"), getBool(context, "demolish")))
                    )
                )
            )
            .then(Commands.literal("setname")
                .then(Commands.argument("townid", string())
                    .suggests(SUGGEST_TOWNS)
                    .then(Commands.argument("newname", component())
                        .executes(context -> setTownName(context.getSource(), getString(context, "townid"), getComponent(context, "newname")))
                    )
                )
            )
            .then(Commands.literal("dwellers")
                .then(Commands.argument("townid", string())
                    .suggests(SUGGEST_TOWNS)
                    .then(Commands.literal("welcome")
                        .then(Commands.argument("npc", entity())
                            .executes(context -> addTownDweller(context.getSource(), getString(context, "townid"), getEntity(context, "npc")))
                        )
                    )
                )
            )
            .then(Commands.literal("project")
                .then(Commands.literal("list")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .executes(context -> listProjects(context.getSource(), getString(context, "townid")))
                    )
                )
                .then(Commands.literal("start")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("buildingtype", string())
                            .suggests(SUGGEST_BUILDING_TYPES)
                            .executes(context -> startProject(context.getSource(), getString(context, "townid"), getString(context, "buildingtype")))
                        )
                    )
                )
                .then(Commands.literal("finish")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("project", string())
                            .suggests(SUGGEST_PROJECTS)
                            .executes(context -> finishProject(context.getSource(), getString(context, "townid"), getString(context, "project")))
                        )
                    )
                )
            )
            .then(Commands.literal("building")
                .then(Commands.literal("list")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .executes(context -> listBuildings(context.getSource(), getString(context, "townid")))
                    )
                )
                .then(Commands.literal("add")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("buildingtype", string())
                            .suggests(SUGGEST_BUILDING_TYPES)
                            .executes(context -> addBuilding(context.getSource(), getString(context, "townid"), getString(context, "buildingtype"), 1))
                            .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                .executes(context -> addBuilding(context.getSource(), getString(context, "townid"), getString(context, "buildingtype"), IntegerArgumentType.getInteger(context, "level")))
                            )
                        )
                    )
                )
                .then(Commands.literal("upgrade")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("building", string())
                            .suggests(SUGGEST_BUILDINGS)
                            .executes(context -> upgradeBuilding(context.getSource(), getString(context, "townid"), getString(context, "building"), -1)) // -1 : special number, means increment the building level by 1.
                            .then(Commands.argument("wantedLevel", IntegerArgumentType.integer(2)) // Specify the wanted level (2 is minimum since buildings start at level 1). If no argument, building level will be incremented by 1.
                                .executes(context -> upgradeBuilding(context.getSource(), getString(context, "townid"), getString(context, "building"), IntegerArgumentType.getInteger(context, "wantedLevel")))
                            )
                        )
                    )
                )
                .then(Commands.literal("delete")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("building", string())
                            .suggests(SUGGEST_BUILDINGS)
                            .then(Commands.argument("demolish", bool())
                                .executes(context -> deleteBuilding(context.getSource(), getString(context, "townid"), getString(context, "building"), getBool(context, "demolish")))
                            )
                        )
                    )
                )
            );
    }

    private static int addTownDweller(CommandSourceStack source, String townId, Entity entity) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            if (entity instanceof Npc npc) {
                town.addDweller(npc);
                source.sendSuccess(() -> Component.literal("Successfully added dweller"), true);
            } else {
                source.sendFailure(Component.literal("The dweller entity must be an Npc (" + Ouat.MOD_ID + ":npc"));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int listTowns(CommandSourceStack source) {
        Collection<Town> towns = LevelTowns.of(source.getLevel()).getAll();
        if (!towns.isEmpty()) {
            MutableComponent output = Component.empty()
                .append(Component.literal(towns.size() + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("town" + (towns.size() > 1 ? "s" : "") + " founded : "));
            towns.forEach(town -> output
                .append(CommonComponents.NEW_LINE)
                .append(Component.empty().append(town.getName()).append(Component.literal(" ")).withStyle(ChatFormatting.YELLOW))
                .append("at ")
                .append(Component.literal(town.getCenter().getX() + " " + town.getCenter().getY() + " " + town.getCenter().getZ())
                    .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + town.getCenter().getX() + " " + town.getCenter().above().getY() + " " + town.getCenter().getZ())))
                )
            );
            source.sendSuccess(() -> output, true);
        } else {
            source.sendSuccess(() -> Component.literal("No towns founded"), true);
        }
        return 1;
    }

    private static int debugTown(CommandSourceStack source, String townId, boolean placeMarkerBlocks) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            var townInfo = Component.literal("Check the console for a description of town ")
                .append(Component.empty().append(town.getName()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" at "))
                .append((Component.literal(town.getCenter().toShortString())).withStyle((style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + town.getCenter().getX() + " " + town.getCenter().above().getY() + " " + town.getCenter().getZ()))
                )));
            source.sendSuccess(() -> townInfo, true);
            town.printConsoleDescription();
            if (placeMarkerBlocks) {
                for (int y = 1; y <= 10; ++y) {
                    source.getLevel().setBlock(town.getNWCorner().above(y), Blocks.RED_WOOL.defaultBlockState(), 2);
                    source.getLevel().setBlock(town.getSECorner().above(y), Blocks.RED_WOOL.defaultBlockState(), 2);
                }
                town.getBuilds().forEach((build -> source.getLevel().setBlock(build.getOriginPos(), Blocks.ORANGE_WOOL.defaultBlockState(), 2)));
                town.getBuds().forEach((bud -> source.getLevel().setBlock(bud.getPosition(), Blocks.PURPLE_WOOL.defaultBlockState(), 2)));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int spawnTown(CommandSourceStack source, String cultureId, Component townName, BlockPos townPos) {
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture != null) {
            ServerLevel level = source.getLevel();
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townPos != null ? townPos : BlockPos.containing(source.getPosition()));
            Town town = LevelTowns.of(level).trySpawnTown(culture, pos, townName);
            if (town != null) {
                source.sendSuccess(() -> Component.literal("Town ")
                    .append(Component.empty().append(townName).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" was successfully generated")), true);
            } else {
                source.sendSuccess(() -> Component.literal("Town ")
                    .append(Component.empty().append(townName).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" has failed to generate, probably because there was not enough free space.")), true);
            }
        } else {
            source.sendFailure(Component.literal("Culture ")
                .append(Component.literal(cultureId).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" doesn't exist")));
        }
        return 1;
    }

    private static int deleteTown(CommandSourceStack source, String townId, boolean demolish) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            if (LevelTowns.of(source.getLevel()).deleteTown(town.getId(), demolish)) {
                source.sendSuccess(() -> Component.literal("Successfully " + (demolish ? "demolished" : "deleted") + " town " + townId), true);
            } else {
                source.sendFailure(Component.literal("Failed to delete the town"));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int setTownName(CommandSourceStack source, String townId, Component newName) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            if (town.setName(newName)) {
                source.sendSuccess(() -> Component.literal("Successfully changed town's name"), true);
            } else {
                source.sendFailure(Component.literal("Failed to change town's name. Make sure the new name is not blank"));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int listBuildings(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            List<String> buildings = new ArrayList<>();
            town.getBuildings().forEach(building -> buildings.add(building.toSafeString()));
            MutableComponent output = Component.literal("Town")
                .append(Component.literal(" " + town.getName()).withStyle(ChatFormatting.YELLOW))
                .append(" has")
                .append(Component.literal(" " + buildings.size()).withStyle(ChatFormatting.GRAY))
                .append(" buildings :");
            buildings.forEach((building) -> output
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal(building).withStyle(ChatFormatting.DARK_AQUA)));
            source.sendSuccess(() -> output, true);
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int startProject(CommandSourceStack source, String townId, String buildTypeId) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            BuildType type = town.getCulture().getBuildType(buildTypeId);
            if (type != null) {
                if (type instanceof BuildingType buildingType) {
                    if (town.createProject(buildingType)) {
                        source.sendSuccess(() -> Component.literal("Construction of a ")
                            .append(Component.literal(buildTypeId).withStyle(ChatFormatting.GREEN))
                            .append(Component.literal(" building will start soon")), true);
                    } else {
                        source.sendFailure(Component.literal("Could not manage to start the project."));
                    }
                } else {
                    source.sendFailure(Component.literal("The build type must be a standard building"));
                }
            } else {
                source.sendFailure(Component.literal("This build type does not exist"));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int finishProject(CommandSourceStack source, String townId, String projectId) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            if (town.finishProject(projectId)) {
                source.sendSuccess(() -> Component.literal("Construction will end soon !"), true);
            } else {
                source.sendFailure(Component.literal("Could not manage to finish the project."));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int listProjects(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            var projects = town.getProjects();
            int projectsSize = projects.size();
            MutableComponent output;
            if (projectsSize > 0) {
                output = Component.empty()
                    .append(Component.literal(projectsSize + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("project" + (projectsSize > 1 ? "s" : "") + " founded : "));
                projects.forEach(project -> output
                    .append(CommonComponents.NEW_LINE)
                    .append(Component.empty().append(project.toSafeString()).withStyle(ChatFormatting.YELLOW))
                    .append(" : ").append(project.getProjectType().getDescription())
                    .append(", progression : ").append(project.getProgression())
                );
            } else {
                output = Component.literal("No projects founded");
            }
            source.sendSuccess(() -> output, true);
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int addBuilding(CommandSourceStack source, String townId, String buildTypeId, int level) {
        // TODO Find a way to also place the blocks of the extended paths.
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            BuildType type = town.getCulture().getBuildType(buildTypeId);
            if (type != null) {
                if (type instanceof BuildingType buildingType) {
                    int maxLevel = buildingType.getLevels().size();
                    if (level <= maxLevel) {
                        if (town.build(buildingType, level)) {
                            source.sendSuccess(() -> Component.literal("Build ")
                                .append(Component.literal(buildTypeId).withStyle(ChatFormatting.GREEN))
                                .append(Component.literal(" was successfully generated")), true);
                        } else {
                            source.sendFailure(Component.literal("Could not manage to place the build."));
                        }
                    } else {
                        source.sendFailure(Component.literal("The build type level must be between 1 and " + maxLevel));
                    }
                } else {
                    source.sendFailure(Component.literal("The build type must be a standard building"));
                }
            } else {
                source.sendFailure(Component.literal("This build type does not exist"));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    private static int upgradeBuilding(CommandSourceStack source, String townId, String buildingName, int wantedLevel) {
        // TODO implement
        source.sendSuccess(() -> Component.literal("This is not implemented yet"), true);
        return 1;
    }

    private static int deleteBuilding(CommandSourceStack source, String townId, String buildingName, boolean demolish) {
        Town town = getTownOrClosest(source, townId);
        if (town != null) {
            Building building = town.getBuilding(buildingName);
            if (building != null) {
                if (town.demolish(building)) {
                    source.sendSuccess(() -> Component.literal("Building %s was successfully %s".formatted(buildingName, (demolish ? "demolished" : "deleted"))), true);
                } else {
                    source.sendFailure(Component.literal("Failed to delete the building"));
                }
            } else {
                source.sendFailure(Component.literal("This building does not exist in this town"));
            }
        } else {
            source.sendSuccess(() -> Component.literal("Town not founded"), true);
        }
        return 1;
    }

    /**
     * @param townId the name of the wanted town
     * @return if <code>townId</code> is null or equals CLOSEST, returns the closest town or null if the closest town is too far away.
     * Else, returns the town with the specified name or null if it does not exist.
     */
    private static @Nullable Town getTownOrClosest(CommandSourceStack source, String townId) {
        return (townId == null || townId.equals(CLOSEST)) ? getClosestTown(source) : LevelTowns.of(source.getLevel()).getTownByFancyId(townId);
    }

    private static @Nullable Town getClosestTown(CommandSourceStack source) {
        return Utils.getNearestTown(source.getLevel(), BlockPos.containing(source.getPosition()), CLOSEST_TOWN_MAX_SEARCH_DIST);
    }

    private static StringArgumentType string() {
        return StringArgumentType.string();
    }

    private static String getString(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private static EntityArgument entity() {
        return EntityArgument.entity();
    }

    private static Entity getEntity(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        return EntityArgument.getEntity(context, name);
    }

    private static ComponentArgument component() {
        return ComponentArgument.textComponent();
    }

    private static Component getComponent(CommandContext<CommandSourceStack> context, String name) {
        return ComponentArgument.getComponent(context, name);
    }

    private static BoolArgumentType bool() {
        return BoolArgumentType.bool();
    }

    private static boolean getBool(CommandContext<CommandSourceStack> context, String name) {
        return BoolArgumentType.getBool(context, name);
    }
}

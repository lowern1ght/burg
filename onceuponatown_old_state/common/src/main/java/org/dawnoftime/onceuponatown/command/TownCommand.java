package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final String CLOSEST = "CLOSEST_TOWN";
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
            town = getClosestTown(context.getSource(), CLOSEST_TOWN_MAX_SEARCH_DIST);
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
            town = getClosestTown(context.getSource(), CLOSEST_TOWN_MAX_SEARCH_DIST);
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
            town = getClosestTown(context.getSource(), CLOSEST_TOWN_MAX_SEARCH_DIST);
        } else {
            town = LevelTowns.of(context.getSource().getLevel()).getTownByFancyId(townId);
        }
        if (town != null) {
            town.getProjects().forEach(project -> suggestions.add(project.toSafeString()));
        }
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandBuildContext buildContext) {
        return Commands.literal("town")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
            .then(Commands.literal("list")
                .executes(context -> listTowns(context.getSource()))
            )
            .then(Commands.literal("closest")
                .executes(context -> showClosestTown(context.getSource()))
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
            .then(Commands.literal("debug")
                .then(Commands.argument("townid", string())
                    .suggests(SUGGEST_TOWNS)
                    .executes(context -> debugTown(context.getSource(), getString(context, "townid"), false))
                    .then(Commands.argument("placemarkerblocks", bool())
                        .executes(context -> debugTown(context.getSource(), getString(context, "townid"), getBool(context, "placemarkerblocks")))
                    )
                )
            )
            /* Citizens */
            .then(Commands.literal("citizen")
                .then(Commands.literal("list")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .executes(context -> listCitizens(context.getSource(), getString(context, "townid")))
                    )
                )
                .then(Commands.literal("add")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("npc", entity())
                            .executes(context -> addCitizen(context.getSource(), getString(context, "townid"), getEntity(context, "npc")))
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("npc", entity())
                            .executes(context -> removeCitizen(context.getSource(), getString(context, "townid"), getEntity(context, "npc")))
                        )
                    )
                )
            )
            /* Buildings */
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
                            .then(Commands.argument("targetlevel", IntegerArgumentType.integer(2)) // Specify the wanted level (2 is minimum since buildings start at level 1). If no argument, building level will be incremented by 1.
                                .executes(context -> upgradeBuilding(context.getSource(), getString(context, "townid"), getString(context, "building"), IntegerArgumentType.getInteger(context, "targetlevel")))
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
            )
            /* Projects */
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
            /* Inventory */
            .then(Commands.literal("inventory")
                .then(Commands.literal("show")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .executes(context -> showInventory(context.getSource(), getString(context, "townid")))
                    )
                )
                .then(Commands.literal("add")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                            .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(context -> addToInventory(context.getSource(), getString(context, "townid"), ItemArgument.getItem(context, "item").getItem(), IntegerArgumentType.getInteger(context, "count")))
                            )
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                            .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(context -> removeFromInventory(context.getSource(), getString(context, "townid"), ItemArgument.getItem(context, "item").getItem(), IntegerArgumentType.getInteger(context, "count")))
                            )
                        )
                    )
                )
                .then(Commands.literal("clear")
                    .then(Commands.argument("townid", string())
                        .suggests(SUGGEST_TOWNS)
                        .executes(context -> clearInventory(context.getSource(), getString(context, "townid")))
                    )
                )
            );
    }

    private static int showClosestTown(CommandSourceStack source) {
        Town town = getClosestTown(source, CLOSEST_TOWN_MAX_SEARCH_DIST);
        MutableComponent output;
        if (town == null) {
            output =  Component.literal("No towns founded in a %s blocks radius".formatted(CLOSEST_TOWN_MAX_SEARCH_DIST));
        } else {
            output = Component.empty()
                .append("Closest town : ")
                .append(CommonComponents.NEW_LINE)
                .append(Component.empty().append(town.getName()).append(Component.literal(" ")).withStyle(ChatFormatting.YELLOW))
                .append("at ")
                .append(Component.literal(town.getCenter().getX() + " " + town.getCenter().getY() + " " + town.getCenter().getZ())
                    .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + town.getCenter().getX() + " " + town.getCenter().above().getY() + " " + town.getCenter().getZ())))
                );
        }
        source.sendSuccess(() -> output, true);
        return 1;
    }

    private static int listTowns(CommandSourceStack source) {
        Collection<Town> towns = LevelTowns.of(source.getLevel()).getAll();
        MutableComponent output;
        if (towns.isEmpty()) {
            output = Component.literal("No towns founded");
        } else {
            output = Component.empty()
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
        }
        source.sendSuccess(() -> output, true);
        return 1;
    }

    private static int spawnTown(CommandSourceStack source, String cultureId, Component townName, BlockPos townPos) {
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture == null) {
            source.sendFailure(Component.literal("Culture ")
                .append(Component.literal(cultureId).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" does not exist")));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, townPos != null ? townPos : BlockPos.containing(source.getPosition()));
        Town town = LevelTowns.of(level).trySpawnTown(culture, pos, townName);
        if (town == null) {
            source.sendFailure(Component.literal("Failed to generate the town. Make sure there is enough free space"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Town ")
            .append(Component.empty().append(townName).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" was successfully generated")), true);
        return 1;
    }

    private static int deleteTown(CommandSourceStack source, String townId, boolean demolish) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (!LevelTowns.of(source.getLevel()).deleteTown(town.getId(), demolish)) {
            source.sendFailure(Component.literal("Failed to delete the town"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Successfully " + (demolish ? "demolished" : "deleted") + " town " + townId), true);
        return 1;
    }

    private static int setTownName(CommandSourceStack source, String townId, Component newName) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (!town.setName(newName)) {
            source.sendFailure(Component.literal("Failed to change town's name. Make sure the new name is not blank"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Successfully changed town's name"), true);
        return 1;
    }

    private static int debugTown(CommandSourceStack source, String townId, boolean placeMarkerBlocks) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        var townInfo = Component.literal("Check the console for a description of town ")
            .append(Component.empty().append(town.getName()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" at "))
            .append((Component.literal(town.getCenter().toShortString())).withStyle((style -> style
                .withColor(ChatFormatting.AQUA)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport")))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + town.getCenter().getX() + " " + town.getCenter().above().getY() + " " + town.getCenter().getZ()))
            )));
        town.printConsoleDescription();
        if (placeMarkerBlocks) {
            for (int y = 1; y <= 10; ++y) {
                source.getLevel().setBlock(town.getNWCorner().above(y), Blocks.RED_WOOL.defaultBlockState(), 2);
                source.getLevel().setBlock(town.getSECorner().above(y), Blocks.RED_WOOL.defaultBlockState(), 2);
            }
            town.getBuilds().forEach((build -> source.getLevel().setBlock(build.getOriginPos(), Blocks.ORANGE_WOOL.defaultBlockState(), 2)));
            town.getBuds().forEach((bud -> source.getLevel().setBlock(bud.getPosition(), Blocks.PURPLE_WOOL.defaultBlockState(), 2)));
        }
        source.sendSuccess(() -> townInfo, true);
        return 1;
    }

    private static int listCitizens(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        var dwellers = town.getCitizens();
        if (dwellers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("This town has no dwellers"), true);
            return 1;
        }
        MutableComponent output = Component.empty()
            .append(Component.literal("Dwellers of town "))
            .append(town.getName())
            .append(" :");
        dwellers.forEach((citizen) -> output
            .append(CommonComponents.NEW_LINE)
            .append(Component.literal(citizen.toString()).withStyle(ChatFormatting.YELLOW)));
        source.sendSuccess(() -> output, true);
        return 1;
    }

    private static int addCitizen(CommandSourceStack source, String townId, Entity entity) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (!(entity instanceof Npc npc)) {
            source.sendFailure(Component.literal("The dweller entity must be a %s entity".formatted(EntityRegistry.REGISTRY.NPC.get().toString())));
            return 0;
        }
        if (!town.addCitizen(npc)) {
            source.sendFailure(Component.literal("Failed to add the dweller to the town"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Successfully added the dweller to the town"), true);
        return 1;
    }

    private static int removeCitizen(CommandSourceStack source, String townId, Entity entity) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (!(entity instanceof Npc npc)) {
            source.sendFailure(Component.literal("The dweller entity must be a %s entity".formatted(EntityRegistry.REGISTRY.NPC.get().toString())));
            return 0;
        }
        if (!town.removeCitizen(npc)) {
            source.sendFailure(Component.literal("Failed to remove the dweller from the town"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Successfully removed the dweller from the town"), true);
        return 1;
    }

    private static int listBuildings(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        var buildings = town.getBuildings();
        if (buildings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("This town has no buildings"), true);
            return 1;
        }
        MutableComponent output = Component.literal("Town")
            .append(Component.literal(" " + town.getName()).withStyle(ChatFormatting.YELLOW))
            .append(" has")
            .append(Component.literal(" " + buildings.size()).withStyle(ChatFormatting.GRAY))
            .append(" buildings :");
        buildings.forEach((building) -> output
            .append(CommonComponents.NEW_LINE)
            .append(Component.literal(building.toSafeString()).withStyle(ChatFormatting.DARK_AQUA)));
        source.sendSuccess(() -> output, true);
        return 1;
    }

    private static int addBuilding(CommandSourceStack source, String townId, String buildTypeId, int level) {
        // TODO Find a way to also place the blocks of the extended paths.
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        BuildType type = town.getCulture().getBuildType(buildTypeId);
        if (type == null) {
            source.sendFailure(Component.literal("This build type does not exist"));
            return 0;
        }
        if (!(type instanceof BuildingType buildingType)) {
            source.sendFailure(Component.literal("The build type must be a standard building"));
            return 0;
        }
        int maxLevel = buildingType.getLevels().size();
        if ((level <= 0 ) || (level > maxLevel)) {
            source.sendFailure(Component.literal("The build type level must be between 1 and " + maxLevel));
            return 0;
        }
        if (!town.build(buildingType, level)) {
            source.sendFailure(Component.literal("Could not manage to place the build"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Build ")
            .append(Component.literal(buildTypeId).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" was successfully generated")), true);
        return 1;
    }

    private static int upgradeBuilding(CommandSourceStack source, String townId, String buildingName, int targetLevel) {
        // TODO implement
        source.sendSuccess(() -> Component.literal("This is not implemented yet"), true);
        return 1;
    }

    private static int deleteBuilding(CommandSourceStack source, String townId, String buildingName, boolean demolish) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        Building building = town.getBuilding(buildingName);
        if (building == null) {
            source.sendFailure(Component.literal("This building does not exist in this town"));
            return 0;
        }
        if (!town.demolish(building)) {
            source.sendFailure(Component.literal("Failed to delete the building"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Building %s was successfully %s".formatted(buildingName, (demolish ? "demolished" : "deleted"))), true);
        return 1;
    }

    private static int listProjects(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        var projects = town.getProjects();
        if (projects.isEmpty()) {
            source.sendSuccess(() -> Component.literal("This town has no construction projects"), true);
            return 1;
        }
        MutableComponent output;
        output = Component.empty()
            .append(Component.literal(projects.size() + " ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("project" + (projects.size() > 1 ? "s" : "") + " founded : "));
        projects.forEach(project -> output
            .append(CommonComponents.NEW_LINE)
            .append(Component.empty().append(project.toSafeString()).withStyle(ChatFormatting.YELLOW))
            .append(" : ").append(project.getProjectType().getDescription())
            .append(", progression : ").append(project.getProgression())
        );
        source.sendSuccess(() -> output, true);
        return 1;
    }

    private static int startProject(CommandSourceStack source, String townId, String buildTypeId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        BuildType type = town.getCulture().getBuildType(buildTypeId);
        if (type == null) {
            source.sendFailure(Component.literal("This build type does not exist"));
            return 0;
        }
        if (!(type instanceof BuildingType buildingType)) {
            source.sendFailure(Component.literal("The build type must be a standard building"));
            return 0;
        }
        if (!town.createProject(buildingType)) {
            source.sendFailure(Component.literal("Could not manage to start the project"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Construction of a ")
            .append(Component.literal(buildTypeId).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" building will start soon")), true);
        return 1;
    }

    private static int finishProject(CommandSourceStack source, String townId, String projectId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (!town.hasProject(projectId)) {
            source.sendFailure(Component.literal("Project not founded"));
            return 0;
        }
        if (!town.finishProject(projectId)) {
            source.sendFailure(Component.literal("Could not manage to finish the project"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Construction will end soon"), true);
        return 1;
    }

    private static int showInventory(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        var inventory = town.getInventory().getContent();
        if (inventory.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Town's inventory is empty"), true);
            return 1;
        }
        MutableComponent output = Component.empty()
            .append(Component.literal("Inventory of town "))
            .append(town.getName())
            .append(" :");
        inventory.forEach((stack) -> output
            .append(CommonComponents.NEW_LINE)
            .append(Component.literal(stack.toString()).withStyle(ChatFormatting.YELLOW)));
        source.sendSuccess(() -> output, true);
        return 1;
    }

    private static int addToInventory(CommandSourceStack source, String townId, Item item, int count) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (item == null || item == Items.AIR) {
            source.sendFailure(Component.literal("Invalid item"));
            return 0;
        } else if (count < 1) {
            source.sendFailure(Component.literal("Item count must be greater than 0"));
            return 0;
        } else if (!town.getInventory().add(new ItemStack(item, count))) {
            source.sendFailure(Component.literal("Failed to add the item in town's inventory"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Added %s %s in town's inventory".formatted(count, item)), true);
        return 1;
    }

    private static int removeFromInventory(CommandSourceStack source, String townId, Item item, int count) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        if (item == null || item == Items.AIR) {
            source.sendFailure(Component.literal("Invalid item"));
            return 0;
        } else if (count < 1) {
            source.sendFailure(Component.literal("Item count must be greater than 0"));
            return 0;
        } else if (!town.getInventory().remove(new ItemStack(item, count))) {
            source.sendFailure(Component.literal("Failed to remove the item from town's inventory"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Removed %s %s from town's inventory".formatted(count, item)), true);
        return 1;
    }

    private static int clearInventory(CommandSourceStack source, String townId) {
        Town town = getTownOrClosest(source, townId);
        if (town == null) {
            source.sendFailure(Component.literal("Town not founded"));
            return 0;
        }
        town.getInventory().clear();
        source.sendSuccess(() -> Component.literal("Cleared town inventory"), true);
        return 1;
    }

    /**
     * @param townId the name of the wanted town
     * @return if <code>townId</code> is null or equals CLOSEST, returns the closest town or null if the closest town is too far away.
     * Else, returns the town with the specified name or null if it does not exist.
     */
    private static @Nullable Town getTownOrClosest(CommandSourceStack source, String townId) {
        return (townId == null || townId.equals(CLOSEST)) ? getClosestTown(source, CLOSEST_TOWN_MAX_SEARCH_DIST) : LevelTowns.of(source.getLevel()).getTownByFancyId(townId);
    }

    private static @Nullable Town getClosestTown(CommandSourceStack source, int maxSearchRadius) {
        return Utils.getNearestTown(source.getLevel(), BlockPos.containing(source.getPosition()), maxSearchRadius);
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

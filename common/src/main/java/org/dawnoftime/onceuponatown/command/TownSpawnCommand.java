package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

public class TownSpawnCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("town_spawn")
                .then(Commands.argument("culture_id", StringArgumentType.string())
                        .then(Commands.argument("town_name", StringArgumentType.string())
                                .executes(context -> spawnTown(context.getSource(), StringArgumentType.getString(context, "culture_id"), StringArgumentType.getString(context, "town_name")))));
    }

    private static int spawnTown(CommandSourceStack source, String cultureId, String townName) {
        try{
            Vec3 pos = source.getPosition();
            Culture culture = CultureManager.getCultureById(cultureId);
            ServerLevel level = source.getLevel();
            Town town = new Town(level, culture, townName, new BlockPos(new BlockPos((int) pos.x, (int) pos.y, (int) pos.z)));
            if(town.buildStarterPack()){
                LevelTowns.of(level).addTown(town);
                // Now we place the blocks.
                BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
                for(Build build: town.getBuilds()){
                    SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
                    for(BlockInfo block: schema.getBlocks()){
                        cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                        level.setBlock(cursor.move(block.pos()), block.state(), 2);
                    }
                    // TODO Do the same for the entities !
                }
                source.sendSuccess(() -> Component.literal("The town ")
                        .append(Component.literal(townName).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" was successfully generated !")), false);
            }else{
                source.sendSuccess(() -> Component.literal("The town ")
                        .append(Component.literal(townName).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" couldn't not be spawned because there wasn't enough free space.")), false);
            }
        } catch (CorruptedCultureException e) {
            source.sendSuccess(() -> Component.literal("The culture ")
                    .append(Component.literal(cultureId).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" doesn't exist.")), false);
        }
        return 1;
    }
}

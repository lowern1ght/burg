package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

public class TownCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandBuildContext context) {
        // /ouat town status is accessible by any player (no op required)
        dispatcher.register(
            Commands.literal("ouat")
                .then(Commands.literal("town")
                    .then(Commands.literal("status")
                        .executes(TownCommand::status)))
        );
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Town town = LevelTowns.get(level).getNearestTown(pos, 128).orElse(null);
        if (town == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No town within 128 blocks"));
            return 0;
        }

        int houses = 0, jobs = 0, gardens = 0, streets = 0;
        for (ConnectionPoint cp : town.getAvailableConnectionPoints()) {
            String pool = cp.targetName();
            if (pool.contains("houses"))        houses++;
            else if (pool.contains("jobs"))     jobs++;
            else if (pool.contains("gardens"))  gardens++;
            else if (pool.contains("streets"))  streets++;
        }

        String slot = " free slot";
        StringBuilder sb = new StringBuilder("[Village status]\n");
        sb.append("Houses  : ").append(houses).append(houses == 1 ? slot : slot + "s").append("\n");
        sb.append("Jobs    : ").append(jobs).append(jobs == 1 ? slot : slot + "s").append("\n");
        sb.append("Gardens : ").append(gardens).append(gardens == 1 ? slot : slot + "s").append("\n");
        sb.append("Streets : ").append(streets).append(streets == 1 ? slot : slot + "s");
        if (houses == 0 && jobs == 0 && gardens == 0 && streets == 0) {
            sb.append("\nNo connection points available -- the village cannot expand.");
        }

        String result = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}

package org.dawnoftime.onceuponatown.command;

import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

public class TownDebugCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("towndebug").executes(context -> listTowns(context.getSource()));
    }

    private static int listTowns(CommandSourceStack source) {
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
            var townInfo = Component.literal("Check in the logs the description of the closest town ")
                    .append(Component.literal(closestTown.getName()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" found in "))
                    .append((Component.literal(closestTown.getCenter().toShortString())).withStyle((style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + finalClosestTown.getCenter().getX() + " " + finalClosestTown.getCenter().getY() + " " + finalClosestTown.getCenter().getZ()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport"))))));
            source.sendSuccess(() -> townInfo, false);
            closestTown.printDescription();
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }
}

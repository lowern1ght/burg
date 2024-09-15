package com.dotteam.onceuponatown.command;

import com.dotteam.onceuponatown.town.Town;
import com.dotteam.onceuponatown.town.TownManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TownDebugCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("towndebug").executes(context -> listTowns(context.getSource()));
    }

    private static int listTowns(CommandSourceStack source) {
        Vec3 sourcePos = source.getPosition();
        List<Town> towns = TownManager.getTowns(source.getLevel());
        if (towns != null) {
            if (!towns.isEmpty()) {
                Town closestTown = null;
                double dist = -1;
                for (Town town : towns){
                    double newDist = town.getCenterPosition().distToCenterSqr(sourcePos);
                    if(dist == -1 || newDist < dist){
                        dist = newDist;
                        closestTown = town;
                    }
                }
                Town finalClosestTown = closestTown;
                var townInfo = Component.literal("Check in the logs the description of the closest town ")
                        .append(Component.literal(closestTown.getName()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" found in "))
                        .append((Component.literal(closestTown.getCenterPosition().toShortString())).withStyle((style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + finalClosestTown.getCenterPosition().getX() + " " + finalClosestTown.getCenterPosition().getY() + " " + finalClosestTown.getCenterPosition().getZ()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport"))))));
                source.sendSuccess(() -> townInfo, false);
                closestTown.townMap.print_description();
                closestTown.townMap.print_map();
            } else {
                source.sendSuccess(() -> Component.literal("No towns found"), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }
}

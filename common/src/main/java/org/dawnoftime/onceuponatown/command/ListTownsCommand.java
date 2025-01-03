package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Collection;

public class ListTownsCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("listtowns").executes(context -> listTowns(context.getSource()));
    }

    private static int listTowns(CommandSourceStack source) {
        LevelTowns manager = LevelTowns.of(source.getLevel());
        Collection<Town> towns = manager.getAllTowns();
        if (!towns.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Following towns found (" + towns.size() + ") :"), false);
            for (Town town : towns) {
                var townInfo = (Component.literal(town.getName()).withStyle(ChatFormatting.YELLOW))
                        .append((Component.literal(" at ")).withStyle(ChatFormatting.WHITE))
                        .append((Component.literal(town.getCenter().toShortString())).withStyle((style ->
                                style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + town.getCenter().getX() + " " + town.getCenter().getY() + " " + town.getCenter().getZ()))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport"))))));
                //.append((Component.literal(" | ID : " + town.getUuid())).withStyle(ChatFormatting.WHITE));
                source.sendSuccess(() -> townInfo, false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("No towns found"), false);
        }
        return 1;
    }
}

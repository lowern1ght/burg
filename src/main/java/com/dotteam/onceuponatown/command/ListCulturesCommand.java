package com.dotteam.onceuponatown.command;

import com.dotteam.onceuponatown.culture.Culture;
import com.dotteam.onceuponatown.culture.CultureManager;
import com.dotteam.onceuponatown.town.Town;
import com.dotteam.onceuponatown.town.TownManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.List;

public class ListCulturesCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("listcultures").executes(context -> listCultures(context.getSource()));
    }

    private static int listCultures(CommandSourceStack source) {
        List<Culture> cultures = CultureManager.getLoadedCultures();
        if (cultures != null && !cultures.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Following cultures found (" + cultures.size() + ") :"), false);
            for (Culture culture : cultures) {
                var townInfo = (Component.literal(culture.getId()).withStyle(ChatFormatting.YELLOW));
                source.sendSuccess(() -> townInfo, false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("No cultures found"), false);
        }
        return 1;
    }
}

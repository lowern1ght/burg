package org.dawnoftime.onceuponatown.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.command.CultureCommand;
import org.dawnoftime.onceuponatown.command.NpcCommand;
import org.dawnoftime.onceuponatown.command.TownCommand;

public class CommandRegistry {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(Ouat.MOD_ABBREVIATION)
                .then(TownCommand.register(context))
                .then(NpcCommand.register())
                .then(CultureCommand.register());
        dispatcher.register(builder);
    }
}

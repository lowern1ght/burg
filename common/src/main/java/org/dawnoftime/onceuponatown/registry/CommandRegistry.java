package org.dawnoftime.onceuponatown.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.command.CultureCommand;
import org.dawnoftime.onceuponatown.command.TownCommand;

public class CommandRegistry {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(Ouat.MOD_ABBREVIATION)
                .then(TownCommand.register())
                .then(CultureCommand.register());
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(builder);
    }
}

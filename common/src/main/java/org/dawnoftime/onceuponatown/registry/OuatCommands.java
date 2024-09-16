package org.dawnoftime.onceuponatown.registry;

import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.command.CultureInfoCommand;
import org.dawnoftime.onceuponatown.command.ListCulturesCommand;
import org.dawnoftime.onceuponatown.command.ListTownsCommand;
import org.dawnoftime.onceuponatown.command.TownDebugCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class OuatCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(Constants.MOD_ID)
                .then(CultureInfoCommand.register())
                .then(ListCulturesCommand.register())
                .then(ListTownsCommand.register())
                .then(TownDebugCommand.register());
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(builder);
        dispatcher.register(Commands.literal("ouat").redirect(node));
    }
}

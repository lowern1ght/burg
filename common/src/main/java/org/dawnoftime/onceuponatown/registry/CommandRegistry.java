package org.dawnoftime.onceuponatown.registry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.command.*;

public class CommandRegistry {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(Ouat.MOD_ID)
                .then(CultureInfoCommand.register())
                .then(ListCulturesCommand.register())
                .then(ListTownsCommand.register())
                .then(TownDebugCommand.register())
                .then(TownSpawnCommand.register())
                .then(TownAddBuildingCommand.register());
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(builder);
        dispatcher.register(Commands.literal("ouat").redirect(node));
    }
}

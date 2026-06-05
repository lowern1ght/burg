package org.dawnoftime.onceuponatown.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.Ouat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LocateCommand.class)
public class LocateCommandMixin {
    @Inject(method = "locateStructure", at = @At(value = "HEAD"))
    private static void notifyWrongVillageCommand(CommandSourceStack sourceStack, ResourceOrTagKeyArgument.Result<Structure> result, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        Optional<ResourceKey<Structure>> optional = result.unwrap().left();
        if (optional.isPresent()) {
            var disabledVillages = Config.getDisabledVillages();
            switch (optional.get().location().getPath()) {
                case "village_plains" -> {
                    if (disabledVillages.contains(optional.get())) {
                        throw new SimpleCommandExceptionType(getExceptionMsg("plains")).create();
                    }
                }
                case "village_desert" -> {
                    if (disabledVillages.contains(optional.get())) {
                        throw new SimpleCommandExceptionType(getExceptionMsg("desert")).create();
                    }
                }
                case "village_taiga" -> {
                    if (disabledVillages.contains(optional.get())) {
                        throw new SimpleCommandExceptionType(getExceptionMsg("taiga")).create();
                    }
                }
                case "village_snowy" -> {
                    if (disabledVillages.contains(optional.get())) {
                        throw new SimpleCommandExceptionType(getExceptionMsg("snowy")).create();
                    }
                }
                case "village_savanna" -> {
                    if (disabledVillages.contains(optional.get())) {
                        throw new SimpleCommandExceptionType(getExceptionMsg("savanna")).create();
                    }
                }
            }
        }
    }

    private static Component getExceptionMsg(String village) {
        String command = "/locate structure " + Ouat.MOD_ID + ":" + village + "_town";
        return Component.literal("Use ")
                .append(Component.literal(command)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to locate")))))
                .append(Component.literal(" instead."));
    }
}

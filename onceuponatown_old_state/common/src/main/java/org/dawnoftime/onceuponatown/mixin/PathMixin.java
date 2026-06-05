package org.dawnoftime.onceuponatown.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Target;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Mixin(Path.class)
public class PathMixin {
    @Shadow
    private Node[] openSet;
    @Shadow
    private Node[] closedSet;
    @Shadow
    private Set<Target> targetNodes;

    @Inject(method = "writeToStream", at = @At(value = "HEAD"))
    public void fixMethod(FriendlyByteBuf buffer, CallbackInfo ci) {
        List<Node> openList = new ArrayList<>();
        List<Node> closedList = new ArrayList<>();
        Path path = (Path) (Object) this;
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (node.closed) {
                closedList.add(node);
            } else {
                openList.add(node);
            }
        }
        openSet = openList.toArray(new Node[0]);
        closedSet = closedList.toArray(new Node[0]);
        targetNodes = Collections.singleton(new Target(0, 0, 0));
    }
}

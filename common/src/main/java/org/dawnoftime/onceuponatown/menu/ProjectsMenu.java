package org.dawnoftime.onceuponatown.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.dawnoftime.onceuponatown.town.Town;
import oshi.util.tuples.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectsMenu extends NpcMenu {
    private final List<Pair<String, ItemStack>> buildings = new ArrayList<>();
    private final List<Pair<String, ItemStack>> projects  = new ArrayList<>();

    public ProjectsMenu(int containerId, Inventory playerInventory, InteractingNpc npc) {
        super(MenuRegistry.REGISTRY.PROJECT_MENU.get(), containerId, npc, playerInventory.player);;
    }

    public ProjectsMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
            new InteractingNpcClient.Builder((Npc) (playerInventory.player.level().getEntity(buf.readInt())), playerInventory.player)
                .build());
        buildings.addAll((Collection<? extends Pair<String, ItemStack>>) buf.readCollection(ArrayList::new,
            buffer -> new Pair<>(buffer.readUtf(), buffer.readItem()))
        );
        projects.addAll((Collection<? extends Pair<String, ItemStack>>) buf.readCollection(ArrayList::new,
                buffer -> new Pair<>(buffer.readUtf(), buffer.readItem())));
    }

    public void startProject(String buildingId) {
        if (getNpc() instanceof Npc npcEntity) {
            Town town = npcEntity.getTown();
            if (town != null && town.getCulture().getBuildType(buildingId) instanceof BuildingType type) {
                town.createProject(type);
            }
        }
    }

    public List<Pair<String, ItemStack>> getBuildings() {
        return buildings;
    }

    public List<Pair<String, ItemStack>> getProjects() {
        return projects;
    }
}

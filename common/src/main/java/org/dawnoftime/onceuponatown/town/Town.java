package org.dawnoftime.onceuponatown.town;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.BuildProject;
import org.dawnoftime.onceuponatown.building.instance.Build;
import org.dawnoftime.onceuponatown.building.instance.Building;
import org.dawnoftime.onceuponatown.building.instance.Road;
import org.dawnoftime.onceuponatown.building.schematic.SchematicBlock;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.culture.Specialization;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.Profession;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class Town extends ProtoTown {
    private final ServerLevel level; // The Level this Town belongs to
    private final int id; // Unique Id
    private Component name; // Not unique name
    private final List<BuildProject> projects; // List of Builds under construction
    private final List<Citizen> citizens; // Citizens of this Town
    private final HashMap<Specialization, Integer> progression;
    private final TownInventory inventory;
    private long lastProductionHarvest; // Last time this Town collected resources from its producers
    private int experience;
    private long lastActive;
    // Unsaved attributes
    private final List<ServerPlayer> visitors = new ArrayList<>(); // Players in Town's boundaries
    private boolean active;
    private boolean rushProjects;

    private Town(
        Culture culture, // ProtoTown attributes
        BlockPos center,
        BlockPos NWCorner,
        BlockPos SECorner,
        List<BuildBud> buds,
        List<Build> builds,
        int buildsWeight,
        BiFunction<Integer, Integer, Integer> getSurfaceY,
        ServerLevel level, // Town attributes
        int id,
        Component name,
        List<BuildProject> projects,
        ListTag citizens,
        HashMap<Specialization, Integer> progression,
        TownInventory inventory,
        long lastProductionHarvest,
        int experience,
        long lastActive
    ) {
        super(culture, center, NWCorner, SECorner, buds, builds, buildsWeight, getSurfaceY);
        this.level = level;
        this.id = id;
        this.name = name;
        this.projects = projects;
        this.citizens = new ArrayList<>();
        this.progression = progression;
        this.inventory = inventory;
        this.lastProductionHarvest = lastProductionHarvest;
        this.experience = experience;
        this.lastActive = lastActive;
        // Common init regardless of how this Town was created (world gen, spawned by command, loaded by NBT)
        var buildings = getBuildings();
        for (Tag tag : citizens) {
            CompoundTag citizenTag = (CompoundTag) tag;
            this.citizens.add(new Citizen(
                Citizen.Status.valueOf(citizenTag.getString("Status")),
                Profession.of(citizenTag.getString("Profession")),
                citizenTag.hasUUID("UUID") ? citizenTag.getUUID("UUID") : null,
                buildings.stream().filter(building -> building.toSafeString().equals(citizenTag.getString("Residence"))).findFirst().orElse(null),
                buildings.stream().filter(building -> building.toSafeString().equals(citizenTag.getString("Workplace"))).findFirst().orElse(null)
            ));
        }
    }

    static Town createFromProtoTown(ServerLevel level, int id, CompoundTag protoTownTag) {
        Culture culture = ServerCultures.getCultureOrDefault(protoTownTag.getString("Culture"));
        Town town = new Town(
            culture,
            NbtUtils.readBlockPos(protoTownTag.getCompound("Center")),
            NbtUtils.readBlockPos(protoTownTag.getCompound("NWCorner")),
            NbtUtils.readBlockPos(protoTownTag.getCompound("SECorner")),
            protoTownTag.getList("BuildBuds", Tag.TAG_COMPOUND).stream().map(budTag -> new BuildBud((CompoundTag) budTag)).collect(Collectors.toList()),
            protoTownTag.getList("Builds", Tag.TAG_COMPOUND).stream().map(buildTag -> Build.load(culture, (CompoundTag) buildTag)).collect(Collectors.toList()),
            protoTownTag.getInt("BuildsWeight"),
            (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1,
            level,
            id,
            getRandomTownName(),
            new ArrayList<>(), // Empty ConstructionProjects
            new ListTag(), // Empty Citizens
            new HashMap<>(), // Empty progression
            new TownInventory(protoTownTag.getCompound("TownInventory")),
            level.getGameTime(), // lastProductionHarvest
            0, // experience
            0 // lastActive
        );
        town.afterSpawnInit();
        return town;
    }

    static Town loadNbt(ServerLevel level, CompoundTag townTag) {
        Culture culture = ServerCultures.getCultureOrDefault(townTag.getString("Culture"));
        Town town = new Town(
            culture,
            NbtUtils.readBlockPos(townTag.getCompound("Center")),
            NbtUtils.readBlockPos(townTag.getCompound("NWCorner")),
            NbtUtils.readBlockPos(townTag.getCompound("SECorner")),
            townTag.getList("BuildBuds", Tag.TAG_COMPOUND).stream().map(budTag -> new BuildBud((CompoundTag) budTag)).collect(Collectors.toList()),
            townTag.getList("Builds", Tag.TAG_COMPOUND).stream().map(buildTag -> Build.load(culture, (CompoundTag) buildTag)).collect(Collectors.toList()),
            townTag.getInt("BuildsWeight"),
            (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1,
            level,
            townTag.getInt("Id"),
            Component.Serializer.fromJson(townTag.getString("Name")),
            new ArrayList<>(),
            townTag.getList("Citizens", Tag.TAG_COMPOUND),
            new HashMap<>(), // TODO Implement progression
            new TownInventory(townTag.getCompound("TownInventory")),
            townTag.getLong("LastProductionHarvest"),
            townTag.getInt("Experience"),
            townTag.getLong("LastActive")
        );
        townTag.getList("Projects", Tag.TAG_COMPOUND).stream()
            .map(projectTag -> BuildProject.load(level, town, (CompoundTag) projectTag))
            .forEach(town.projects::add);
        return town;
    }

    static Town trySpawnAtPosition(Culture culture, ServerLevel level, int id, BlockPos center, @Nullable Component townName) {
        Town town = new Town(
            culture,
            center, // Center
            center, // Initial NWCorner
            center, // Initial SECorner
            new ArrayList<>(), // Empty BuildingBuds
            new ArrayList<>(), // Empty Builds
            0,
            (x, z) -> (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1),
            level,
            id,
            (townName != null) ? townName : getRandomTownName(),
            new ArrayList<>(), // Empty ConstructionProjects
            new ListTag(), // Empty Citizens
            new HashMap<>(), // Empty progression
            new TownInventory(),
            level.getGameTime(), // lastProductionHarvest
            0, // experience
            0 // lastActive
        );
        if (town.buildStarterPack()) {
            // Placing builds
            for (Build build : town.getBuilds()) {
                BuildProject project = new BuildProject(level, BuildProject.Type.NEW_BUILD, town, build);
                project.rush(true);
                town.projects.add(project);
            }
            /*
            BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
            for (Build build : town.getBuilds()) {
                SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
                for (SchematicBlock block : schema.getBlocks()) {
                    cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                    level.setBlock(cursor.move(block.pos()), block.state(), 2);
                    // broken return null;
                }
                // TODO Do the same for the entities !
            }
             */
            town.afterSpawnInit();
            town.rushProjects = true;
            return town;
        } else {
            return null;
        }
    }

    @Override
    public CompoundTag saveNbt() {
        CompoundTag tag = super.saveNbt();
        tag.putInt("Id", id);
        tag.putString("Name", Component.Serializer.toJson(name));
        // ConstructionProjects
        ListTag projectsTag = new ListTag();
        projects.forEach(project -> projectsTag.add(project.save(new CompoundTag())));
        tag.put("Projects", projectsTag);
        // Citizens
        ListTag citizensTag = new ListTag();
        citizens.forEach(citizen -> citizensTag.add(citizen.saveNbt()));
        tag.put("Citizens", citizensTag);
        // TODO Implement progression
        tag.put("TownInventory", inventory.save());
        tag.putLong("LastProductionHarvest", lastProductionHarvest);
        tag.putInt("Experience", experience);
        tag.putLong("LastActive", lastActive);
        return tag;
    }

    private void afterSpawnInit() {
        addCitizens();
    }

    private void addCitizens() {
        List<Citizen> citizenList = new ArrayList<>();
        List<Building> buildings = getBuildings();
        // Counting the number of dwelling slots
        int townTotalDwellingSlots = buildings.stream().mapToInt(Building::getTotalBeds).sum();
        // Adding the proper amount of Citizens
        for (int i = 0; i < townTotalDwellingSlots; i++) {
            citizenList.add(new Citizen(Citizen.Status.NOT_SPAWNED, Profession.UNEMPLOYED, null, null, null));
        }
        // Professions
        assigningJobs:
        for (Citizen citizen : citizenList) {
            for (Building building : buildings) {
                Profession available = building.getNextAvailableProfession();
                if (available != null) {
                    citizen.profession = available;
                    citizen.workplace = building;
                    building.addWorker(available);
                    continue assigningJobs;
                }
            }
        }
        // Special profession : Builder
        Citizen builder = citizenList.stream().filter(Citizen::isUnemployed).findFirst()
            .orElse(new Citizen(Citizen.Status.NOT_SPAWNED, Profession.UNEMPLOYED, null, null, null));
        builder.profession = Profession.BUILDER;
        if (!citizenList.contains(builder)) {
            citizenList.add(builder);
        }
        // Residences
        assigningHomes:
        for (Citizen citizen : citizenList) {
            // Prioritizing living at the workplace
            if (!citizen.isUnemployed()) {
                if (citizen.workplace != null && citizen.workplace.getFreeBeds() > 0) {
                    citizen.residence = citizen.workplace;
                    citizen.residence.addResident();
                    continue;
                }
            }
            for (Building building : buildings) {
                if (building.getFreeBeds() > 0) {
                    citizen.residence = building;
                    building.addResident();
                    continue assigningHomes;
                }
            }
            Ouat.error("Town %s (id:%s) failed to assign a home to citizen".formatted(name, id));
            // TODO what if unable to assign a home ?
        }
        citizens.addAll(citizenList);
    }

    private void updateStatus() {
        if (!active && !visitors.isEmpty()) {
            setActive();
        } else if (active && visitors.isEmpty()) {
            setInactive();
        }
    }

    private void setActive() {
        active = true;
        maybeCollectProduction();
    }

    private void setInactive() {
        active = false;
        lastActive = level.getGameTime();
    }

    void tick() {
        if (level.getServer().getTickCount() % (20 * Config.TOWN_TICK_RATE_SECONDS) == 0) {
            updateVisitors();
            updateStatus();
            if (active) {
                handleCitizens();
                //projects.stream().filter(BuildProject::isCompleted).toList().forEach(projects::remove);
            }
        }

        if (active && level.getServer().getTickCount() % 2 == 0) {
            List<BuildProject> rushingProjects = new ArrayList<>(projects.stream().filter(BuildProject::rushing).toList());
            for (BuildProject project : rushingProjects) {
                int attempt = 0;
                while (attempt < 10 &&
                    project.getNextAction() == BuildProject.Action.NOTHING ||
                    project.getNextAction() == BuildProject.Action.SPAWN_ENTITY
                ) {
                    ++attempt;
                    project.nextStep();
                }
                project.nextNSteps(4);
            }
        }
    }

    private void handleCitizens() {
        for (Citizen citizen : citizens) {
            switch (citizen.status) {
                case NOT_SPAWNED -> {
                    BlockPos pos;
                    if (citizen.residence != null) {
                        pos = citizen.residence.getOriginPos();
                        pos.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()));
                    } else {
                        pos = getCenter();
                    }
                    if (level.isLoaded(pos.above())) {
                        Npc npc = EntityRegistry.REGISTRY.NPC.get().create(level);
                        if (npc != null) {
                            npc.moveTo(pos.above(), 0.0F, 0.0F);
                            if (level.addFreshEntity(npc)) {
                                npc.setCultureId(culture.getId());
                                if (citizen.profession != null) {
                                    npc.setProfessionId(citizen.profession.getId());
                                } else {
                                    npc.setProfessionId("unemployed");
                                }
                                npc.assignTown(this);
                                citizen.entityUUID = npc.getUUID();
                                citizen.status = Citizen.Status.LOADED;
                            }
                        }
                    }
                }
            }
            if (citizen.entityUUID != null) {
                Entity entity = level.getEntity(citizen.entityUUID);
                if (entity instanceof Npc npc) {

                }
            }
        }
    }

    public boolean build(BuildingType buildingType, int startingLevel) {
        Building building = tryAddBuilding(buildingType, startingLevel);
        boolean success = building != null;
        if (success) {
            BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
            SchematicContent schema = building.getSchematicContent(level.getServer().getResourceManager());
            for (SchematicBlock block : schema.getBlocks()) {
                cursor.set(building.getOriginPos().getX(), building.getOriginPos().getY(), building.getOriginPos().getZ());
                level.setBlock(cursor.move(block.pos()), block.state(), 2);
            }
            building.setStatus(Build.Status.COMPLETED);
        }
        return success;
    }

    private void setForceLoaded(boolean forceLoad, int margin) {
        margin = Math.max(0, margin);
        BoundingBox box = townBox.inflatedBy(margin);
        int chunkXSpan = (int) Math.ceil(box.getXSpan() / 16.0D);
        int chunkZSpan = (int) Math.ceil(box.getZSpan() / 16.0D);
        int startX = level.getChunk(new BlockPos(box.minX(), box.minY(), box.minZ())).getPos().x;
        int startZ = level.getChunk(new BlockPos(box.minX(), box.minY(), box.minZ())).getPos().z;
        for (int x = startX; x <= startX + chunkXSpan; ++x) {
            for (int z = startZ; z <= startZ + chunkZSpan; ++z) {
                //Ouat.info((forceLoad ? "Forced" : "Freed") + "chunk at " + x + " " + z);
                level.setChunkForced(x, z, forceLoad);
            }
        }
    }

    public boolean demolish(Build build) {
        boolean success = removeBuild(build);
        if (success) {
            BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
            SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
            for (SchematicBlock block : schema.getBlocks()) {
                cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                // success &= broken
                level.destroyBlock(cursor.move(block.pos()), false);
            }
        }
        return success;
    }

    public boolean createProject(BuildingType buildingType) {
        Building building = tryAddBuilding(buildingType, 1);
        if (building != null) {
            BuildProject project = new BuildProject(level, BuildProject.Type.NEW_BUILD, this, building);
            projects.add(project);
            return true;
        }
        return false;
    }

    public boolean finishProject(String projectId) {
        for (BuildProject project : projects) {
            if (project.toSafeString().equals(projectId)) {
                project.rush(true);
                return true;
            }
        }
        return false;
    }

    public void notifyProjectCompleted(BuildProject project) {
        projects.remove(project);
    }

    public BuildProject getPendingProject() {
        return projects.stream().filter(project -> project.isAvailable() && !project.rushing()).findFirst().orElse(null);
    }

    private void maybeCollectProduction() {
        long now = level.getGameTime();
        long lastHarvest = lastProductionHarvest;
        int availableHarvests = (int) ((now - lastHarvest) / Config.PRODUCTION_HARVEST_RATE);
        if (availableHarvests > 0) {
            for (int i = 0; i < availableHarvests; ++i) {
                collectProduction();
            }
            lastProductionHarvest = now;
        }
    }

    void delete(boolean demolish) {
        //setForceLoaded(true, 64);
        for (Citizen citizen : citizens) {
            var uuid = citizen.entityUUID;
            if (uuid != null) {
                if (level.getEntity(citizen.entityUUID) instanceof Npc npc) {
                    npc.refreshAi(level);
                    npc.setProfessionId(Profession.UNEMPLOYED.getId());
                }
            }
        }
        if (demolish) {
            List<Build> concurrentSafe = new ArrayList<>(getBuilds());
            for (Build build : concurrentSafe) {
                demolish(build); // will remove Build from this.builds list
            }
        }
        //setForceLoaded(false, 64);
    }

    private void collectProduction() {

    }

    private void tryStartSurpriseRaid() {

    }

    public void ringTownBell(TownBellRingType ringType) {
    }

    private void updateVisitors() {
        final List<ServerPlayer> playersInTown = level.getPlayers(player -> playerInsideBoundaries(player, 0));
        List<ServerPlayer> leaving = new ArrayList<>(visitors);
        leaving.removeAll(playersInTown);
        leaving.forEach(this::byePlayer);
        List<ServerPlayer> entering = new ArrayList<>(playersInTown);
        entering.removeAll(visitors);
        entering.forEach(this::greetPlayer);
        visitors.removeAll(leaving);
        visitors.addAll(entering);
    }

    private boolean playerInsideBoundaries(Player player, int margin) {
        return townBox.inflatedBy(margin).isInside(player.blockPosition());
    }

    private void greetPlayer(ServerPlayer player) {
        Component greetings = Component.literal("Entering ").append(getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(greetings, true);
    }

    private void byePlayer(ServerPlayer player) {
        Component bye = Component.literal("Leaving ").append(getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(bye, true);
    }

    private static Component getRandomTownName() {
        return Component.literal(Ouat.translatable("town_name." + RANDOM.nextInt(1, 20)).getString());
    }

    public CompoundTag getTownMapData() {
        CompoundTag mapDataTag = new CompoundTag();
        mapDataTag.putString("Name", Component.Serializer.toJson(name));
        mapDataTag.put("NWCorner", NbtUtils.writeBlockPos(getNWCorner()));
        mapDataTag.put("SECorner", NbtUtils.writeBlockPos(getSECorner()));
        ListTag elementsTag = new ListTag();
        for (Build build : getBuilds()) {
            elementsTag.add(build.getGuiDescription());
        }
        for (BuildBud bud : getBuds()) {
            elementsTag.add(bud.getGuiDescription());
        }
        mapDataTag.put("Elements", elementsTag);
        return mapDataTag;
    }

    /**
     * Prints a description of this Town.
     */
    public void printConsoleDescription() {
        System.out.println("//---------------------------------------------- " + getName() + " [" + builds.size() + " Builds ] ---------------------------------------------//");
        System.out.println("UUID: " + id);
        System.out.println("Active : " + active);
        System.out.println("Last active : " + lastActive);
        System.out.println("Experience : " + experience);
        System.out.println("Citizens : ");
        for (Citizen citizen : citizens) {
            System.out.print("UUID : " + (citizen.entityUUID == null ? "none" : citizen.entityUUID));
            System.out.print(" | Status : " + (citizen.status == null ? "no status ??" : citizen.status));
            System.out.print(" | Profession : " + (citizen.profession == null ? "no profession ??" : citizen.profession.getId()));
            System.out.print(" | Residence : " + (citizen.residence == null ? "none" : citizen.residence.toSafeString()));
            System.out.println(" | Workplace : " + (citizen.workplace == null ? "none" : citizen.workplace.toSafeString()));
            System.out.println();
        }
        System.out.println("Builds weight : " + buildsWeight);
        System.out.println("Town Center: " + Utils.blockPosToString(getCenter()));
        System.out.println("Size: " + getTownMap()[0].length + "×" + getTownMap().length);
        System.out.println("North-West corner : " + Utils.blockPosToString(getNWCorner()));
        System.out.println("South-East corner : " + Utils.blockPosToString(getSECorner()));
        var buildings = getBuildings();
        System.out.println("Buildings (" + buildings.size() + ") :");
        for (Building building : buildings) {
            System.out.println("    - " + building.getBuildType().getId()
                + ", variant: " + building.getVariantId()
                + ", direction: " + building.getDirection().getName()
                + ", origin: " + Utils.blockPosToString(building.getOriginPos())
                + ", size: [" + building.getSizeX() + "×" + building.getSizeZ() + "]"
                + ", level: " + building.getLevel());
        }
        var roads = getRoads();
        System.out.println("Roads (" + roads.size() + ") :");
        for (Road road : roads) {
            System.out.println("    - " + road.getBuildType().getId()
                + ", direction: " + road.getDirection().getName()
                + ", origin: " + Utils.blockPosToString(road.getOriginPos())
                + ", size: [" + road.getSizeX() + "×" + road.getSizeZ() + "]"
                + ", level: " + road.getLevel());
        }
        var buds = getBuds();
        System.out.println("Buds (" + buds.size() + ") :");
        for (BuildBud buildBud : buds) {
            System.out.println("    - Bud [origin: " + Utils.blockPosToString(buildBud.getPosition()) + ", distance: " + buildBud.getSqrDistToTownCenter(getCenter()) + ", corner: " + buildBud.getCorner() + "]");
        }
        System.out.println("//------------------------------------------------------------------------------------------------------------------//");
    }

    public List<BuildProject> getProjects() {
        return projects;
    }

    public String getFancyId() {
        return "town_" + id;
    }

    public int getId() {
        return id;
    }

    public boolean setName(Component newName) {
        if (newName != null) {
            name = newName;
            return true;
        }
        return false;
    }

    public Component getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public enum TownBellRingType {
        DAWN,
        NOON,
        DUSK,
        RAID_ALERT,
        RAID_VICTORY,
        TOWN_COMPLETED,
        CHRISTMAS_EVENT
    }
}

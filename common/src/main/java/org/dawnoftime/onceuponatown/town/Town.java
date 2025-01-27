package org.dawnoftime.onceuponatown.town;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.Building;
import org.dawnoftime.onceuponatown.building.Road;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.construction.ConstructionProject;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.culture.Specialization;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.Profession;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class Town extends ProtoTown {
    private final ServerLevel level; // The Level this Town belongs to
    private final int id; // Unique Id
    private final String name; // Not unique name
    private final List<ConstructionProject> projects; // List of Builds under construction
    private final List<Citizen> citizens; // Citizens of this Town
    private final HashMap<Specialization, Integer> progression;
    private final TownInventory inventory;
    private long lastProductionHarvest; // Last time this Town collected resources from its producers
    private int experience;
    private long lastActive;
    // Unsaved attributes
    private final List<ServerPlayer> visitors = new ArrayList<>(); // Players in Town's boundaries
    private final CustomBossEvent townInfoBar; // Boss bar for displaying info
    private boolean active;

    private Town(Culture culture, // ProtoTown attributes
                 BlockPos center,
                 BlockPos NWCorner,
                 BlockPos SECorner,
                 List<BuildBud> buds,
                 List<Build> builds,
                 int buildsWeight,
                 BiFunction<Integer, Integer, Integer> getSurfaceY,
                 ServerLevel level, // Town attributes
                 int id,
                 String name,
                 List<ConstructionProject> projects,
                 List<Citizen> citizens,
                 HashMap<Specialization, Integer> progression,
                 TownInventory inventory,
                 long lastProductionHarvest,
                 int experience,
                 long lastActive) {
        super(culture, center, NWCorner, SECorner, buds, builds, buildsWeight, getSurfaceY);
        this.level = level;
        this.id = id;
        this.name = name;
        this.projects = projects;
        this.citizens = citizens;
        this.progression = progression;
        this.inventory = inventory;
        this.lastProductionHarvest = lastProductionHarvest;
        this.experience = experience;
        this.lastActive = lastActive;
        // Common init regardless of how this Town was created (world gen, spawned by command, loaded by NBT)
        CustomBossEvents bossEvents = level.getServer().getCustomBossEvents();
        String barId = (name + "_bar").replaceAll("\\s", "").toLowerCase();
        if (bossEvents.get(Ouat.modResource(barId)) == null) {
            this.townInfoBar = bossEvents.create(Ouat.modResource(barId), Component.literal(name).withStyle(ChatFormatting.WHITE));
            this.townInfoBar.setColor(BossEvent.BossBarColor.WHITE);
        } else {
            this.townInfoBar = bossEvents.get(Ouat.modResource(barId));
        }
        for (Citizen citizen : citizens) {
            // Reassign homes and workplaces
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
            new ArrayList<>(), // Empty Citizens
            new HashMap<>(), // Empty progression
            new TownInventory(),
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
            townTag.getString("Name"),
            townTag.getList("Projects", Tag.TAG_COMPOUND).stream().map(projectTag -> new ConstructionProject(level, (CompoundTag) projectTag)).collect(Collectors.toList()),
            new ArrayList<>(),
            //townTag.getList("Citizens", Tag.TAG_COMPOUND).stream().map(citizenTag -> new Citizen((CompoundTag) citizenTag)).collect(Collectors.toList()),
            new HashMap<>(), // TODO Implement progression
            new TownInventory(),
            townTag.getLong("LastProductionHarvest"),
            townTag.getInt("Experience"),
            townTag.getLong("LastActive")
        );
        return town;
    }

    static Town trySpawnAtPosition(Culture culture, ServerLevel level, int id, BlockPos center) {
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
            getRandomTownName(),
            new ArrayList<>(), // Empty ConstructionProjects
            new ArrayList<>(), // Empty Citizens
            new HashMap<>(), // Empty progression
            new TownInventory(),
            level.getGameTime(), // lastProductionHarvest
            0, // experience
            0 // lastActive
        );
        if (town.buildStarterPack()) {
            town.afterSpawnInit();
            return town;
        } else {
            return null;
        }
    }

    @Override
    public CompoundTag saveNbt() {
        CompoundTag tag = super.saveNbt();
        tag.putInt("Id", id);
        tag.putString("Name", name);
        // ConstructionProjects
        ListTag projectsTag = new ListTag();
        projects.forEach(project -> projectsTag.add(project.save(new CompoundTag())));
        tag.put("Projects", projectsTag);
        // Citizens
        //ListTag citizensTag = new ListTag();
        //citizens.forEach(citizen -> citizensTag.add(citizen.saveNbt()));
        // tag.put("Citizens", citizensTag);
        // TODO Implement progression
        tag.put("Inventory", inventory.writeNBT());
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
            citizenList.add(new Citizen(null, Citizen.Status.NOT_SPAWNED, Profession.UNEMPLOYED, null, null));
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
            .orElse(new Citizen(null, Citizen.Status.NOT_SPAWNED, null, null, null));
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
            // TODO what if unable to assign a home ?
        }
        citizens.addAll(citizenList);
    }

    public void updateStatus() {
        if (!active && !visitors.isEmpty()) {
            setActive();
        } else if (active && visitors.isEmpty()) {
            setInactive();
        }
    }

    private void setActive() {
        active = true;
        // Citizens
        freezeCitizens(false);
        // Builds
        maybeCollectProduction();
    }

    private void setInactive() {
        active = false;
        lastActive = level.getGameTime();
        // Citizens
        freezeCitizens(true);
        // Builds
    }

    public void tick() {
        updateVisitors();
        updateStatus();
        if (active) {
            handleCitizens();
        }
    }

    private void handleCitizens() {
        for (Citizen citizen : citizens) {
            switch (citizen.status) {
                case NOT_SPAWNED -> {
                    if (level.isLoaded(getCenter())) {
                        Npc npc = EntityRegistry.REGISTRY.NPC.get().create(level);
                        if (npc != null) {
                            npc.moveTo(getCenter().above(), 0.0F, 0.0F);
                            if (level.addFreshEntity(npc)) {
                                npc.setCultureId(culture.getId());
                                if (citizen.profession != null) {
                                    npc.setProfessionId(citizen.profession.getId());
                                    npc.setCustomName(Component.literal(Utils.capitalize(citizen.profession.getId())));
                                } else {
                                    npc.setProfessionId("unemployed");
                                    npc.setCustomName(Component.literal("Unemployed"));
                                }
                                citizen.entityUUID = npc.getUUID();
                                citizen.status = Citizen.Status.LOADED;
                            }
                        }
                    }
                }
            }
        }
    }

    private void freezeCitizens(boolean freeze) {
        for (Citizen citizen : citizens) {
            var uuid = citizen.entityUUID;
            if (uuid != null) {
                if (level.getEntity(citizen.entityUUID) instanceof Npc npc) {
                    npc.setInvulnerable(freeze);
                    npc.setNoAi(freeze);
                }
            }
        }
    }

    private boolean build(BuildingType buildingType) {
        boolean success = false;
        return success;
    }

    private void demolish(Build build) {
        BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
        SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
        for (BlockInfo block : schema.getBlocks()) {
            cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
            if (!level.destroyBlock(cursor.move(block.pos()), false)) {
                Ouat.error("Town %s has failed to properly demolish the build %s at %s".formatted(name, build, cursor));
            }
        }
    }

    private boolean addProject(BuildingType buildingType) {
        return false;
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

    void delete() {
        for (Citizen citizen : citizens) {
            var uuid = citizen.entityUUID;
            if (uuid != null) {
                if (level.getEntity(citizen.entityUUID) instanceof Npc npc) {
                    npc.setProfessionId(Profession.UNEMPLOYED.getId());
                    npc.clearAi();
                }
            }
        }
    }

    void deleteAndDemolish() {
        delete();
        for (Build build : getBuilds()) {
            demolish(build);
        }
    }

    private void collectProduction() {

    }

    private void tryStartSurpriseRaid() {

    }

    public void ringTownBell(TownBellRingType ringType) {
    }

    private void updateVisitors() {
        final List<ServerPlayer> playersInTown = level.getPlayers(player -> playerInsideBoundaries(player, 0));
        List<ServerPlayer> toBye = new ArrayList<>(visitors);
        toBye.removeAll(playersInTown);
        toBye.forEach(this::byePlayer);
        List<ServerPlayer> toGreet = new ArrayList<>(playersInTown);
        toGreet.removeAll(visitors);
        toGreet.forEach(this::greetPlayer);
        visitors.removeAll(toBye);
        visitors.addAll(toGreet);
    }

    private boolean playerInsideBoundaries(Player player, int margin) {
        return townBox.inflatedBy(margin).isInside(player.blockPosition());
    }

    private void greetPlayer(ServerPlayer player) {
        Component greetings = Component.literal("Entering " + getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(greetings, true);
        //townInfoBar.addPlayer(player);
    }

    private void byePlayer(ServerPlayer player) {
        Component bye = Component.literal("Leaving " + getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(bye, true);
        //townInfoBar.removePlayer(player);
    }

    private static String getRandomTownName() {
        return Ouat.translatable("town_name." + RANDOM.nextInt(1, 20)).getString();
    }

    public CompoundTag getTownMapDataForGui() {
        CompoundTag mapDataTag = new CompoundTag();
        mapDataTag.putString("TownName", getName());
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
    public void printDescription() {
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
        for (int y = 1; y <= 20; ++y) {
            level.setBlock(getNWCorner().above(y), Blocks.RED_WOOL.defaultBlockState(), 2);
        }
        System.out.println("South-East corner : " + Utils.blockPosToString(getSECorner()));
        for (int y = 1; y <= 20; ++y) {
            level.setBlock(getSECorner().above(y), Blocks.RED_WOOL.defaultBlockState(), 2);
        }
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

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
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

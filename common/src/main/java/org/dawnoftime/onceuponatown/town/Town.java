package org.dawnoftime.onceuponatown.town;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.NpcBuild;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.construction.ConstructionProject;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.culture.Specialization;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Town extends ProtoTown {
    public static final int ACTIVE_AREA_RADIUS = 80;
    public static final int PRODUCTION_HARVEST_RATE = SharedConstants.TICKS_PER_GAME_DAY;

    public final Level level;
    private final TownInventory inventory;
    private final List<UUID> npcs;
    private final List<ConstructionProject> constructionProjects;
    // The variables bellow are not saved.
    private long lastProductionHarvest;
    private HashMap<Integer, Specialization> developmentProgress;
    private int experience;
    private int buildingClutter;
    private CustomBossEvent townXpBar;
    private List<Player> visitors;
    private boolean active;
    private long lastActiveMoment;
    private List<NpcRecord> npcRecordList;

    /**
     * This constructor is used to create a Town instance from the Tag stored in NBT.
     *
     * @param level Level where the Town is located.
     * @param tag   CompoundTag that contains all the information regarding this Town.
     * @throws CorruptedCultureException if the corresponding culture could not be found.
     */
    public Town(Level level, CompoundTag tag) throws CorruptedCultureException {
        this(level, ServerCultures.getCultureOrDefault(tag.getString("Culture")), tag);
    }

    private Town(Level level, Culture culture, CompoundTag tag) throws CorruptedCultureException {
        super(
                tag.getUUID("UUID"),
                culture,
                tag.getString("Name"),
                NbtUtils.readBlockPos(tag.getCompound("Center")),
                NbtUtils.readBlockPos(tag.getCompound("NWCorner")).mutable(),
                NbtUtils.readBlockPos(tag.getCompound("SECorner")).mutable(),
                tag.getList("BuildBuds", Tag.TAG_COMPOUND).stream().map(budTag -> new BuildBud((CompoundTag) budTag)).collect(Collectors.toList()),
                tag.getList("Builds", Tag.TAG_COMPOUND).stream().map(buildTag -> NpcBuild.load(culture, (CompoundTag) buildTag)).collect(Collectors.toList()),
                (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
        );
        this.level = level;
        this.inventory = (tag.contains("Inventory")) ? new TownInventory(tag.getList("Inventory", Tag.TAG_COMPOUND)) : new TownInventory();
        this.npcs = new ArrayList<>();
        if (tag.contains("Npcs")) {
            ListTag npcsTag = tag.getList("Npcs", Tag.TAG_COMPOUND);
            for (Tag npcTag : npcsTag) {
                if (npcTag instanceof CompoundTag npcCompoundTag) {
                    this.npcs.add(npcCompoundTag.getUUID("UUID"));
                }
            }
        }
        this.constructionProjects = new ArrayList<>();
        this.init();
    }

    /**
     * Constructor used to create a Town directly in game, thought command for example.
     *
     * @param level   Level where the Town is located.
     * @param culture
     * @param name
     * @param center
     */
    public Town(Level level, Culture culture, String name, BlockPos center) {
        super(culture, name, center, (x, z) -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
        this.level = level;
        this.inventory = new TownInventory();
        this.npcs = new ArrayList<>();
        this.constructionProjects = new ArrayList<>();
        this.init();
    }

    /**
     * Function to run after Town instance creation or loading to create the associated Builds, NPCs, etc.
     */
    private void init() {
        this.createOrLoadXpBar();
        this.createBuildingsWorldGen();
        this.updateConstructionProject();
    }


    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.put("Inventory", this.inventory.writeNBT());
        ListTag npcsTag = new ListTag();
        for (UUID uuid : this.npcs) {
            CompoundTag npcTag = new CompoundTag();
            npcTag.putUUID("UUID", uuid);
            npcsTag.add(npcTag);
        }
        tag.put("Npcs", npcsTag);
        return tag;
    }

    private void createBuildingsWorldGen() {
        /*
        Iterate through the town map, read building types and create associated Building instances
        For each building, spawn npc dwellers if the associated bed is in a loaded chunk.
         */
    }

    private void updateConstructionProject() {

    }

    private void beginNewConstructionProject(BuildingType buildingType) {
        // ask town map for adding a building
        // town map add a MapBuilding to the map (marked not built) and return the MapBuilding
        // creates the ConstructionProject class using MapBuilding class
    }

    private void finishConstructionProject(BuildingType buildingType) {
        // notifies town map, mark the associated MapBuilding built
        // creates the Building instance using ConstructionProject
        // deletes ConstructionProject
    }

    private void abortConstructionProject(BuildingType buildingType) {
    }

    private void addBuildingInstantConstruction(BuildingType buildingType, int wantedLevel) {
        // ask town map for adding a building
        // town map add a MapBuilding to the map (marked built) and return the MapBuilding
        // creates the Building instance using MapBuilding class
    }

    private void createOrLoadXpBar() {
        CustomBossEvents customBossEvents = this.level.getServer().getCustomBossEvents();
        String barID = (getName() + "_bar").replaceAll("\\s", "").toLowerCase();
        if (customBossEvents.get(Ouat.modResource(barID)) == null) {
            Component barText = Component.literal(this.getName()).withStyle(ChatFormatting.WHITE);
            this.townXpBar = customBossEvents.create(Ouat.modResource(barID), barText);
            this.townXpBar.setColor(BossEvent.BossBarColor.WHITE);
        } else {
            this.townXpBar = customBossEvents.get(Ouat.modResource(barID));
        }
    }

    private void updateAfterInactivity() {
        long currentTime = this.level.getGameTime();
        // Move npcs
        // Update constructions
        // Collect building production;
        maybeCollectProduction();
    }

    private void maybeCollectProduction() {
        long now = this.level.getGameTime();
        long lastHarvest = this.lastProductionHarvest;
        if (now >= lastHarvest + PRODUCTION_HARVEST_RATE) {
            int availableHarvests = (int) ((now - lastHarvest) / PRODUCTION_HARVEST_RATE);
            for (int i = 0; i < availableHarvests; ++i) {
                collectProduction();
            }
            this.lastProductionHarvest = this.level.getGameTime();
        }
    }

    void unregister() {

    }

    void destroy() {
        var builds = getBuilds();
        for (NpcBuild build : builds) {
            BlockPos.MutableBlockPos cursor = new BlockPos(0, 0, 0).mutable();
            SchematicContent schema = build.getSchematicContent(level.getServer().getResourceManager());
            for (BlockInfo block : schema.getBlocks()) {
                cursor.set(build.getOriginPos().getX(), build.getOriginPos().getY(), build.getOriginPos().getZ());
                level.destroyBlock(cursor.move(block.pos()),false);
            }
        }
    }

    private void collectProduction() {

    }

    private void handleAllUnloadedNpc() {
        List<Npc> allUnloadedNpc = new ArrayList<>();
        for (UUID npcUUID : this.npcs) {
            if (this.level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(npcUUID);
                if (entity instanceof Npc npc && !serverLevel.isLoaded(npc.blockPosition())) {
                    allUnloadedNpc.add(npc);
                }
            }
        }
        for (Npc npc : allUnloadedNpc) {
            npc.sendSystemMessage(Component.literal("I am unloaded !"));
        }
    }

    public void tick() {
        // updateVisitors();
        //updateStatus();
        // If no construction project, creates one
        if (this.active) {
            //this.level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Ticking \"" + getName() + "\" in ACTIVE mode"), true);
            //maybeCollectProduction();
            //handleUnloadedNpcs();
            tryStartSurpriseRaid();
        }
    }

    private void tryStartSurpriseRaid() {

    }

    private void spawnFireworksAt(BlockPos blockPos) {

    }

    public void ringTownBell(TownBellRingType ringType) {
        // If the town has a bell, plays the desired sound at bell location
    }

    private void updateVisitors() {
        List<Player> oldPlayers = this.visitors;
        List<Player> newPlayers = this.level.getNearbyPlayers(TargetingConditions.forNonCombat(), null, AABB.ofSize(this.getCenter().getCenter(), 160, 160, 160));

        List<Player> arrivingPlayers = new ArrayList<>();
        List<Player> leavingPlayers = new ArrayList<>();

        if (oldPlayers != null) {
            for (Player player : oldPlayers) {
                if (!newPlayers.contains(player)) {
                    leavingPlayers.add(player);
                }
            }
            leavingPlayers.forEach(this::onPlayerLeavesTown);

            for (Player player : newPlayers) {
                if (!oldPlayers.contains(player)) {
                    arrivingPlayers.add(player);
                }
            }
            arrivingPlayers.forEach(this::onPlayerEntersTown);
        }
        this.visitors = newPlayers;
    }

    private void onPlayerEntersTown(Player player) {
        // Player has already been added the town player list
        Component component = Component.literal("Entering " + getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(component, true);
        this.townXpBar.addPlayer(this.level.getServer().getPlayerList().getPlayer(player.getUUID()));
    }

    private void onPlayerLeavesTown(Player player) {
        // Player has already been removed from the town player list
        Component component = Component.literal("Leaving " + getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(component, true);
        this.townXpBar.removePlayer(this.level.getServer().getPlayerList().getPlayer(player.getUUID()));
    }

    public void updateStatus() {
        boolean hasVisitors = !this.visitors.isEmpty();
        if (this.active && !hasVisitors) {
            setInactive();
        } else if (!this.active && hasVisitors) {
            setActive();
        }
    }

    public void setActive() {
        this.active = true;
        updateAfterInactivity();
    }

    public void setInactive() {
        this.active = false;
        this.lastActiveMoment = this.level.getGameTime();
    }

    public ConstructionProject getCurrentConstructionProject() {
        return null;
    }

    public boolean isActive() {
        return this.active;
    }

    private enum NpcStatus {
        NOT_SPAWNED,
        LOADED,
        UNLOADED,
        DEAD,
        MISSING
    }

    private record NpcRecord(UUID entityUUID, NpcStatus status) {
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

package org.dawnoftime.onceuponatown.town;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.construction.ConstructionProject;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.culture.Orientation;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Town extends ProtoTown {
    public static final int ACTIVE_AREA_RADIUS = 80;
    public static final int PRODUCTION_HARVEST_RATE = SharedConstants.TICKS_PER_GAME_DAY;

    public final Level level;
    private HashMap<Integer, Orientation> developmentProgress;
    private int experience;
    private int buildingClutter;
    private final TownInventory inventory;
    private final List<UUID> npcs;
    private long lastProductionHarvest;
    private final List<ConstructionProject> constructionProjects;
    private CustomBossEvent townXpBar;
    private List<Player> visitors;
    private boolean active;
    private long lastActiveMoment;
    private List<NpcRecord> npcRecordList;

    private Town(UUID uuid, Level level, Culture culture, String name, BlockPos townCenter, TownInventory townInventory, List<UUID> npcs, List<ConstructionProject> constructionProjects) {
        super(culture, name, townCenter);
        this.level = level;
        this.inventory = townInventory;
        this.npcs = npcs;
        this.constructionProjects = constructionProjects;
        createOrLoadXpBar();
    }

    public Town(CompoundTag tag) throws CorruptedCultureException {
        super(
                tag.getUUID("UUID"),
                CultureManager.getCultureById(tag.getString("Culture")),
                tag.getString("Name"),
                NbtUtils.readBlockPos(tag.getCompound("Center")),
                NbtUtils.readBlockPos(tag.getCompound("NWCorner")).mutable(),
                NbtUtils.readBlockPos(tag.getCompound("SECorner")).mutable());

    }

    public static Town createWorldGenOld(Level level, Culture culture, String name, TownMap townMap) {;
        TownInventory townInventory = new TownInventory();
        Town town = new Town(Mth.createInsecureUUID(RandomSource.create()), level, culture, name, townMap.getCenter(), townMap, townInventory,  new ArrayList<>(),  new ArrayList<>());
        town.createBuildingsWorldGen(townMap);
        town.updateConstructionProject();
        return town;
    }

    /*
     * Creates a new instance of Town from the NBT data saved during world generation.
     * @param level Level in which the Town was generated.
     * @param tag NBT component that contains the raw information.
     * @return The new instance of Town.
     */
    /*
    public static Town createFromWorldGen(Level level, CompoundTag tag) {;
        TownInventory townInventory = new TownInventory();
        Town town = new Town(Mth.createInsecureUUID(RandomSource.create()), level, culture, name, townMap.getCenter(), townMap, townInventory,  new ArrayList<>(),  new ArrayList<>(),  new ArrayList<>());
        town.createBuildingsWorldGen(townMap);
        town.updateConstructionProject();
        return town;
    }
     */

    public static Town createFromCommand(Level level, Culture culture, String name) {
        return null;
    }

    private void createBuildingsWorldGen(TownMap townMap) {
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


    static Town readNBT(Level level, CompoundTag tag) {
        List<Build> builds = new ArrayList<>();
        List<ConstructionProject> constructionProjects = new ArrayList<>();
        List<UUID> npcs = new ArrayList<>();

        UUID uuid = tag.getUUID("UUID");
        //Culture culture = Cultures.PLAINS;
        String name = tag.getString("Name");
        BlockPos townCenter = NbtUtils.readBlockPos(tag.getCompound("Position"));
        TownInventory inventory = new TownInventory(tag.getCompound("TownInventory"));
        ListTag npcsTag = tag.getList("Npcs", 10);
        for (int i = 0; i < npcsTag.size(); ++i) {
            CompoundTag npcTag = npcsTag.getCompound(i);
            npcs.add(npcTag.getUUID("UUID"));
            /*
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(npcTag.getUUID("UUID"));
                if (entity instanceof Npc npc) {
                    npcs.add(npc);
                }
            }*/
        }
        return null;
        //return new Town(uuid, level, culture, name, townCenter, null, inventory, npcs, buildings, constructionProjects);
    }

    private void createOrLoadXpBar() {
        CustomBossEvents customBossEvents = this.level.getServer().getCustomBossEvents();
        String barID = (getName() + "_bar").replaceAll("\\s","").toLowerCase();
        if (customBossEvents.get(Ouat.createOuatResource(barID)) == null) {
            Component barText = Component.literal(this.getName()).withStyle(ChatFormatting.WHITE);
            this.townXpBar = customBossEvents.create(Ouat.createOuatResource(barID), barText);
            this.townXpBar.setColor(BossEvent.BossBarColor.WHITE);
        } else {
            this.townXpBar = customBossEvents.get(Ouat.createOuatResource(barID));
        }
    }

    @Override
    public void writeNBT(CompoundTag tag) {
        super.writeNBT(tag);
        this.inventory.saveNBT(tag);
        ListTag npcsTag = new ListTag();
        for (UUID uuid : this.npcs) {
            CompoundTag npcTag = new CompoundTag();
            npcTag.putUUID("UUID", uuid);
            npcsTag.add(npcTag);
        }
        tag.put("Npcs", npcsTag);
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
            int availableHarvests = (int)((now - lastHarvest) / PRODUCTION_HARVEST_RATE);
            for (int i = 0; i < availableHarvests; ++i) {
                collectProduction();
            }
            this.lastProductionHarvest = this.level.getGameTime();
        }
    }

    void softDelete() {

    }

    void hardDelete() {

    }

    private void collectProduction() {
        for (Build<BuildType> build : this.getBuilds()) {
            HashMap<ResourceLocation, Integer> production = build.getProduction();
            //production.forEach(this.inventory::add);
        }
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

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return this.name;
    }

    private enum NpcStatus {
        NOT_SPAWNED,
        LOADED,
        UNLOADED,
        DEAD,
        MISSING
    }
    private record NpcRecord(UUID entityUUID, NpcStatus status) {}
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

package com.dotteam.onceuponatown.town;

import com.dotteam.onceuponatown.building.Building;
import com.dotteam.onceuponatown.construction.project.ConstructionProject;
import com.dotteam.onceuponatown.culture.BuildingType;
import com.dotteam.onceuponatown.culture.Culture;
import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.town.map.TownMap;
import com.dotteam.onceuponatown.util.OuatUtils;
import net.minecraft.ChatFormatting;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Town {
    private final UUID uuid;
    public final Level level;
    private final Culture culture;
    private final String name;
    private final BlockPos centerPosition;
    public TownMap townMap;
    private final TownInventory inventory;
    private final List<UUID> citizens;
    private final List<Building> buildings;
    public static final long BUILDING_PRODUCTION_HARVEST_INTERVAL = 24000L;
    private long lastProductionHarvest;
    private final List<ConstructionProject> constructionProjects;
    private CustomBossEvent townXpBar;
    private List<Player> visitors;
    public static int ACTIVE_AREA_RADIUS = 80;
    private boolean active;
    private long lastActiveMoment;
    private enum CitizenStatus {NOT_SPAWNED, LOADED, UNLOADED, DEAD, MISSING}
    private List<CitizenRecord> citizenRecordList;
    private record CitizenRecord(UUID entityUUID, CitizenStatus status) {}
    public enum TownBellRingType {DAWN_TIME, NOON_TIME, DUSK_TIME, RAID_ALERT, RAID_CELEBRATE_VICTORY, TOWN_COMPLETED, CHRISTMAS_EVENT}

    private Town(UUID uuid, Level level, Culture culture, String name, BlockPos townCenter, TownMap townMap, TownInventory townInventory, List<UUID> citizens, List<Building> buildings, List<ConstructionProject> constructionProjects) {
        this.uuid = uuid;
        this.level = level;
        this.culture = culture;
        this.name = name;
        this.centerPosition = townCenter;
        this.townMap = townMap;
        this.inventory = townInventory;
        this.citizens = citizens;
        this.buildings = buildings;
        this.constructionProjects = constructionProjects;
        createOrLoadXpBar();
    }

    static Town createWorldGen(Level level, Culture culture, String name, TownMap townMap) {;
        TownInventory townInventory = new TownInventory();
        Town town = new Town(Mth.createInsecureUUID(RandomSource.create()), level, culture, name, townMap.getCenter(), townMap, townInventory,  new ArrayList<>(),  new ArrayList<>(),  new ArrayList<>());
        town.createBuildingsWorldGen(townMap);
        town.updateConstructionProject();
        return town;
    }

    private void createBuildingsWorldGen(TownMap townMap) {
        /*
        Iterate through the town map, read building types and create associated Building instances
        For each building, spawn citizen dwellers if the associated bed is in a loaded chunk.
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


    static Town load(Level level, CompoundTag tag) {
        List<Building> buildings = new ArrayList<>();
        List<ConstructionProject> constructionProjects = new ArrayList<>();
        List<UUID> citizens = new ArrayList<>();

        UUID uuid = tag.getUUID("UUID");
        //Culture culture = Cultures.PLAINS;
        String name = tag.getString("Name");
        BlockPos townCenter = NbtUtils.readBlockPos(tag.getCompound("Position"));
        TownInventory inventory = new TownInventory(tag.getCompound("TownInventory"));
        ListTag citizensTag = tag.getList("Citizens", 10);
        for (int i = 0; i < citizensTag.size(); ++i) {
            CompoundTag citizenTag = citizensTag.getCompound(i);
            citizens.add(citizenTag.getUUID("UUID"));
            /*
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(citizenTag.getUUID("UUID"));
                if (entity instanceof Citizen citizen) {
                    citizens.add(citizen);
                }
            }*/
        }
        return null;
        //return new Town(uuid, level, culture, name, townCenter, null, inventory, citizens, buildings, constructionProjects);
    }

    private void createOrLoadXpBar() {
        CustomBossEvents customBossEvents = this.level.getServer().getCustomBossEvents();
        String barID = (getName() + "_bar").replaceAll("\\s","").toLowerCase();
        if (customBossEvents.get(OuatUtils.resource(barID)) == null) {
            Component barText = Component.literal(this.name).withStyle(ChatFormatting.WHITE);
            this.townXpBar = customBossEvents.create(OuatUtils.resource(barID), barText);
            this.townXpBar.setColor(BossEvent.BossBarColor.WHITE);
        } else {
            this.townXpBar = customBossEvents.get(OuatUtils.resource(barID));
        }
    }

    public void save(CompoundTag tag) {
        tag.putUUID("UUID", this.uuid);
        tag.putString("Culture", this.culture.getId());
        tag.putString("Name", this.name);
        tag.put("Position", NbtUtils.writeBlockPos(this.centerPosition));
        this.inventory.saveNBT(tag);
        ListTag citizensTag = new ListTag();
        for (UUID uuid : this.citizens) {
            CompoundTag citizenTag = new CompoundTag();
            citizenTag.putUUID("UUID", uuid);
            citizensTag.add(citizenTag);
        }
        tag.put("Citizens", citizensTag);
    }

    public void addBuilding(Building building) {
        this.buildings.add(building);
    }

    private void updateAfterInactivity() {
        long currentTime = this.level.getGameTime();
        // Move citizens
        // Update constructions
        // Collect building production;
        maybeCollectProduction();
    }

    private void maybeCollectProduction() {
        long now = this.level.getGameTime();
        long lastHarvest = this.lastProductionHarvest;
        if (now >= lastHarvest + BUILDING_PRODUCTION_HARVEST_INTERVAL) {
            int availableHarvests = (int)((now - lastHarvest) / BUILDING_PRODUCTION_HARVEST_INTERVAL);
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
        for (Building building : this.buildings) {
            HashMap<ResourceLocation, Integer> production = building.getProduction();
            //production.forEach(this.inventory::add);
        }
    }

    private void handleUnloadedCitizens() {
        List<Citizen> unloadedCitizens = new ArrayList<>();
        for (UUID citizenUUID : this.citizens) {
            if (this.level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(citizenUUID);
                if (entity instanceof Citizen citizen && !serverLevel.isLoaded(citizen.blockPosition())) {
                    unloadedCitizens.add(citizen);
                }
            }
        }
        for (Citizen citizen : unloadedCitizens) {
            citizen.sendSystemMessage(Component.literal("I am unloaded !"));
        }
    }

    public void tick() {
       // updateVisitors();
        //updateStatus();
        // If no construction project, creates one
        if (this.active) {
            //this.level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Ticking \"" + getName() + "\" in ACTIVE mode"), true);
            //maybeCollectProduction();
            //handleUnloadedCitizens();
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
        List<Player> newPlayers = this.level.getNearbyPlayers(TargetingConditions.forNonCombat(), null, AABB.ofSize(centerPosition.getCenter(), 160, 160, 160));

        List<Player> arrivingPlayers = new ArrayList<>();
        List<Player> leavingPlayers = new ArrayList<>();

        if (oldPlayers != null) {
            for (Player player : oldPlayers) {
                if (!newPlayers.contains(player)) {
                    leavingPlayers.add(player);
                }
            }
            leavingPlayers.forEach(this::onPlayerLeavestown);

            for (Player player : newPlayers) {
                if (!oldPlayers.contains(player)) {
                    arrivingPlayers.add(player);
                }
            }
            arrivingPlayers.forEach(this::onPlayerEnterstown);
        }
        this.visitors = newPlayers;
    }

    private void onPlayerEnterstown(Player player) {
        // Player has already been added the town player list
        Component component = Component.literal("Entering " + getName()).withStyle(ChatFormatting.YELLOW);
        player.displayClientMessage(component, true);
        this.townXpBar.addPlayer(this.level.getServer().getPlayerList().getPlayer(player.getUUID()));
    }

    private void onPlayerLeavestown(Player player) {
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

    public boolean isActive() {
        return this.active;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return this.name;
    }

    public BlockPos getCenterPosition() {
        return this.centerPosition;
    }
}

package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.BehaviorEngine;
import org.lowern1ght.burg.behavior.DiplomaticAIDriver;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticAI;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticRegistry;
import org.lowern1ght.burg.behavior.diplomacy.DiplomaticStatus;
import org.lowern1ght.burg.behavior.intent.BuildIntent;
import org.lowern1ght.burg.behavior.intent.IntentCost;
import org.lowern1ght.burg.behavior.intent.IntentScheduler;
import org.lowern1ght.burg.behavior.intent.NpcSupplier;
import org.lowern1ght.burg.behavior.intent.TradeIntent;
import org.lowern1ght.burg.behavior.morale.MoraleState;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.behavior.role.RoleAssigner;
import org.lowern1ght.burg.behavior.task.BuildTask;
import org.lowern1ght.burg.behavior.task.CitizenTask;
import org.lowern1ght.burg.behavior.task.IdleTask;
import org.lowern1ght.burg.behavior.task.TaskQueue;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.registry.EntityRegistry;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class WireUpGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(WireUpGameTest.class);
    private static final ResourceLocation SETTLEMENT =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "settlement");
    private static final ResourceLocation TRADE_JOB =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "wire_up_trade");

    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void intentFilteredByRole_builderNotAssignedToIdler(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
        Npc idler = spawnCitizen(level, anchor);
        Npc builder = spawnCitizen(level, anchor.east());
        Town town = townWithCitizens("RoleFilter", idler, builder);
        town.setBuilderNpcIdAtSlot(0, builder.getUUID());

        RoleAssigner roleAssigner = new RoleAssigner();
        roleAssigner.assignManually(idler.getUUID(), CitizenRole.IDLE);
        roleAssigner.assignManually(builder.getUUID(), CitizenRole.BUILDER);
        NpcSupplier supplier = new ListNpcSupplier(town, List.of(idler, builder));
        IntentScheduler scheduler = new IntentScheduler(supplier, roleAssigner);
        BehaviorEngine engine = new BehaviorEngine(
            scheduler,
            new TaskQueue(),
            supplier,
            roleAssigner,
            new MoraleState(),
            new DiplomaticRegistry(),
            new DiplomaticAI()
        );
        FakeBuildExecutor executor = new FakeBuildExecutor();
        BehaviorEngine.register(executor, supplier);

        try {
            scheduler.enqueue(new BuildIntent(SETTLEMENT, town, 10, IntentCost.empty(), Town.Zone.CORE));
            engine.onServerTick(level, level.getGameTime());

            CitizenTask builderTask = engine.tasks().currentTaskForId(builder.getUUID()).orElse(null);
            helper.assertTrue(builderTask instanceof BuildTask,
                "the BUILDER receives the BuildTask");
            helper.assertTrue(engine.tasks().currentTaskForId(idler.getUUID()).isEmpty(),
                "the IDLE citizen remains unassigned");
        } finally {
            BehaviorEngine.register(null, null);
        }
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void intentWithoutRequiredRoles_acceptsAnyRole(GameTestHelper helper) {
        TradeIntent intent = new TradeIntent(TRADE_JOB, new Town(), 1, IntentCost.empty());

        helper.assertTrue(intent.requiredRoles().isEmpty(),
            "TradeIntent keeps TownIntent's empty-set any-role default");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void moraleMultiplier_highMoraleFaster(GameTestHelper helper) {
        Npc citizen = spawnCitizen(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        MoraleState morale = new MoraleState();
        morale.set(citizen.getUUID(), 90);
        float multiplier = new IdleTask(citizen).moraleMultiplier(morale, citizen);
        LOGGER.info("[BEHAVIOR-TEST] morale 90 multiplier {}", multiplier);

        helper.assertTrue(multiplier > 1.0f, "90 morale is faster than neutral");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void moraleMultiplier_lowMoraleSlower(GameTestHelper helper) {
        Npc citizen = spawnCitizen(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        MoraleState morale = new MoraleState();
        morale.set(citizen.getUUID(), 10);
        float multiplier = new IdleTask(citizen).moraleMultiplier(morale, citizen);
        LOGGER.info("[BEHAVIOR-TEST] morale 10 multiplier {}", multiplier);

        helper.assertTrue(multiplier < 1.0f, "10 morale is slower than neutral");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "behavior")
    public static void moraleMultiplier_neutralReturnsOne(GameTestHelper helper) {
        Npc citizen = spawnCitizen(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        MoraleState morale = new MoraleState();
        morale.set(citizen.getUUID(), 50);
        float multiplier = new IdleTask(citizen).moraleMultiplier(morale, citizen);
        LOGGER.info("[BEHAVIOR-TEST] morale 50 multiplier {}", multiplier);

        helper.assertTrue(Float.compare(multiplier, 1.0f) == 0,
            "50 morale returns exactly 1.0");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void diplomaticAIDriver_declaresWarOnMoraleAdvantage(GameTestHelper helper) {
        DiplomacyFixture fixture = diplomacyFixture(helper, "WarAdvantage", 80, 40);
        DiplomaticRegistry registry = new DiplomaticRegistry();
        DiplomaticAIDriver driver = new DiplomaticAIDriver(
            registry, new DiplomaticAI(), fixture.morale());

        runDiplomacyInterval(driver, helper.getLevel());

        helper.assertTrue(registry.between(fixture.first(), fixture.second()).status()
                == DiplomaticStatus.AT_WAR,
            "a 40-point morale advantage declares war");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void diplomaticAIDriver_noActionWhenMoraleEqual(GameTestHelper helper) {
        DiplomacyFixture fixture = diplomacyFixture(helper, "EqualMorale", 50, 50);
        DiplomaticRegistry registry = new DiplomaticRegistry();
        DiplomaticAIDriver driver = new DiplomaticAIDriver(
            registry, new DiplomaticAI(), fixture.morale());

        runDiplomacyInterval(driver, helper.getLevel());

        helper.assertTrue(registry.between(fixture.first(), fixture.second()).status()
                == DiplomaticStatus.NEUTRAL,
            "equal morale leaves the relation neutral");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 80, batch = "behavior")
    public static void diplomaticAIDriver_skipsAlreadyAtWar(GameTestHelper helper) {
        DiplomacyFixture fixture = diplomacyFixture(helper, "AlreadyAtWar", 80, 40);
        DiplomaticRegistry registry = new DiplomaticRegistry();
        registry.declareWar(fixture.first(), fixture.second());
        DiplomaticAIDriver driver = new DiplomaticAIDriver(
            registry, new DiplomaticAI(), fixture.morale());

        runDiplomacyInterval(driver, helper.getLevel());

        helper.assertTrue(registry.between(fixture.first(), fixture.second()).status()
                == DiplomaticStatus.AT_WAR,
            "an existing war remains unchanged");
        helper.succeed();
    }

    private static DiplomacyFixture diplomacyFixture(GameTestHelper helper, String name,
                                                       int firstMorale, int secondMorale) {
        ServerLevel level = helper.getLevel();
        BlockPos firstAnchor = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos secondAnchor = helper.absolutePos(new BlockPos(4, 1, 4));
        Npc firstCitizen = spawnCitizen(level, firstAnchor);
        Npc secondCitizen = spawnCitizen(level, secondAnchor);
        Town first = townWithCitizens(name + "A", firstCitizen);
        Town second = townWithCitizens(name + "B", secondCitizen);
        LevelTowns.get(level).registerTown(firstAnchor, first);
        LevelTowns.get(level).registerTown(secondAnchor, second);

        MoraleState morale = new MoraleState();
        morale.set(firstCitizen.getUUID(), firstMorale);
        morale.set(secondCitizen.getUUID(), secondMorale);
        return new DiplomacyFixture(first, second, morale);
    }

    private static Town townWithCitizens(String name, Npc... citizens) {
        Town town = new Town();
        town.setName(name);
        for (Npc citizen : citizens) town.addResident(citizen.getUUID());
        return town;
    }

    private static Npc spawnCitizen(ServerLevel level, BlockPos position) {
        Npc citizen = EntityRegistry.NPC.create(level);
        citizen.setPersistenceRequired();
        citizen.moveTo(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5);
        level.addFreshEntity(citizen);
        return citizen;
    }

    private static void runDiplomacyInterval(DiplomaticAIDriver driver, ServerLevel level) {
        for (int tick = 0; tick < 600; tick++) driver.onServerTick(level);
    }

    private record DiplomacyFixture(Town first, Town second, MoraleState morale) {}

    private record ListNpcSupplier(Town target, List<Npc> citizens) implements NpcSupplier {
        @Override
        public List<Npc> freeCitizens(Town town) {
            return town == target ? citizens : List.of();
        }

        @Override
        public Optional<Npc> findByUuid(UUID id) {
            return citizens.stream().filter(citizen -> citizen.getUUID().equals(id)).findFirst();
        }
    }
}

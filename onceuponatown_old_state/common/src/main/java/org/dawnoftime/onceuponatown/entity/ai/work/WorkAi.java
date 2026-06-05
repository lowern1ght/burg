package org.dawnoftime.onceuponatown.entity.ai.work;

public enum WorkAi {
    /* Special */
    EMPTY(WorkTask.NOTHING),

    /* Producers */
    LUMBERJACK(WorkTask.NOTHING),
    QUARRYMAN(WorkTask.NOTHING),
    MINER(WorkTask.NOTHING),
    FARMER(WorkTask.NOTHING),
    COWMAN(WorkTask.NOTHING),
    SHEPHERD(WorkTask.NOTHING),
    PIG_FARMER(WorkTask.NOTHING),
    POULTRY_FARMER(WorkTask.NOTHING),
    FISHERMAN(Fishing::create),
    SUGARCANE_FARMER(WorkTask.NOTHING),
    BEEKEEPER(WorkTask.NOTHING),
    HUNTER(WorkTask.NOTHING),
    MONSTER_HUNTER(WorkTask.NOTHING),

    /* Craftsmen */
    CARPENTER(WorkTask.NOTHING),
    STONEMASON(WorkTask.NOTHING),
    TOOLSMITH(WorkTask.NOTHING),
    POTTER(WorkTask.NOTHING),
    GLASSBLOWER(WorkTask.NOTHING),
    WEAPONSMITH(WorkTask.NOTHING),
    ARMORER(WorkTask.NOTHING),
    LIBRARIAN(WorkTask.NOTHING),
    CARTOGRAPHER(WorkTask.NOTHING),
    DYER(WorkTask.NOTHING),
    REDSTONE_ENGINEER(WorkTask.NOTHING),
    ALCHEMIST(WorkTask.NOTHING),

    /* Military */
    MAYOR(WorkTask.NOTHING),
    GUARD(WorkTask.NOTHING),
    FOOT_SOLDIER(WorkTask.NOTHING),
    ARCHER(WorkTask.NOTHING),
    KNIGHT(WorkTask.NOTHING),

    /* Others */
    INNKEEPER(WorkTask.NOTHING),
    PRIEST(WorkTask.NOTHING),
    WANDERING_TRADER(WorkTask.NOTHING),
    GARDENER(WorkTask.NOTHING),
    FARRIER(WorkTask.NOTHING),
    BARD(WorkTask.NOTHING),
    NITWIT(WorkTask.NOTHING),
    WANDERER(WorkTask.NOTHING),
    BUILDER(Building::create);

    private final WorkTask workTask;
    
    WorkAi(WorkTask workTask) {
        this.workTask = workTask;
    }

    public WorkTask getWorkTask() {
        return workTask;
    }
}


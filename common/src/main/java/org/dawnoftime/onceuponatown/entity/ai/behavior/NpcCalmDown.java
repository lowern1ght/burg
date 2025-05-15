package org.dawnoftime.onceuponatown.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class NpcCalmDown {
    private static final int SAFE_DISTANCE_FROM_DANGER = 36;

    public static BehaviorControl<LivingEntity> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                    instance.registered(MemoryModuleType.HURT_BY),
                    instance.registered(MemoryModuleType.HURT_BY_ENTITY),
                    instance.registered(MemoryModuleType.NEAREST_HOSTILE)
                )
                .apply(
                    instance,
                    (hurtByAccessor, hurtByEntityAccessor, nearestHostileAccessor) ->
                        (serverLevel, self, l) -> {
                            boolean bl = instance.tryGet(hurtByAccessor).isPresent()
                                || instance.tryGet(nearestHostileAccessor).isPresent()
                                || instance.tryGet(hurtByEntityAccessor).filter(hurtByEntity -> hurtByEntity.distanceToSqr(self) <= 36.0).isPresent();
                            if (!bl) {
                                hurtByAccessor.erase();
                                hurtByEntityAccessor.erase();
                                self.getBrain().updateActivityFromSchedule(serverLevel.getDayTime(), serverLevel.getGameTime());
                            }

                            return true;
                        }
                )
        );
    }
}

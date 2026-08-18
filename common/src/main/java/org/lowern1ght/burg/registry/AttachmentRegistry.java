package org.lowern1ght.burg.registry;

import net.neoforged.neoforge.attachment.AttachmentType;
import org.lowern1ght.burg.entity.citizen.CitizenData;

/**
 * Holder for the mod's data attachments, filled by the loader once its registries have run.
 *
 * <p>Same shape as {@link BlockRegistry} and {@link EntityRegistry}: the common module owns
 * the field so common code can read it, and {@code OuatForge} owns the {@code
 * DeferredRegister} that assigns it, because only the loader has a mod event bus to register
 * on. Nothing touches this before {@code FMLCommonSetupEvent}, so nothing sees the null.
 */
public class AttachmentRegistry {

    /** Town membership and identity on a vanilla villager. See {@link CitizenData}. */
    public static AttachmentType<CitizenData> CITIZEN;
}

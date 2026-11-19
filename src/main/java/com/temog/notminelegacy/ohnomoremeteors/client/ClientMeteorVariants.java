package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Client only: stores the synced variant on the client copy of the meteor, under the same ForgeData key the server
 * uses, so {@link MeteorVariant#fromEntity} works on both sides.
 */
public final class ClientMeteorVariants {

    private ClientMeteorVariants() {
    }

    public static void apply(int entityId, int ordinal) {
        var level = Minecraft.getInstance().level;
        if (level == null || ordinal < 0 || ordinal >= MeteorVariant.values().length) {
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (entity instanceof MeteorProjectileEntity) {
            MeteorVariant.values()[ordinal].writeTo(entity);
        }
    }
}

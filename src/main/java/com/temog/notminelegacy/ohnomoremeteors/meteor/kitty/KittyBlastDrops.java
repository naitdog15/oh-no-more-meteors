package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fewer loose items from kitty blasts (2026-11-21, lag). While {@link KittyImpact#blast} runs its explosion, each
 * item entity that joins the level is kept with the config blastDropShare chance (default 0.35) and cancelled
 * otherwise. A shower is dozens of blasts, and the dropped blocks were the largest pile of new entities it left.
 *
 * Server thread only: the flag is a plain counter set and cleared around one explode() call, so no other item spawn
 * is ever touched.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KittyBlastDrops {

    private static int depth;

    private KittyBlastDrops() {
    }

    static void begin() {
        depth++;
    }

    static void end() {
        depth = Math.max(0, depth - 1);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (depth <= 0 || event.loadedFromDisk() || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ItemEntity)) {
            return;
        }
        try {
            double share = Config.KITTY_BLAST_DROP_SHARE.get();
            if (share >= 1.0D) {
                return;
            }
            if (share <= 0.0D || event.getLevel().getRandom().nextDouble() >= share) {
                event.setCanceled(true);
            }
        } catch (RuntimeException ex) {
            KittyShowers.warnOnce("blast-drops", ex);
        }
    }
}

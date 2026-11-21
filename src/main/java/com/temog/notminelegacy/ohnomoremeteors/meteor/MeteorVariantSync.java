package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.kitty.KomitKitty;
import com.temog.notminelegacy.ohnomoremeteors.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tells a player which variant a falling meteor is when the meteor comes into their view. Forge fires StartTracking
 * after the entity's spawn packet, so the client already has the entity when the message arrives.
 *
 * A Komit Kitty is sent as Veyrite (its look), even when Veyrite meteors are switched off.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorVariantSync {

    private static boolean loggedFailure;

    private MeteorVariantSync() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || !MeteorEvents.isOmmMeteor(event.getTarget())) {
                return;
            }
            MeteorVariant look = MeteorVisuals.lookOf(event.getTarget());
            if (look != null && (look.isEnabled() || KomitKitty.isKitty(event.getTarget()))) {
                ModNetwork.sendVariant(player, event.getTarget().getId(), look);
            }
            if (look == null) {
                // Another mod's registered variant look (ForeignVariantLooks): its claim string, checked again on the client.
                ForeignLook foreign = MeteorVisuals.foreignLookOf(event.getTarget());
                if (foreign != null) {
                    ModNetwork.sendForeignVariant(player, event.getTarget().getId(), foreign.claim());
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedFailure) {
                loggedFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not sync a meteor variant to a player; it shows the plain look (this is logged once).", ex);
            }
        }
    }
}

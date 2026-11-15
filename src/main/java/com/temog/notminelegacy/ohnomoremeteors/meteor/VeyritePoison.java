package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.block.VeyriteBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Veyrite exposure (a stand-in for radiation): a player standing on charged Veyrite is poisoned.
 *
 *  - Cool Veyrite (charge 0) does nothing. Warm (charge 1-2) gives Poison I, heated (charge 3) Poison II.
 *  - Nothing happens for the first veyritePoisonDelayTicks (default 40 = 2 seconds) on the block.
 *  - After that the poison is (re)applied once a second. Its duration equals the total time the player has stood on
 *    charged Veyrite so far (capped by veyritePoisonMaxTicks), so the longer they stay, the longer it lasts.
 *  - Stepping off for longer than {@link #GRACE_TICKS} resets the count (a jump or a step between blocks does not).
 *
 * Applies to every survival or adventure player on the server side; sneaking does not protect.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeyritePoison {

    /** Ticks off the block before the exposure count resets. */
    private static final int GRACE_TICKS = 10;

    private static final Map<UUID, Exposure> EXPOSURE = new HashMap<>();

    private static final class Exposure {
        int onTicks;
        int offTicks;
    }

    private VeyritePoison() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!Config.VEYRITE_POISON_ENABLED.get() || player.isSpectator() || player.isCreative()) {
            EXPOSURE.remove(player.getUUID());
            return;
        }
        // The block the player stands on (getOnPos looks just under the feet, so slabs and carpets on top do not count).
        int charge = player.onGround() ? VeyriteBlock.chargeOf(player.level().getBlockState(player.getOnPos())) : 0;
        Exposure exposure = EXPOSURE.get(player.getUUID());
        if (charge <= 0) {
            if (exposure != null && ++exposure.offTicks > GRACE_TICKS) {
                EXPOSURE.remove(player.getUUID());
            }
            return;
        }
        if (exposure == null) {
            exposure = new Exposure();
            EXPOSURE.put(player.getUUID(), exposure);
        }
        exposure.offTicks = 0;
        exposure.onTicks++;

        int delay = Config.VEYRITE_POISON_DELAY_TICKS.get();
        if (exposure.onTicks < delay || (exposure.onTicks - delay) % 20 != 0) {
            return;
        }
        int duration = Math.min(exposure.onTicks, Config.VEYRITE_POISON_MAX_TICKS.get());
        int amplifier = charge >= VeyriteBlock.MAX_CHARGE ? 1 : 0;
        // addEffect keeps the stronger / longer of the old and new effect.
        player.addEffect(new MobEffectInstance(MobEffects.POISON, duration, amplifier));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EXPOSURE.remove(event.getEntity().getUUID());
    }
}

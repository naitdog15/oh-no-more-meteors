package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.block.VeyriteBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 *  - Hopping and riding do not avoid it: a rider counts as standing where their vehicle is, and a jump keeps counting
 *    while a charged block is just below. Stepping off for longer than {@link #GRACE_TICKS} (25 ticks) resets the count.
 *
 * Applies to every survival or adventure player on the server side; sneaking does not protect.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VeyritePoison {

    /** Ticks off the block before the exposure count resets. */
    private static final int GRACE_TICKS = 25;

    /**
     * Depths below the body (blocks) probed while it is airborne, top-down; the deepest covers a ~1.25 block jump
     * apex. The charge of the first solid block found counts, so a floor over Veyrite protects.
     */
    private static final double[] AIR_PROBE_DEPTHS = {0.2D, 0.75D, 1.3D};

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
        int charge = exposureCharge(player);
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

    /**
     * The charge the player is exposed to this tick. The body is the player, or the vehicle they ride (the root
     * vehicle, so a horse or boat over Veyrite counts). On the ground it is the block under the feet (getOnPos
     * looks just under them, so slabs and carpets on top do not count). In the air it is the charge of the first
     * solid (collision) block within 0.2, 0.75 and 1.3 below the body, which covers the apex of a ~1.25 block jump,
     * so hopping does not avoid the poison, but a floor (slab, carpet, stone) over Veyrite does protect.
     */
    private static int exposureCharge(ServerPlayer player) {
        Entity body = player.getRootVehicle();
        Level level = player.level();
        if (body.onGround()) {
            BlockPos on = body.getOnPos();
            return level.isLoaded(on) ? VeyriteBlock.chargeOf(level.getBlockState(on)) : 0;
        }
        for (double d : AIR_PROBE_DEPTHS) {
            BlockPos probe = BlockPos.containing(body.getX(), body.getY() - d, body.getZ());
            if (!level.isLoaded(probe)) {
                return 0;
            }
            BlockState state = level.getBlockState(probe);
            if (!state.getCollisionShape(level, probe).isEmpty()) {
                return VeyriteBlock.chargeOf(state);
            }
        }
        return 0;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EXPOSURE.remove(event.getEntity().getUUID());
    }
}

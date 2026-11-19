package com.temog.notminelegacy.ohnomoremeteors.block;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.gear.GearAbilities;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModArmorMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Contact hazards of the crater blocks at their extreme stage (2026-11-19):
 *  - Cindrite at heat 3: anything resting on its top (mobs, players, dropped items...) catches fire after
 *    cindriteHotContactTicks (1.5 s) of contact, and keeps being re-lit while it stays.
 *  - Rimerite at frost 3: a living entity standing on it freezes exactly like one standing in powder snow
 *    (frozen-ticks build up one per tick, freeze damage once fully frozen), after rimeriteColdContactTicks.
 *
 * Living entities are checked every tick here (a standing server player only "moves" when a movement packet
 * comes in, so Block#stepOn alone would be too rare). Items and other non-living entities are fed in from
 * CindriteBlock#stepOn, which an item resting on the block triggers every few ticks.
 *
 * The timers live in a server-side weak map, not in entity data. A gap of up to GRACE_TICKS keeps the timer
 * running, so hopping does not reset it.
 *
 * Freezing note: the powder-snow flag cannot be used from here (it is cleared at the start of the entity's own
 * tick, after this event), so the frozen ticks are driven directly: +3 here, minus the 2 vanilla takes off when
 * the entity is not in powder snow = +1 per tick, the same rate as powder snow; allowed 2 past the freeze
 * threshold so vanilla's "fully frozen" damage check still fires.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContactHazards {

    /** Ticks without contact before a timer starts over (covers jumping and items that only tick every 4 ticks). */
    private static final int GRACE_TICKS = 12;

    /** {contact start, last contact} game times per entity. */
    private static final Map<Entity, long[]> HOT = new WeakHashMap<>();
    private static final Map<Entity, long[]> COLD = new WeakHashMap<>();
    private static boolean loggedFailure;

    private ContactHazards() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        Level level = living.level();
        if (level.isClientSide() || !living.isAlive() || living.isSpectator()) {
            return;
        }
        try {
            if (!living.onGround()) {
                return;
            }
            BlockPos on = living.getOnPos();
            BlockState state = level.getBlockState(on);
            if (state.getBlock() instanceof CindriteBlock && state.getValue(CindriteBlock.HEAT) == CindriteBlock.MAX_HEAT) {
                hotContact(living, level);
            } else if (state.getBlock() instanceof RimeriteBlock && state.getValue(RimeriteBlock.FROST) == RimeriteBlock.MAX_FROST) {
                coldContact(living, level);
            }
        } catch (RuntimeException ex) {
            warnOnce(ex);
        }
    }

    /** One contact tick with full-heat Cindrite. Also called from CindriteBlock#stepOn for non-living entities. */
    public static void hotContact(Entity entity, Level level) {
        if (!Config.CINDRITE_HOT_CONTACT_IGNITES.get() || entity.fireImmune() || entity.isSpectator()) {
            return;
        }
        if (entity instanceof Player player && (player.isCreative() || player.getAbilities().invulnerable)) {
            return;
        }
        // The full Cindrite set is immune to fire anyway (GearAbilities puts its fire out); igniting it would flicker.
        if (entity instanceof LivingEntity living && Config.CINDRITE_ABILITIES.get()
                && GearAbilities.fullSet(living, ModArmorMaterials.CINDRITE)) {
            return;
        }
        if (contactLongEnough(HOT, entity, level.getGameTime(), Config.CINDRITE_HOT_CONTACT_TICKS.get())
                && entity.getRemainingFireTicks() < 20) {
            entity.setSecondsOnFire(Config.CINDRITE_HOT_CONTACT_FIRE_SECONDS.get());
        }
    }

    /** One contact tick with full-frost Rimerite: freezes like powder snow once the contact time is reached. */
    public static void coldContact(LivingEntity living, Level level) {
        if (!Config.RIMERITE_COLD_CONTACT_FREEZES.get() || !living.canFreeze()) {
            return;
        }
        if (contactLongEnough(COLD, living, level.getGameTime(), Config.RIMERITE_COLD_CONTACT_TICKS.get())) {
            int required = living.getTicksRequiredToFreeze();
            living.setTicksFrozen(Math.min(required + 2, living.getTicksFrozen() + 3));
        }
    }

    /** Updates the entity's contact timer and returns true once contact has lasted {@code ticks}. */
    private static boolean contactLongEnough(Map<Entity, long[]> timers, Entity entity, long now, int ticks) {
        long[] t = timers.get(entity);
        if (t == null || now - t[1] > GRACE_TICKS) {
            t = new long[]{now, now};
            timers.put(entity, t);
        }
        t[1] = now;
        return now - t[0] >= ticks;
    }

    private static void warnOnce(RuntimeException ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The Cindrite/Rimerite contact check failed; skipped (this is logged once).", ex);
        }
    }
}

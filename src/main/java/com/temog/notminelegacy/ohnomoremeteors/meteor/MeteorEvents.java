package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Game events of the meteor integration:
 *  - the one-time variant roll when a new Oh My, Meteors! meteor joins the world,
 *  - keeping the fall-trail list in step with meteors joining and leaving,
 *  - driving the trail once per level tick.
 * Every handler fails open: an error here must never stop an entity from joining or the level from ticking.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorEvents {

    private static final ResourceLocation METEOR_TYPE_ID = new ResourceLocation("ohmymeteors", "meteor_projectile");

    private static boolean loggedRollFailure;
    private static boolean loggedTickFailure;

    private MeteorEvents() {
    }

    /** True for OMM's falling meteor (the entity type id is checked as well, so subclasses of other kinds are ignored). */
    static boolean isOmmMeteor(Entity entity) {
        if (!(entity instanceof MeteorProjectileEntity)) {
            return false;
        }
        return METEOR_TYPE_ID.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    /**
     * The roll. NORMAL priority, so it runs before add-ons that roll at LOW (Building Bridges). Nothing is
     * written on a miss, so a later add-on can still claim the meteor. Any existing value under the claim key
     * means "already decided": no reroll, no overwrite.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onJoinRoll(EntityJoinLevelEvent event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level) || level.isClientSide() || event.loadedFromDisk()) {
                return;
            }
            Entity entity = event.getEntity();
            if (!isOmmMeteor(entity)) {
                return;
            }
            if (MeteorVariant.hasAnyClaim(entity)) {
                return;
            }
            MeteorProjectileEntity meteor = (MeteorProjectileEntity) entity;
            // Scatter children never become special meteors. OMM sets size and the scatter flag before it adds the entity.
            if (meteor.isScatterMeteor() || meteor.getSize() < Config.MIN_METEOR_SIZE.get()) {
                return;
            }
            RandomSource random = level.getRandom();
            // The climate band at the meteor's column weights both chances (NEUTRAL when the feature is off).
            // Read once; it draws no random numbers, so the roll below consumes the same randomness as before.
            ThermalReader.Band band = MeteorVariant.columnBand(level, meteor.blockPosition());
            // Fire is rolled first, then ice, then space; each only if it is switched on.
            if (MeteorVariant.CINDRITE.isEnabled() && random.nextDouble() < MeteorVariant.CINDRITE.chance(band)) {
                MeteorVariant.CINDRITE.writeTo(entity);
            } else if (MeteorVariant.RIMERITE.isEnabled() && random.nextDouble() < MeteorVariant.RIMERITE.chance(band)) {
                MeteorVariant.RIMERITE.writeTo(entity);
            } else if (MeteorVariant.VEYRITE.isEnabled() && random.nextDouble() < MeteorVariant.VEYRITE.chance(band)) {
                MeteorVariant.VEYRITE.writeTo(entity);
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedRollFailure) {
                loggedRollFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor variant roll failed; the meteor stays normal.", ex);
            }
        }
    }

    /**
     * Registers a claimed meteor for the fall trail. LOWEST priority so every add-on has finished writing its
     * claim; meteors loaded from disk are included (they are still falling).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoinTrack(EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) {
                return;
            }
            Entity entity = event.getEntity();
            if (!isOmmMeteor(entity)) {
                return;
            }
            MeteorVariant variant = MeteorVariant.fromEntity(entity);
            if (variant != null && variant.isEnabled()) {
                MeteorTrail.track(entity, variant);
            }
        } catch (RuntimeException | LinkageError ex) {
            warnTickOnce(ex);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) {
                return;
            }
            MeteorTrail.untrack(event.getEntity());
        } catch (RuntimeException | LinkageError ex) {
            warnTickOnce(ex);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MeteorTrail.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        try {
            MeteorTrail.tick(level);
        } catch (RuntimeException | LinkageError ex) {
            warnTickOnce(ex);
        }
    }

    private static void warnTickOnce(Throwable ex) {
        if (!loggedTickFailure) {
            loggedTickFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor trail failed once and will stay quiet about it: {}", ex.toString());
        }
    }
}

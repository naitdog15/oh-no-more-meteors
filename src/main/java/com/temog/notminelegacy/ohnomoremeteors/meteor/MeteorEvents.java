package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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

    private static final String BASE_ACCEL_KEY = "ohnomoremeteors:base_accel";
    private static final String SIZE_SCALED_KEY = "ohnomoremeteors:size_scaled";
    /** OMM's constructor value for accelerationPower. */
    private static final double DEFAULT_ACCEL = 0.1D;

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
     * Random size and fall speed (impactFormula.randomizeMeteors). HIGH priority, so it runs before the variant roll:
     * the roll's minimum-size check sees the changed size, and the variant sizeMult is applied on top of it.
     * Decided once per meteor (a stored flag), never for scatter children, never for a meteor another add-on has
     * already claimed, and never for meteors loaded from disk. The speed roll is stored and re-applied on every
     * load by onJoinTrack, because OMM does not save the meteor's acceleration.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoinRandomize(EntityJoinLevelEvent event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) {
                return;
            }
            Entity entity = event.getEntity();
            if (!isOmmMeteor(entity)) {
                return;
            }
            MeteorProjectileEntity meteor = (MeteorProjectileEntity) entity;
            CompoundTag data = meteor.getPersistentData();
            if (data.getBoolean(ImpactFormula.RANDOMIZED_KEY) || meteor.isScatterMeteor() || MeteorVariant.hasAnyClaim(entity)) {
                return;
            }
            data.putBoolean(ImpactFormula.RANDOMIZED_KEY, true);
            if (!Config.RANDOMIZE_METEORS.get()) {
                return;
            }
            RandomSource random = level.getRandom();
            meteor.setSize(ImpactFormula.jitterSize(meteor.getSize(), random));
            data.putDouble(ImpactFormula.SPEED_ROLL_KEY, ImpactFormula.rollSpeed(random));
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedRollFailure) {
                loggedRollFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The meteor size/speed randomness failed; the meteor keeps its own.", ex);
            }
        }
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
            MeteorVariant claimed = MeteorVariant.fromEntity(entity);
            if (claimed != null) {
                applyClaimSize(meteor, claimed);
                applySpeed(meteor, claimed);
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
                // Re-applied on every join: OMM does not save accelerationPower, so a meteor loaded from disk is back at 0.1.
                applySpeed((MeteorProjectileEntity) entity, variant);
                MeteorTrail.track(entity, variant);
            } else if (variant == null && ForeignVariantLooks.isRegisteredClaim(entity)) {
                // Another mod's registered variant: that mod owns its fall speed (applySpeed skips it), and its look's
                // trail runs through the same trail tick and budget as ours. Disk loads come through here too.
                ForeignLook look = MeteorVisuals.foreignLookOf(entity);
                if (look != null) {
                    MeteorTrail.trackForeign(entity, look);
                }
            } else if (entity.getPersistentData().contains(ImpactFormula.SPEED_ROLL_KEY)) {
                // A plain meteor with a random fall speed (impactFormula.randomizeMeteors).
                applySpeed((MeteorProjectileEntity) entity, null);
            }
        } catch (RuntimeException | LinkageError ex) {
            warnTickOnce(ex);
        }
    }

    /**
     * Sets OMM's accelerationPower to (base * travelSpeedMult * stored speed roll). {@code variant} may be null for a
     * plain meteor (multiplier 1.0). The base is the value OMM gave the meteor, stored
     * the first time so repeated calls (reload, config change) never compound. Clamped 0.01 - 0.5 as a safety net.
     */
    static void applySpeed(MeteorProjectileEntity meteor, @javax.annotation.Nullable MeteorVariant variant) {
        try {
            // A meteor claimed by another mod with a registered look: that mod sets its fall speed (on every join, from
            // OMM's base, the speed roll if present and its own multiplier), so the plain fallback never touches it.
            if (variant == null && ForeignVariantLooks.isRegisteredClaim(meteor)) {
                return;
            }
            CompoundTag data = meteor.getPersistentData();
            double base;
            if (data.contains(BASE_ACCEL_KEY)) {
                base = data.getDouble(BASE_ACCEL_KEY);
            } else {
                base = meteor.accelerationPower;
                if (!(base > 0.0D) || base > 1.0D) {
                    base = DEFAULT_ACCEL;
                }
                data.putDouble(BASE_ACCEL_KEY, base);
            }
            meteor.accelerationPower = Mth.clamp(base * VariantTuning.speedMult(variant) * ImpactFormula.speedRoll(meteor), 0.01D, 0.5D);
        } catch (RuntimeException | LinkageError ex) {
            warnTickOnce(ex);
        }
    }

    /** Scales the size once, right after the claim (never on load, never twice). */
    static void applyClaimSize(MeteorProjectileEntity meteor, MeteorVariant variant) {
        CompoundTag data = meteor.getPersistentData();
        if (data.getBoolean(SIZE_SCALED_KEY)) {
            return;
        }
        data.putBoolean(SIZE_SCALED_KEY, true);
        int size = meteor.getSize();
        int scaled = Mth.clamp((int) Math.round(size * VariantTuning.sizeMult(variant)), 1, 50);
        if (scaled != size) {
            meteor.setSize(scaled);
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

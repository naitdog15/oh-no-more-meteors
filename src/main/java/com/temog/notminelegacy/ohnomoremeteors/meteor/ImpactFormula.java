package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/**
 * The impact formula (2026-11-19) and the per-meteor random rolls it reads.
 *
 * Each meteor carries two numbers: its size (OMM's, after the random size change and the variant sizeMult) and
 * a stored speed roll (its random fall speed factor; 1.0 when randomness is off). One impact "energy" comes from
 * them:
 * <pre>
 *   energy = size x sqrt(speedRoll)
 * </pre>
 * The square root keeps speed a real but softer factor than size (speed rolls range about 0.7 - 1.5). Measured
 * impact speed is NOT used: an OMM meteor keeps accelerating, so it mostly reflects the drop height.
 *
 * Everything the impact does scales from the energy:
 * <ul>
 *   <li>blast power = OMM base power(size) x OMM explosion_power_multiplier x variant blastPowerMult x sqrt(speedRoll)</li>
 *   <li>crater half-width (the area the crater code looks at) = ceil(power) + 2, times the variant craterHalfWidthMult, capped</li>
 *   <li>ore blocks = round(energy x resourcesPerEnergy), 1 .. maxResourcesPerImpact</li>
 *   <li>lump blocks (ore + rock) = round(energy x lumpBlocksPerEnergy), at least the ore count, at most maxLumpBlocks</li>
 *   <li>hearing range of the distant crash = distantImpactMinDistance + energy x hearingRangePerEnergy (capped by the max distance)</li>
 *   <li>loudness of the distant crash = loudnessBase + energy x loudnessPerEnergy, at most loudnessCap</li>
 * </ul>
 */
public final class ImpactFormula {

    /** ForgeData key: the meteor's random fall speed factor (stored once, re-applied on every load). */
    public static final String SPEED_ROLL_KEY = "ohnomoremeteors:speed_roll";
    /** ForgeData key: the random size/speed roll has been decided for this meteor (never roll twice). */
    public static final String RANDOMIZED_KEY = "ohnomoremeteors:randomized";

    private ImpactFormula() {
    }

    /** The numbers of one impact. */
    public record Impact(int size, double speedRoll, double energy, float power, int halfWidth, int resources,
                         int lumpBlocks, double hearingRange, float loudness) {
    }

    /** The stored speed roll, 1.0 when none was stored (randomness off, or an old meteor). */
    public static double speedRoll(Entity meteor) {
        CompoundTag data = meteor.getPersistentData();
        if (!data.contains(SPEED_ROLL_KEY)) {
            return 1.0D;
        }
        double roll = data.getDouble(SPEED_ROLL_KEY);
        return roll > 0.0D && roll < 10.0D ? roll : 1.0D;
    }

    /** A random speed roll between randomSpeedMin and randomSpeedMax (either order). */
    public static double rollSpeed(RandomSource random) {
        double a = Config.RANDOM_SPEED_MIN.get();
        double b = Config.RANDOM_SPEED_MAX.get();
        double lo = Math.min(a, b);
        double hi = Math.max(a, b);
        return lo + random.nextDouble() * (hi - lo);
    }

    /** A size changed by up to +-randomSizeSpread, clamped to OMM's 1 - 50. */
    public static int jitterSize(int size, RandomSource random) {
        double spread = Config.RANDOM_SIZE_SPREAD.get();
        double factor = 1.0D + (random.nextDouble() * 2.0D - 1.0D) * spread;
        return Mth.clamp((int) Math.round(size * factor), 1, 50);
    }

    /** OMM's base blast power before its multiplier: size + modifier + the sphere size adjustment. */
    public static int ommBasePower(int size) {
        var behaviour = OhMyMeteors.CONFIG.meteorBehaviourSection;
        // The sphere size adjustment only applies to OMM's better (sphere) explosions.
        int adjust = !behaviour.use_better_explosions ? 0 : (size > 10 ? 3 : (size > 3 ? 2 : 0));
        return size + behaviour.explosion_power_modifier + adjust;
    }

    /** Works out an impact for a meteor; {@code variant} may be null for a plain meteor (no blast/ore numbers needed). */
    public static Impact of(MeteorProjectileEntity meteor, @Nullable MeteorVariant variant) {
        return compute(meteor.getSize(), speedRoll(meteor), variant);
    }

    /** The formula itself, separate from the entity so it can be tested and documented. */
    public static Impact compute(int size, double speedRoll, @Nullable MeteorVariant variant) {
        double speedWeight = Math.sqrt(Math.max(0.01D, speedRoll));
        double energy = Math.max(0.0D, size * speedWeight);

        float power = (float) (ommBasePower(size) * OhMyMeteors.CONFIG.meteorBehaviourSection.explosion_power_multiplier
                * VariantTuning.blastMult(variant) * speedWeight);
        int halfWidth = Mth.clamp((int) Math.round((Math.ceil(Math.max(0.0F, power)) + 2.0D) * VariantTuning.craterMult(variant)),
                0, Config.CRATER_MAX_HALF_WIDTH.get());

        int resources = Mth.clamp((int) Math.round(energy * Config.RESOURCES_PER_ENERGY.get()), 1, Config.MAX_RESOURCES_PER_IMPACT.get());
        int lump = Mth.clamp((int) Math.round(energy * Config.LUMP_BLOCKS_PER_ENERGY.get()), resources,
                Math.max(resources, Config.MAX_LUMP_BLOCKS.get()));

        double min = Config.DISTANT_IMPACT_MIN_DISTANCE.get();
        double max = Math.max(min + 1.0D, Config.DISTANT_IMPACT_MAX_DISTANCE.get());
        double hearing = Mth.clamp(min + energy * Config.HEARING_RANGE_PER_ENERGY.get(), min + 1.0D, max);
        float loudness = (float) Mth.clamp(Config.LOUDNESS_BASE.get() + energy * Config.LOUDNESS_PER_ENERGY.get(),
                0.0D, Config.LOUDNESS_CAP.get());

        return new Impact(size, speedRoll, energy, power, halfWidth, resources, lump, hearing, loudness);
    }
}

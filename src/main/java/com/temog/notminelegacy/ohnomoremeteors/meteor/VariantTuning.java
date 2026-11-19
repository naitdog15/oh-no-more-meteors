package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;

/**
 * Per-variant meteor tuning from the [variants.cindrite], [variants.rimerite] and [variants.veyrite] config
 * sections. Every value is read from the config at call time (never cached). Multipliers default to 1.0
 * behaviour-wise when a variant is null.
 */
public final class VariantTuning {

    private VariantTuning() {
    }

    /** Multiplier on OMM's accelerationPower (fall speed gained per tick). */
    public static double speedMult(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_TRAVEL_SPEED_MULT.get();
            case RIMERITE -> Config.RIMERITE_V_TRAVEL_SPEED_MULT.get();
            case VEYRITE -> Config.VEYRITE_V_TRAVEL_SPEED_MULT.get();
        };
    }

    /** Multiplier on the meteor size, applied once at claim time. */
    public static double sizeMult(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_SIZE_MULT.get();
            case RIMERITE -> Config.RIMERITE_V_SIZE_MULT.get();
            case VEYRITE -> Config.VEYRITE_V_SIZE_MULT.get();
        };
    }

    /** Multiplier on the impact explosion power. */
    public static double blastMult(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_BLAST_POWER_MULT.get();
            case RIMERITE -> Config.RIMERITE_V_BLAST_POWER_MULT.get();
            case VEYRITE -> Config.VEYRITE_V_BLAST_POWER_MULT.get();
        };
    }

    /** Multiplier on crater.craterSpreadPerSize. */
    public static double craterMult(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_CRATER_HALF_WIDTH_MULT.get();
            case RIMERITE -> Config.RIMERITE_V_CRATER_HALF_WIDTH_MULT.get();
            case VEYRITE -> Config.VEYRITE_V_CRATER_HALF_WIDTH_MULT.get();
        };
    }

    /** Multiplier on blast damage to living entities. */
    public static double damageMult(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_ENTITY_DAMAGE_MULT.get();
            case RIMERITE -> Config.RIMERITE_V_ENTITY_DAMAGE_MULT.get();
            case VEYRITE -> Config.VEYRITE_V_ENTITY_DAMAGE_MULT.get();
        };
    }

    /** Per-variant trail switch (the global trail.trailEnabled is still the master switch). */
    public static boolean trailOn(MeteorVariant v) {
        if (v == null) return true;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_TRAIL_ENABLED.get();
            case RIMERITE -> Config.RIMERITE_V_TRAIL_ENABLED.get();
            case VEYRITE -> Config.VEYRITE_V_TRAIL_ENABLED.get();
        };
    }

    public static double trailDensity(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_TRAIL_DENSITY.get();
            case RIMERITE -> Config.RIMERITE_V_TRAIL_DENSITY.get();
            case VEYRITE -> Config.VEYRITE_V_TRAIL_DENSITY.get();
        };
    }

    public static boolean burstOn(MeteorVariant v) {
        if (v == null) return true;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_IMPACT_BURST_ENABLED.get();
            case RIMERITE -> Config.RIMERITE_V_IMPACT_BURST_ENABLED.get();
            case VEYRITE -> Config.VEYRITE_V_IMPACT_BURST_ENABLED.get();
        };
    }

    public static double burstDensity(MeteorVariant v) {
        if (v == null) return 1.0D;
        return switch (v) {
            case CINDRITE -> Config.CINDRITE_V_IMPACT_BURST_DENSITY.get();
            case RIMERITE -> Config.RIMERITE_V_IMPACT_BURST_DENSITY.get();
            case VEYRITE -> Config.VEYRITE_V_IMPACT_BURST_DENSITY.get();
        };
    }

    /** A particle count scaled by a density multiplier, rounded; 0 means "send nothing". */
    public static int densityCount(int base, double mult) {
        if (base <= 0 || mult <= 0.0D) return 0;
        return (int) Math.round(base * mult);
    }
}

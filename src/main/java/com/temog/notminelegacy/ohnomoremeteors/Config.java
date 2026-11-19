package com.temog.notminelegacy.ohnomoremeteors;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON config for Oh No, More Meteors! (file: ohnomoremeteors-common.toml).
 *
 * Every key for every work package is declared here up front, so no package has to edit this file.
 * Read a value with {@code Config.SOME_KEY.get()} at the point of use (never cache it in a static
 * field: the value is only valid after the config has loaded, and can change on reload).
 *
 * Sections and owners (who reads the key):
 *  - variants     : WP-METEOR (the join-time roll)
 *  - temperature  : temperature.ThermalReader (bands) + WP-METEOR (biome-weighted roll), pass 2
 *  - crater       : WP-METEOR (crater fill + after-crater)
 *  - trail        : WP-METEOR (fall particle trail)
 *  - cindrite     : WP-BLOCKS (cindrite block behaviour)
 *  - rimerite     : WP-BLOCKS (rimerite block behaviour; the snow/freeze keys also used by WP-METEOR after-crater)
 *  - meteoricRock : WP-BUGS (Oh My, Meteors! meteoric rock water quench mixin), pass 2
 *  - ommFixes     : WP-BUGS (patches for Oh My, Meteors! 1.5.0 bugs), pass 2
 *
 * Temperatures are in TU, the Not Mine Legacy thermal unit: a biome-like index where plains are about 0.8,
 * deserts about 2.0 and snowy biomes about 0.0 or below. Block heat sources (lava, fire, magma) are signed
 * TU deltas added on top (lava +3.0, fire +2.5, magma +1.5, ice -0.8, blue ice -1.6).
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;

    // ---- variants ----
    public static final ForgeConfigSpec.BooleanValue CINDRITE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RIMERITE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue VEYRITE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_CHANCE;

    // ---- gear abilities ----
    public static final ForgeConfigSpec.BooleanValue RIMERITE_ABILITIES;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_SNOWBALL_CHANCE;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_ABILITIES;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_ORE_SMELT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_SMELT_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_FIRE_CHARGE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_FURNACE_REPAIR;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_HEAT_FUELS;
    public static final ForgeConfigSpec.BooleanValue RIMERITE_FROSTERY_REPAIR;

    // ---- veyrite block ----
    public static final ForgeConfigSpec.DoubleValue VEYRITE_DISCHARGE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue VEYRITE_POISON_ENABLED;
    public static final ForgeConfigSpec.IntValue VEYRITE_POISON_DELAY_TICKS;
    public static final ForgeConfigSpec.IntValue VEYRITE_POISON_MAX_TICKS;
    public static final ForgeConfigSpec.IntValue MIN_METEOR_SIZE;

    // ---- variants.cindrite (per-variant meteor tuning) ----
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_TRAVEL_SPEED_MULT;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_SIZE_MULT;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_BLAST_POWER_MULT;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_CRATER_HALF_WIDTH_MULT;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_ENTITY_DAMAGE_MULT;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_V_TRAIL_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_TRAIL_DENSITY;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_V_IMPACT_BURST_ENABLED;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_V_IMPACT_BURST_DENSITY;
    public static final ForgeConfigSpec.IntValue CINDRITE_V_BLAST_FIRE_SECONDS;

    // ---- variants.rimerite (per-variant meteor tuning) ----
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_TRAVEL_SPEED_MULT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_SIZE_MULT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_BLAST_POWER_MULT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_CRATER_HALF_WIDTH_MULT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_ENTITY_DAMAGE_MULT;
    public static final ForgeConfigSpec.BooleanValue RIMERITE_V_TRAIL_ENABLED;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_TRAIL_DENSITY;
    public static final ForgeConfigSpec.BooleanValue RIMERITE_V_IMPACT_BURST_ENABLED;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_IMPACT_BURST_DENSITY;
    public static final ForgeConfigSpec.IntValue RIMERITE_V_BLAST_FREEZE_TICKS;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_V_IMPACT_SNOW_MULT;

    // ---- variants.veyrite (per-variant meteor tuning) ----
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_TRAVEL_SPEED_MULT;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_SIZE_MULT;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_BLAST_POWER_MULT;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_CRATER_HALF_WIDTH_MULT;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_ENTITY_DAMAGE_MULT;
    public static final ForgeConfigSpec.BooleanValue VEYRITE_V_TRAIL_ENABLED;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_TRAIL_DENSITY;
    public static final ForgeConfigSpec.BooleanValue VEYRITE_V_IMPACT_BURST_ENABLED;
    public static final ForgeConfigSpec.DoubleValue VEYRITE_V_IMPACT_BURST_DENSITY;

    // ---- temperature (pass 2) ----
    public static final ForgeConfigSpec.DoubleValue COLD_BELOW;
    public static final ForgeConfigSpec.DoubleValue WARM_ABOVE;
    public static final ForgeConfigSpec.DoubleValue HOT_ABOVE;
    public static final ForgeConfigSpec.DoubleValue RAIN_DELTA;
    public static final ForgeConfigSpec.DoubleValue SNOW_DELTA;
    public static final ForgeConfigSpec.BooleanValue BIOME_WEIGHTED_VARIANTS;
    public static final ForgeConfigSpec.DoubleValue HOT_CINDRITE_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue HOT_RIMERITE_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue COLD_RIMERITE_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue COLD_CINDRITE_WEIGHT;

    // ---- crater ----
    public static final ForgeConfigSpec.DoubleValue CRATER_SPREAD_PER_SIZE;
    public static final ForgeConfigSpec.IntValue CRATER_MAX_HALF_WIDTH;
    public static final ForgeConfigSpec.DoubleValue CRATER_FILL_CHANCE;
    public static final ForgeConfigSpec.IntValue CRATER_RARE_BLOCK_ONE_IN;
    public static final ForgeConfigSpec.IntValue CRATER_MAX_BLOCK_WRITES;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_IMPACT_SNOW_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_IMPACT_MELT_HOT;

    // ---- trail ----
    public static final ForgeConfigSpec.BooleanValue TRAIL_ENABLED;
    public static final ForgeConfigSpec.IntValue TRAIL_RANGE;

    // ---- impact formula + meteor randomness (2026-11-19) ----
    /** How a fire/ice/space meteor leaves its material: CORE = a compact meteorite lump (OMM style), LINED = the old Falling Meteors lined crater. */
    public enum CraterStyle { CORE, LINED }
    public static final ForgeConfigSpec.BooleanValue RANDOMIZE_METEORS;
    public static final ForgeConfigSpec.DoubleValue RANDOM_SIZE_SPREAD;
    public static final ForgeConfigSpec.DoubleValue RANDOM_SPEED_MIN;
    public static final ForgeConfigSpec.DoubleValue RANDOM_SPEED_MAX;
    public static final ForgeConfigSpec.IntValue COMMAND_RANDOM_SIZE_MIN;
    public static final ForgeConfigSpec.IntValue COMMAND_RANDOM_SIZE_MAX;
    public static final ForgeConfigSpec.EnumValue<CraterStyle> CRATER_STYLE;
    public static final ForgeConfigSpec.DoubleValue RESOURCES_PER_ENERGY;
    public static final ForgeConfigSpec.IntValue MAX_RESOURCES_PER_IMPACT;
    public static final ForgeConfigSpec.DoubleValue LUMP_BLOCKS_PER_ENERGY;
    public static final ForgeConfigSpec.IntValue MAX_LUMP_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue HEARING_RANGE_PER_ENERGY;
    public static final ForgeConfigSpec.DoubleValue LOUDNESS_BASE;
    public static final ForgeConfigSpec.DoubleValue LOUDNESS_PER_ENERGY;
    public static final ForgeConfigSpec.DoubleValue LOUDNESS_CAP;
    public static final ForgeConfigSpec.BooleanValue LOG_IMPACTS;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_LAVA_POCKET_CHANCE;
    public static final ForgeConfigSpec.IntValue CINDRITE_LAVA_POCKET_MAX;
    public static final ForgeConfigSpec.BooleanValue PLAIN_METEORS_NO_ICE_OR_LAVA;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> PLAIN_METEOR_STRIPPED_BLOCKS;

    // ---- contact hazards (2026-11-19) ----
    public static final ForgeConfigSpec.BooleanValue CINDRITE_HOT_CONTACT_IGNITES;
    public static final ForgeConfigSpec.IntValue CINDRITE_HOT_CONTACT_TICKS;
    public static final ForgeConfigSpec.IntValue CINDRITE_HOT_CONTACT_FIRE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue RIMERITE_COLD_CONTACT_FREEZES;
    public static final ForgeConfigSpec.IntValue RIMERITE_COLD_CONTACT_TICKS;

    // ---- distant impact broadcast ----
    public static final ForgeConfigSpec.BooleanValue DISTANT_IMPACT_ENABLED;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_VERY_FAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue DISTANT_IMPACT_VOLUME;

    // ---- cindrite block ----
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_STEP_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_TO_BLACKSTONE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_QUENCHES_IN_WATER;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_DAMAGES_ENTITIES;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_MULT_COLD;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_MULT_NEUTRAL;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_MULT_WARM;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_MULT_HOT;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_REHEAT_ABOVE;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_REHEAT_CHANCE;
    public static final ForgeConfigSpec.IntValue CINDRITE_NETHER_MIN_HEAT;
    public static final ForgeConfigSpec.BooleanValue CINDRITE_COLD_QUENCH;

    // ---- rimerite block ----
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FREEZE_WATER_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_SNOW_CHANCE;
    public static final ForgeConfigSpec.BooleanValue RIMERITE_MELTING;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_MELT_CHANCE_WARM;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_MELT_CHANCE_HOT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_HEAT_MELT_ABOVE;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_MELT_CHANCE_HEAT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_SNOW_BELOW;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_COLD_EFFECT_MULT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FROST_DECAY_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FROST_DECAY_MULT_COLD;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FROST_DECAY_MULT_NEUTRAL;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FROST_DECAY_MULT_WARM;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FROST_DECAY_MULT_HOT;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_FROST_REFREEZE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue RIMERITE_THAW_TO_RIME_WORN_CHANCE;

    // ---- OMM meteoric rock (pass 2) ----
    public static final ForgeConfigSpec.BooleanValue METEORIC_ROCK_WATER_QUENCH;
    public static final ForgeConfigSpec.BooleanValue METEORIC_ROCK_SOUNDS;

    // ---- OMM bug fixes (pass 2) ----
    public static final ForgeConfigSpec.BooleanValue FIX_BASIC_LASER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue FIX_SPECIAL_ONLY_HANG;
    public static final ForgeConfigSpec.BooleanValue FIX_ENTITY_HIT_CLAIM_CHECK;
    public static final ForgeConfigSpec.BooleanValue FIX_STALE_STRUCTURE_LIST;
    public static final ForgeConfigSpec.BooleanValue FIX_THIN_TERRAIN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Which meteors become fire or ice meteors. The roll happens once, when a meteor enters the world.").push("variants");
        CINDRITE_ENABLED = b.comment("Allow fire (Cindrite) meteors.")
                .define("cindriteEnabled", true);
        RIMERITE_ENABLED = b.comment("Allow ice (Rimerite) meteors.")
                .define("rimeriteEnabled", true);
        CINDRITE_CHANCE = b.comment("Chance (0.0 - 1.0) that a new meteor is a Cindrite (fire) meteor.")
                .defineInRange("cindriteChance", 0.06D, 0.0D, 1.0D);
        RIMERITE_CHANCE = b.comment("Chance (0.0 - 1.0) that a new meteor is a Rimerite (ice) meteor. Rolled after Cindrite misses.")
                .defineInRange("rimeriteChance", 0.10D, 0.0D, 1.0D);
        VEYRITE_ENABLED = b.comment("Allow space (Veyrite) meteors.")
                .define("veyriteEnabled", true);
        VEYRITE_CHANCE = b.comment("Chance (0.0 - 1.0) that a new meteor is a Veyrite (space) meteor. Rolled after Cindrite and Rimerite miss. Not weighted by biome.")
                .defineInRange("veyriteChance", 0.05D, 0.0D, 1.0D);
        MIN_METEOR_SIZE = b.comment("Smallest Oh My, Meteors! size that can become a fire or ice meteor. Scatter meteors never do.")
                .defineInRange("minMeteorSize", 2, 1, 100);

        b.comment("How Cindrite (fire) meteors fall, hit and look. Multipliers on top of Oh My, Meteors! and the global crater / trail settings; 1.0 = unchanged.").push("cindrite");
        CINDRITE_V_TRAVEL_SPEED_MULT = b.comment("Fall speed multiplier (0.25 - 4.0). Multiplies how much speed the meteor gains every tick (OMM accelerationPower, normally 0.1).")
                .defineInRange("travelSpeedMult", 1.5D, 0.25D, 4.0D);
        CINDRITE_V_SIZE_MULT = b.comment("Size multiplier (0.25 - 4.0), applied once when the meteor becomes this variant. The result is rounded and kept within OMM's 1 - 50.")
                .defineInRange("sizeMult", 1.0D, 0.25D, 4.0D);
        CINDRITE_V_BLAST_POWER_MULT = b.comment("Impact explosion power multiplier (0.0 - 4.0).")
                .defineInRange("blastPowerMult", 1.25D, 0.0D, 4.0D);
        CINDRITE_V_CRATER_HALF_WIDTH_MULT = b.comment("Crater width multiplier (0.0 - 4.0) on top of crater.craterSpreadPerSize. crater.craterMaxHalfWidth still caps it.")
                .defineInRange("craterHalfWidthMult", 1.0D, 0.0D, 4.0D);
        CINDRITE_V_ENTITY_DAMAGE_MULT = b.comment("Multiplier (0.0 - 10.0) for the blast damage this meteor deals to living things (players included).")
                .defineInRange("entityDamageMult", 1.25D, 0.0D, 10.0D);
        CINDRITE_V_TRAIL_ENABLED = b.comment("Show this variant's fall trail. trail.trailEnabled must also be on.")
                .define("trailEnabled", true);
        CINDRITE_V_TRAIL_DENSITY = b.comment("Fall trail particle amount multiplier (0.0 - 4.0). 0 shows none.")
                .defineInRange("trailDensity", 0.8D, 0.0D, 4.0D);
        CINDRITE_V_IMPACT_BURST_ENABLED = b.comment("Show the burst of particles when this meteor lands.")
                .define("impactBurstEnabled", true);
        CINDRITE_V_IMPACT_BURST_DENSITY = b.comment("Impact burst particle amount multiplier (0.0 - 4.0). 0 shows none.")
                .defineInRange("impactBurstDensity", 0.8D, 0.0D, 4.0D);
        CINDRITE_V_BLAST_FIRE_SECONDS = b.comment("Living things inside the blast are set on fire for this many seconds (0 - 60, 0 = off). Fire-immune mobs are skipped.")
                .defineInRange("blastFireSeconds", 6, 0, 60);
        b.pop();

        b.comment("How Rimerite (ice) meteors fall, hit and look. Multipliers on top of Oh My, Meteors! and the global crater / trail settings; 1.0 = unchanged.").push("rimerite");
        RIMERITE_V_TRAVEL_SPEED_MULT = b.comment("Fall speed multiplier (0.25 - 4.0). Multiplies how much speed the meteor gains every tick (OMM accelerationPower, normally 0.1).")
                .defineInRange("travelSpeedMult", 0.75D, 0.25D, 4.0D);
        RIMERITE_V_SIZE_MULT = b.comment("Size multiplier (0.25 - 4.0), applied once when the meteor becomes this variant. The result is rounded and kept within OMM's 1 - 50.")
                .defineInRange("sizeMult", 1.25D, 0.25D, 4.0D);
        RIMERITE_V_BLAST_POWER_MULT = b.comment("Impact explosion power multiplier (0.0 - 4.0).")
                .defineInRange("blastPowerMult", 0.9D, 0.0D, 4.0D);
        RIMERITE_V_CRATER_HALF_WIDTH_MULT = b.comment("Crater width multiplier (0.0 - 4.0) on top of crater.craterSpreadPerSize. crater.craterMaxHalfWidth still caps it.")
                .defineInRange("craterHalfWidthMult", 1.25D, 0.0D, 4.0D);
        RIMERITE_V_ENTITY_DAMAGE_MULT = b.comment("Multiplier (0.0 - 10.0) for the blast damage this meteor deals to living things (players included).")
                .defineInRange("entityDamageMult", 0.8D, 0.0D, 10.0D);
        RIMERITE_V_TRAIL_ENABLED = b.comment("Show this variant's fall trail. trail.trailEnabled must also be on.")
                .define("trailEnabled", true);
        RIMERITE_V_TRAIL_DENSITY = b.comment("Fall trail particle amount multiplier (0.0 - 4.0). 0 shows none.")
                .defineInRange("trailDensity", 0.8D, 0.0D, 4.0D);
        RIMERITE_V_IMPACT_BURST_ENABLED = b.comment("Show the burst of particles when this meteor lands.")
                .define("impactBurstEnabled", true);
        RIMERITE_V_IMPACT_BURST_DENSITY = b.comment("Impact burst particle amount multiplier (0.0 - 4.0). 0 shows none.")
                .defineInRange("impactBurstDensity", 0.8D, 0.0D, 4.0D);
        RIMERITE_V_BLAST_FREEZE_TICKS = b.comment("Living things inside the blast get this many freeze ticks, like powder snow (0 - 600, 0 = off; 140 = fully frozen).")
                .defineInRange("blastFreezeTicks", 200, 0, 600);
        RIMERITE_V_IMPACT_SNOW_MULT = b.comment("Multiplier (0.0 - 4.0) on crater.rimeriteImpactSnowChance: how much snow the impact spreads.")
                .defineInRange("impactSnowMult", 1.0D, 0.0D, 4.0D);
        b.pop();

        b.comment("How Veyrite (space) meteors fall, hit and look. Multipliers on top of Oh My, Meteors! and the global crater / trail settings; 1.0 = unchanged.").push("veyrite");
        VEYRITE_V_TRAVEL_SPEED_MULT = b.comment("Fall speed multiplier (0.25 - 4.0). Multiplies how much speed the meteor gains every tick (OMM accelerationPower, normally 0.1).")
                .defineInRange("travelSpeedMult", 0.5D, 0.25D, 4.0D);
        VEYRITE_V_SIZE_MULT = b.comment("Size multiplier (0.25 - 4.0), applied once when the meteor becomes this variant. The result is rounded and kept within OMM's 1 - 50.")
                .defineInRange("sizeMult", 1.0D, 0.25D, 4.0D);
        VEYRITE_V_BLAST_POWER_MULT = b.comment("Impact explosion power multiplier (0.0 - 4.0).")
                .defineInRange("blastPowerMult", 1.0D, 0.0D, 4.0D);
        VEYRITE_V_CRATER_HALF_WIDTH_MULT = b.comment("Crater width multiplier (0.0 - 4.0) on top of crater.craterSpreadPerSize. crater.craterMaxHalfWidth still caps it.")
                .defineInRange("craterHalfWidthMult", 1.0D, 0.0D, 4.0D);
        VEYRITE_V_ENTITY_DAMAGE_MULT = b.comment("Multiplier (0.0 - 10.0) for the blast damage this meteor deals to living things (players included).")
                .defineInRange("entityDamageMult", 1.0D, 0.0D, 10.0D);
        VEYRITE_V_TRAIL_ENABLED = b.comment("Show this variant's fall trail. trail.trailEnabled must also be on.")
                .define("trailEnabled", true);
        VEYRITE_V_TRAIL_DENSITY = b.comment("Fall trail particle amount multiplier (0.0 - 4.0). 0 shows none.")
                .defineInRange("trailDensity", 0.8D, 0.0D, 4.0D);
        VEYRITE_V_IMPACT_BURST_ENABLED = b.comment("Show the burst of particles when this meteor lands.")
                .define("impactBurstEnabled", true);
        VEYRITE_V_IMPACT_BURST_DENSITY = b.comment("Impact burst particle amount multiplier (0.0 - 4.0). 0 shows none.")
                .defineInRange("impactBurstDensity", 0.8D, 0.0D, 4.0D);
        b.pop();
        b.pop();

        b.comment("Temperature bands and the biome-weighted meteor roll. Temperatures come from Not Mine Legacy, in TU",
                        "(thermal units: plains 0.8, forests about 0.7, desert 2.0, snowy biomes 0.0 or below).",
                        "Below coldBelow is COLD; at or above hotAbove is HOT; otherwise at or above warmAbove is WARM;",
                        "everything else (plains, forests) is NEUTRAL. Keep coldBelow < warmAbove <= hotAbove.")
                .push("temperature");
        COLD_BELOW = b.comment("Below this temperature (TU) a place counts as COLD.")
                .defineInRange("coldBelow", 0.15D, -10.0D, 10.0D);
        WARM_ABOVE = b.comment("At or above this temperature (TU) a place counts as WARM (unless it is HOT).")
                .defineInRange("warmAbove", 1.0D, -10.0D, 10.0D);
        HOT_ABOVE = b.comment("At or above this temperature (TU) a place counts as HOT.")
                .defineInRange("hotAbove", 1.5D, -10.0D, 10.0D);
        RAIN_DELTA = b.comment("Felt-temperature change (TU) for a Cindrite or Rimerite block that rain falls on (open to the sky, rain falling at its spot).",
                        "Added on top of the ambient and neighbour heat. Negative = cooler. 0 = rain makes no difference. Not applied in the Nether and other ultra-warm dimensions.")
                .defineInRange("rainDelta", -0.6D, -10.0D, 10.0D);
        SNOW_DELTA = b.comment("Felt-temperature change (TU) for a block that snow falls on (open to the sky, snowing at its spot).",
                        "Added on top of the ambient and neighbour heat. Negative = colder. Not applied in the Nether and other ultra-warm dimensions.")
                .defineInRange("snowDelta", -1.0D, -10.0D, 10.0D);
        BIOME_WEIGHTED_VARIANTS = b.comment("Weight the fire / ice meteor roll by the biome under the meteor (hot biomes get more fire meteors, cold biomes more ice meteors).")
                .define("biomeWeightedVariants", true);
        HOT_CINDRITE_WEIGHT = b.comment("In a HOT biome, the Cindrite (fire) meteor chance is multiplied by this.")
                .defineInRange("hotCindriteWeight", 2.0D, 0.0D, 10.0D);
        HOT_RIMERITE_WEIGHT = b.comment("In a HOT biome, the Rimerite (ice) meteor chance is multiplied by this.")
                .defineInRange("hotRimeriteWeight", 0.25D, 0.0D, 10.0D);
        COLD_RIMERITE_WEIGHT = b.comment("In a COLD biome, the Rimerite (ice) meteor chance is multiplied by this.")
                .defineInRange("coldRimeriteWeight", 2.0D, 0.0D, 10.0D);
        COLD_CINDRITE_WEIGHT = b.comment("In a COLD biome, the Cindrite (fire) meteor chance is multiplied by this.")
                .defineInRange("coldCindriteWeight", 0.25D, 0.0D, 10.0D);
        b.pop();

        b.comment("The crater of Cindrite / Rimerite blocks a fire or ice meteor leaves behind.").push("crater");
        CRATER_SPREAD_PER_SIZE = b.comment("Crater half-width = round(meteor size * this value), capped by craterMaxHalfWidth.")
                .defineInRange("craterSpreadPerSize", 1.0D, 0.0D, 4.0D);
        CRATER_MAX_HALF_WIDTH = b.comment("Hard cap on the crater half-width, in blocks.")
                .defineInRange("craterMaxHalfWidth", 12, 1, 24);
        CRATER_FILL_CHANCE = b.comment("Chance that an eligible block next to the blast hollow becomes crater material (the floor of the hollow, two blocks deep, always does).")
                .defineInRange("craterFillChance", 0.30D, 0.0D, 1.0D);
        CRATER_RARE_BLOCK_ONE_IN = b.comment("One in this many crater blocks is the rare block instead (magma block for Cindrite, blue ice for Rimerite). 0 disables.")
                .defineInRange("craterRareBlockOneIn", 45, 0, 10000);
        CRATER_MAX_BLOCK_WRITES = b.comment("Hard cap on blocks written per impact, so a huge meteor cannot stall a tick.")
                .defineInRange("craterMaxBlockWrites", 4000, 1, 20000);
        RIMERITE_IMPACT_SNOW_CHANCE = b.comment("After a Rimerite impact: chance that an exposed top surface of the blast hollow gets a snow layer.")
                .defineInRange("rimeriteImpactSnowChance", 0.5D, 0.0D, 1.0D);
        RIMERITE_IMPACT_MELT_HOT = b.comment("A Rimerite crater in a HOT biome: chance that each Rimerite block is placed as water instead (air in the Nether and other ultra-warm dimensions).")
                .defineInRange("rimeriteImpactMeltHot", 0.3D, 0.0D, 1.0D);
        b.pop();

        b.comment("Particles shown while a fire or ice meteor falls. Server side only, restrained counts.").push("trail");
        TRAIL_ENABLED = b.comment("Show the fall trail.")
                .define("trailEnabled", true);
        TRAIL_RANGE = b.comment("Only players within this many blocks of the meteor see the trail.")
                .defineInRange("trailRange", 128, 16, 512);
        b.pop();

        b.comment("Meteor randomness and the impact formula. Every impact works out one 'energy' value:",
                "  energy = meteor size x sqrt(speed roll)   (size after randomness and the variant sizeMult; speed roll = the meteor's random fall speed factor, 1.0 when randomness is off)",
                "and everything an impact does scales from it: blast power and crater area, how much ore and rock it leaves, and how far / how loud the distant crash is.")
                .push("impactFormula");
        RANDOMIZE_METEORS = b.comment("Give every Oh My, Meteors! meteor (plain ones too, and command-summoned ones without an explicit size) a random size and fall speed.",
                        "Turn off for strict meteors: sizes then come only from Oh My, Meteors! and the variant sizeMult, speeds only from the variant travelSpeedMult.")
                .define("randomizeMeteors", true);
        RANDOM_SIZE_SPREAD = b.comment("Random size change, as a fraction: 0.25 = each meteor is 75% - 125% of its rolled size.")
                .defineInRange("randomSizeSpread", 0.25D, 0.0D, 0.9D);
        RANDOM_SPEED_MIN = b.comment("Lowest random fall speed factor (multiplies the meteor's acceleration).")
                .defineInRange("randomSpeedMin", 0.7D, 0.2D, 3.0D);
        RANDOM_SPEED_MAX = b.comment("Highest random fall speed factor. If lower than randomSpeedMin the two are swapped.")
                .defineInRange("randomSpeedMax", 1.5D, 0.2D, 3.0D);
        COMMAND_RANDOM_SIZE_MIN = b.comment("/ohnomoremeteors meteor without a size: smallest random size.")
                .defineInRange("commandRandomSizeMin", 4, 1, 50);
        COMMAND_RANDOM_SIZE_MAX = b.comment("/ohnomoremeteors meteor without a size: largest random size.")
                .defineInRange("commandRandomSizeMax", 16, 1, 50);
        CRATER_STYLE = b.comment("CORE: a compact meteorite lump in the crater, close to an Oh My, Meteors! meteor (default).",
                        "LINED: the old Falling Meteors style crater lined with ore (uses the [crater] fill settings; much more material).")
                .defineEnum("craterStyle", CraterStyle.CORE);
        RESOURCES_PER_ENERGY = b.comment("Ore blocks (Cindrite / Rimerite / Veyrite) in the lump = round(energy x this). A plain size-10 Oh My, Meteors! meteor leaves about 5 meteoric rock.")
                .defineInRange("resourcesPerEnergy", 0.45D, 0.0D, 5.0D);
        MAX_RESOURCES_PER_IMPACT = b.comment("Most ore blocks one impact can leave.")
                .defineInRange("maxResourcesPerImpact", 40, 1, 256);
        LUMP_BLOCKS_PER_ENERGY = b.comment("Total blocks in the meteorite lump (ore + rock filler) = round(energy x this); never fewer than the ore blocks.")
                .defineInRange("lumpBlocksPerEnergy", 4.5D, 0.0D, 40.0D);
        MAX_LUMP_BLOCKS = b.comment("Most blocks in one meteorite lump.")
                .defineInRange("maxLumpBlocks", 360, 1, 2000);
        HEARING_RANGE_PER_ENERGY = b.comment("Distant crash hearing range = distantImpactMinDistance + energy x this, capped by distantImpactMaxDistance.")
                .defineInRange("hearingRangePerEnergy", 60.0D, 0.0D, 500.0D);
        LOUDNESS_BASE = b.comment("Distant crash loudness = loudnessBase + energy x loudnessPerEnergy, capped by loudnessCap, then faded with distance.")
                .defineInRange("loudnessBase", 0.30D, 0.0D, 1.0D);
        LOUDNESS_PER_ENERGY = b.comment("See loudnessBase.")
                .defineInRange("loudnessPerEnergy", 0.02D, 0.0D, 0.2D);
        LOUDNESS_CAP = b.comment("Loudest the distant crash can ever be (1.0 = full volume). Keeps huge meteors from being deafening.")
                .defineInRange("loudnessCap", 0.8D, 0.05D, 1.0D);
        LOG_IMPACTS = b.comment("Log the worked-out numbers (energy, blast, ore, hearing range) for every fire / ice / space impact.")
                .define("logImpacts", true);
        CINDRITE_LAVA_POCKET_CHANCE = b.comment("Chance that a Cindrite meteorite lump holds a small sealed lava pocket (lava only where it cannot flow out).")
                .defineInRange("cindriteLavaPocketChance", 0.2D, 0.0D, 1.0D);
        CINDRITE_LAVA_POCKET_MAX = b.comment("Most lava source blocks in one Cindrite lava pocket (1 to this many).")
                .defineInRange("cindriteLavaPocketMax", 2, 1, 8);
        PLAIN_METEORS_NO_ICE_OR_LAVA = b.comment("Plain Oh My, Meteors! meteors stay plain rock: OMM's icy structures and its special (easter egg) structures are never picked,",
                        "and every block in plainMeteorStrippedBlocks that the impact itself places is swapped (fluids / snow -> air, the rest -> blackstone / smooth basalt).",
                        "Ice and fire belong to this add-on's Rimerite and Cindrite meteors. Ice or lakes already there are never touched; meteors claimed by other add-ons are left alone.")
                .define("plainMeteorsNoIceOrLava", true);
        PLAIN_METEOR_STRIPPED_BLOCKS = b.comment("Blocks a plain meteor's impact may not leave (see plainMeteorsNoIceOrLava). Default: ice of every kind, snow, water, lava,",
                        "and free loot (netherite / coal / raw iron blocks, diamond / iron / lapis ores, gilded blackstone). Magma is allowed.")
                .defineListAllowEmpty(java.util.List.of("plainMeteorStrippedBlocks"),
                        () -> com.temog.notminelegacy.ohnomoremeteors.meteor.PlainMeteorCleanup.DEFAULT_STRIPPED,
                        o -> o instanceof String str && net.minecraft.resources.ResourceLocation.tryParse(str) != null);
        b.pop();

        b.comment("Distant impact sound: players far from any meteor landing hear a subtitled crash from its direction.").push("distantImpact");
        DISTANT_IMPACT_ENABLED = b.comment("Play the distant impact sound for every Oh My, Meteors! landing (plain, fire and ice meteors).")
                .define("distantImpactEnabled", true);
        DISTANT_IMPACT_MIN_DISTANCE = b.comment("Players closer than this hear the normal explosion instead.")
                .defineInRange("distantImpactMinDistance", 64, 16, 1024);
        DISTANT_IMPACT_VERY_FAR_DISTANCE = b.comment("From this distance the softer 'very far' rumble plays instead of the 'far' one.")
                .defineInRange("distantImpactVeryFarDistance", 512, 16, 100000);
        DISTANT_IMPACT_MAX_DISTANCE = b.comment("Players farther than this hear nothing.")
                .defineInRange("distantImpactMaxDistance", 4096, 64, 100000);
        DISTANT_IMPACT_COOLDOWN_TICKS = b.comment("A player hears at most one distant impact per this many ticks (meteor showers).")
                .defineInRange("distantImpactCooldownTicks", 60, 0, 6000);
        DISTANT_IMPACT_VOLUME = b.comment("Overall loudness multiplier of the distant impact sound.")
                .defineInRange("distantImpactVolume", 1.0D, 0.0D, 2.0D);
        b.pop();

        b.comment("Cindrite block behaviour.").push("cindrite");
        CINDRITE_HOT_CONTACT_IGNITES = b.comment("Anything touching the top of Cindrite at full heat (3) (mobs, players, dropped items...) catches fire after cindriteHotContactTicks.",
                        "Fire-immune entities, fire-resistant items, creative players and wearers of the full Cindrite set are spared.")
                .define("cindriteHotContactIgnites", true);
        CINDRITE_HOT_CONTACT_TICKS = b.comment("Ticks of contact before catching fire (30 = 1.5 s). Short hops do not reset it.")
                .defineInRange("cindriteHotContactTicks", 30, 1, 400);
        CINDRITE_HOT_CONTACT_FIRE_SECONDS = b.comment("Seconds of burning when it catches; refreshed while contact lasts.")
                .defineInRange("cindriteHotContactFireSeconds", 4, 1, 30);
        CINDRITE_COOL_STEP_CHANCE = b.comment("Base chance per random tick that a hot Cindrite block cools down one heat step.",
                        "Multiplied by the cindriteCoolMult* value of the band of the felt temperature (ambient + neighbour heat + rain/snow delta).")
                .defineInRange("cindriteCoolStepChance", 0.5D, 0.0D, 1.0D);
        CINDRITE_COOL_TO_BLACKSTONE_CHANCE = b.comment("When the heat runs out by normal cooling, chance that the block becomes blackstone instead of cooled Cindrite.",
                        "Touching water or cold blocks still makes obsidian, not blackstone.")
                .defineInRange("cindriteCoolToBlackstoneChance", 0.25D, 0.0D, 1.0D);
        CINDRITE_QUENCHES_IN_WATER = b.comment("A hot Cindrite block touching water turns into obsidian.")
                .define("cindriteQuenchesInWater", true);
        CINDRITE_DAMAGES_ENTITIES = b.comment("While hot, Cindrite hurts entities that walk on it (like a magma block).")
                .define("cindriteDamagesEntities", true);
        CINDRITE_COOL_MULT_COLD = b.comment("Cooling speed multiplier in a COLD place (cools faster).")
                .defineInRange("cindriteCoolMultCold", 2.0D, 0.0D, 10.0D);
        CINDRITE_COOL_MULT_NEUTRAL = b.comment("Cooling speed multiplier in a NEUTRAL place (plains, forests).")
                .defineInRange("cindriteCoolMultNeutral", 1.0D, 0.0D, 10.0D);
        CINDRITE_COOL_MULT_WARM = b.comment("Cooling speed multiplier in a WARM place (cools slower).")
                .defineInRange("cindriteCoolMultWarm", 0.6D, 0.0D, 10.0D);
        CINDRITE_COOL_MULT_HOT = b.comment("Cooling speed multiplier in a HOT place such as a desert (cools much slower).")
                .defineInRange("cindriteCoolMultHot", 0.35D, 0.0D, 10.0D);
        CINDRITE_REHEAT_ABOVE = b.comment("If the heat from the six neighbouring blocks plus the rain/snow delta adds up to at least this (TU; lava +3.0, fire +2.5, magma +1.5; rain -0.6),",
                        "hot Cindrite stops cooling and may gain heat back. The biome temperature is not part of this sum, so a desert alone never holds it hot. Default 1.6 sits just above one magma block, so crater magma alone",
                        "does not keep Cindrite hot forever; lava and fire still do. Must be above 0, or hot Cindrite would never cool.")
                .defineInRange("cindriteReheatAbove", 1.6D, 0.01D, 16.0D);
        CINDRITE_REHEAT_CHANCE = b.comment("Chance per random tick that Cindrite next to strong heat gains one heat step (up to the maximum of 3).")
                .defineInRange("cindriteReheatChance", 0.25D, 0.0D, 1.0D);
        CINDRITE_NETHER_MIN_HEAT = b.comment("In the Nether and other ultra-warm dimensions, Cindrite never cools below this heat step (0 - 3).")
                .defineInRange("cindriteNetherMinHeat", 1, 0, 3);
        CINDRITE_COLD_QUENCH = b.comment("Hot Cindrite next to ice, snow or powder snow melts that block and loses one heat step (hiss and steam).",
                        "The cold block set is the block tag ohnomoremeteors:quenches_cindrite.")
                .define("cindriteColdQuench", true);
        b.pop();

        b.comment("Rimerite block behaviour.").push("rimerite");
        RIMERITE_COLD_CONTACT_FREEZES = b.comment("A living entity standing on Rimerite at full frost (3) starts freezing like in powder snow, after rimeriteColdContactTicks of contact.",
                        "Leather armour, creative mode and freeze-immune mobs are spared, as with powder snow.")
                .define("rimeriteColdContactFreezes", true);
        RIMERITE_COLD_CONTACT_TICKS = b.comment("Ticks of contact before the freezing starts (30 = 1.5 s). Short hops do not reset it.")
                .defineInRange("rimeriteColdContactTicks", 30, 1, 400);
        RIMERITE_FREEZE_WATER_CHANCE = b.comment("Chance per random tick that Rimerite freezes one adjacent water source block into ice. Only below warmAbove.")
                .defineInRange("rimeriteFreezeWaterChance", 0.25D, 0.0D, 1.0D);
        RIMERITE_SNOW_CHANCE = b.comment("Chance per random tick that Rimerite puts a snow layer on the air block above it. Only below rimeriteSnowBelow.")
                .defineInRange("rimeriteSnowChance", 0.15D, 0.0D, 1.0D);
        RIMERITE_MELTING = b.comment("Rimerite melts into water in warm and hot places and next to strong heat (air in the Nether and other ultra-warm dimensions).",
                        "Cold and neutral places never melt it on their own.",
                        "Turning it off also stops strong heat (lava, fire) stripping frost, so frost can come back even next to heat.")
                .define("rimeriteMelting", true);
        RIMERITE_MELT_CHANCE_WARM = b.comment("Chance per random tick that Rimerite in a WARM place melts.")
                .defineInRange("rimeriteMeltChanceWarm", 0.02D, 0.0D, 1.0D);
        RIMERITE_MELT_CHANCE_HOT = b.comment("Chance per random tick that Rimerite in a HOT place melts.")
                .defineInRange("rimeriteMeltChanceHot", 0.08D, 0.0D, 1.0D);
        RIMERITE_HEAT_MELT_ABOVE = b.comment("If the heat from the six neighbouring blocks adds up to at least this (TU; lava +3.0, fire +2.5, magma +1.5, lit campfire +1.5),",
                        "Rimerite melts with chance rimeriteMeltChanceHeat whatever the biome. Must be above 0, or Rimerite would melt everywhere.")
                .defineInRange("rimeriteHeatMeltAbove", 1.0D, 0.01D, 16.0D);
        RIMERITE_MELT_CHANCE_HEAT = b.comment("Chance per random tick that Rimerite next to strong heat melts.")
                .defineInRange("rimeriteMeltChanceHeat", 0.25D, 0.0D, 1.0D);
        RIMERITE_SNOW_BELOW = b.comment("Rimerite only lays snow when the temperature (TU) is below this (default 0.75: plains no, cold biomes yes).",
                        "Water and ice next to the block are left out of the temperature used for Rimerite's own snow, water freezing and frost hold.")
                .defineInRange("rimeriteSnowBelow", 0.75D, -10.0D, 10.0D);
        RIMERITE_COLD_EFFECT_MULT = b.comment("In a COLD place the freeze-water and snow chances are multiplied by this.")
                .defineInRange("rimeriteColdEffectMult", 1.5D, 0.0D, 10.0D);
        RIMERITE_FROST_DECAY_CHANCE = b.comment("Rimerite frost (0 - 3; crater blocks start at 3). Base chance per random tick that a frosted block loses one frost step (0.0 - 1.0).",
                        "It is multiplied by the climate multiplier below for the block's place.")
                .defineInRange("rimeriteFrostDecayChance", 0.3D, 0.0D, 1.0D);
        RIMERITE_FROST_DECAY_MULT_COLD = b.comment("Frost decay multiplier in a COLD place (0.0 - 10.0). 0 means frost never fades there.")
                .defineInRange("rimeriteFrostDecayMultCold", 0.0D, 0.0D, 10.0D);
        RIMERITE_FROST_DECAY_MULT_NEUTRAL = b.comment("Frost decay multiplier in a NEUTRAL place (0.0 - 10.0).")
                .defineInRange("rimeriteFrostDecayMultNeutral", 0.5D, 0.0D, 10.0D);
        RIMERITE_FROST_DECAY_MULT_WARM = b.comment("Frost decay multiplier in a WARM place (0.0 - 10.0).")
                .defineInRange("rimeriteFrostDecayMultWarm", 1.0D, 0.0D, 10.0D);
        RIMERITE_FROST_DECAY_MULT_HOT = b.comment("Frost decay multiplier in a HOT place and in ultra-warm dimensions such as the Nether (0.0 - 10.0).")
                .defineInRange("rimeriteFrostDecayMultHot", 2.0D, 0.0D, 10.0D);
        RIMERITE_FROST_REFREEZE_CHANCE = b.comment("Chance per random tick (0.0 - 1.0) that a frosted block regains one frost step, in a COLD place or next to ice, snow or powder snow.",
                        "Strong heat next to the block always strips frost first (rimeriteMeltChanceHeat chance), then melts it at frost 0.")
                .defineInRange("rimeriteFrostRefreezeChance", 0.2D, 0.0D, 1.0D);
        RIMERITE_THAW_TO_RIME_WORN_CHANCE = b.comment("When the last frost step (1 to 0) fades by normal thawing, chance that the block becomes Rime-worn Stone instead of bare Rimerite.",
                        "Melting into water and strong-heat stripping are not affected.")
                .defineInRange("rimeriteThawToRimeWornChance", 0.25D, 0.0D, 1.0D);
        b.pop();

        b.comment("Abilities of the meteor gear. Meteoric gear has none (its strength is durability).").push("gear");
        RIMERITE_ABILITIES = b.comment("Rimerite 'cool' abilities: pickaxe turns magma blocks into obsidian (not with Silk Touch) and keeps ice, shovel finds snowballs in sand and an extra one in snow layers,",
                        "hoe finds snowballs in leaves and grass, sword/axe chill targets and hit fire mobs harder, full armour chills melee attackers.",
                        "No snowball or magma bonus with Silk Touch.")
                .define("rimeriteAbilities", true);
        RIMERITE_SNOWBALL_CHANCE = b.comment("Chance that a Rimerite shovel finds 1-2 snowballs in sand (the hoe uses half of it on leaves and grass).")
                .defineInRange("rimeriteSnowballChance", 0.15D, 0.0D, 1.0D);
        CINDRITE_ABILITIES = b.comment("Cindrite 'heat' abilities: tools sometimes smelt what they mine, the pickaxe finds fire charges in coal ore, basalt and tuff,",
                        "sword/axe set targets on fire, the full armour set ignores fire and lava (each 10 seconds spent burning or in lava, added up, costs every piece 1 durability; the fire goes out once the wearer leaves fire and lava).")
                .define("cindriteAbilities", true);
        CINDRITE_ORE_SMELT_CHANCE = b.comment("Chance per drop that a Cindrite pickaxe smelts what an ore drops (raw iron to an iron ingot...).")
                .defineInRange("cindriteOreSmeltChance", 0.25D, 0.0D, 1.0D);
        CINDRITE_SMELT_CHANCE = b.comment("Chance per drop that a Cindrite shovel, axe or hoe smelts what it mines (sand to glass, logs to charcoal...).")
                .defineInRange("cindriteSmeltChance", 0.15D, 0.0D, 1.0D);
        CINDRITE_FIRE_CHARGE_CHANCE = b.comment("Chance that a Cindrite pickaxe also gets a fire charge from coal ore, basalt or tuff.")
                .defineInRange("cindriteFireChargeChance", 0.02D, 0.0D, 1.0D);
        CINDRITE_FURNACE_REPAIR = b.comment("Damaged Cindrite gear in a furnace's input slot is repaired by the heat fuel it burns (lava bucket a lot, magma block a little).",
                        "A repair uses up the rest of that fuel item's burn time.")
                .define("cindriteFurnaceRepair", true);
        CINDRITE_HEAT_FUELS = b.comment("Magma blocks, magma cream, blaze powder and fire charges count as furnace fuel (needed to repair with them; applies to all furnaces).",
                        "Also read on each client from its own config file: keep it the same on the server and all clients.")
                .define("cindriteHeatFuels", true);
        RIMERITE_FROSTERY_REPAIR = b.comment("Damaged Rimerite gear in a Frostery's input slot is repaired by the cold fuel it uses (blue ice a lot, a snowball a little).",
                        "A repair uses up the rest of that fuel item's burn time.",
                        "Also read on each client from its own config file: keep it the same on the server and all clients.")
                .define("rimeriteFrosteryRepair", true);
        b.pop();

        b.comment("Veyrite block behaviour.").push("veyrite");
        VEYRITE_DISCHARGE_CHANCE = b.comment("Chance per random tick that charged Veyrite loses one charge step (3 heated, 2-1 warm, 0 cool).")
                .defineInRange("veyriteDischargeChance", 0.2D, 0.0D, 1.0D);
        VEYRITE_POISON_ENABLED = b.comment("Players standing on charged Veyrite are poisoned: warm (charge 1-2) Poison I, heated (charge 3) Poison II. Cool Veyrite is harmless.")
                .define("veyritePoisonEnabled", true);
        VEYRITE_POISON_DELAY_TICKS = b.comment("Ticks a player can stand on charged Veyrite before the poison starts (20 ticks = 1 second).")
                .defineInRange("veyritePoisonDelayTicks", 40, 0, 1200);
        VEYRITE_POISON_MAX_TICKS = b.comment("The poison lasts as long as the player has stood on charged Veyrite, up to this many ticks.")
                .defineInRange("veyritePoisonMaxTicks", 600, 20, 12000);
        b.pop();

        b.comment("Oh My, Meteors! meteoric rock.").push("meteoricRock");
        METEORIC_ROCK_WATER_QUENCH = b.comment("Hot or mid (warm) meteoric rock that is not preserved and touches water drops straight to normal, with a hiss and steam.")
                .define("meteoricRockWaterQuench", true);
        METEORIC_ROCK_SOUNDS = b.comment("Meteoric rock plays this add-on's own break/step/place/hit/fall sounds instead of vanilla stone.",
                        "Also read on each client from its own config file: keep it the same on the server and all clients.")
                .define("meteoricRockSounds", true);
        b.pop();

        b.comment("Patches for bugs in Oh My, Meteors! 1.5.0. Each one can be turned off on its own. The active ones are listed in the log at server start.")
                .push("ommFixes");
        FIX_BASIC_LASER_RADIUS = b.comment("The Basic Meteor Laser uses its configured area radius (OMM basic_laser_area_radius, default 32) instead of its height (default 64).",
                        "Note: at OMM defaults this halves the real coverage of the basic laser, to what the OMM config says it is.")
                .define("fixBasicLaserRadius", true);
        FIX_SPECIAL_ONLY_HANG = b.comment("Stops the server hanging when a meteor size class only has special structures (an endless re-roll loop in OMM).")
                .define("fixSpecialOnlyHang", true);
        FIX_ENTITY_HIT_CLAIM_CHECK = b.comment("A meteor that hits an entity is removed only inside protected claims (Flan / YAWP), not outside them (OMM has the check the wrong way round).",
                        "Only matters with OMM explode_on_entity_collision=true.")
                .define("fixEntityHitClaimCheck", true);
        FIX_STALE_STRUCTURE_LIST = b.comment("Clears the cached OMM meteor structure list when a server stops, so opening another world does not reuse the old world's list.")
                .define("fixStaleStructureList", true);
        FIX_THIN_TERRAIN = b.comment("A meteor that passes through ground thinner than its dig-in depth (superflat, End islands, thin cave roofs) explodes at its first contact point instead of falling into the void.",
                        "Also removes a meteor that never hit anything once it is 64 blocks below the world.")
                .define("fixThinTerrain", true);
        b.pop();

        SPEC = b.build();
    }

    private Config() {
    }
}

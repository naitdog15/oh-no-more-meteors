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

    // ---- temperature (pass 2) ----
    public static final ForgeConfigSpec.DoubleValue COLD_BELOW;
    public static final ForgeConfigSpec.DoubleValue WARM_ABOVE;
    public static final ForgeConfigSpec.DoubleValue HOT_ABOVE;
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

    // ---- distant impact broadcast ----
    public static final ForgeConfigSpec.BooleanValue DISTANT_IMPACT_ENABLED;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_VERY_FAR_DISTANCE;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_MAX_DISTANCE;
    public static final ForgeConfigSpec.IntValue DISTANT_IMPACT_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue DISTANT_IMPACT_VOLUME;

    // ---- cindrite block ----
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_STEP_CHANCE;
    public static final ForgeConfigSpec.DoubleValue CINDRITE_COOL_TO_OBSIDIAN_CHANCE;
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

    // ---- OMM meteoric rock (pass 2) ----
    public static final ForgeConfigSpec.BooleanValue METEORIC_ROCK_WATER_QUENCH;
    public static final ForgeConfigSpec.BooleanValue METEORIC_ROCK_SOUNDS;

    // ---- OMM bug fixes (pass 2) ----
    public static final ForgeConfigSpec.BooleanValue FIX_BASIC_LASER_RADIUS;
    public static final ForgeConfigSpec.BooleanValue FIX_SPECIAL_ONLY_HANG;
    public static final ForgeConfigSpec.BooleanValue FIX_ENTITY_HIT_CLAIM_CHECK;
    public static final ForgeConfigSpec.BooleanValue FIX_STALE_STRUCTURE_LIST;

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
                .defineInRange("craterMaxBlockWrites", 4000, 1, 100000);
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
        CINDRITE_COOL_STEP_CHANCE = b.comment("Base chance per random tick that a hot Cindrite block cools down one heat step.",
                        "Multiplied by the cindriteCoolMult* value of the temperature band it sits in.")
                .defineInRange("cindriteCoolStepChance", 0.5D, 0.0D, 1.0D);
        CINDRITE_COOL_TO_OBSIDIAN_CHANCE = b.comment("When the heat runs out, chance that the block becomes obsidian instead of cooled Cindrite.")
                .defineInRange("cindriteCoolToObsidianChance", 0.25D, 0.0D, 1.0D);
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
        CINDRITE_REHEAT_ABOVE = b.comment("If the heat from the six neighbouring blocks adds up to at least this (TU; lava +3.0, fire +2.5, magma +1.5),",
                        "hot Cindrite stops cooling and may gain heat back. Default 1.6 sits just above one magma block, so crater magma alone",
                        "does not keep Cindrite hot forever; lava and fire still do.")
                .defineInRange("cindriteReheatAbove", 1.6D, 0.0D, 16.0D);
        CINDRITE_REHEAT_CHANCE = b.comment("Chance per random tick that Cindrite next to strong heat gains one heat step (up to the maximum of 3).")
                .defineInRange("cindriteReheatChance", 0.25D, 0.0D, 1.0D);
        CINDRITE_NETHER_MIN_HEAT = b.comment("In the Nether and other ultra-warm dimensions, Cindrite never cools below this heat step (0 - 3).")
                .defineInRange("cindriteNetherMinHeat", 1, 0, 3);
        CINDRITE_COLD_QUENCH = b.comment("Hot Cindrite next to ice, snow or powder snow melts that block and loses one heat step (hiss and steam).",
                        "The cold block set is the block tag ohnomoremeteors:quenches_cindrite.")
                .define("cindriteColdQuench", true);
        b.pop();

        b.comment("Rimerite block behaviour.").push("rimerite");
        RIMERITE_FREEZE_WATER_CHANCE = b.comment("Chance per random tick that Rimerite freezes one adjacent water source block into ice. Only below warmAbove.")
                .defineInRange("rimeriteFreezeWaterChance", 0.25D, 0.0D, 1.0D);
        RIMERITE_SNOW_CHANCE = b.comment("Chance per random tick that Rimerite puts a snow layer on the air block above it. Only below rimeriteSnowBelow.")
                .defineInRange("rimeriteSnowChance", 0.15D, 0.0D, 1.0D);
        RIMERITE_MELTING = b.comment("Rimerite melts into water in warm and hot places and next to strong heat (air in the Nether and other ultra-warm dimensions).",
                        "Cold and neutral places never melt it on their own.")
                .define("rimeriteMelting", true);
        RIMERITE_MELT_CHANCE_WARM = b.comment("Chance per random tick that Rimerite in a WARM place melts.")
                .defineInRange("rimeriteMeltChanceWarm", 0.02D, 0.0D, 1.0D);
        RIMERITE_MELT_CHANCE_HOT = b.comment("Chance per random tick that Rimerite in a HOT place melts.")
                .defineInRange("rimeriteMeltChanceHot", 0.08D, 0.0D, 1.0D);
        RIMERITE_HEAT_MELT_ABOVE = b.comment("If the heat from the six neighbouring blocks adds up to at least this (TU; lava +3.0, fire +2.5, magma +1.5, lit campfire +1.5),",
                        "Rimerite melts with chance rimeriteMeltChanceHeat whatever the biome.")
                .defineInRange("rimeriteHeatMeltAbove", 1.0D, 0.0D, 16.0D);
        RIMERITE_MELT_CHANCE_HEAT = b.comment("Chance per random tick that Rimerite next to strong heat melts.")
                .defineInRange("rimeriteMeltChanceHeat", 0.25D, 0.0D, 1.0D);
        RIMERITE_SNOW_BELOW = b.comment("Rimerite only lays snow when the temperature (TU) is below this (default 0.75: plains no, cold biomes yes).")
                .defineInRange("rimeriteSnowBelow", 0.75D, -10.0D, 10.0D);
        RIMERITE_COLD_EFFECT_MULT = b.comment("In a COLD place the freeze-water and snow chances are multiplied by this.")
                .defineInRange("rimeriteColdEffectMult", 1.5D, 0.0D, 10.0D);
        b.pop();

        b.comment("Abilities of the meteor gear. Meteoric gear has none (its strength is durability).").push("gear");
        RIMERITE_ABILITIES = b.comment("Rimerite 'cool' abilities: pickaxe turns magma blocks into obsidian and keeps ice, shovel finds snowballs in sand,",
                        "hoe finds snowballs in leaves and grass, sword/axe chill targets and hit fire mobs harder, full armour chills melee attackers.")
                .define("rimeriteAbilities", true);
        RIMERITE_SNOWBALL_CHANCE = b.comment("Chance that a Rimerite shovel finds 1-2 snowballs in sand (the hoe uses half of it on leaves and grass).")
                .defineInRange("rimeriteSnowballChance", 0.15D, 0.0D, 1.0D);
        CINDRITE_ABILITIES = b.comment("Cindrite 'heat' abilities: tools sometimes smelt what they mine, the pickaxe finds fire charges in coal ore, basalt and tuff,",
                        "sword/axe set targets on fire, the full armour set ignores fire and lava (wearing 1 durability per piece every 10 seconds while burning).")
                .define("cindriteAbilities", true);
        CINDRITE_ORE_SMELT_CHANCE = b.comment("Chance per drop that a Cindrite pickaxe smelts what an ore drops (raw iron to an iron ingot...).")
                .defineInRange("cindriteOreSmeltChance", 0.25D, 0.0D, 1.0D);
        CINDRITE_SMELT_CHANCE = b.comment("Chance per drop that a Cindrite shovel, axe or hoe smelts what it mines (sand to glass, logs to charcoal...).")
                .defineInRange("cindriteSmeltChance", 0.15D, 0.0D, 1.0D);
        CINDRITE_FIRE_CHARGE_CHANCE = b.comment("Chance that a Cindrite pickaxe also gets a fire charge from coal ore, basalt or tuff.")
                .defineInRange("cindriteFireChargeChance", 0.08D, 0.0D, 1.0D);
        CINDRITE_FURNACE_REPAIR = b.comment("Damaged Cindrite gear in a furnace's input slot is repaired by the heat fuel it burns (lava bucket a lot, magma block a little).")
                .define("cindriteFurnaceRepair", true);
        CINDRITE_HEAT_FUELS = b.comment("Magma blocks, magma cream, blaze powder and fire charges count as furnace fuel (needed to repair with them; applies to all furnaces).")
                .define("cindriteHeatFuels", true);
        RIMERITE_FROSTERY_REPAIR = b.comment("Damaged Rimerite gear in a Frostery's input slot is repaired by the cold fuel it uses (blue ice a lot, a snowball a little).")
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
        METEORIC_ROCK_SOUNDS = b.comment("Meteoric rock plays this add-on's own break/step/place/hit/fall sounds instead of vanilla stone.")
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
        b.pop();

        SPEC = b.build();
    }

    private Config() {
    }
}

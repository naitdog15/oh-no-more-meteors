package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.block.CindriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.VeyriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * The two kinds of special meteor this mod adds. A meteor is marked by one string in its persistent
 * (ForgeData) tag under {@link #NBT_KEY}. That key is a shared claim convention: another add-on
 * (Building Bridges writes "buildingbridges:ae2") uses the same key, and whoever writes it first owns
 * the meteor. This mod only ever acts on its own two values.
 */
public enum MeteorVariant {
    CINDRITE("ohnomoremeteors:cindrite"),
    RIMERITE("ohnomoremeteors:rimerite"),
    VEYRITE("ohnomoremeteors:veyrite");

    /** Root key of the entity's persistent data. */
    public static final String NBT_KEY = "omm_variant";

    private final String claim;

    MeteorVariant(String claim) {
        this.claim = claim;
    }

    /** The exact string stored under {@link #NBT_KEY}. */
    public String claim() {
        return claim;
    }

    /** Writes this variant's claim onto the entity. */
    public void writeTo(Entity entity) {
        entity.getPersistentData().putString(NBT_KEY, claim);
    }

    /** Reads the variant from the claim string, or null for an unknown or foreign value. */
    @Nullable
    public static MeteorVariant fromClaim(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (MeteorVariant v : values()) {
            if (v.claim.equals(value)) {
                return v;
            }
        }
        return null;
    }

    /** The variant this entity carries, or null if it is unclaimed or claimed by someone else. */
    @Nullable
    public static MeteorVariant fromEntity(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NBT_KEY, Tag.TAG_STRING)) {
            return null;
        }
        return fromClaim(data.getString(NBT_KEY));
    }

    /** True if the entity has ANY value under the claim key (ours or foreign). */
    public static boolean hasAnyClaim(Entity entity) {
        return entity.getPersistentData().contains(NBT_KEY);
    }

    /** Config switch for this variant. */
    public boolean isEnabled() {
        return switch (this) {
            case CINDRITE -> Config.CINDRITE_ENABLED.get();
            case RIMERITE -> Config.RIMERITE_ENABLED.get();
            case VEYRITE -> Config.VEYRITE_ENABLED.get();
        };
    }

    /** Config chance (0..1) of a new meteor becoming this variant. */
    public double chance() {
        return switch (this) {
            case CINDRITE -> Config.CINDRITE_CHANCE.get();
            case RIMERITE -> Config.RIMERITE_CHANCE.get();
            case VEYRITE -> Config.VEYRITE_CHANCE.get();
        };
    }

    /**
     * Config chance (0..1) of a new meteor becoming this variant, weighted by the climate band of the place the
     * meteor will land (see {@link #columnBand}). The no-arg {@link #chance()} stays the plain config value.
     * Hot places favour fire meteors, cold places favour ice meteors; WARM and NEUTRAL are unweighted.
     * The result is clamped to 0..1, so a weight above 1 can never push a chance past certainty.
     */
    public double chance(ThermalReader.Band band) {
        double base = chance();
        double weight = 1.0;
        if (this == VEYRITE) {
            return Mth.clamp(base, 0.0, 1.0); // space meteors ignore the climate
        }
        if (band == ThermalReader.Band.HOT) {
            weight = this == CINDRITE ? Config.HOT_CINDRITE_WEIGHT.get() : Config.HOT_RIMERITE_WEIGHT.get();
        } else if (band == ThermalReader.Band.COLD) {
            weight = this == CINDRITE ? Config.COLD_CINDRITE_WEIGHT.get() : Config.COLD_RIMERITE_WEIGHT.get();
        }
        return Mth.clamp(base * weight, 0.0, 1.0);
    }

    /** Same as {@link #chance(ThermalReader.Band)}, reading the band at the meteor's column first. */
    public double chance(ServerLevel level, BlockPos meteorPos) {
        return chance(columnBand(level, meteorPos));
    }

    /**
     * The climate band of the column a new meteor is in. Uses the biome base temperature (it needs no chunk
     * and cannot be NaN). The column's surface height comes from the WORLD_SURFACE heightmap only when the
     * chunk is already loaded; otherwise the meteor's own position is used, so no chunk is ever loaded here.
     * Returns NEUTRAL (no weighting) when the feature is switched off or the reading fails.
     */
    public static ThermalReader.Band columnBand(ServerLevel level, BlockPos meteorPos) {
        if (!Config.BIOME_WEIGHTED_VARIANTS.get()) {
            return ThermalReader.Band.NEUTRAL;
        }
        try {
            BlockPos column = meteorPos;
            if (level.hasChunkAt(meteorPos)) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, meteorPos.getX(), meteorPos.getZ());
                y = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                column = new BlockPos(meteorPos.getX(), y, meteorPos.getZ());
            }
            float base = ThermalReader.biomeBase(level, column);
            return Float.isNaN(base) ? ThermalReader.Band.NEUTRAL : ThermalReader.band(base);
        } catch (RuntimeException ex) {
            return ThermalReader.Band.NEUTRAL; // fall back to the unweighted roll
        }
    }

    /** The block a crater is mostly made of (cindrite is placed fully hot). */
    public BlockState craterState() {
        return switch (this) {
            case CINDRITE -> CindriteBlock.hotState();
            case RIMERITE -> com.temog.notminelegacy.ohnomoremeteors.block.RimeriteBlock.frozenState();
            case VEYRITE -> VeyriteBlock.chargedState();
        };
    }

    /** The rare crater block: magma block for fire, blue ice for ice, crying obsidian for space. */
    public BlockState rareState() {
        return switch (this) {
            case CINDRITE -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case RIMERITE -> Blocks.BLUE_ICE.defaultBlockState();
            case VEYRITE -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
        };
    }
}

package com.temog.notminelegacy.ohnomoremeteors.temperature;

import com.temog.notminelegacy.environment.temperature.NmlTemperature;
import com.temog.notminelegacy.environment.temperature.TemperatureChannel;
import com.temog.notminelegacy.environment.temperature.ThermalSource;
import com.temog.notminelegacy.environment.temperature.ThermalSources;
import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The ONLY class in Oh No, More Meteors! that talks to the Not Mine Legacy (NML) temperature API.
 * Every other class asks this one.
 *
 * Units: TU, the NML thermal unit. It is a biome-like index: plains about 0.8, forests about 0.7,
 * desert 2.0, snowy biomes about 0.0 or below. Block heat sources are signed TU deltas
 * (lava +3.0, fire +2.5, magma +1.5, ice -0.8, blue ice -1.6, water -0.4).
 *
 * Rules every caller must follow:
 *  - Server thread only (random ticks, scheduled ticks, impact code, the join-time roll). Off the server
 *    thread every reading below except {@link #biomeBase} is NaN.
 *  - A NaN answer means "temperature unknown" (NML is switched off, the column is not loaded, or a call
 *    came from the wrong thread). The caller must then FREEZE: change no block state, roll nothing.
 *    Test with {@code Float.isNaN(value)}.
 *  - Nothing is cached here. Each call reads the world as it is now.
 *
 * Self-feedback rule: this mod's own blocks (Cindrite, Rimerite) are registered as NML heat / cold sources by
 * datapack JSON. So a block that decides its own state must never read NML's LOCAL or EFFECTIVE channel at its
 * own position, because that sum may include the block itself. This class therefore never calls
 * {@code local}, {@code effective} or {@code localRanged}. It reads the AMBIENT channel at the position plus the
 * sources of the six neighbouring blocks, which never includes the block itself.
 */
public final class ThermalReader {

    /** Temperature bands, cut by the config keys temperature.coldBelow / warmAbove / hotAbove. */
    public enum Band {
        /** Below coldBelow (default 0.15 TU): snowy and frozen biomes. */
        COLD,
        /** From coldBelow up to warmAbove (default 0.15 - 1.0 TU): plains, forests, most of the world. */
        NEUTRAL,
        /** From warmAbove up to hotAbove (default 1.0 - 1.5 TU): savannas, jungles and the like. */
        WARM,
        /** At or above hotAbove (default 1.5 TU): deserts, badlands, the Nether. */
        HOT
    }

    private static final Direction[] DIRECTIONS = Direction.values();

    /** A fluid source block holds this many fluid levels; used to scale a fluid source by how full it is. */
    private static final float FULL_FLUID_AMOUNT = 8.0F;

    /** One warning per game run is enough when NML throws; afterwards the reader just answers NaN quietly. */
    private static volatile boolean loggedFailure;

    private ThermalReader() {
    }

    // ------------------------------------------------------------------ total reading

    /**
     * The temperature a block at {@code pos} feels, in TU: {@link #ambient} plus {@link #neighbourHeat}.
     * NaN when either part is unknown (caller freezes).
     */
    public static float read(ServerLevel level, BlockPos pos) {
        return read(level, pos, null);
    }

    /**
     * Same as {@link #read(ServerLevel, BlockPos)}, but neighbours whose state matches {@code skipNeighbour}
     * are left out of the neighbour sum (for example, other blocks of the same kind).
     */
    public static float read(ServerLevel level, BlockPos pos, @Nullable Predicate<BlockState> skipNeighbour) {
        float ambient = ambient(level, pos);
        if (Float.isNaN(ambient)) {
            return Float.NaN;
        }
        float neighbours = neighbourHeat(level, pos, skipNeighbour);
        if (Float.isNaN(neighbours)) {
            return Float.NaN;
        }
        return ambient + neighbours;
    }

    // ------------------------------------------------------------------ the two parts

    /**
     * NML AMBIENT temperature at {@code pos}, in TU: biome noise, dimension and biome offsets, altitude,
     * day/night, weather and the underground blend toward 0.6. Never includes block sources.
     * NaN when the AMBIENT channel is unavailable, the column is not loaded, or this is not the server thread.
     */
    public static float ambient(ServerLevel level, BlockPos pos) {
        if (!onServerThread(level)) {
            return Float.NaN;
        }
        try {
            if (!NmlTemperature.isAvailable(level, TemperatureChannel.AMBIENT)) {
                return Float.NaN;
            }
            return NmlTemperature.ambient(level, pos);
        } catch (RuntimeException ex) {
            warnOnce(ex);
            return Float.NaN;
        }
    }

    /**
     * Signed sum, in TU, of the heat (positive) and cold (negative) that the six neighbouring blocks send into
     * {@code pos}. The block at {@code pos} itself is never counted (self-feedback rule).
     * For each neighbour: its NML block source, or, if it has none, the source of the fluid it holds (so lava
     * and water count through the fluid path, and a waterlogged block is counted once). A source counts only if
     * it emits toward {@code pos}; it counts at full strength (contact range). Neighbours in unloaded chunks or
     * outside the world height count as nothing.
     * NaN when NML's LOCAL channel is unavailable (kill switch) or this is not the server thread.
     */
    public static float neighbourHeat(ServerLevel level, BlockPos pos) {
        return neighbourHeat(level, pos, null);
    }

    /**
     * Same as {@link #neighbourHeat(ServerLevel, BlockPos)}, but neighbours whose state matches
     * {@code skipNeighbour} are left out (null skips nothing).
     */
    public static float neighbourHeat(ServerLevel level, BlockPos pos, @Nullable Predicate<BlockState> skipNeighbour) {
        if (!onServerThread(level)) {
            return Float.NaN;
        }
        try {
            if (!NmlTemperature.isAvailable(level, TemperatureChannel.LOCAL)) {
                return Float.NaN;
            }
            float sum = 0.0F;
            BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
            for (Direction face : DIRECTIONS) {
                neighbour.setWithOffset(pos, face);
                // isLoaded never loads a chunk and is false outside the world height.
                if (!level.isLoaded(neighbour)) {
                    continue;
                }
                BlockState state = level.getBlockState(neighbour);
                if (skipNeighbour != null && skipNeighbour.test(state)) {
                    continue;
                }
                // The neighbour sits on side "face" of pos, so its heat travels in the opposite direction to reach pos.
                sum += sourceValue(level, state, face.getOpposite());
            }
            return sum;
        } catch (RuntimeException ex) {
            warnOnce(ex);
            return Float.NaN;
        }
    }

    /** The value one neighbour state sends in {@code emissionDirection}, or 0 when it is not a source that way. */
    private static float sourceValue(ServerLevel level, BlockState state, Direction emissionDirection) {
        ThermalSource source = ThermalSources.forState(level, state);
        if (source != null) {
            // NML resolves a block state to its own entry, else to the entry of the fluid it holds, with any
            // fluid-amount scaling already applied. So this one lookup covers lava, water and waterlogged blocks.
            return source.emitsToward(emissionDirection) ? source.strength() : 0.0F;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            return 0.0F;
        }
        // Fallback only: a fluid with an entry that the block-state table did not carry.
        ThermalSource fluidSource = ThermalSources.forFluid(level, fluid.getType());
        if (fluidSource == null || !fluidSource.emitsToward(emissionDirection)) {
            return 0.0F;
        }
        float strength = fluidSource.strength();
        if ((fluidSource.flags() & ThermalSource.FLAG_SCALE_WITH_FLUID_AMOUNT) != 0) {
            strength *= Math.min(fluid.getAmount(), (int) FULL_FLUID_AMOUNT) / FULL_FLUID_AMOUNT;
        }
        return strength;
    }

    // ------------------------------------------------------------------ biome base and bands

    /**
     * Raw biome base temperature at {@code pos}, in TU (NML BIOME_BASE). Works on any thread and level, never
     * NaN. Use it only for the meteor spawn roll; block behaviour uses {@link #read}.
     */
    public static float biomeBase(LevelReader level, BlockPos pos) {
        return NmlTemperature.biomeBase(level, pos);
    }

    /**
     * The band a temperature (TU) falls in, from the config keys temperature.coldBelow, warmAbove and hotAbove:
     * t < coldBelow is COLD; t >= hotAbove is HOT; otherwise t >= warmAbove is WARM; everything else NEUTRAL.
     * NaN gives NEUTRAL, but callers must test {@code Float.isNaN} first and freeze instead.
     */
    public static Band band(float t) {
        if (t < Config.COLD_BELOW.get()) {
            return Band.COLD;
        }
        if (t >= Config.HOT_ABOVE.get()) {
            return Band.HOT;
        }
        if (t >= Config.WARM_ABOVE.get()) {
            return Band.WARM;
        }
        return Band.NEUTRAL;
    }

    /** True in dimensions where water cannot exist (the Nether and any other "ultra-warm" dimension type). */
    public static boolean isUltraWarm(Level level) {
        return level.dimensionType().ultraWarm();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean onServerThread(ServerLevel level) {
        return level.getServer().isSameThread();
    }

    private static void warnOnce(RuntimeException ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Reading the Not Mine Legacy temperature failed; temperature-driven behaviour stays frozen.", ex);
        }
    }
}

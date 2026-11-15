package com.temog.notminelegacy.ohnomoremeteors.block;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Rimerite: the ice-meteor crater block. Slightly slippery (friction 0.9 is set in ModBlocks).
 * It has no blockstate properties, and it always gets random ticks.
 *
 * Random tick behaviour (pass 2, T7/T8). The temperature T is read through ThermalReader: the ambient
 * temperature at the block plus the heat and cold of its six neighbours. Other Rimerite is left out of that sum,
 * so a crater does not keep itself frozen (see CONTRACT-PASS2.md 2.1); everything else (hot Cindrite, lava, ice)
 * still counts. When the temperature is unknown (NML switched off, wrong thread) the block does nothing that tick.
 *  - Melt (config rimeriteMelting, on by default):
 *      - next to strong heat (the neighbours add up to at least rimeriteHeatMeltAbove, e.g. lava, fire, magma,
 *        a lit campfire, hot Cindrite): chance rimeriteMeltChanceHeat, whatever the biome;
 *      - otherwise in a HOT place rimeriteMeltChanceHot, in a WARM place rimeriteMeltChanceWarm;
 *      - cold and neutral places never melt it on their own.
 *    A melted block becomes a water source, or air with a fizz in ultra-warm dimensions (the Nether). A block
 *    that melted does nothing else that tick.
 *  - Cold effects run only while T is below temperature.warmAbove:
 *      - it may freeze one adjacent water source block into ice (rimeriteFreezeWaterChance);
 *      - while T is also below rimeriteSnowBelow it may lay a snow layer on the empty block above it, if snow can
 *        stay there (rimeriteSnowChance).
 *    In a COLD place both chances are multiplied by rimeriteColdEffectMult.
 */
public class RimeriteBlock extends Block {

    /** Leaves other Rimerite out of the temperature reading, so a crater does not hold its own cold. */
    private static final Predicate<BlockState> SAME_KIND = s -> s.getBlock() instanceof RimeriteBlock;

    public RimeriteBlock(Properties properties) {
        super(properties);
    }

    /** Rimerite can always melt, freeze water or snow, so every state ticks (it is also registered with randomTicks()). */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // One temperature reading per tick: the ambient part and the neighbour part, other Rimerite left out.
        float ambient = ThermalReader.ambient(level, pos);
        if (Float.isNaN(ambient)) {
            return; // Temperature unknown: freeze.
        }
        float neighbourHeat = ThermalReader.neighbourHeat(level, pos, SAME_KIND);
        if (Float.isNaN(neighbourHeat)) {
            return; // Temperature unknown: freeze.
        }
        float temperature = ambient + neighbourHeat;
        ThermalReader.Band band = ThermalReader.band(temperature);

        // T7: melt ladder.
        if (Config.RIMERITE_MELTING.get()) {
            double meltChance;
            if (neighbourHeat >= Config.RIMERITE_HEAT_MELT_ABOVE.get()) {
                // Strong heat next to the block: the heat chance applies whatever the biome (T7).
                meltChance = Config.RIMERITE_MELT_CHANCE_HEAT.get();
            } else {
                meltChance = switch (band) {
                    case HOT -> Config.RIMERITE_MELT_CHANCE_HOT.get();
                    case WARM -> Config.RIMERITE_MELT_CHANCE_WARM.get();
                    default -> 0.0D;
                };
            }
            if (meltChance > 0.0D && random.nextDouble() < meltChance) {
                melt(level, pos);
                return;
            }
        }

        // T8: cold effects only below warmAbove; stronger in a COLD place.
        if (temperature >= Config.WARM_ABOVE.get()) {
            return;
        }
        double coldMult = band == ThermalReader.Band.COLD ? Config.RIMERITE_COLD_EFFECT_MULT.get() : 1.0D;

        if (random.nextDouble() < Math.min(1.0D, Config.RIMERITE_FREEZE_WATER_CHANCE.get() * coldMult)) {
            freezeOneNeighbour(level, pos, random);
        }

        if (temperature < Config.RIMERITE_SNOW_BELOW.get()
                && random.nextDouble() < Math.min(1.0D, Config.RIMERITE_SNOW_CHANCE.get() * coldMult)) {
            BlockPos above = pos.above();
            BlockState snow = Blocks.SNOW.defaultBlockState();
            // Only under open sky: underground ambient trends to about 0.6, which would otherwise snow caves in every biome.
            if (level.isLoaded(above) && level.isEmptyBlock(above) && level.canSeeSky(above) && snow.canSurvive(level, above)) {
                level.setBlockAndUpdate(above, snow);
            }
        }
    }

    /** Melts the block: a water source, or air with a fizz and a puff of steam in ultra-warm dimensions. */
    private static void melt(ServerLevel level, BlockPos pos) {
        if (ThermalReader.isUltraWarm(level)) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            // Placeholder fizz (vanilla fire extinguish), as when water is poured out in the Nether.
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    2.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    6, 0.25D, 0.25D, 0.25D, 0.01D);
            return;
        }
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
    }

    /** Turns one randomly chosen adjacent water source block into ice, if there is one with nothing standing in it. */
    private static void freezeOneNeighbour(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState ice = Blocks.ICE.defaultBlockState();
        List<BlockPos> candidates = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            // isLoaded never loads a chunk.
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            BlockState neighbourState = level.getBlockState(neighbour);
            if (neighbourState.is(Blocks.WATER)
                    && neighbourState.getFluidState().isSource()
                    && level.isUnobstructed(ice, neighbour, CollisionContext.empty())) {
                candidates.add(neighbour);
            }
        }
        if (!candidates.isEmpty()) {
            level.setBlockAndUpdate(candidates.get(random.nextInt(candidates.size())), ice);
        }
    }
}

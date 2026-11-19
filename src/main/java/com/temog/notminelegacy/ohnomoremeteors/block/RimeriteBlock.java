package com.temog.notminelegacy.ohnomoremeteors.block;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Rimerite: the ice-meteor crater block. Slightly slippery (friction 0.9 is set in ModBlocks).
 * It always gets random ticks. Frost (0 - 3, like Cindrite heat): a meteor crater places frost 3, the block
 * placed from the item is frost 0. Frost 3 sheds a cold mist particle on the client.
 *
 * Frost rules (random tick, after the temperature reading):
 *  - strong heat next to the block (same test as the melt ladder) strips one frost step with chance
 *    rimeriteMeltChanceHeat instead of melting it, and nothing else happens that tick;
 *  - otherwise frost fades with chance rimeriteFrostDecayChance times the climate multiplier of the place
 *    (COLD is 0 by default, so frost holds in the cold); a block that faded does nothing else that tick;
 *  - in a COLD place, or next to ice / snow (tag ohnomoremeteors:refreezes_rimerite), frost comes back one step
 *    with chance rimeriteFrostRefreezeChance (never above 3);
 *  - the melt ladder below only runs at frost 0.
 *
 *
 * Random tick behaviour (pass 2, T7/T8). The felt temperature T is read through ThermalReader: the ambient
 * temperature at the block plus the heat and cold of its six neighbours plus the rain/snow delta (temperature.rainDelta / snowDelta,
 * so snowfall pushes T colder and holds frost, rain in a cold place does too). "Strong heat" means neighbours + weather only. Other Rimerite is left out of that sum,
 * so a crater does not keep itself frozen (see CONTRACT-PASS2.md 2.1); everything else (hot Cindrite, lava, ice)
 * still counts there. Rimerite's own cold effects (frost decay and refreeze, freezing water, snow) use a second
 * sum that also leaves out water, ice and snow neighbours, so the ice and snow it makes cannot hold itself in the cold band.
 * The strong-heat test and the melt ladder keep the full sum. When the temperature is unknown (NML switched off, wrong thread) the block does nothing that tick.
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

    /**
     * Same as SAME_KIND, and also leaves water, ice and snow neighbours out. Used for Rimerite's OWN cold effects (frost
     * decay and refreeze, freezing water, snow): the water, ice and snow it makes must not count as cold that then
     * holds the block in the cold band.
     */
    private static final Predicate<BlockState> SAME_KIND_OR_WATER_ICE =
            SAME_KIND.or(s -> s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.getFluidState().is(FluidTags.WATER));

    public static final int MAX_FROST = 3;
    public static final IntegerProperty FROST = IntegerProperty.create("frost", 0, MAX_FROST);

    /** Blocks that let frost come back (ice, snow, powder snow). */
    public static final TagKey<Block> REFREEZES_RIMERITE = TagKey.create(Registries.BLOCK, OhNoMoreMeteors.id("refreezes_rimerite"));

    public RimeriteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FROST, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FROST);
    }

    /** The state a meteor impact places: frost 3. */
    public static BlockState frozenState() {
        return ModBlocks.RIMERITE.get().defaultBlockState().setValue(FROST, MAX_FROST);
    }

    /** Client: a deep-frozen block sheds a slow wisp of cold mist from a side face that is open to the air. */
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        // Like redstone ore: hitting / starting to mine deep-frozen Rimerite knocks off a few wisps of cold mist (client side only).
        if (level.isClientSide() && state.getValue(FROST) == MAX_FROST) {
            RandomSource r = level.getRandom();
            for (int i = 0, n = 2 + level.getRandom().nextInt(2); i < n; i++) {
                level.addParticle(ModParticles.RIMERITE_FROST_MIST.get(),
                        pos.getX() + 0.15D + r.nextDouble() * 0.7D, pos.getY() + 0.3D + r.nextDouble() * 0.6D, pos.getZ() + 0.15D + r.nextDouble() * 0.7D,
                        0.0D, -0.01D, 0.0D);
            }
        }
        super.attack(state, level, pos, player);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        // Breaking deep-frozen (frost 3) Rimerite throws a burst of rime crystals and cold mist, seen by everyone nearby.
        try {
            if (level instanceof ServerLevel server && state.getValue(FROST) == MAX_FROST
                    && com.temog.notminelegacy.ohnomoremeteors.meteor.VariantTuning.burstOn(com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant.RIMERITE)) {
                double x = pos.getX() + 0.5D, y = pos.getY() + 0.5D, z = pos.getZ() + 0.5D;
                server.sendParticles(ModParticles.RIMERITE_BURST.get(), x, y, z, 8, 0.3D, 0.3D, 0.3D, 0.05D);
                server.sendParticles(ModParticles.RIMERITE_FROST_MIST.get(), x, y, z, 6, 0.4D, 0.2D, 0.4D, 0.01D);
            }
        } catch (RuntimeException ex) {
            // cosmetic only; never block the break
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        try {
            if (state.getValue(FROST) < MAX_FROST || random.nextInt(4) != 0) {
                return;
            }
            Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos outside = pos.relative(face);
            if (level.getBlockState(outside).isSolidRender(level, outside)) {
                return;
            }
            double x = pos.getX() + 0.5D + face.getStepX() * 0.55D + (face.getAxis() == Direction.Axis.X ? 0.0D : random.nextDouble() - 0.5D) * 0.9D;
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + 0.5D + face.getStepZ() * 0.55D + (face.getAxis() == Direction.Axis.Z ? 0.0D : random.nextDouble() - 0.5D) * 0.9D;
            // Gentle outward push so the mist drifts about half a block clear of the face before it sinks.
            double push = 0.018D + random.nextDouble() * 0.012D;
            level.addParticle(ModParticles.RIMERITE_FROST_MIST.get(), x, y, z,
                    face.getStepX() * push, -0.005D - random.nextDouble() * 0.01D, face.getStepZ() * push);
        } catch (RuntimeException | LinkageError ignored) {
            // Cosmetic only: never break rendering.
        }
    }

    /** Rimerite can always melt, freeze water or snow, so every state ticks (it is also registered with randomTicks()). */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // One felt-temperature reading per tick: ambient + neighbour heat/cold (other Rimerite left out) + rain/snow delta.
        ThermalReader.Felt felt = ThermalReader.feltTemperature(level, pos, SAME_KIND);
        if (!felt.known()) {
            return; // Temperature unknown: freeze.
        }
        float temperature = felt.total();
        // Strong heat is judged on the local part (neighbours + weather) so a hot biome alone is not "strong heat".
        float neighbourHeat = felt.local();
        ThermalReader.Band band = ThermalReader.band(temperature);
        // Rimerite's own cold effects (frost decay/refreeze, freezing, snow) use a sum that also leaves out water, ice and
        // snow neighbours, so the cold it makes itself does not hold it in the cold band. Heat tests keep the full sum.
        ThermalReader.Felt own = new ThermalReader.Felt(felt.ambient(), ThermalReader.neighbourHeat(level, pos, SAME_KIND_OR_WATER_ICE), felt.weather());
        if (!own.known()) {
            return; // Temperature unknown: freeze.
        }
        float ownTemperature = own.total();
        ThermalReader.Band ownBand = ThermalReader.band(ownTemperature);

        // Frost steps (see the class comment). Strong heat strips frost before anything can melt.
        int frost = state.getValue(FROST);
        boolean strongHeat = neighbourHeat >= Config.RIMERITE_HEAT_MELT_ABOVE.get() && Config.RIMERITE_MELTING.get();
        if (frost > 0) {
            if (strongHeat) {
                if (random.nextDouble() < Config.RIMERITE_MELT_CHANCE_HEAT.get()) {
                    level.setBlockAndUpdate(pos, state.setValue(FROST, frost - 1));
                }
                return;
            }
            // Ice or snow next to the block (tag refreezes_rimerite) holds its frost like a COLD place does. The own sum
            // leaves ice and snow out (T1-03) so they cannot drive snow-laying or freezing, but they still keep frost.
            ThermalReader.Band frostBand = ThermalReader.isUltraWarm(level) ? ThermalReader.Band.HOT
                    : nextToRefreezer(level, pos) ? ThermalReader.Band.COLD : ownBand;
            double decay = Math.min(1.0D, Config.RIMERITE_FROST_DECAY_CHANCE.get() * switch (frostBand) {
                case COLD -> Config.RIMERITE_FROST_DECAY_MULT_COLD.get();
                case NEUTRAL -> Config.RIMERITE_FROST_DECAY_MULT_NEUTRAL.get();
                case WARM -> Config.RIMERITE_FROST_DECAY_MULT_WARM.get();
                case HOT -> Config.RIMERITE_FROST_DECAY_MULT_HOT.get();
            });
            if (decay > 0.0D && random.nextDouble() < decay) {
                if (frost == 1 && random.nextDouble() < Config.RIMERITE_THAW_TO_RIME_WORN_CHANCE.get()) {
                    // The last frost fades and leaves bare, weathered stone behind.
                    level.setBlockAndUpdate(pos, ModBlocks.RIME_WORN_STONE.get().defaultBlockState());
                    return;
                }
                level.setBlockAndUpdate(pos, state.setValue(FROST, frost - 1));
                return;
            }
        }
        if (!strongHeat && frost < MAX_FROST && (ownBand == ThermalReader.Band.COLD || nextToRefreezer(level, pos))
                && random.nextDouble() < Config.RIMERITE_FROST_REFREEZE_CHANCE.get()) {
            level.setBlockAndUpdate(pos, state.setValue(FROST, frost + 1));
            return;
        }

        // T7: melt ladder (only at frost 0; frosted blocks only lose frost above).
        if (frost == 0 && Config.RIMERITE_MELTING.get()) {
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
        if (ownTemperature >= Config.WARM_ABOVE.get()) {
            return;
        }
        double coldMult = ownBand == ThermalReader.Band.COLD ? Config.RIMERITE_COLD_EFFECT_MULT.get() : 1.0D;

        if (random.nextDouble() < Math.min(1.0D, Config.RIMERITE_FREEZE_WATER_CHANCE.get() * coldMult)) {
            freezeOneNeighbour(level, pos, random);
        }

        if (ownTemperature < Config.RIMERITE_SNOW_BELOW.get()
                && random.nextDouble() < Math.min(1.0D, Config.RIMERITE_SNOW_CHANCE.get() * coldMult)) {
            BlockPos above = pos.above();
            BlockState snow = Blocks.SNOW.defaultBlockState();
            // Only under open sky: underground ambient trends to about 0.6, which would otherwise snow caves in every biome.
            if (level.isLoaded(above) && level.isEmptyBlock(above) && level.canSeeSky(above) && snow.canSurvive(level, above)) {
                level.setBlockAndUpdate(above, snow);
            }
        }
    }

    /** True when a face neighbour is ice, packed or blue ice, a snow block, snow layer or powder snow. */
    private static boolean nextToRefreezer(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (level.isLoaded(neighbour) && level.getBlockState(neighbour).is(REFREEZES_RIMERITE)) {
                return true;
            }
        }
        return false;
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

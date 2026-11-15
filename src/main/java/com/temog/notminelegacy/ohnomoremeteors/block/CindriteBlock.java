package com.temog.notminelegacy.ohnomoremeteors.block;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Cindrite: the fire-meteor crater block. Heat runs 3 (just landed) down to 0 (cooled).
 *
 * Behaviour (server side):
 *  - Only a hot block (heat above 0) gets random ticks. Cooled Cindrite (heat 0) is final: nothing warms it
 *    back up, so it drops out of the random tick set.
 *  - Water quench (tier 1, unchanged): a hot block touching water turns into obsidian straight away (neighbour
 *    change, plus a short delayed check after it is placed, so a crater that lands next to water quenches too).
 *  - Cold quench (pass 2, T6): a hot block next to a block in the tag ohnomoremeteors:quenches_cindrite (ice,
 *    packed ice, blue ice, snow block, powder snow, snow layer) melts one of those blocks on its random or
 *    scheduled tick and loses one heat step, with a hiss and steam. Ice melts into water, and that water then
 *    quenches a still-hot block into obsidian through the tier-1 rule.
 *  - Temperature (pass 2, T4/T5), read through ThermalReader only:
 *      - Next to strong heat (the six neighbours add up to at least cindriteReheatAbove, e.g. lava, fire,
 *        magma) the block does not cool, and may gain one heat step back (up to 3).
 *      - Otherwise it cools one step with cindriteCoolStepChance times the multiplier of its temperature band
 *        (cold cools faster, warm and hot slower).
 *      - Neighbouring Cindrite is left out of both readings, so a crater does not keep itself hot (see
 *        CONTRACT-PASS2.md 2.1). Other blocks (Rimerite, ice, water) still count.
 *      - In ultra-warm dimensions (the Nether) heat never drops below cindriteNetherMinHeat.
 *      - When the temperature is unknown (NML switched off, wrong thread) the block does not cool or reheat.
 *    When the heat runs out by cooling it becomes cooled Cindrite (heat 0), or, with the config chance,
 *    obsidian instead.
 *  - A hot block hurts living entities that walk on it, like a vanilla magma block (not when sneaking, not
 *    fire-immune entities, not with Frost Walker boots).
 *
 * Public API (frozen, see CONTRACT.md): MAX_HEAT, HEAT, LIGHT_BY_HEAT, lightFor, hotState().
 * Pass 2 adds QUENCHES_CINDRITE. The default state is heat 0, so the placed Silk Touch item is a cooled block.
 */
public class CindriteBlock extends Block {

    public static final int MAX_HEAT = 3;
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, MAX_HEAT);

    /** Light level for heat 0, 1, 2, 3. */
    public static final int[] LIGHT_BY_HEAT = {0, 4, 7, 10};

    /** Blocks that cool hot Cindrite down and melt doing it (data: data/ohnomoremeteors/tags/blocks/quenches_cindrite.json). */
    public static final TagKey<Block> QUENCHES_CINDRITE = TagKey.create(Registries.BLOCK, OhNoMoreMeteors.id("quenches_cindrite"));

    /** Delay in ticks before the post-placement water and cold check runs. */
    private static final int PLACE_CHECK_DELAY = 2;

    /** Delay in ticks before a cold block that appeared next to hot Cindrite melts (long enough to see it happen). */
    private static final int COLD_QUENCH_DELAY = 10;

    /** Leaves other Cindrite out of the temperature reading, so a crater does not hold its own heat. */
    private static final Predicate<BlockState> SAME_KIND = s -> s.getBlock() instanceof CindriteBlock;

    public CindriteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAT);
    }

    /** Light emission for a Cindrite state (used by the block properties). */
    public static int lightFor(BlockState state) {
        return LIGHT_BY_HEAT[state.getValue(HEAT)];
    }

    /** The state a meteor impact places: heat 3. */
    public static BlockState hotState() {
        return ModBlocks.CINDRITE.get().defaultBlockState().setValue(HEAT, MAX_HEAT);
    }

    /** True while the block is still hot. */
    public static boolean isHot(BlockState state) {
        return state.getValue(HEAT) > 0;
    }

    // ------------------------------------------------------------------ random tick: quench, reheat, cool

    /**
     * Only hot blocks tick. This must depend on the state alone (the chunk counts ticking blocks when a block is
     * set), and every state that can still change is a hot one: cooled Cindrite never warms up again, not next to
     * lava and not in the Nether (the Nether rule only stops cooling below cindriteNetherMinHeat).
     */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return isHot(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isHot(state)) {
            return;
        }
        // Contact quenches come first. They do not read the temperature, so they still work when it is unknown.
        if (quenchIfWet(state, level, pos)) {
            return;
        }
        if (quenchOnColdNeighbour(state, level, pos, random)) {
            return;
        }

        // One temperature reading per tick: the ambient part and the neighbour part, other Cindrite left out.
        float ambient = ThermalReader.ambient(level, pos);
        if (Float.isNaN(ambient)) {
            return; // Temperature unknown: freeze.
        }
        float neighbourHeat = ThermalReader.neighbourHeat(level, pos, SAME_KIND);
        if (Float.isNaN(neighbourHeat)) {
            return; // Temperature unknown: freeze.
        }

        int heat = state.getValue(HEAT);

        // T5: strong heat next to the block holds it hot and may heat it back up.
        if (neighbourHeat >= Config.CINDRITE_REHEAT_ABOVE.get()) {
            if (heat < MAX_HEAT && random.nextDouble() < Config.CINDRITE_REHEAT_CHANCE.get()) {
                level.setBlockAndUpdate(pos, state.setValue(HEAT, heat + 1));
            }
            return;
        }

        // T4: cooling speed depends on the temperature band the block sits in.
        float temperature = ambient + neighbourHeat;
        double chance = Math.min(1.0D, Config.CINDRITE_COOL_STEP_CHANCE.get() * coolMultiplier(ThermalReader.band(temperature)));
        if (random.nextDouble() >= chance) {
            return;
        }
        loseHeatStep(state, level, pos, random, true);
    }

    /** The T4 cooling multiplier for a temperature band. */
    private static double coolMultiplier(ThermalReader.Band band) {
        return switch (band) {
            case COLD -> Config.CINDRITE_COOL_MULT_COLD.get();
            case NEUTRAL -> Config.CINDRITE_COOL_MULT_NEUTRAL.get();
            case WARM -> Config.CINDRITE_COOL_MULT_WARM.get();
            case HOT -> Config.CINDRITE_COOL_MULT_HOT.get();
        };
    }

    /** The lowest heat a block may cool to here: cindriteNetherMinHeat in ultra-warm dimensions, else 0. */
    private static int minHeat(Level level) {
        return ThermalReader.isUltraWarm(level) ? Config.CINDRITE_NETHER_MIN_HEAT.get() : 0;
    }

    /**
     * Drops the block one heat step, unless that would take it below the ultra-warm floor.
     * When {@code mayTurnToObsidian} is true and the heat runs out, the block may set into obsidian instead
     * (config cindriteCoolToObsidianChance). Returns true when the block changed.
     */
    private static boolean loseHeatStep(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, boolean mayTurnToObsidian) {
        int newHeat = state.getValue(HEAT) - 1;
        if (newHeat < minHeat(level)) {
            return false;
        }
        if (newHeat <= 0 && mayTurnToObsidian && random.nextDouble() < Config.CINDRITE_COOL_TO_OBSIDIAN_CHANCE.get()) {
            // The last of the heat is gone and this one set into glass-hard obsidian.
            level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
            return true;
        }
        level.setBlockAndUpdate(pos, state.setValue(HEAT, newHeat));
        return true;
    }

    // ------------------------------------------------------------------ placement and neighbour checks

    /** After a block is placed, look at the water and cold blocks around it a moment later (not inside the setBlock call). */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && !oldState.is(this) && isHot(state)) {
            level.scheduleTick(pos, this, PLACE_CHECK_DELAY);
        }
    }

    /** Scheduled check (after placement, or after a cold block appeared next to it): water quench, then cold quench. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isHot(state)) {
            return;
        }
        if (quenchIfWet(state, level, pos)) {
            return;
        }
        quenchOnColdNeighbour(state, level, pos, random);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        if (!level.isClientSide && !quenchIfWet(state, level, pos)) {
            // A cold block just appeared (or changed) next to hot Cindrite: melt it on a scheduled tick shortly,
            // not inside this neighbour update.
            if (isHot(state)
                    && Config.CINDRITE_COLD_QUENCH.get()
                    && level.getBlockState(neighborPos).is(QUENCHES_CINDRITE)
                    && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, COLD_QUENCH_DELAY);
            }
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
    }

    // ------------------------------------------------------------------ water quench (tier 1)

    /** Turns a hot block that touches water into obsidian. Returns true when it did. */
    private static boolean quenchIfWet(BlockState state, Level level, BlockPos pos) {
        if (!isHot(state) || !Config.CINDRITE_QUENCHES_IN_WATER.get() || !touchesWater(level, pos)) {
            return false;
        }
        level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    5, 0.25D, 0.1D, 0.25D, 0.01D);
        }
        return true;
    }

    /** True when any of the six neighbours holds water (source, flowing or waterlogged). */
    private static boolean touchesWater(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ cold quench (pass 2, T6)

    /**
     * If cold quenching is on and a block from the quenches_cindrite tag touches this hot block, melts one of them
     * (picked at random) and drops this block one heat step (not below the ultra-warm floor; the cold block still
     * melts there). Plays a hiss with steam at the melted block. Returns true when a cold block melted.
     *
     * Order matters: this block's own heat is written first and the neighbour melts second. Melting ice makes water,
     * and the water's neighbour update can quench this block into obsidian (tier-1 rule) inside that call; writing
     * our own heat afterwards would overwrite the obsidian with a stale state. After the melt nothing here touches
     * {@code pos} again.
     *
     * Ultra-warm floor: at cindriteNetherMinHeat the heat step is refused but the cold block still melts, so in
     * the Nether a block held at the floor is an inexhaustible heat source for melting. This is intended: there
     * the melt only destroys or downgrades the cold block (ice to air, packed to ice, blue to packed), so it
     * gains the player nothing, and the Nether is meant to keep Cindrite hot.
     */
    private static boolean quenchOnColdNeighbour(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isHot(state) || !Config.CINDRITE_COLD_QUENCH.get()) {
            return false;
        }
        List<BlockPos> cold = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            // isLoaded never loads a chunk.
            if (level.isLoaded(neighbour) && level.getBlockState(neighbour).is(QUENCHES_CINDRITE)) {
                cold.add(neighbour);
            }
        }
        if (cold.isEmpty()) {
            return false;
        }
        BlockPos target = cold.get(random.nextInt(cold.size()));
        BlockState melted = meltResult(level.getBlockState(target), level);

        // Cooling from a quench never sets into obsidian by chance: obsidian comes from water only.
        loseHeatStep(state, level, pos, random, false);
        level.setBlockAndUpdate(target, melted);
        hissAndSteam(level, target);
        return true;
    }

    /**
     * What a cold block becomes when hot Cindrite melts it. Ice (and frosted ice) melts into water, or into nothing
     * in ultra-warm dimensions; packed ice turns into ice; blue ice turns into packed ice; snow blocks, powder snow
     * and snow layers vanish. Any other block a datapack adds to the tag also vanishes (air).
     */
    private static BlockState meltResult(BlockState cold, Level level) {
        if (cold.is(Blocks.ICE) || cold.is(Blocks.FROSTED_ICE)) {
            return ThermalReader.isUltraWarm(level) ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
        }
        if (cold.is(Blocks.PACKED_ICE)) {
            return Blocks.ICE.defaultBlockState();
        }
        if (cold.is(Blocks.BLUE_ICE)) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** Placeholder hiss (vanilla fire extinguish) plus a puff of steam at {@code at}. */
    private static void hissAndSteam(ServerLevel level, BlockPos at) {
        level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                2.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
        level.sendParticles(ParticleTypes.CLOUD,
                at.getX() + 0.5D, at.getY() + 0.6D, at.getZ() + 0.5D,
                4, 0.25D, 0.15D, 0.25D, 0.02D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                at.getX() + 0.5D, at.getY() + 0.6D, at.getZ() + 0.5D,
                3, 0.2D, 0.1D, 0.2D, 0.01D);
    }

    // ------------------------------------------------------------------ hot floor

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide
                && isHot(state)
                && Config.CINDRITE_DAMAGES_ENTITIES.get()
                && entity instanceof LivingEntity living
                && !entity.isSteppingCarefully()
                && !entity.fireImmune()
                && !EnchantmentHelper.hasFrostWalker(living)) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }
        super.stepOn(level, pos, state, entity);
    }
}

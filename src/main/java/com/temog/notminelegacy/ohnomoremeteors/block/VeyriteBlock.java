package com.temog.notminelegacy.ohnomoremeteors.block;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Veyrite: the space-meteor crater block. Violet meteorite stone charged with green energy. Charge runs 3
 * (heated, just landed) through 2 and 1 (warm) down to 0 (cool, inert).
 *
 *  - Only charged blocks (charge above 0) get random ticks; each one discharges a step with veyriteDischargeChance.
 *    A cool block never recharges.
 *  - Light 10 / 6 / 2 / 0 by charge; charge 2 and 3 render at full brightness.
 *  - Charged blocks now and then shed a small end-rod mote (client only, rarer as the charge falls).
 *  - Standing on charged Veyrite poisons players: see {@link com.temog.notminelegacy.ohnomoremeteors.meteor.VeyritePoison}.
 *
 * The default state is charge 0, so the placed Silk Touch item is a cool block.
 */
public class VeyriteBlock extends Block {

    public static final int MAX_CHARGE = 3;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);

    /** Light level for charge 0, 1, 2, 3. */
    public static final int[] LIGHT_BY_CHARGE = {0, 2, 6, 10};

    public VeyriteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    public static int lightFor(BlockState state) {
        return LIGHT_BY_CHARGE[state.getValue(CHARGE)];
    }

    /** The state a meteor impact places: fully charged. */
    public static BlockState chargedState() {
        return ModBlocks.VEYRITE.get().defaultBlockState().setValue(CHARGE, MAX_CHARGE);
    }

    /** Charge of a Veyrite state, 0 for any other block. */
    public static int chargeOf(BlockState state) {
        return state.getBlock() instanceof VeyriteBlock ? state.getValue(CHARGE) : 0;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(CHARGE) > 0;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int charge = state.getValue(CHARGE);
        if (charge > 0 && random.nextDouble() < Config.VEYRITE_DISCHARGE_CHANCE.get()) {
            level.setBlockAndUpdate(pos, state.setValue(CHARGE, charge - 1));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int charge = state.getValue(CHARGE);
        // The client samples each nearby block every few seconds; 1 in 4 / 8 / 16 of those samples at charge 3 / 2 / 1
        // shed a mote, and only from a block with air above it.
        if (charge > 0 && random.nextInt(4 << (MAX_CHARGE - charge)) == 0 && level.isEmptyBlock(pos.above())) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.05D, pos.getZ() + random.nextDouble(),
                    0.0D, 0.01D + random.nextDouble() * 0.02D, 0.0D);
        }
    }
}

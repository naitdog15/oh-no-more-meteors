package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Frostery: the cold twin of the furnace. Same layout and behaviour as a furnace (input, fuel, output; facing and
 * lit states), but it runs freezing recipes and burns only cold items. Damaged Rimerite gear in its input slot is
 * repaired by the cold fuel it uses (FrosteryRepair).
 */
public class FrosteryBlock extends AbstractFurnaceBlock {

    public FrosteryBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FrosteryBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModFrostery.FROSTERY_BLOCK_ENTITY.get(), FrosteryBlockEntity::serverTick);
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof FrosteryBlockEntity frostery) {
            player.openMenu((MenuProvider) frostery);
        }
    }

    /** While working: a soft frosty crackle now and then, and snowflakes drifting out of the front. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;
        // Soft cold hum about every 4-5 s (the takes are 1.2-2.5 s long, so a furnace-like 10% rate would stack them).
        if (random.nextDouble() < 0.012D) {
            level.playLocalSound(x, y, z, com.temog.notminelegacy.ohnomoremeteors.registry.ModSounds.FROSTERY_CRACKLE.get(),
                    SoundSource.BLOCKS, 1.0F, 0.95F + random.nextFloat() * 0.1F, false);
        }
        Direction facing = state.getValue(FACING);
        double side = random.nextDouble() * 0.6D - 0.3D;
        double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * 0.52D : side;
        double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * 0.52D : side;
        double dy = random.nextDouble() * 6.0D / 16.0D;
        level.addParticle(ParticleTypes.SNOWFLAKE, x + dx, y + dy, z + dz, facing.getStepX() * 0.01D, 0.0D, facing.getStepZ() * 0.01D);
    }
}

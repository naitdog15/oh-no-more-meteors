package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.fix.OmmFixes;
import me.emafire003.dev.ohmymeteors.blocks.MeteoricRockBlock;
import me.emafire003.dev.ohmymeteors.blocks.RockTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Q1: hot or mid OMM meteoric rock that is not preserved and touches water (on any of its six faces, including
 * a waterlogged neighbour) drops straight to normal, with a hiss and steam. Gated by
 * meteoricRock.meteoricRockWaterQuench.
 *
 * Hooks the TAIL of neighborChanged and onPlace. Both are vanilla Block overrides declared in MeteoricRockBlock,
 * so there is NO class-level remap = false: the method names go through the refmap. TAIL, not HEAD, so OMM's
 * own neighbour logic has run first; the state is re-read afterwards because OMM may already have changed it.
 *
 * Loop safety: the write to NORMAL calls onPlace again (same block, new state). That second call re-reads the
 * state, sees NORMAL and stops. A preserved rock is never touched.
 */
@Mixin(MeteoricRockBlock.class)
public abstract class MeteoricRockQuenchMixin {

    @Inject(
            method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("TAIL"),
            require = 1
    )
    private void onmm$quenchOnNeighbourChange(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                              BlockPos neighborPos, boolean movedByPiston, CallbackInfo ci) {
        onmm$tryQuench(level, pos);
    }

    @Inject(
            method = "onPlace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At("TAIL"),
            require = 1
    )
    private void onmm$quenchOnPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                                    boolean movedByPiston, CallbackInfo ci) {
        onmm$tryQuench(level, pos);
    }

    /** Shared check. Server side only; never throws (a failure just leaves the rock as OMM made it). */
    private void onmm$tryQuench(Level level, BlockPos pos) {
        try {
            if (!(level instanceof ServerLevel serverLevel) || !Config.METEORIC_ROCK_WATER_QUENCH.get()) {
                return;
            }
            // Re-read: OMM's own neighborChanged may already have demoted the rock, or the block may be gone.
            BlockState current = level.getBlockState(pos);
            if (!current.is((Block) (Object) this)) {
                return;
            }
            if (current.getValue(MeteoricRockBlock.PRESERVED)) {
                return;
            }
            RockTemperature temperature = current.getValue(MeteoricRockBlock.ROCK_TEMPERATURE);
            if (temperature != RockTemperature.HOT && temperature != RockTemperature.MID) {
                return;
            }
            // Water on any face. getFluidState also reports water inside waterlogged blocks, and flowing water.
            boolean touchesWater = false;
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                // isLoaded never loads a chunk: a neighbour across an unloaded chunk border counts as dry.
                if (!level.isLoaded(neighbour)) {
                    continue;
                }
                if (level.getFluidState(neighbour).is(FluidTags.WATER)) {
                    touchesWater = true;
                    break;
                }
            }
            if (!touchesWater) {
                return;
            }

            // Straight to normal. This re-enters onPlace, which finds NORMAL and stops.
            level.setBlockAndUpdate(pos, current.setValue(MeteoricRockBlock.ROCK_TEMPERATURE, RockTemperature.NORMAL));

            // Placeholder hiss (vanilla fire-extinguish sound) and steam; real sound pass comes later.
            serverLevel.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    2.2F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D, 6, 0.3D, 0.2D, 0.3D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D, 4, 0.3D, 0.2D, 0.3D, 0.05D);
        } catch (RuntimeException ex) {
            OmmFixes.warnOnce("meteoricRockWaterQuench", "The meteoric rock water quench failed; the rock stays as it is.", ex);
        }
    }
}

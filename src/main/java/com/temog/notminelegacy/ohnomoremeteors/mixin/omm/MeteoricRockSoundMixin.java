package com.temog.notminelegacy.ohnomoremeteors.mixin.omm;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModSounds;
import me.emafire003.dev.ohmymeteors.blocks.MeteoricRockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives Oh My Meteors' meteoric rock this add-on's own sound family (block.meteoric_rock.*), all three temperatures.
 *
 * OMM registers the rock without .sound(...), so it falls back to vanilla stone. This mixin ADDS an override of
 * Forge's IForgeBlock#getSoundType(BlockState, LevelReader, BlockPos, Entity), the context-aware lookup that Forge
 * routes break, step, place, hit and fall sounds through. It is a Forge method (not obfuscated), so the class is
 * remap = false. Gated by meteoricRock.meteoricRockSounds; when off it returns the block's own (vanilla stone) type.
 *
 * It extends Block only so the merged method can call super; the mixin class itself is never instantiated.
 */
@Mixin(value = MeteoricRockBlock.class, remap = false)
public abstract class MeteoricRockSoundMixin extends Block {

    private MeteoricRockSoundMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        if (Config.METEORIC_ROCK_SOUNDS.get()) {
            return ModSounds.METEORIC_ROCK_TYPE;
        }
        return super.getSoundType(state, level, pos, entity);
    }
}

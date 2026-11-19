package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.block.CindriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.RimeriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.VeyriteBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block registry. Ids: cindrite, rimerite, veyrite, raw_cindrite_block, raw_rimerite_block, raw_veyrite_block.
 *
 * Properties follow DECISIONS.md and CONTRACT.md. All four blocks are stone-like (bass drum note block
 * sound) and need the right tool to drop anything: iron tier for the two crater blocks, stone tier
 * for the raw storage blocks (the tiers themselves come from the data tags).
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, OhNoMoreMeteors.MODID);

    /** Fire-meteor crater block. Light follows the heat property; pickaxe, iron tier. */
    public static final RegistryObject<Block> CINDRITE = BLOCKS.register("cindrite", () ->
            new CindriteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops()
                    // Random ticks are decided by CindriteBlock#isRandomlyTicking (hot blocks only).
                    .lightLevel(CindriteBlock::lightFor)
                    // Warm and hot stages glow at full brightness, like a magma block.
                    .emissiveRendering((state, level, pos) -> state.getValue(CindriteBlock.HEAT) >= 2)
                    .sound(ModSounds.CINDRITE_TYPE)));

    /** Ice-meteor crater block. Slightly slippery (0.9); pickaxe, iron tier. */
    public static final RegistryObject<Block> RIMERITE = BLOCKS.register("rimerite", () ->
            new RimeriteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .randomTicks()
                    .friction(0.9F)
                    .sound(ModSounds.RIMERITE_TYPE)));

    /** Plain decorative stone left behind when Rimerite thaws. */
    public static final RegistryObject<Block> RIME_WORN_STONE = BLOCKS.register("rime_worn_stone", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_LIGHT_BLUE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    /** Space-meteor crater block. Light and emissive glow follow the charge; pickaxe, iron tier. */
    public static final RegistryObject<Block> VEYRITE = BLOCKS.register("veyrite", () ->
            new VeyriteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops()
                    .lightLevel(VeyriteBlock::lightFor)
                    .emissiveRendering((state, level, pos) -> state.getValue(VeyriteBlock.CHARGE) >= 2)
                    .sound(ModSounds.VEYRITE_TYPE)));

    /** Storage block for 9 raw Veyrite. Uses the Veyrite sound family. */
    public static final RegistryObject<Block> RAW_VEYRITE_BLOCK = BLOCKS.register("raw_veyrite_block", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_PURPLE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(ModSounds.VEYRITE_TYPE)));

    /** Storage block for 9 raw Cindrite. Uses the Cindrite sound family. */
    public static final RegistryObject<Block> RAW_CINDRITE_BLOCK = BLOCKS.register("raw_cindrite_block", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_ORANGE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(ModSounds.CINDRITE_TYPE)));

    /** Storage block for 9 raw Rimerite. Uses the Rimerite sound family. */
    public static final RegistryObject<Block> RAW_RIMERITE_BLOCK = BLOCKS.register("raw_rimerite_block", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_LIGHT_BLUE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(ModSounds.RIMERITE_TYPE)));

    private ModBlocks() {
    }
}

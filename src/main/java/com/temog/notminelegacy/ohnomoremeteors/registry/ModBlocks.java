package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.block.CindriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.RimeriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.VeyriteBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Block registry. Ids: cindrite, rimerite, veyrite, raw_cindrite_block, raw_rimerite_block, raw_veyrite_block, plus the Tektite set
 * (tektite, polished_tektite, tektite_bricks with stairs/slab/wall, cracked_tektite_bricks, chiseled_tektite).
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

    // ---- Tektite (Veyrite crater rock): dark bottle-green fused glass, stone-like; pickaxe, iron tier ----

    public static final RegistryObject<Block> TEKTITE = BLOCKS.register("tektite", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_GREEN)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)));
    public static final RegistryObject<Block> TEKTITE_STAIRS = BLOCKS.register("tektite_stairs", () ->
            new StairBlock(() -> TEKTITE.get().defaultBlockState(), BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> TEKTITE_SLAB = BLOCKS.register("tektite_slab", () ->
            new SlabBlock(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> TEKTITE_WALL = BLOCKS.register("tektite_wall", () ->
            new WallBlock(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> POLISHED_TEKTITE = BLOCKS.register("polished_tektite", () ->
            new Block(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> POLISHED_TEKTITE_STAIRS = BLOCKS.register("polished_tektite_stairs", () ->
            new StairBlock(() -> POLISHED_TEKTITE.get().defaultBlockState(), BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> POLISHED_TEKTITE_SLAB = BLOCKS.register("polished_tektite_slab", () ->
            new SlabBlock(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> POLISHED_TEKTITE_WALL = BLOCKS.register("polished_tektite_wall", () ->
            new WallBlock(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> TEKTITE_BRICKS = BLOCKS.register("tektite_bricks", () ->
            new Block(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> TEKTITE_BRICK_STAIRS = BLOCKS.register("tektite_brick_stairs", () ->
            new StairBlock(() -> TEKTITE_BRICKS.get().defaultBlockState(), BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> TEKTITE_BRICK_SLAB = BLOCKS.register("tektite_brick_slab", () ->
            new SlabBlock(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> TEKTITE_BRICK_WALL = BLOCKS.register("tektite_brick_wall", () ->
            new WallBlock(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> CRACKED_TEKTITE_BRICKS = BLOCKS.register("cracked_tektite_bricks", () ->
            new Block(BlockBehaviour.Properties.copy(TEKTITE.get())));
    public static final RegistryObject<Block> CHISELED_TEKTITE = BLOCKS.register("chiseled_tektite", () ->
            new Block(BlockBehaviour.Properties.copy(TEKTITE.get())));

    private ModBlocks() {
    }
}

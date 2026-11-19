package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain Oh My, Meteors! meteors without the off-theme blocks (2026-11-19, user ruling): no ice of any kind, no lava,
 * no free loot (netherite, ores, raw iron, gilded blackstone) and no easter eggs. Ice belongs to Rimerite and fire to
 * Cindrite.
 *
 * Two parts:
 *  - structure pick (MeteorStructurePickMixin): OMM's icy structures and its special (easter egg) structures are
 *    not picked for a plain meteor ({@link #isExcludedStructure});
 *  - block cleanup: before OMM's impact the blocks of the configured list already around the impact are noted, and
 *    right after it every listed block the impact itself placed is swapped: fluids and snow become air, the rest
 *    meteor rock (blackstone / smooth basalt). Ice in a snowy biome or an existing lake is never touched.
 * Only for meteors with no claim or this mod's plain claim (another add-on's meteors are left alone).
 */
public final class PlainMeteorCleanup {

    /** Default list (config impactFormula.plainMeteorStrippedBlocks). */
    public static final List<String> DEFAULT_STRIPPED = List.of(
            "minecraft:lava", "minecraft:water", "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice",
            "minecraft:frosted_ice", "minecraft:snow_block", "minecraft:snow", "minecraft:powder_snow",
            "minecraft:netherite_block", "minecraft:coal_block", "minecraft:raw_iron_block",
            "minecraft:deepslate_diamond_ore", "minecraft:deepslate_iron_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore", "minecraft:iron_ore", "minecraft:lapis_ore", "minecraft:gilded_blackstone");

    /** OMM structures a plain meteor never uses (the icy ones). Specials are excluded by their folder. */
    private static final Set<String> EXCLUDED_STRUCTURES = Set.of(
            "small/small_meteor_2", "medium/medium_meteor_2", "big/big_meteor_2", "huge/huge_meteor_2");

    private static boolean loggedFailure;

    private PlainMeteorCleanup() {
    }

    /** True if the plain-meteor rules apply to this meteor (config on, no variant, no foreign claim). */
    public static boolean applies(MeteorProjectileEntity meteor) {
        if (!Config.PLAIN_METEORS_NO_ICE_OR_LAVA.get() || MeteorVariant.fromEntity(meteor) != null) {
            return false;
        }
        if (!MeteorVariant.hasAnyClaim(meteor)) {
            return true;
        }
        return MeteorCommand.PLAIN_CLAIM.equals(meteor.getPersistentData().getString(MeteorVariant.NBT_KEY));
    }

    /** OMM structure ids (path) a plain meteor should not get: the icy ones and every special (easter egg). */
    public static boolean isExcludedStructure(ResourceLocation id) {
        String path = id.getPath();
        return EXCLUDED_STRUCTURES.contains(path) || path.contains("/special");
    }

    /** What the cube around the impact held before: positions that already had a listed block. */
    public static final class Before {
        final ServerLevel level;
        final BlockPos min;
        final int side;
        final boolean[] listedBefore;
        final Set<Block> listed;

        Before(ServerLevel level, BlockPos min, int side, Set<Block> listed) {
            this.level = level;
            this.min = min;
            this.side = side;
            this.listedBefore = new boolean[side * side * side];
            this.listed = listed;
        }

        int index(int x, int y, int z) {
            return (y * side + z) * side + x;
        }
    }

    /** Called at the start of OMM's impact. Null when nothing needs doing. */
    @Nullable
    public static Before capture(MeteorProjectileEntity meteor) {
        try {
            if (!(meteor.level() instanceof ServerLevel level) || !applies(meteor)
                    || !OhMyMeteors.CONFIG.meteorBehaviourSection.meteor_structure) {
                return null;
            }
            Set<Block> listed = listedBlocks();
            if (listed.isEmpty()) {
                return null;
            }
            // OMM structures reach about size + a few blocks around the impact (huge ones are the biggest).
            int half = Mth.clamp(meteor.getSize() + 8, 6, 32);
            int side = half * 2 + 1;
            BlockPos centre = meteor.blockPosition();
            Before before = new Before(level, centre.offset(-half, -half, -half), side, listed);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < side; y++) {
                for (int z = 0; z < side; z++) {
                    for (int x = 0; x < side; x++) {
                        pos.set(before.min.getX() + x, before.min.getY() + y, before.min.getZ() + z);
                        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                            continue;
                        }
                        before.listedBefore[before.index(x, y, z)] = listed.contains(level.getBlockState(pos).getBlock());
                    }
                }
            }
            return before;
        } catch (RuntimeException ex) {
            warnOnce(ex);
            return null;
        }
    }

    /** Called right after OMM's impact (blast + structure). Returns the number of blocks swapped. */
    public static int apply(@Nullable Before before) {
        if (before == null) {
            return 0;
        }
        try {
            ServerLevel level = before.level;
            RandomSource random = level.getRandom();
            int swapped = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < before.side; y++) {
                for (int z = 0; z < before.side; z++) {
                    for (int x = 0; x < before.side; x++) {
                        if (before.listedBefore[before.index(x, y, z)]) {
                            continue;
                        }
                        pos.set(before.min.getX() + x, before.min.getY() + y, before.min.getZ() + z);
                        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                            continue;
                        }
                        BlockState now = level.getBlockState(pos);
                        if (!before.listed.contains(now.getBlock())) {
                            continue;
                        }
                        BlockState rock = replacement(now, random);
                        level.setBlock(pos, rock, Block.UPDATE_ALL);
                        swapped++;
                    }
                }
            }
            return swapped;
        } catch (RuntimeException ex) {
            warnOnce(ex);
            return 0;
        }
    }

    /** Fluids, snow and powder snow become air; everything else meteor rock. */
    private static BlockState replacement(BlockState state, RandomSource random) {
        if (!state.getFluidState().isEmpty() || state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
            return Blocks.AIR.defaultBlockState();
        }
        return (random.nextInt(3) == 0 ? Blocks.SMOOTH_BASALT : Blocks.BLACKSTONE).defaultBlockState();
    }

    private static Set<Block> listedBlocks() {
        Set<Block> out = new HashSet<>();
        for (String id : Config.PLAIN_METEOR_STRIPPED_BLOCKS.get()) {
            ResourceLocation key = ResourceLocation.tryParse(id.trim());
            if (key == null) {
                continue;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            if (block != null && block != Blocks.AIR) {
                out.add(block);
            }
        }
        return out;
    }

    private static void warnOnce(RuntimeException ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The plain meteor cleanup failed; OMM's blocks stay (this is logged once).", ex);
        }
    }
}

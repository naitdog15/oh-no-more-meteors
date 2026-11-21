package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.meteor.CraterBuilder;
import com.temog.notminelegacy.ohnomoremeteors.meteor.MeteorVariant;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * The kitty capsule: a tiny enclosure a landed Komit Kitty leaves at the end of its tunnel, built after the
 * tunnel and the blast.
 *
 *  - Centre: one air cell.
 *  - Shell (the 26 cells of the 3x3x3 around the centre): a random number of Veyrite blocks (between
 *    capsuleMinVeyrite and capsuleMaxVeyrite, default 4 to 6), placed on the 6 face cells first. The 6 face cells
 *    (the ones touching the cat's air cell) are always Veyrite or crying obsidian; the 20 edge and corner cells are
 *    crying obsidian or tektite (user ruling 2026-11-21).
 *  - Optional outer shell (config capsuleOuterShell, off by default since 2026-11-21 to keep the cluster small):
 *    the 98 cells of the 5x5x5 that are not in the 3x3x3, Veyrite crater filler of {@link CraterBuilder#filler}.
 *  - Optionally an OMM Meteor Cat in the centre cell. Players dig it out.
 *
 * A cell is only overwritten when {@link KittyTunnel#replaceable} says so: inside the world height, loaded, allowed
 * by the claim guard and something the kitty could have dug (bedrock, unbreakable blocks, block entities,
 * explosion-safe blocks and obsidian or harder are kept). Where a block is kept the shell has a gap, which still
 * encloses the cat.
 */
public final class KittyCapsule {

    /** The id of OMM's Meteor Cat. */
    private static final ResourceLocation CAT_ID = new ResourceLocation("ohmymeteors", "meteor_cat");

    /** One warning per game run if the cat cannot be placed. */
    private static volatile boolean loggedCatFailure;

    private KittyCapsule() {
    }

    /** What one build did: cells written, and the cat that was placed (null when none). */
    public record Result(int written, @Nullable Entity cat) {
    }

    /** As {@link #build(ServerLevel, BlockPos, RandomSource, boolean, KittyClaimGuard)} with a fresh claim guard (for tests). */
    public static Result build(ServerLevel level, BlockPos centre, RandomSource r, boolean withCat) {
        return build(level, centre, r, withCat, KittyClaimGuard.of(level));
    }

    /**
     * Builds the capsule around the centre cell: the outer shell first, then the inner shell, then the centre.
     * Every write uses UPDATE_CLIENTS (at most 125 cells). If the centre itself is not replaceable there is no
     * capsule: the result has 0 cells written and, when a cat was asked for, a loose cat in that cell.
     * The caller must call this after the blast has finished, so the blast cannot break the capsule.
     */
    public static Result build(ServerLevel level, BlockPos centre, RandomSource r, boolean withCat, KittyClaimGuard guard) {
        if (!KittyTunnel.replaceable(level, centre, guard)) {
            return new Result(0, withCat ? placeCat(level, centre, r) : null);
        }
        int written = 0;

        // Optional outer shell: Veyrite crater filler.
        for (int dy = -2; dy <= 2 && Config.KITTY_CAPSULE_OUTER_SHELL.get(); dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != 2) {
                        continue;
                    }
                    BlockPos p = centre.offset(dx, dy, dz);
                    if (KittyTunnel.replaceable(level, p, guard)) {
                        written += put(level, p, CraterBuilder.filler(MeteorVariant.VEYRITE, r));
                    }
                }
            }
        }

        // Inner shell. Veyrite goes on the 6 face cells first (shuffled), any extra on the 20 edge/corner cells.
        int min = Mth.clamp(Config.KITTY_CAPSULE_MIN_VEYRITE.get(), 0, 26);
        int max = Mth.clamp(Config.KITTY_CAPSULE_MAX_VEYRITE.get(), 0, 26);
        if (max < min) {
            max = min;
        }
        int veyriteCount = min + r.nextInt(max - min + 1);
        boolean[] faceVeyrite = new boolean[6];
        int onFaces = Math.min(6, veyriteCount);
        for (int i = 0; i < onFaces; i++) {
            int j;
            do {
                j = r.nextInt(6);
            } while (faceVeyrite[j]);
            faceVeyrite[j] = true;
        }
        int extra = veyriteCount - onFaces;
        boolean[] otherVeyrite = new boolean[20];
        for (int i = 0; i < extra; i++) {
            int j;
            do {
                j = r.nextInt(20);
            } while (otherVeyrite[j]);
            otherVeyrite[j] = true;
        }
        BlockState veyriteState = MeteorVariant.VEYRITE.craterState();
        int face = 0;
        int other = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int n = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (n == 0) {
                        continue;
                    }
                    BlockPos p = centre.offset(dx, dy, dz);
                    BlockState chosen;
                    if (n == 1) {
                        // Touching the cat's air cell: Veyrite or crying obsidian only (always placed: the seal).
                        chosen = faceVeyrite[face++] ? veyriteState : Blocks.CRYING_OBSIDIAN.defaultBlockState();
                    } else {
                        chosen = otherVeyrite[other++] ? veyriteState : innerMix(r);
                        // Rounded, uneven shell (user ruling 2026-11-21: "sphere" shaped, not always a clean one):
                        // edges are usually kept, corners only sometimes, so the 3x3x3 cube loses its square outline.
                        if (r.nextDouble() >= (n == 2 ? EDGE_KEEP : CORNER_KEEP)) {
                            continue;
                        }
                    }
                    if (KittyTunnel.replaceable(level, p, guard)) {
                        written += put(level, p, chosen);
                    }
                }
            }
        }

        // Bumps: some cells one step past each face (radius 2 on the axes), so the outline reads round and lumpy.
        for (Direction d : Direction.values()) {
            if (r.nextDouble() < BUMP_CHANCE) {
                BlockPos p = centre.relative(d, 2);
                if (KittyTunnel.replaceable(level, p, guard)) {
                    written += put(level, p, innerMix(r));
                }
            }
        }

        // Centre: one air cell (water here would drown the cat, so it is cleared too).
        written += put(level, centre, Blocks.AIR.defaultBlockState());

        Entity cat = withCat ? placeCat(level, centre, r) : null;
        return new Result(written, cat);
    }

    /** Chance an edge cell (2 axes off the centre) of the shell is placed. */
    private static final double EDGE_KEEP = 0.85D;
    /** Chance a corner cell (3 axes off the centre) of the shell is placed. */
    private static final double CORNER_KEEP = 0.3D;
    /** Chance of a bump cell two steps out along each of the six axes. */
    private static final double BUMP_CHANCE = 0.5D;

    /** One edge or corner block that is not Veyrite: crying obsidian (1 in 3) or tektite (2 in 3); one nextInt(3) draw. */
    private static BlockState innerMix(RandomSource r) {
        return switch (r.nextInt(3)) {
            case 0 -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
            default -> ModBlocks.TEKTITE.get().defaultBlockState();
        };
    }

    /** Writes one block (no write when it is already there). Returns 1 when a block was written. */
    private static int put(ServerLevel level, BlockPos p, BlockState state) {
        if (level.getBlockState(p) == state) {
            return 0;
        }
        level.setBlock(p, state, Block.UPDATE_CLIENTS);
        return 1;
    }

    /**
     * Places one OMM Meteor Cat in the cell: finalised as an event spawn, marked for the despawn rule of
     * {@link CapsuleCats}, then added. Also used for a loose cat when there is no capsule (griefing off, a
     * mid-air landing, or the capsule switched off). Returns null when the cat could not be placed.
     */
    @Nullable
    public static Entity placeCat(ServerLevel level, BlockPos cell, RandomSource r) {
        try {
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(CAT_ID)) {
                return null; // the registry would hand back its default entity for an unknown id
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(CAT_ID);
            Entity entity = type == null ? null : type.create(level);
            if (!(entity instanceof Mob cat)) {
                return null;
            }
            cat.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
            cat.finalizeSpawn(level, level.getCurrentDifficultyAt(cell), MobSpawnType.EVENT, null, null);
            CapsuleCats.mark(cat, level.getGameTime());
            if (!level.addFreshEntity(cat)) {
                return null;
            }
            level.playSound(null, cell, SoundEvents.CAT_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.0F);
            return cat;
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedCatFailure) {
                loggedCatFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not place a capsule cat (this is logged once).", ex);
            }
            return null;
        }
    }
}

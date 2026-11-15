package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.block.CindriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.RimeriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.block.VeyriteBlock;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import com.temog.notminelegacy.ohnomoremeteors.temperature.ThermalReader;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.util.MeteorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The crater of a fire or ice meteor: the blast hollows the ground, then blocks that border the hollow are
 * turned into Cindrite or Rimerite (sparse veins on the walls, a dense floor), plus a rare block now and then.
 * An ice meteor also dusts the hollow with snow and freezes exposed water.
 *
 * Use: {@link #capture} BEFORE the explosion, then {@link #build} AFTER it. The "hollow" is every position that
 * was solid in the snapshot and is empty now, so the crater follows what the explosion really removed
 * (nothing is built when the blast destroyed nothing, for example with meteor griefing off).
 *
 * The idea (a cube around the impact, 30% veins, 100% floor, 1 in 45 rare block) follows the old Falling
 * Meteors crater generator as a concept only; the code is new.
 */
public final class CraterBuilder {

    /** Block update flags for crater writes: send to clients, skip neighbour shape updates (cheap, no cascades). */
    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Blocks at or above this blast resistance (obsidian, reinforced deepslate, anchors...) are never overwritten. */
    private static final float MAX_REPLACEABLE_RESISTANCE = 1200.0F;

    private CraterBuilder() {
    }

    /**
     * Land-claim check for crater writes. The blast itself is filtered by claim mods, but crater writes are plain
     * setBlock calls, so each column is checked here through Oh My, Meteors! own claim check (Flan, YAWP,
     * OpenPartiesAndClaims). Answers are cached per column for the one impact this object lives for.
     *
     * If the claim check itself throws (a broken or mismatched claim mod), the column counts as protected and the
     * crater skips it: an unanswered claim check must never let crater blocks overwrite someone's claim. The
     * failure is logged once per game run.
     */
    private static final class ClaimGuard {
        /** One warning per game run is enough; afterwards failing columns are skipped quietly. */
        private static volatile boolean loggedFailure;

        private final ServerLevel level;
        private final Map<Long, Boolean> columns = new HashMap<>();

        private ClaimGuard(ServerLevel level) {
            this.level = level;
        }

        boolean allows(BlockPos pos) {
            long key = (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
            Boolean cached = columns.get(key);
            if (cached == null) {
                boolean ok;
                try {
                    ok = MeteorUtils.canSpawnInModdedRegion(level, pos);
                } catch (RuntimeException | LinkageError ex) {
                    ok = false; // fail closed: treat the column as protected
                    if (!loggedFailure) {
                        loggedFailure = true;
                        OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The Oh My, Meteors! claim check failed; crater blocks skip the columns it could not check.", ex);
                    }
                }
                cached = ok;
                columns.put(key, cached);
            }
            return cached;
        }
    }

    /** What the cube around the impact looked like before the blast. */
    public static final class Snapshot {
        private final BlockPos min;
        private final int side;
        private final boolean[] solidBefore;
        /** Climate band at the impact centre, read once before the blast (NEUTRAL when unknown). */
        private final ThermalReader.Band climate;

        private Snapshot(BlockPos min, int side, boolean[] solidBefore, ThermalReader.Band climate) {
            this.min = min;
            this.side = side;
            this.solidBefore = solidBefore;
            this.climate = climate;
        }

        private int index(int x, int y, int z) {
            return (y * side + z) * side + x;
        }
    }

    /** Crater half-width in blocks for a meteor size: round(size * spread), capped. 0 means no crater. */
    public static int halfWidth(int meteorSize) {
        double raw = meteorSize * Config.CRATER_SPREAD_PER_SIZE.get();
        return Mth.clamp((int) Math.round(raw), 0, Config.CRATER_MAX_HALF_WIDTH.get());
    }

    /** Records which positions of the cube are solid now. Returns null when the crater would be empty. */
    public static Snapshot capture(ServerLevel level, BlockPos centre, int halfWidth) {
        if (halfWidth < 1) {
            return null;
        }
        int side = halfWidth * 2 + 1;
        BlockPos min = centre.offset(-halfWidth, -halfWidth, -halfWidth);
        boolean[] solid = new boolean[side * side * side];
        Snapshot snap = new Snapshot(min, side, solid, readClimate(level, centre));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    pos.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    solid[snap.index(x, y, z)] = !state.isAir() && state.getFluidState().isEmpty();
                }
            }
        }
        return snap;
    }

    /**
     * The climate band at the impact centre, read ONCE per impact from the AMBIENT temperature only (biome,
     * weather, altitude, dimension offsets, and the underground blend for a deeply buried impact). Block heat
     * next to the impact (lava, fire, magma) is left out on purpose: T11 is about the wrong climate, and a
     * meteor landing beside lava in a cold place must not read HOT. This also keeps the impact in step with the
     * biome-based spawn roll. An unknown reading (NML off, wrong thread, unloaded column) gives NEUTRAL, which
     * means no climate effect. Used by the hot-place melt (T11) and to skip impact snow in a HOT place.
     */
    private static ThermalReader.Band readClimate(ServerLevel level, BlockPos centre) {
        try {
            float t = ThermalReader.ambient(level, centre);
            return Float.isNaN(t) ? ThermalReader.Band.NEUTRAL : ThermalReader.band(t);
        } catch (RuntimeException ex) {
            return ThermalReader.Band.NEUTRAL;
        }
    }

    /** Builds the crater (and the ice meteor's aftermath) after the blast. Returns the number of blocks written. */
    public static int build(ServerLevel level, Snapshot snap, MeteorVariant variant) {
        if (snap == null) {
            return 0;
        }
        int side = snap.side;
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Pass 1: find the hollow (solid before, empty now).
        boolean[] hollow = new boolean[snap.solidBefore.length];
        boolean anyHollow = false;
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    int i = snap.index(x, y, z);
                    if (!snap.solidBefore[i]) {
                        continue;
                    }
                    pos.set(snap.min.getX() + x, snap.min.getY() + y, snap.min.getZ() + z);
                    BlockState now = level.getBlockState(pos);
                    if (now.isAir() || now.is(BlockTags.FIRE)) {
                        hollow[i] = true;
                        anyHollow = true;
                    }
                }
            }
        }
        if (!anyHollow) {
            return 0;
        }

        int cap = Config.CRATER_MAX_BLOCK_WRITES.get();
        double fillChance = Config.CRATER_FILL_CHANCE.get();
        int rareOneIn = Config.CRATER_RARE_BLOCK_ONE_IN.get();
        int writes = 0;
        ClaimGuard claims = new ClaimGuard(level);

        // A Rimerite crater in a HOT place: some of its blocks are placed as water (air where water cannot exist).
        boolean meltHot = variant == MeteorVariant.RIMERITE && snap.climate == ThermalReader.Band.HOT;
        double meltChance = Config.RIMERITE_IMPACT_MELT_HOT.get();
        boolean ultraWarm = ThermalReader.isUltraWarm(level);
        // Positions written as meltwater, so the ice aftermath does not freeze them straight back.
        Set<Long> meltWater = new HashSet<>();
        // Hollow cells from which water could run out of the crater. Only worked out when meltwater can be placed.
        boolean[] leaky = (meltHot && !ultraWarm && meltChance > 0.0D) ? findLeakyHollow(level, snap, hollow) : null;

        // Pass 2: replace blocks that touch the hollow. Bottom up, so the floor wins if the write cap is hit.
        outer:
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    int i = snap.index(x, y, z);
                    if (hollow[i] || !snap.solidBefore[i]) {
                        continue;
                    }
                    // The floor is the surface under any hollow cell plus the block below that surface, both always filled.
                    // The block below the surface does not touch the hollow, so only the surface needs to.
                    boolean floorSurface = y + 1 < side && hollow[snap.index(x, y + 1, z)];
                    boolean floorUnder = y + 2 < side && !hollow[snap.index(x, y + 1, z)]
                            && snap.solidBefore[snap.index(x, y + 1, z)] && hollow[snap.index(x, y + 2, z)];
                    boolean floor = floorSurface || floorUnder;
                    if (!floor && !bordersHollow(snap, hollow, x, y, z)) {
                        continue;
                    }
                    if (!floor && random.nextDouble() >= fillChance) {
                        continue;
                    }
                    pos.set(snap.min.getX() + x, snap.min.getY() + y, snap.min.getZ() + z);
                    BlockState current = level.getBlockState(pos);
                    if (!canReplace(level, pos, current) || !claims.allows(pos)) {
                        continue;
                    }
                    boolean rare = rareOneIn > 0 && random.nextInt(rareOneIn) == 0;
                    BlockState placed = rare ? variant.rareState() : variant.craterState();
                    // The melt roll only happens in a hot place, so every other crater uses the same random draws as before.
                    if (!rare && meltHot && random.nextDouble() < meltChance) {
                        if (ultraWarm) {
                            placed = Blocks.AIR.defaultBlockState();
                        } else if (leaky != null && waterStaysContained(level, snap, hollow, leaky, pos)) {
                            placed = Blocks.WATER.defaultBlockState();
                            meltWater.add(pos.asLong());
                        }
                    }
                    level.setBlock(pos, placed, WRITE_FLAGS);
                    if (++writes >= cap) {
                        break outer;
                    }
                }
            }
        }

        if (variant == MeteorVariant.RIMERITE && writes < cap) {
            // No impact snow in a HOT place: snow there would clash with the meltwater and with T8 (snow only when cold).
            boolean snow = snap.climate != ThermalReader.Band.HOT;
            writes += iceAftermath(level, snap, hollow, random, cap - writes, claims, meltWater, snow);
        }
        return writes;
    }

    /**
     * True if a water source written at {@code pos} can only run into a sealed part of the crater's hollow:
     *  - every neighbour that is a hollow cell must be one the water cannot escape from (not in {@code leaky});
     *  - every other neighbour must be a solid block with a sturdy face towards {@code pos}.
     * A neighbour that is air, a plant, another fluid (including a water source this pass wrote), unloaded, or
     * outside the world rejects the placement, and the block stays Rimerite. Only the block being written is
     * replaced; nothing around it is touched.
     */
    private static boolean waterStaysContained(ServerLevel level, Snapshot snap, boolean[] hollow, boolean[] leaky, BlockPos pos) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            n.setWithOffset(pos, d);
            int nx = n.getX() - snap.min.getX();
            int ny = n.getY() - snap.min.getY();
            int nz = n.getZ() - snap.min.getZ();
            boolean inCube = nx >= 0 && ny >= 0 && nz >= 0 && nx < snap.side && ny < snap.side && nz < snap.side;
            if (inCube && hollow[snap.index(nx, ny, nz)]) {
                if (leaky[snap.index(nx, ny, nz)]) {
                    return false; // water would run through this part of the hollow and out of the crater
                }
                continue;
            }
            if (!holdsWaterBack(level, n, d.getOpposite())) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the block at {@code at} stops water coming at it from the side {@code face}: it is loaded,
     * inside the world, holds no fluid and has a sturdy face that way. Air, plants, slabs, fluids and unloaded
     * positions all count as openings.
     *
     * A falling block (sand, gravel, concrete powder) with nothing to rest on (air, fire, a fluid, or an unknown
     * position below it) also counts as an opening: it is about to fall, for example sand above the blast hollow
     * or over a cave, and the gap it leaves would let water out. Hot craters are mostly in sand, so this matters.
     */
    private static boolean holdsWaterBack(ServerLevel level, BlockPos at, Direction face) {
        if (level.isOutsideBuildHeight(at) || !level.hasChunkAt(at)) {
            return false;
        }
        BlockState state = level.getBlockState(at);
        if (!state.getFluidState().isEmpty() || !state.isFaceSturdy(level, at, face)) {
            return false;
        }
        if (state.getBlock() instanceof FallingBlock) {
            BlockPos below = at.below();
            if (level.isOutsideBuildHeight(below) || !level.hasChunkAt(below)) {
                return false;
            }
            return !FallingBlock.isFree(level.getBlockState(below));
        }
        return true;
    }

    /** The directions water can flow in from a cell: sideways and down, never up. */
    private static final Direction[] WATER_FLOW = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * Marks every hollow cell from which water could leave the crater. Water flows sideways and down only, so a
     * hollow cell leaks when, going sideways or down through hollow cells, it reaches an opening: a neighbour
     * outside the hollow that does not hold water back (air outside the crater, a plant, a fluid, a cave, the
     * edge of the cube with nothing sturdy past it, an unloaded chunk).
     *
     * The live world after the blast decides what an opening is, not the snapshot, because the snapshot counts
     * grass and flowers as solid. Worked out once per hot Rimerite crater, before any crater block is written;
     * the crater only ever replaces full blocks with Rimerite, blue ice or sealed meltwater, so the answer stays
     * true while the crater is built. Uses no randomness.
     *
     * How: first every hollow cell with an opening on a side or below is marked. Then the marks spread backwards
     * along the flow: if water in cell C can reach a leaky cell L (L is beside C or below C), C is leaky too, so
     * a leaky cell marks the hollow cells beside it and the hollow cell above it.
     */
    private static boolean[] findLeakyHollow(ServerLevel level, Snapshot snap, boolean[] hollow) {
        int side = snap.side;
        boolean[] leaky = new boolean[hollow.length];
        int[] queue = new int[hollow.length];
        int head = 0;
        int tail = 0;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();

        // Seeds: hollow cells with an opening beside or below them.
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    int i = snap.index(x, y, z);
                    if (!hollow[i]) {
                        continue;
                    }
                    cell.set(snap.min.getX() + x, snap.min.getY() + y, snap.min.getZ() + z);
                    for (Direction d : WATER_FLOW) {
                        int nx = x + d.getStepX();
                        int ny = y + d.getStepY();
                        int nz = z + d.getStepZ();
                        boolean inCube = nx >= 0 && ny >= 0 && nz >= 0 && nx < side && ny < side && nz < side;
                        if (inCube && hollow[snap.index(nx, ny, nz)]) {
                            continue; // more hollow: handled by the spreading step
                        }
                        n.setWithOffset(cell, d);
                        if (!holdsWaterBack(level, n, d.getOpposite())) {
                            leaky[i] = true;
                            queue[tail++] = i;
                            break;
                        }
                    }
                }
            }
        }

        // Spread backwards along the flow: the hollow cells beside a leaky cell, and the hollow cell above it.
        while (head < tail) {
            int i = queue[head++];
            int x = i % side;
            int z = (i / side) % side;
            int y = i / (side * side);
            for (Direction d : Direction.values()) {
                if (d == Direction.DOWN) {
                    continue; // water in the cell below cannot climb up into this one
                }
                int nx = x + d.getStepX();
                int ny = y + d.getStepY();
                int nz = z + d.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= side || ny >= side || nz >= side) {
                    continue;
                }
                int j = snap.index(nx, ny, nz);
                if (hollow[j] && !leaky[j]) {
                    leaky[j] = true;
                    queue[tail++] = j;
                }
            }
        }
        return leaky;
    }

    /** True if any of the six neighbours inside the cube is a hollow cell. */
    private static boolean bordersHollow(Snapshot snap, boolean[] hollow, int x, int y, int z) {
        int side = snap.side;
        for (Direction d : Direction.values()) {
            int nx = x + d.getStepX();
            int ny = y + d.getStepY();
            int nz = z + d.getStepZ();
            if (nx < 0 || ny < 0 || nz < 0 || nx >= side || ny >= side || nz >= side) {
                continue;
            }
            if (hollow[snap.index(nx, ny, nz)]) {
                return true;
            }
        }
        return false;
    }

    /** Which blocks the crater may overwrite: plain solid blocks, nothing special, nothing protected. */
    private static boolean canReplace(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false; // bedrock and other unbreakable blocks
        }
        if (state.hasBlockEntity() || state.is(OhMyMeteors.METEOR_EXPLOSION_SAFE) || state.is(BlockTags.LEAVES)) {
            return false;
        }
        // Anything the blast itself could not break (obsidian, reinforced deepslate, respawn anchor, ancient debris).
        if (state.getBlock().getExplosionResistance() >= MAX_REPLACEABLE_RESISTANCE) {
            return false;
        }
        // Our own blocks and player storage blocks (iron/gold/diamond blocks, the raw cindrite/rimerite blocks...) stay.
        if (state.getBlock() instanceof CindriteBlock || state.getBlock() instanceof RimeriteBlock
                || state.getBlock() instanceof VeyriteBlock
                || state.is(ModBlocks.RAW_CINDRITE_BLOCK.get()) || state.is(ModBlocks.RAW_RIMERITE_BLOCK.get())
                || state.is(ModBlocks.RAW_VEYRITE_BLOCK.get())
                || state.is(Tags.Blocks.STORAGE_BLOCKS)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Rimerite aftermath: a snow layer on the exposed floor of the hollow (config chance; skipped when
     * {@code laySnow} is false, i.e. in a HOT place), and exposed water source blocks in the cube freeze to ice
     * (meltwater from a hot-place impact excepted). Returns the number of blocks written.
     */
    private static int iceAftermath(ServerLevel level, Snapshot snap, boolean[] hollow, RandomSource random, int budget, ClaimGuard claims, Set<Long> meltWater, boolean laySnow) {
        int side = snap.side;
        double snowChance = Config.RIMERITE_IMPACT_SNOW_CHANCE.get();
        BlockState snow = Blocks.SNOW.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int writes = 0;

        for (int y = 0; laySnow && y < side && writes < budget; y++) {
            for (int z = 0; z < side && writes < budget; z++) {
                for (int x = 0; x < side && writes < budget; x++) {
                    if (!hollow[snap.index(x, y, z)]) {
                        continue;
                    }
                    pos.set(snap.min.getX() + x, snap.min.getY() + y, snap.min.getZ() + z);
                    if (!level.getBlockState(pos).isAir() || random.nextDouble() >= snowChance || !claims.allows(pos)) {
                        continue;
                    }
                    // Only on a floor that can carry snow (a sturdy top face below, not ice).
                    if (snow.canSurvive(level, pos)) {
                        level.setBlock(pos, snow, WRITE_FLAGS);
                        writes++;
                    }
                }
            }
        }

        // Exposed water: a source block with air straight above it.
        for (int y = 0; y < side && writes < budget; y++) {
            for (int z = 0; z < side && writes < budget; z++) {
                for (int x = 0; x < side && writes < budget; x++) {
                    pos.set(snap.min.getX() + x, snap.min.getY() + y, snap.min.getZ() + z);
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) {
                        continue;
                    }
                    if (meltWater.contains(pos.asLong())) {
                        continue; // meltwater from a hot-place impact stays water
                    }
                    if (level.getBlockState(pos.above()).isAir() && claims.allows(pos)) {
                        level.setBlock(pos, Blocks.ICE.defaultBlockState(), WRITE_FLAGS | Block.UPDATE_NEIGHBORS);
                        writes++;
                    }
                }
            }
        }
        return writes;
    }
}

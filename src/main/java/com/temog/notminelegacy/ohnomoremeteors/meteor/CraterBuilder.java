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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The crater of a fire or ice meteor: the blast hollows the ground, then blocks that border the hollow are
 * turned into Cindrite or Rimerite (sparse veins on the walls, a dense floor), plus a rare block now and then.
 * An ice meteor also dusts the hollow with snow and freezes exposed water (in a HOT place it does neither:
 * no impact snow, and no freezing of exposed water).
 *
 * Use: {@link #capture} BEFORE the explosion, then {@link #build} AFTER it. The "hollow" is every position that
 * was solid in the snapshot and is empty now, so the crater follows what the explosion really removed
 * (nothing is built when the blast destroyed nothing, for example with meteor griefing off).
 *
 * The idea (a cube around the impact, 30% veins, 100% floor, 1 in 45 rare block) follows the old Falling
 * Meteors crater generator as a concept only; the code is new.
 */
public final class CraterBuilder {

    /**
     * Block update flags for crater writes: send to clients, skip neighbour shape updates (cheap, no cascades).
     * Meltwater and melted-away (air) writes add UPDATE_NEIGHBORS, so water beside the crater re-evaluates and flows.
     */
    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Blocks at or above this blast resistance (obsidian, reinforced deepslate, anchors...) are never overwritten. */
    private static final float MAX_REPLACEABLE_RESISTANCE = 1200.0F;

    private CraterBuilder() {
    }

    /**
     * Land-claim check for crater writes. The blast itself is filtered by claim mods, but crater writes are plain
     * setBlock calls, so each position is checked here through Oh My, Meteors! own claim check (Flan, YAWP,
     * OpenPartiesAndClaims). Answers are cached per position for the one impact this object lives for.
     *
     * If the claim check itself throws (a broken or mismatched claim mod), the position counts as protected and the
     * crater skips it: an unanswered claim check must never let crater blocks overwrite someone's claim. The
     * failure is logged once per game run.
     */
    static final class ClaimGuard {
        /** One warning per game run is enough; afterwards failing positions are skipped quietly. */
        private static volatile boolean loggedFailure;

        private final ServerLevel level;
        private final Map<Long, Boolean> columns = new HashMap<>();

        ClaimGuard(ServerLevel level) {
            this.level = level;
        }

        boolean allows(BlockPos pos) {
            long key = pos.asLong();
            Boolean cached = columns.get(key);
            if (cached == null) {
                boolean ok;
                try {
                    ok = MeteorUtils.canSpawnInModdedRegion(level, pos);
                } catch (RuntimeException | LinkageError ex) {
                    ok = false; // fail closed: treat the position as protected
                    if (!loggedFailure) {
                        loggedFailure = true;
                        OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The Oh My, Meteors! claim check failed; crater blocks skip the positions it could not check.", ex);
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
        /** Per column (z * side + x): highest cube Y index of ground (a solid block that is not leaves or a log), -1 when none. */
        private final int[] top;

        private Snapshot(BlockPos min, int side, boolean[] solidBefore, ThermalReader.Band climate, int[] top) {
            this.min = min;
            this.side = side;
            this.solidBefore = solidBefore;
            this.climate = climate;
            this.top = top;
        }

        /**
         * World Y of the ground around the impact column before the blast: the median ground height of the columns 3 to 6
         * blocks from the column (a fresh dig-in shaft or a tree on the column itself cannot skew it), or the column's own
         * ground when the cube is too small for a ring. Integer.MIN_VALUE when unknown (no ground, or it reaches the top
         * of the cube).
         */
        int groundY() {
            int hw = (side - 1) / 2;
            int[] ys = new int[side * side];
            int n = 0;
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    double d = Math.sqrt((x - hw) * (x - hw) + (z - hw) * (z - hw));
                    int t = top[z * side + x];
                    if (d < 3.0D || d > 6.0D || t < 0) {
                        continue;
                    }
                    ys[n++] = t;
                }
            }
            if (n == 0) {
                int t = top[hw * side + hw];
                return t < 0 ? Integer.MIN_VALUE : min.getY() + t;
            }
            Arrays.sort(ys, 0, n);
            return min.getY() + ys[n / 2];
        }

        private int index(int x, int y, int z) {
            return (y * side + z) * side + x;
        }

        /** Edge length of the captured cube (2 x half-width + 1). */
        public int side() {
            return side;
        }
    }

    /** Crater half-width in blocks for a meteor size: round(size * spread), capped. 0 means no crater. */
    public static int halfWidth(int meteorSize) {
        return halfWidth(meteorSize, 1.0D);
    }

    /** Crater half-width for a variant: the global spread times the variant's craterHalfWidthMult, same cap. */
    public static int halfWidth(int meteorSize, MeteorVariant variant) {
        return halfWidth(meteorSize, VariantTuning.craterMult(variant));
    }

    private static int halfWidth(int meteorSize, double mult) {
        double raw = meteorSize * Config.CRATER_SPREAD_PER_SIZE.get() * mult;
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
        int[] top = new int[side * side];
        Arrays.fill(top, -1);
        Snapshot snap = new Snapshot(min, side, solid, readClimate(level, centre), top);
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
                    if (state.blocksMotion() && state.getFluidState().isEmpty()
                            && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
                        top[z * side + x] = y; // y ascends, so the last hit is the highest ground
                    }
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

    /**
     * Builds what the meteor leaves in its crater, in the configured impactFormula.craterStyle:
     * CORE (default) = a compact meteorite lump sized by the impact formula, LINED = the old ore-lined crater.
     * Returns the number of blocks written.
     */
    public static int build(ServerLevel level, Snapshot snap, MeteorVariant variant, ImpactFormula.Impact impact, BlockPos centre) {
        return buildDry(level, snap, variant, impact, centre, null);
    }

    /**
     * The dry build with the crater carve (2026-11-21): the blast alone leaves patchy holes (OMM's sphere explosion casts
     * few rays), so before the lump is built every weak block in a solid bowl under the impact and along the meteor's
     * last approach {@code approach} (its velocity, may be null) is cleared, and the lump is placed in that bowl with
     * one block of air around it. Skipped (the old anchor logic stays) with meteor griefing off (no snapshot), in the
     * LINED style, and when the ground cannot be read.
     */
    public static int buildDry(ServerLevel level, Snapshot snap, MeteorVariant variant, ImpactFormula.Impact impact, BlockPos centre, Vec3 approach) {
        lastCoreStats = "";
        if (Config.CRATER_STYLE.get() == Config.CraterStyle.LINED) {
            return build(level, snap, variant);
        }
        Carve carve = snap == null ? null : carve(level, snap, impact, approach);
        int written = buildCore(level, snap, variant, impact, centre, null, carve);
        return written + (carve == null ? 0 : carve.cleared);
    }

    /**
     * Wet landing (2026-11-21): always the CORE lump. Water and lava cells count as candidates, so the cluster keeps
     * its shape; a SETTLE landing (no blast, snap may be null) rests the lump on the floor using fluid cells only.
     * {@code wet} null is the dry build above.
     */
    public static int build(ServerLevel level, Snapshot snap, MeteorVariant variant, ImpactFormula.Impact impact, BlockPos centre, FluidLanding.Wet wet) {
        lastCoreStats = "";
        return buildCore(level, snap, variant, impact, centre, wet, null);
    }

    /** Short description of the last CORE build (blast-hole cells, candidates, chosen), for the impact log. */
    static String lastCoreStats = "";

    /** The description of the last CORE build (game tests read it). */
    public static String lastCoreStats() {
        return lastCoreStats;
    }

    /** Vertical squash of the meteorite lump (a slightly flattened ball). */
    private static final double LUMP_SQUASH = 0.75D;

    /** A block is weak (the crater carve removes it) at or under this hardness... */
    private static final float WEAK_MAX_HARDNESS = 3.0F;
    /** ...and at or under this blast resistance (dirt, sand, gravel, leaves, logs, stone, deepslate; not obsidian, end stone). */
    private static final float WEAK_MAX_RESISTANCE = 6.0F;

    /** The lump ball radius for an impact (shared by the carve and the lump). */
    private static double lumpRadius(ImpactFormula.Impact impact) {
        int lump = Math.max(1, impact.lumpBlocks());
        return Math.max(0.75D, Math.cbrt(3.0D * lump / (4.0D * Math.PI * LUMP_SQUASH)));
    }

    /**
     * Weak = the crater carve may remove it: not air, no fluid, destroy speed 0..3, blast resistance <= 6, no block entity,
     * not meteor_explosion_safe, not an ore (forge:ores), not this mod's own or a storage block. No randomness.
     */
    public static boolean weak(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > WEAK_MAX_HARDNESS) {
            return false;
        }
        if (state.getBlock().getExplosionResistance() > WEAK_MAX_RESISTANCE) {
            return false;
        }
        if (state.hasBlockEntity() || state.is(OhMyMeteors.METEOR_EXPLOSION_SAFE) || state.is(Tags.Blocks.ORES)) {
            return false;
        }
        return !isOwnOrStorage(state);
    }

    /** Our own blocks and player storage blocks (iron/gold/diamond blocks, the raw cindrite/rimerite blocks...). */
    private static boolean isOwnOrStorage(BlockState state) {
        return state.getBlock() instanceof CindriteBlock || state.getBlock() instanceof RimeriteBlock
                || state.getBlock() instanceof VeyriteBlock
                || state.is(ModBlocks.RAW_CINDRITE_BLOCK.get()) || state.is(ModBlocks.RAW_RIMERITE_BLOCK.get())
                || state.is(ModBlocks.RAW_VEYRITE_BLOCK.get())
                || state.is(Tags.Blocks.STORAGE_BLOCKS);
    }

    /** What the carve did: the bowl (ground, depth, radius, column) and the lump core it fixes. */
    static final class Carve {
        final int ground;
        final int depth;
        final int radius;
        final int cx;
        final int cz;
        final int coreY;
        int cleared;
        int margin;

        Carve(int ground, int depth, int radius, int cx, int cz, int coreY) {
            this.ground = ground;
            this.depth = depth;
            this.radius = radius;
            this.cx = cx;
            this.cz = cz;
            this.coreY = coreY;
        }

        BlockPos core() {
            return new BlockPos(cx, coreY, cz);
        }

        /** Bowl surface height at a column: the solid floor top; the ground itself outside the bowl. */
        double surface(int wx, int wz) {
            double d = Math.sqrt((wx - cx) * (double) (wx - cx) + (wz - cz) * (double) (wz - cz));
            return d <= radius ? ground - depth * (1.0D - d * d / ((double) radius * radius)) : ground;
        }
    }

    /**
     * The crater carve: clears every weak block of a solid bowl and of a tube along the approach, bottom up, with the
     * claim check and the write cap (it leaves room for the lump). No randomness, so the same terrain gives the same
     * carve. Returns null when nothing is carved (ground unknown, or nothing weak in the way).
     *
     * Bowl: radius Rb = clamp(round(0.6 * power), 2, halfWidth - 1), depth Db = max(2, ceil(0.5 Rb), ceil(1.4 * lump half
     * height)); solid floor top at ground - Db, surface ground - Db * (1 - d^2 / Rb^2), cleared up to ground + 3.
     * The lump core is then fixed at (ground - Db) + ceil(half height) - embed, embed = max(1, round(0.6 * half height)).
     */
    private static Carve carve(ServerLevel level, Snapshot snap, ImpactFormula.Impact impact, Vec3 approach) {
        int ground = snap.groundY();
        if (ground == Integer.MIN_VALUE) {
            return null;
        }
        int side = snap.side;
        int hw = (side - 1) / 2;
        double ballRadius = lumpRadius(impact);
        double hl = ballRadius * LUMP_SQUASH;
        int rb = Math.max(2, Math.min((int) Math.round(0.6D * impact.power()), hw - 1));
        int db = Math.max(2, Math.max((int) Math.ceil(0.5D * rb), (int) Math.ceil(1.4D * hl)));
        int embed = Math.max(1, (int) Math.round(0.6D * hl));
        int cx = snap.min.getX() + hw;
        int cz = snap.min.getZ() + hw;
        int coreY = ground - db + (int) Math.ceil(hl) - embed;
        Carve carve = new Carve(ground, db, rb, cx, cz, coreY);
        int minY = snap.min.getY();

        boolean[] mask = new boolean[side * side * side];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int wx = snap.min.getX() + x;
                int wz = snap.min.getZ() + z;
                double d = Math.sqrt((wx - cx) * (double) (wx - cx) + (wz - cz) * (double) (wz - cz));
                if (d > rb) {
                    continue;
                }
                double bs = carve.surface(wx, wz);
                for (int y = 0; y < side; y++) {
                    int wy = minY + y;
                    if (wy > bs + 1.0E-6D && wy <= ground + 3) {
                        mask[snap.index(x, y, z)] = true;
                    }
                }
            }
        }
        // The tube along the last approach: from the core point back along -velocity (straight up when it is unknown).
        double ux = 0.0D, uy = 1.0D, uz = 0.0D;
        if (approach != null && approach.length() >= 0.05D) {
            Vec3 u = approach.normalize().scale(-1.0D);
            ux = u.x;
            uy = u.y;
            uz = u.z;
        }
        double tubeR = Math.ceil(ballRadius) + 0.5D;
        int steps = (int) Math.ceil((db + 4) / 0.5D);
        int r = (int) Math.ceil(tubeR);
        for (int s = 0; s <= steps; s++) {
            double px = cx + 0.5D + ux * 0.5D * s;
            double py = coreY + 0.5D + uy * 0.5D * s;
            double pz = cz + 0.5D + uz * 0.5D * s;
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        int bx = (int) Math.floor(px) + dx;
                        int by = (int) Math.floor(py) + dy;
                        int bz = (int) Math.floor(pz) + dz;
                        double ex = bx + 0.5D - px;
                        double ey = by + 0.5D - py;
                        double ez = bz + 0.5D - pz;
                        if (ex * ex + ey * ey + ez * ez > tubeR * tubeR) {
                            continue;
                        }
                        int x = bx - snap.min.getX();
                        int y = by - minY;
                        int z = bz - snap.min.getZ();
                        if (x >= 0 && y >= 0 && z >= 0 && x < side && y < side && z < side) {
                            mask[snap.index(x, y, z)] = true;
                        }
                    }
                }
            }
        }

        ClaimGuard claims = new ClaimGuard(level);
        int writeCap = Config.CRATER_MAX_BLOCK_WRITES.get();
        int budget = Math.max(writeCap / 2, writeCap - Math.max(1, impact.lumpBlocks()) - 64);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        outer:
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    if (!mask[snap.index(x, y, z)]) {
                        continue;
                    }
                    pos.set(snap.min.getX() + x, minY + y, snap.min.getZ() + z);
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    if (!weak(level, pos, level.getBlockState(pos)) || !claims.allows(pos)) {
                        continue;
                    }
                    if (carve.cleared >= budget) {
                        break outer;
                    }
                    level.setBlock(pos, air, WRITE_FLAGS);
                    carve.cleared++;
                }
            }
        }
        return carve.cleared > 0 ? carve : null;
    }

    /**
     * CORE style (2026-11-19, OMM-like): a compact meteorite lump at the bottom of the blast hole, half sunk into the
     * ground, made of impact.lumpBlocks() blocks, impact.resources() of them ore (the variant's crater block, set
     * towards the middle), the rest the variant's rock filler with now and then the rare block. Only blasted-out
     * cells and plain replaceable blocks are used, with the same claim and protection checks as the lined crater.
     * The Rimerite snow/freeze aftermath still runs. Nothing is built when the blast removed nothing.
     */
    private static int buildCore(ServerLevel level, Snapshot snap, MeteorVariant variant, ImpactFormula.Impact impact, BlockPos centre, FluidLanding.Wet wet, Carve carve) {
        if (snap == null && wet == null) {
            return 0;
        }
        int side = snap == null ? 0 : snap.side;
        boolean[] hollow = snap == null ? null : findHollow(level, snap);
        if (hollow == null && wet == null) {
            lastCoreStats = "no blast hole";
            return 0;
        }
        // Fluid cells join the lump's candidates on a wet landing, and (fillExposedFluid) when a dry blast hole opens onto fluid.
        boolean settle = wet != null && (wet.mode() == FluidLanding.Mode.SETTLE || hollow == null);
        boolean fluidCells = wet != null || (Config.FL_ENABLED.get() && Config.FL_FILL_EXPOSED_FLUID.get() && hollowTouchesFluid(level, snap, hollow));
        RandomSource random = level.getRandom();
        ClaimGuard claims = new ClaimGuard(level);
        // The carve's writes count against the same cap (it kept lump + 64 blocks back for the lump and the margin).
        int cap = Config.CRATER_MAX_BLOCK_WRITES.get() - (carve == null ? 0 : carve.cleared);

        BlockPos core = settle ? null : (carve != null ? carve.core() : hollowAnchor(snap, hollow, centre));

        int lump = Math.max(1, impact.lumpBlocks());
        int ore = Math.min(lump, Math.max(1, impact.resources()));
        double radius = lumpRadius(impact);
        // Search wide enough that a lump fits even in a shallow or odd-shaped hole; the nearest cells win anyway.
        double maxNorm = 2.6D;
        int reach = (int) Math.ceil(radius * maxNorm) + 1;
        if (core == null) {
            // SETTLE: no blast hole. The squashed ball rests on the floor, its lowest layer touching it.
            core = new BlockPos(centre.getX(), wet.floorTop().getY() + (int) Math.ceil(radius * LUMP_SQUASH) - 1, centre.getZ());
        }

        // Candidates: blasted-out cells of the crater and plain replaceable blocks, ranked by distance plus noise.
        // Each entry: {rank score, ore score, x, y, z}.
        List<double[]> cells = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    double vy = dy / LUMP_SQUASH;
                    double norm = Math.sqrt(dx * dx + vy * vy + dz * dz) / radius;
                    if (norm > maxNorm) {
                        continue;
                    }
                    pos.set(core.getX() + dx, core.getY() + dy, core.getZ() + dz);
                    if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    int x = snap == null ? -1 : pos.getX() - snap.min.getX();
                    int y = snap == null ? -1 : pos.getY() - snap.min.getY();
                    int z = snap == null ? -1 : pos.getZ() - snap.min.getZ();
                    boolean inCube = hollow != null && x >= 0 && y >= 0 && z >= 0 && x < side && y < side && z < side;
                    BlockState now = level.getBlockState(pos);
                    boolean blasted = inCube && hollow[snap.index(x, y, z)] && (now.isAir() || now.is(BlockTags.FIRE));
                    if (settle) {
                        // Nothing is destroyed: only fluid cells take the lump.
                        if (!FluidLanding.isFluidCell(level, pos, now)) {
                            continue;
                        }
                    } else if (!blasted && !canReplace(level, pos, now) && !(fluidCells && FluidLanding.isFluidCell(level, pos, now))
                            && !(carve != null && now.isAir())) { // a carved bowl: the air above the old ground takes the lump too
                        continue;
                    }
                    cells.add(new double[]{norm + random.nextDouble() * 0.45D, norm + random.nextDouble() * 0.6D,
                            pos.getX(), pos.getY(), pos.getZ()});
                }
            }
        }
        cells.sort(Comparator.comparingDouble(c -> c[0]));
        List<double[]> chosen = new ArrayList<>();
        int limit = Math.min(lump, cap);
        for (double[] c : cells) {
            if (chosen.size() >= limit) {
                break;
            }
            pos.set((int) c[2], (int) c[3], (int) c[4]);
            if (claims.allows(pos)) {
                chosen.add(c);
            }
        }
        int holeCells = 0;
        if (hollow != null) {
            for (boolean b : hollow) {
                if (b) holeCells++;
            }
        }
        lastCoreStats = "hole " + holeCells + ", core " + core.toShortString() + ", candidates " + cells.size() + ", chosen " + chosen.size();
        if (carve != null) {
            lastCoreStats += ", carve " + carve.cleared + " (ground " + carve.ground + ", floor " + (carve.ground - carve.depth)
                    + ", r " + carve.radius + ")";
        }
        if (wet != null) {
            lastCoreStats += ", wet " + (settle ? "settle" : "blast");
        } else if (fluidCells) {
            lastCoreStats += ", exposed fluid filled";
        }
        // Ore goes to the cells with the lowest ore score (towards the middle, with some scatter).
        List<double[]> byOre = new ArrayList<>(chosen);
        byOre.sort(Comparator.comparingDouble(c -> c[1]));
        Set<double[]> oreCells = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < Math.min(ore, byOre.size()); i++) {
            oreCells.add(byOre.get(i));
        }

        // Rimerite in an ultra-warm dimension (the Nether) evaporates: its ore cells are left empty.
        boolean meltAway = variant == MeteorVariant.RIMERITE && ThermalReader.isUltraWarm(level);
        int rareOneIn = Config.CRATER_RARE_BLOCK_ONE_IN.get();
        int fillerRareOneIn = rareOneIn <= 0 ? 0 : Math.max(2, rareOneIn / 4);
        int writes = 0;
        for (double[] c : chosen) {
            pos.set((int) c[2], (int) c[3], (int) c[4]);
            BlockState placed;
            if (oreCells.contains(c)) {
                placed = meltAway ? Blocks.AIR.defaultBlockState() : variant.craterState();
            } else if (fillerRareOneIn > 0 && random.nextInt(fillerRareOneIn) == 0) {
                placed = variant.rareState();
            } else {
                placed = filler(variant, random);
            }
            level.setBlock(pos, placed, placed.isAir() ? WRITE_FLAGS | Block.UPDATE_NEIGHBORS : WRITE_FLAGS);
            writes++;
        }

        // Air margin (carved bowl only): every weak block touching the lump above the bowl surface is cleared, so the lump
        // has at least one block of air around it everywhere except where it sits in the ground.
        if (carve != null) {
            Set<Long> lumpCells = new HashSet<>();
            for (double[] c : chosen) {
                lumpCells.add(BlockPos.asLong((int) c[2], (int) c[3], (int) c[4]));
            }
            BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            BlockState airState = Blocks.AIR.defaultBlockState();
            marginLoop:
            for (double[] c : chosen) {
                for (Direction d : Direction.values()) {
                    n.set((int) c[2] + d.getStepX(), (int) c[3] + d.getStepY(), (int) c[4] + d.getStepZ());
                    if (lumpCells.contains(n.asLong()) || n.getY() <= carve.surface(n.getX(), n.getZ()) + 1.0E-6D
                            || level.isOutsideBuildHeight(n) || !level.hasChunkAt(n)) {
                        continue;
                    }
                    if (!weak(level, n, level.getBlockState(n)) || !claims.allows(n)) {
                        continue;
                    }
                    if (writes >= cap) {
                        break marginLoop;
                    }
                    level.setBlock(n, airState, WRITE_FLAGS);
                    writes++;
                    carve.margin++;
                }
            }
            lastCoreStats += ", margin " + carve.margin + ", core y " + carve.coreY;
        }

        // Cindrite: now and then a small sealed lava pocket inside the lump (never where it could run out).
        if (variant == MeteorVariant.CINDRITE && random.nextDouble() < Config.CINDRITE_LAVA_POCKET_CHANCE.get()) {
            int want = 1 + random.nextInt(Math.max(1, Config.CINDRITE_LAVA_POCKET_MAX.get()));
            List<double[]> inner = new ArrayList<>(chosen);
            inner.sort(Comparator.comparingDouble(c -> c[0]));
            int placed = 0;
            for (double[] c : inner) {
                if (placed >= want) {
                    break;
                }
                if (oreCells.contains(c)) {
                    continue;
                }
                pos.set((int) c[2], (int) c[3], (int) c[4]);
                if (sealedBelowAndSides(level, pos)) {
                    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), WRITE_FLAGS);
                    placed++;
                }
            }
            lastCoreStats += ", lava " + placed;
        }

        if (variant == MeteorVariant.RIMERITE && writes < cap && hollow != null && !(wet != null && wet.lava())) {
            boolean snow = snap.climate != ThermalReader.Band.HOT;
            writes += iceAftermath(level, snap, hollow, random, cap - writes, claims, new HashSet<>(), snow);
        }
        return writes;
    }

    /** True when any blasted-out cell of a dry crater has a water or lava block (not waterlogged) beside it. Reads blocks only. */
    private static boolean hollowTouchesFluid(ServerLevel level, Snapshot snap, boolean[] hollow) {
        if (snap == null || hollow == null) {
            return false;
        }
        int side = snap.side;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    if (!hollow[snap.index(x, y, z)]) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        pos.set(snap.min.getX() + x + d.getStepX(), snap.min.getY() + y + d.getStepY(), snap.min.getZ() + z + d.getStepZ());
                        if (!level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)
                                && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** The bottom of the blast hole near the impact column (the CORE lump's anchor). */
    private static BlockPos hollowAnchor(Snapshot snap, boolean[] hollow, BlockPos centre) {
        int side = snap.side;
        // Anchor: the bottom of the blast hole near the impact column (deepest blasted-out cell, preferring cells
        // close to the column). The impact point itself can be in open air (a meteor that clipped a tree).
        int cx = Mth.clamp(centre.getX() - snap.min.getX(), 0, side - 1);
        int cz = Mth.clamp(centre.getZ() - snap.min.getZ(), 0, side - 1);
        int bestIndex = -1;
        double bestScore = Double.MAX_VALUE;
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    if (!hollow[snap.index(x, y, z)]) {
                        continue;
                    }
                    double score = y + 0.5D * Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (score < bestScore) {
                        bestScore = score;
                        bestIndex = snap.index(x, y, z);
                    }
                }
            }
        }
        int bx = bestIndex % side;
        int bz = (bestIndex / side) % side;
        int by = bestIndex / (side * side);
        BlockPos core = new BlockPos(snap.min.getX() + bx, snap.min.getY() + by, snap.min.getZ() + bz);
        return core;
    }

    /** True when the blocks below and on all four sides are solid, non-fluid blocks (lava placed here cannot flow out). */
    private static boolean sealedBelowAndSides(ServerLevel level, BlockPos pos) {
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = pos.relative(d);
            if (level.isOutsideBuildHeight(n) || !level.hasChunkAt(n)) {
                return false;
            }
            BlockState st = level.getBlockState(n);
            if (st.isAir() || !st.getFluidState().isEmpty() || !st.isFaceSturdy(level, n, d.getOpposite())) {
                return false;
            }
        }
        return true;
    }

    /** The rock around the ore in a meteorite lump, per variant (public: the Komit Kitty capsule's outer shell reuses it). */
    public static BlockState filler(MeteorVariant variant, RandomSource random) {
        double r = random.nextDouble();
        BlockState state = switch (variant) {
            case CINDRITE -> (r < 0.55D ? Blocks.BLACKSTONE : r < 0.85D ? Blocks.BASALT : Blocks.SMOOTH_BASALT).defaultBlockState();
            case RIMERITE -> (r < 0.5D ? Blocks.PACKED_ICE : r < 0.85D ? ModBlocks.RIME_WORN_STONE.get() : Blocks.SNOW_BLOCK).defaultBlockState();
            case VEYRITE -> (r < 0.5D ? ModBlocks.TEKTITE.get() : r < 0.85D ? Blocks.DEEPSLATE : Blocks.CRYING_OBSIDIAN).defaultBlockState();
        };
        // Crater extras (other mods' blocks in the stone filler, tag crater_extras/<variant>): draws nothing more when
        // the tag is empty, the chance is 0 or the filler is not stone, so craters without extras stay exactly as before.
        return CraterExtras.apply(variant, state, random);
    }

    /** Every cube cell that was solid before the blast and is air or fire now; null when there is none. */
    private static boolean[] findHollow(ServerLevel level, Snapshot snap) {
        int side = snap.side;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] hollow = new boolean[snap.solidBefore.length];
        boolean any = false;
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
                        any = true;
                    }
                }
            }
        }
        return any ? hollow : null;
    }

    /** LINED style: the old Falling Meteors ore-lined crater (and the ice meteor's aftermath). Returns the number of blocks written. */
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
        // Positions written as meltwater (the ice aftermath also skips them; it does not freeze at all in a HOT place).
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
                    level.setBlock(pos, placed, (placed.isAir() || placed.is(Blocks.WATER)) ? WRITE_FLAGS | Block.UPDATE_NEIGHBORS : WRITE_FLAGS);
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
        if (isOwnOrStorage(state)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Rimerite aftermath: a snow layer on the exposed floor of the hollow (config chance; skipped when
     * {@code laySnow} is false, i.e. in a HOT place), and exposed water source blocks in the cube freeze to ice
     * (meltwater from a hot-place impact excepted). In a HOT place the freezing is skipped entirely, so existing
     * water there stays water. Returns the number of blocks written.
     */
    private static int iceAftermath(ServerLevel level, Snapshot snap, boolean[] hollow, RandomSource random, int budget, ClaimGuard claims, Set<Long> meltWater, boolean laySnow) {
        int side = snap.side;
        double snowChance = Math.min(1.0D, Config.RIMERITE_IMPACT_SNOW_CHANCE.get() * Config.RIMERITE_V_IMPACT_SNOW_MULT.get());
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

        // Exposed water: a source block with air straight above it. Not in a HOT place (same reason as the snow).
        for (int y = 0; snap.climate != ThermalReader.Band.HOT && y < side && writes < budget; y++) {
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

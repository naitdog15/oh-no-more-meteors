package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Water, lava and ice landings of claimed meteors (2026-11-21). A meteor that meets an ice sheet over a fluid
 * shatters an ice disc sized to itself and keeps falling; at the fluid floor the water, lava and ice it passed
 * through have slowed it:
 * <pre>
 *   remaining = sqrt(speedRoll) - waterSlow x water - lavaSlow x lava - iceSlow x iceLayers
 * </pre>
 * Below the destruction threshold the meteor only SETTLEs (no blast, the lump rests on the floor); otherwise it
 * makes a WET BLAST at the floor (power scaled by remaining / sqrt(speedRoll), fluid cells neither stop the rays
 * nor get removed). Dry landings never reach the write or random-draw parts of this class: {@link #detect} only
 * reads blocks.
 */
public final class FluidLanding {

    /** ForgeData key: ice layers this meteor has shattered on its way down. */
    public static final String ICE_LAYERS_KEY = "ohnomoremeteors:ice_layers";
    /** ForgeData key: how many times this meteor shattered an ice sheet (loop guard). */
    public static final String ICE_SHATTERS_KEY = "ohnomoremeteors:ice_shatters";
    /** The claim string Building Bridges writes on its Sky Stone meteors. */
    public static final String AE2_CLAIM = "buildingbridges:ae2";

    private static final long SALT_ICE = 0x1CE5A77E12L;
    private static final int MAX_SHATTERS = 4;
    private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static volatile boolean loggedFailure;

    public enum Mode { SETTLE, BLAST }

    /**
     * One landing read. {@code wet} false = dry (every other field then unused). {@code floorTop} is the lowest
     * fluid cell of the column (the cell just above the floor), {@code entry} is where the meteor came through the
     * surface (traced back along its velocity), {@code lava} is true when the floor cell holds lava.
     */
    public record Wet(boolean wet, Mode mode, BlockPos floorTop, BlockPos entry, int nWater, int nLava, int mIce,
                      double remaining, double v0, boolean lava) {
    }

    private static final Wet DRY = new Wet(false, null, null, null, 0, 0, 0, 0.0D, 1.0D, false);

    private FluidLanding() {
    }

    public static void warnFailureOnce(String what, Throwable ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The fluid landing step '{}' failed; the old behaviour is used (this is logged once).", what, ex);
        }
    }

    /** True when the meteor carries Building Bridges' Sky Stone claim. */
    public static boolean isAe2Claim(Entity meteor) {
        CompoundTag data = meteor.getPersistentData();
        return data.contains(MeteorVariant.NBT_KEY, Tag.TAG_STRING)
                && AE2_CLAIM.equals(data.getString(MeteorVariant.NBT_KEY));
    }

    /** True for water or lava (tagged, so modded water-likes count); every other fluid keeps the old behaviour. */
    public static boolean isWaterOrLava(FluidState fluid) {
        return !fluid.isEmpty() && (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA));
    }

    /** A fluid cell: water or lava (source or flowing), or a non-solid plant / bubble column that sits in water. */
    public static boolean isFluidCell(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isWaterOrLava(state.getFluidState())) {
            return false;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
    }

    /**
     * Reads the column under the meteor. Returns null when fluidLandings are switched off (the caller keeps the
     * old behaviour), a result with wet false for a dry landing, otherwise the wet landing with its mode.
     * Reads blocks only: no writes, no random draws.
     */
    @Nullable
    public static Wet detect(ServerLevel level, Entity meteor, BlockPos centre) {
        if (!Config.FL_ENABLED.get()) {
            return null;
        }
        int maxScan = Config.FL_MAX_SCAN.get();
        BlockPos.MutableBlockPos p = centre.mutable();
        BlockPos firstFluid = null;
        for (int i = 0; i < maxScan; i++) {
            if (!loaded(level, p)) {
                return DRY;
            }
            BlockState s = level.getBlockState(p);
            if (isFluidCell(level, p, s)) {
                firstFluid = p.immutable();
                break;
            }
            if (!(s.isAir() || s.is(BlockTags.FIRE) || s.is(OhMyMeteors.METEOR_BYPASSES))) {
                return DRY;
            }
            p.move(Direction.DOWN);
        }
        if (firstFluid == null) {
            return DRY;
        }
        // The floor: follow the fluid down to the first non-fluid cell.
        BlockPos floorTop = firstFluid;
        BlockPos.MutableBlockPos q = firstFluid.mutable();
        boolean floorFound = false;
        for (int i = 0; i < maxScan; i++) {
            q.move(Direction.DOWN);
            if (!loaded(level, q)) {
                break;
            }
            if (!isFluidCell(level, q, level.getBlockState(q))) {
                floorFound = true;
                break;
            }
            floorTop = q.immutable();
        }
        if (!floorFound) {
            // Scan limit used up, or the column ends in unloaded chunks / the world bottom: no real floor, so fail open
            // (the old behaviour) rather than land the meteor in mid-water.
            return DRY;
        }
        // The column the meteor passed through: from the floor up to the surface.
        int nWater = 0;
        int nLava = 0;
        q.set(floorTop);
        for (int i = 0; i < maxScan * 2; i++) {
            if (!loaded(level, q)) {
                break;
            }
            BlockState s = level.getBlockState(q);
            if (!isFluidCell(level, q, s)) {
                break;
            }
            if (s.getFluidState().is(FluidTags.LAVA)) {
                nLava++;
            } else {
                nWater++;
            }
            q.move(Direction.UP);
        }
        boolean lava = level.getFluidState(floorTop).is(FluidTags.LAVA);
        // Entry point: trace back from the floor along the (reversed) velocity until the fluid ends.
        Vec3 v = meteor.getDeltaMovement();
        Vec3 back = (v.lengthSqr() > 1.0E-6D && v.y < 0.0D) ? v.scale(-1.0D).normalize() : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 at = Vec3.atCenterOf(floorTop);
        BlockPos entry = floorTop;
        for (int i = 0; i < maxScan * 4; i++) {
            at = at.add(back.scale(0.5D));
            entry = BlockPos.containing(at);
            if (!loaded(level, entry) || !isFluidCell(level, entry, level.getBlockState(entry))) {
                break;
            }
        }
        int mIce = meteor.getPersistentData().getInt(ICE_LAYERS_KEY);
        return classify(meteor, floorTop, entry, nWater, nLava, mIce, lava);
    }

    private static Wet classify(Entity meteor, BlockPos floorTop, BlockPos entry, int nWater, int nLava, int mIce, boolean lava) {
        double v0 = Math.sqrt(Math.max(0.01D, ImpactFormula.speedRoll(meteor)));
        double remaining = Math.max(0.0D, v0 - Config.FL_WATER_SLOW.get() * nWater - Config.FL_LAVA_SLOW.get() * nLava
                - Config.FL_ICE_SLOW.get() * mIce);
        Mode mode = remaining < Config.FL_THRESHOLD.get() ? Mode.SETTLE : Mode.BLAST;
        return new Wet(true, mode, floorTop, entry, nWater, nLava, mIce, remaining, v0, lava);
    }

    /**
     * Plain ice that OMM already bypassed (a bounding-box hole, the rest of the sheet still stands): shatter the
     * sheet around the entry point once, and count its layers into the slowing. Returns the (possibly updated) wet.
     */
    public static Wet resolveBypassedSheet(ServerLevel level, MeteorProjectileEntity meteor, Wet wet) {
        if (!wet.wet() || wet.mIce() > 0) {
            return wet;
        }
        int layers = shatterAndRecord(level, meteor, wet.entry());
        if (layers <= 0) {
            return wet;
        }
        return classify(meteor, wet.floorTop(), wet.entry(), wet.nWater(), wet.nLava(), layers, wet.lava());
    }

    /**
     * Building Bridges' Sky Stone meteors are not ours to land, but they get the same ice disc: before BB's own
     * impact runs, shatter the bypassed sheet at the entry point. The caller catches any failure (fail open).
     */
    public static void shatterForForeign(ServerLevel level, MeteorProjectileEntity meteor) {
        BlockPos centre = BlockPos.containing(meteor.position());
        Wet wet = detect(level, meteor, centre);
        if (wet != null && wet.wet()) {
            resolveBypassedSheet(level, meteor, wet);
        }
    }

    /**
     * Case A: the meteor was stopped by a non-bypassed ice block (packed, blue, frosted) lying over fluid. Shatters
     * the disc and records it; the caller then resets OMM's embed state and cancels the impact so the meteor flies
     * on. Returns false (nothing changed) for every other meteor or landing.
     */
    public static boolean shatterIceAhead(ServerLevel level, MeteorProjectileEntity meteor) {
        if (!Config.FL_ENABLED.get()) {
            return false;
        }
        MeteorVariant variant = MeteorVariant.fromEntity(meteor);
        if (!((variant != null && variant.isEnabled()) || isAe2Claim(meteor))) {
            return false;
        }
        if (meteor.getPersistentData().getInt(ICE_SHATTERS_KEY) >= MAX_SHATTERS) {
            return false;
        }
        BlockPos top = iceBelow(level, meteor);
        if (top == null) {
            return false;
        }
        return shatterAndRecord(level, meteor, top) > 0;
    }

    /** The topmost ice cell under the meteor's feet when it lies over a fluid (at most iceMaxThickness thick), else null. */
    @Nullable
    private static BlockPos iceBelow(ServerLevel level, Entity meteor) {
        BlockPos.MutableBlockPos p = BlockPos.containing(meteor.position()).mutable();
        BlockPos top = null;
        for (int i = 0; i < 4; i++) {
            if (!loaded(level, p)) {
                return null;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.ICE)) {
                top = p.immutable();
                break;
            }
            if (!(s.isAir() || s.is(BlockTags.FIRE) || s.is(OhMyMeteors.METEOR_BYPASSES))) {
                return null;
            }
            p.move(Direction.DOWN);
        }
        if (top == null) {
            return null;
        }
        int maxT = Config.FL_ICE_MAX_THICKNESS.get();
        int thick = 0;
        BlockPos.MutableBlockPos q = top.mutable();
        while (thick <= maxT && loaded(level, q) && level.getBlockState(q).is(BlockTags.ICE)) {
            thick++;
            q.move(Direction.DOWN);
        }
        if (thick > maxT || !loaded(level, q) || !isFluidCell(level, q, level.getBlockState(q))) {
            return null;
        }
        return top;
    }

    private static int shatterAndRecord(ServerLevel level, MeteorProjectileEntity meteor, BlockPos entry) {
        int layers = shatter(level, meteor.getSize(), entry);
        if (layers > 0) {
            CompoundTag data = meteor.getPersistentData();
            data.putInt(ICE_LAYERS_KEY, data.getInt(ICE_LAYERS_KEY) + layers);
            data.putInt(ICE_SHATTERS_KEY, data.getInt(ICE_SHATTERS_KEY) + 1);
        }
        return layers;
    }

    /**
     * Clears an ice disc of radius ceil(size / 2 x scale) around {@code entry}: every ice cell in the entry layer
     * and the contiguous ice above and below it (at most iceMaxThickness each way), with a ragged ring one block
     * wide (each ring column cleared at 50%, seeded from the entry position). Cells become air: no drops, no
     * meltwater. Claim-protected cells and meteor_griefing=false leave the ice alone. Returns the number of layers
     * (heights) cleared.
     */
    private static int shatter(ServerLevel level, int size, BlockPos entry) {
        if (!OhMyMeteors.CONFIG.meteorBehaviourSection.meteor_griefing) {
            return 0;
        }
        int r = Math.max(1, (int) Math.ceil(size / 2.0D * Config.FL_ICE_SHATTER_SCALE.get()));
        int maxT = Config.FL_ICE_MAX_THICKNESS.get();
        RandomSource rng = RandomSource.create(entry.asLong() ^ SALT_ICE);
        CraterBuilder.ClaimGuard claims = new CraterBuilder.ClaimGuard(level);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        java.util.Set<Integer> layers = new java.util.HashSet<>();
        int cleared = 0;
        int writeCap = Math.max(1, Config.CRATER_MAX_BLOCK_WRITES.get());
        // Columns picked as before (same random draws), then cleared from the middle outwards so a capped shatter
        // still opens a usable hole along the flight path.
        java.util.List<int[]> columns = new java.util.ArrayList<>();
        for (int dz = -r - 1; dz <= r + 1; dz++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > (r + 1) * (r + 1)) {
                    continue;
                }
                if (d2 > r * r && rng.nextBoolean()) {
                    continue;
                }
                columns.add(new int[]{d2, dx, dz});
            }
        }
        columns.sort(java.util.Comparator.comparingInt(c -> c[0]));
        for (int[] col : columns) {
            int dx = col[1];
            int dz = col[2];
            {
                for (int dir = 0; dir < 2; dir++) {
                    // dir 0: the entry layer and the ice above it; dir 1: the ice below it.
                    for (int k = 0; k < maxT; k++) {
                        pos.set(entry.getX() + dx, entry.getY() + (dir == 0 ? k : -k - 1), entry.getZ() + dz);
                        if (!loaded(level, pos) || !level.getBlockState(pos).is(BlockTags.ICE)) {
                            break;
                        }
                        if (!claims.allows(pos)) {
                            break;
                        }
                        if (cleared >= writeCap) {
                            break;
                        }
                        level.setBlock(pos, air, WRITE_FLAGS);
                        cleared++;
                        layers.add(pos.getY());
                    }
                }
            }
            if (cleared >= writeCap) {
                break;
            }
        }
        if (cleared <= 0) {
            return 0;
        }
        try {
            double x = entry.getX() + 0.5D;
            double y = entry.getY() + 0.5D;
            double z = entry.getZ() + 0.5D;
            level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5D, z, Math.min(120, 20 + cleared), r * 0.6D, 0.4D, r * 0.6D, 0.15D);
            level.playSound(null, entry, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.0F, 0.6F);
        } catch (RuntimeException ex) {
            warnFailureOnce("ice shatter effects", ex);
        }
        return layers.size();
    }
}

package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.blocks.advanced_laser.AdvancedMeteorLaserBlockEntity;
import me.emafire003.dev.ohmymeteors.blocks.basic_laser.BasicMeteorLaserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteor laser discovery and the pure maths of the laser roll for Komit Kitty showers.
 *
 * Each laser has a PASS CHANCE: the exact probability that a kitty landing inside its square gets past it
 * (Basic 0.10, Advanced 0.0 by default). The chance is spread over the laser's "band" (the column from a little above
 * the laser up to OMM's detection height) with one small roll per tick, and whatever share of the band is left when the
 * kitty hits the ground is rolled once at impact. Survival over all the rolls multiplies out to exactly the pass chance,
 * whatever the band height, the kitty's speed or the terrain.
 */
public final class KittyLasers {

    /** Hard cap on the discovery range in blocks (at most 33 x 33 chunk lookups). */
    static final double MAX_REACH = 256.0D;

    private KittyLasers() {
    }

    /**
     * One laser block found in a loaded chunk. The block entity is kept so the laser can be checked again right
     * before it kills (it may have been broken or roofed over since the list was built).
     */
    public static final class Laser {
        public final BlockPos pos;
        public final BlockEntity be;
        public final boolean advanced;
        /** Half-width of the square the laser guards against kitties, in blocks (the larger of OMM's radius and the kitty key). */
        public final int radius;
        /** Lowest y of the band: the laser's y plus {@code laserMinAltitude}. */
        public final double bandBottom;
        /** Highest y of the band: the top of OMM's detection slab. */
        public final double bandTop;
        /** Game time of the last shot sound, for the per-laser sound limit. */
        long lastSoundTick = Long.MIN_VALUE / 2;
        /** Set when a use-time check failed; skipped until the next refresh builds a fresh list. */
        boolean dead;

        public Laser(BlockPos pos, BlockEntity be, boolean advanced, int radius, double bandBottom, double bandTop) {
            this.pos = pos;
            this.be = be;
            this.advanced = advanced;
            this.radius = radius;
            this.bandBottom = bandBottom;
            this.bandTop = bandTop;
        }
    }

    /**
     * Finds the Meteor Lasers that can guard a shower around (cx, cz) with the given spread. Only loaded chunks are
     * read (never loaded by this call), and only the block-entity maps are iterated, so there is no block scan.
     * Returns an empty list in dimensions without skylight, because OMM runs no laser there.
     */
    public static ArrayList<Laser> gather(ServerLevel level, double cx, double cz, double spread) {
        ArrayList<Laser> out = new ArrayList<>();
        if (!level.dimensionType().hasSkyLight()) {
            return out;
        }
        var cfg = OhMyMeteors.CONFIG.lasersSection;
        int basicRadius = Config.FIX_BASIC_LASER_RADIUS.get() ? cfg.basic_laser_area_radius : cfg.basic_laser_height;
        int advancedRadius = cfg.advanced_laser_area_radius;
        // Kitty-only reach (user ruling 2026-11-20): wider than OMM's own square, never narrower.
        basicRadius = Math.max(basicRadius, Config.KITTY_BASIC_LASER_RADIUS.get());
        advancedRadius = Math.max(advancedRadius, Config.KITTY_ADVANCED_LASER_RADIUS.get());
        int spawnHeight = OhMyMeteors.CONFIG.meteorSpawning.meteor_spawn_height;
        double minAltitude = Config.KITTY_LASER_MIN_ALTITUDE.get();

        double reach = Math.min(spread + Math.max(basicRadius, advancedRadius), MAX_REACH);
        int minChunkX = Mth.floor(cx - reach) >> 4;
        int maxChunkX = Mth.floor(cx + reach) >> 4;
        int minChunkZ = Mth.floor(cz - reach) >> 4;
        int maxChunkZ = Mth.floor(cz + reach) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    // The advanced class is tested first. The two block entity classes are unrelated, but this keeps the order safe.
                    boolean advanced = be instanceof AdvancedMeteorLaserBlockEntity;
                    if (!advanced && !(be instanceof BasicMeteorLaserBlockEntity)) {
                        continue;
                    }
                    if (be.isRemoved()) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos().immutable();
                    int radius = advanced ? advancedRadius : basicRadius;
                    // Its square must touch the square the shower can land in, or it can never cover a kitty.
                    if (Math.abs(pos.getX() + 0.5D - cx) > radius + spread || Math.abs(pos.getZ() + 0.5D - cz) > radius + spread) {
                        continue;
                    }
                    if (!level.canSeeSky(pos.above())) {
                        continue;
                    }
                    int height = advanced ? cfg.advanced_laser_height : cfg.basic_laser_height;
                    // OMM's detection slab is centred on y = min(laserY + height, spawnHeight), 3 blocks tall (y-1 .. y+2).
                    int slabY = Math.min(pos.getY() + height, spawnHeight);
                    double bandTop = slabY + 2.0D;
                    double bandBottom = pos.getY() + minAltitude;
                    // An empty band (bottom >= top) is kept on purpose: the laser still covers its square and rolls at impact.
                    out.add(new Laser(pos, be, advanced, radius, bandBottom, bandTop));
                }
            }
        }
        return out;
    }

    /**
     * Indexes of the lasers whose square contains the target column (tx, tz). Coverage is judged on the landing
     * column: "this laser protects the place the kitty is going to hit".
     */
    public static int[] covering(List<Laser> lasers, double tx, double tz) {
        if (lasers.isEmpty()) {
            return new int[0];
        }
        int[] tmp = new int[lasers.size()];
        int n = 0;
        for (int i = 0; i < tmp.length; i++) {
            Laser l = lasers.get(i);
            if (Math.abs(tx - (l.pos.getX() + 0.5D)) <= l.radius && Math.abs(tz - (l.pos.getZ() + 0.5D)) <= l.radius) {
                tmp[n++] = i;
            }
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    /** The configured pass chance of a laser: the chance a covered kitty gets past it. */
    public static double passChance(Laser l) {
        double p = l.advanced ? Config.KITTY_ADVANCED_LASER_PASS.get() : Config.KITTY_BASIC_LASER_PASS.get();
        return Mth.clamp(p, 0.0D, 1.0D);
    }

    /** Vertical blocks of the band [bottom, top] that a fall from prevY to curY crossed (0 if none). */
    public static double bandOverlap(double prevY, double curY, double bottom, double top) {
        return Math.max(0.0D, Math.min(prevY, top) - Math.max(curY, bottom));
    }

    /**
     * Chance that a kitty is shot while crossing {@code share} (0..1) of a laser's band, given the laser's pass chance.
     * It is {@code 1 - passChance^share}: crossing the whole band in pieces multiplies back to exactly the pass chance.
     * A zero or negative share never kills ({@code Math.pow(0, 0)} is 1, so the guard is explicit).
     */
    public static double tickChance(double passChance, double share) {
        if (!(share > 0.0D)) {
            return 0.0D;
        }
        double p = Math.max(0.0D, Math.min(1.0D, passChance));
        return 1.0D - Math.pow(p, Math.min(share, 1.0D));
    }

    /** One roll of {@link #tickChance}. */
    public static boolean roll(double passChance, double share, RandomSource r) {
        return share > 0.0D && r.nextDouble() < tickChance(passChance, share);
    }
}

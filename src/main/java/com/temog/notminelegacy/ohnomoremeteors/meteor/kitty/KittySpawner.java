package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.meteor.ImpactFormula;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Turns one planned {@link KittyDrop} into a falling Oh My, Meteors! meteor with the Komit Kitty claim.
 *
 * The kitty is aimed so it lands on the drop's target column. It starts fast and speeds up faster than a plain meteor
 * (a small, punchy fall). No chunk is ever loaded or ticketed for it: a drop whose target (or start) is not in an
 * entity-ticking chunk simply fizzles.
 */
public final class KittySpawner {

    private static final ResourceLocation METEOR_TYPE_ID = new ResourceLocation("ohmymeteors", "meteor_projectile");
    /** The kitty never starts closer than this many blocks above its target, so there is room to fall. */
    private static final int MIN_FALL_HEIGHT = 32;
    private static final double MAX_ANGLE_DEG = 80.0D;

    private KittySpawner() {
    }

    /** Spawns the kitty for the level's active shower (if any) and registers it there. */
    @Nullable
    public static MeteorProjectileEntity spawn(ServerLevel level, KittyDrop drop) {
        return spawn(level, drop, KittyShowers.active(level));
    }

    /**
     * Spawns the kitty and, when {@code shower} is not null, registers its flight there with the lasers that cover the
     * landing column.
     *
     * @return the kitty, or null when the drop fizzled (no player near the target or the start) or the spawn failed
     */
    @Nullable
    public static MeteorProjectileEntity spawn(ServerLevel level, KittyDrop drop, @Nullable KittyShower shower) {
        double tx = drop.tx();
        double tz = drop.tz();
        int bx = Mth.floor(tx);
        int bz = Mth.floor(tz);

        // Target height: the surface of the column if its chunk is loaded, else sea level (the fizzle test then fails below).
        double ty = level.hasChunkAt(new BlockPos(bx, level.getSeaLevel(), bz))
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz)
                : level.getSeaLevel();
        // No player near the target means no ticking chunk: the kitty fizzles, and no chunk is loaded for it.
        if (!level.isPositionEntityTicking(BlockPos.containing(tx, ty, tz))) {
            fizzle(shower);
            return null;
        }

        int maxY = level.getMaxBuildHeight() - 2;
        double sy = Math.min(OhMyMeteors.CONFIG.meteorSpawning.meteor_spawn_height, maxY);
        sy = Math.min(Math.max(sy, ty + MIN_FALL_HEIGHT), maxY);
        if (sy - ty < 4.0D) {
            // No room to fall (a column right under the build limit): nothing sensible to spawn.
            fizzle(shower);
            return null;
        }

        // Same geometry as MeteorCommand.spawn: the kitty starts back along its heading so it still lands on the target.
        double angle = Math.toRadians(Mth.clamp(drop.angleDeg(), 0.0F, (float) MAX_ANGLE_DEG));
        double hx = -Math.sin(Math.toRadians(drop.yawDeg()));
        double hz = Math.cos(Math.toRadians(drop.yawDeg()));
        double back = (sy - ty) * Math.tan(angle);
        double sx = tx - hx * back;
        double sz = tz - hz * back;
        if (back > 1.0E-3D && !level.isPositionEntityTicking(BlockPos.containing(sx, sy, sz))) {
            // The slanted start is outside the ticking area: drop it straight down instead.
            sx = tx;
            sz = tz;
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(METEOR_TYPE_ID);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof MeteorProjectileEntity kitty)) {
            return null;
        }
        kitty.moveTo(sx, sy, sz, 0.0F, 90.0F);
        // Hard clamp: size <= 3 keeps OMM's own size/2 dig-in away, so the kitty uses only KittyTunnel.
        kitty.setSize(Mth.clamp((int) drop.size(), 1, 3));
        Vec3 dir = new Vec3(tx - sx, ty - sy, tz - sz).normalize();
        kitty.setDeltaMovement(dir.scale(Config.KITTY_START_SPEED.get()));
        kitty.accelerationPower = Config.KITTY_ACCELERATION.get();
        kitty.setSilenced(true);
        // The claim and the "already randomized" flag are written BEFORE the join, so no other system touches it.
        KomitKitty.mark(kitty);
        kitty.getPersistentData().putBoolean(ImpactFormula.RANDOMIZED_KEY, true);
        if (!level.addFreshEntity(kitty)) {
            fizzle(shower);
            return null;
        }

        if (shower != null) {
            KittyFlight flight = new KittyFlight(shower, kitty, sy, tx, ty, tz);
            flight.bind(shower.lasers, null);
            shower.alive.add(flight);
            shower.byEntity.put(kitty, flight);
            shower.dropped++;
        }
        return kitty;
    }

    private static void fizzle(@Nullable KittyShower shower) {
        if (shower != null) {
            shower.fizzled++;
        }
    }
}

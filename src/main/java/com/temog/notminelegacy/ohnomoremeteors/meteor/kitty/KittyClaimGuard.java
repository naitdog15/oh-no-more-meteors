package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.util.MeteorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.Map;

/**
 * Land-claim check for every cell a landing Komit Kitty changes: the tunnel, the blast, the capsule and the fire pass.
 * Oh My, Meteors! only checks claims at the spot where the meteor is, but a kitty's tunnel, blast and capsule reach
 * further, so each cell is asked here through OMM's own claim check (Flan, YAWP, OpenPartiesAndClaims).
 *
 * One guard lives for one landing. It is active only when one of those claim mods is loaded; an inactive guard allows
 * everything and costs nothing. Answers are cached per position. If the claim check itself throws (a broken or
 * mismatched claim mod), the position counts as protected (fail closed) and one warning is logged per game run.
 *
 * This is a sibling of the private claim guard inside CraterBuilder, which is left as it is.
 */
public final class KittyClaimGuard {

    /** Mod ids whose claims OMM can check. */
    private static final String[] CLAIM_MODS = {"flan", "yawp", "openpartiesandclaims"};

    /** One warning per game run is enough; afterwards failing positions are refused quietly. */
    private static volatile boolean loggedFailure;

    private final ServerLevel level;
    private final boolean active;
    private final Map<Long, Boolean> answers = new HashMap<>();

    private KittyClaimGuard(ServerLevel level, boolean active) {
        this.level = level;
        this.active = active;
    }

    /** A fresh guard for one landing. Checks once whether any claim mod is loaded. */
    public static KittyClaimGuard of(ServerLevel level) {
        boolean anyClaimMod;
        try {
            ModList mods = ModList.get();
            anyClaimMod = false;
            if (mods != null) {
                for (String id : CLAIM_MODS) {
                    if (mods.isLoaded(id)) {
                        anyClaimMod = true;
                        break;
                    }
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            // Cannot tell which mods are loaded: stay active so the per-cell check decides (and fails closed).
            anyClaimMod = true;
            warnOnce(ex);
        }
        return new KittyClaimGuard(level, anyClaimMod);
    }

    /** True when a claim mod is loaded, so {@link #allows} actually asks something. */
    public boolean active() {
        return active;
    }

    /** True when the kitty may change this cell. Inactive guards answer true at once. */
    public boolean allows(BlockPos pos) {
        if (!active) {
            return true;
        }
        long key = pos.asLong();
        Boolean cached = answers.get(key);
        if (cached == null) {
            boolean ok;
            try {
                ok = MeteorUtils.canSpawnInModdedRegion(level, pos);
            } catch (RuntimeException | LinkageError ex) {
                ok = false; // fail closed: treat the position as protected
                warnOnce(ex);
            }
            cached = ok;
            answers.put(key, cached);
        }
        return cached;
    }

    private static void warnOnce(Throwable ex) {
        if (!loggedFailure) {
            loggedFailure = true;
            OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] The Oh My, Meteors! claim check failed; Komit Kitty landings skip the positions it could not check.", ex);
        }
    }
}

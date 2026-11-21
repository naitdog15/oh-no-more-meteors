package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Takes Komit Kitties out of the list of meteors that an Oh My, Meteors! laser block is about to shoot.
 *
 * The kitty shower has its own laser rules (KittyInterceptor), so OMM's lasers must not also act on kitties.
 * The two laser mixins call {@link #withoutKitties} on the list OMM builds each tick, before OMM checks whether
 * it is empty (so a list that held only kitties is empty and OMM's laser does not switch to its firing state).
 */
public final class KittyLaserFilter {

    /** How many kitties have been removed from an OMM laser's target list since start (a check that the hook is live; read by tests). */
    public static final AtomicLong SKIPPED = new AtomicLong();

    private static boolean loggedFailure;

    private KittyLaserFilter() {
    }

    /**
     * Returns the list without kitties. When the list holds no kitty (the common case) the same list comes back,
     * with no allocation. Null-safe and fail-open: on any error the original list is returned.
     */
    public static <T extends Entity> List<T> withoutKitties(@Nullable List<T> list) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        try {
            int kitties = 0;
            for (T entity : list) {
                if (KomitKitty.isKitty(entity)) {
                    kitties++;
                }
            }
            if (kitties == 0) {
                return list;
            }
            List<T> kept = new ArrayList<>(list.size() - kitties);
            for (T entity : list) {
                if (!KomitKitty.isKitty(entity)) {
                    kept.add(entity);
                }
            }
            SKIPPED.addAndGet(kitties);
            return kept;
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedFailure) {
                loggedFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not filter Komit Kitties out of a meteor laser's targets; OMM's laser may shoot them (this is logged once).", ex);
            }
            return list;
        }
    }
}

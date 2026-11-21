package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import net.minecraft.util.RandomSource;

/**
 * The numbers that describe one shower. Tests and the command can build their own; natural showers read them from the
 * config with {@link #fromConfig}.
 *
 * @param count             how many kitties fall
 * @param warningTicks      ticks between the start and the first possible drop. The start sends the early warning
 *                          when this is longer than the final warning.
 * @param durationTicks     ticks the drops are spread over, after the warning
 * @param spreadRadius      radius in blocks of the disk the kitties land in, around the shower centre
 * @param maxEntryAngle     steepest entry angle from straight down, in degrees
 * @param finalWarningTicks ticks before the first drop at which the final warning goes out and the sky starts to
 *                          shift (never more than warningTicks)
 */
public record KittyShowerParams(int count, int warningTicks, int durationTicks, double spreadRadius, double maxEntryAngle,
                                int finalWarningTicks) {

    /** Default final warning of the five-number form: 10 seconds, or the whole lead if that is shorter. */
    public static final int DEFAULT_FINAL_WARNING = 200;

    /** The five-number form (kept for tests and callers that predate the early warning). */
    public KittyShowerParams(int count, int warningTicks, int durationTicks, double spreadRadius, double maxEntryAngle) {
        this(count, warningTicks, durationTicks, spreadRadius, maxEntryAngle, Math.min(Math.max(0, warningTicks), DEFAULT_FINAL_WARNING));
    }

    /** The same shower with another lead; the final warning is clamped to it. */
    public KittyShowerParams withLead(int lead, int finalWarning) {
        int l = Math.max(0, lead);
        return new KittyShowerParams(count, l, durationTicks, spreadRadius, maxEntryAngle, Math.min(l, Math.max(0, finalWarning)));
    }

    /**
     * The numbers of a natural shower: a count drawn uniformly between the configured minimum and maximum (swapped if
     * the minimum is above the maximum), everything else straight from the config. The lead is the longer of the
     * early and the final warning.
     */
    public static KittyShowerParams fromConfig(RandomSource r) {
        int a = Config.KITTY_MIN_COUNT.get();
        int b = Config.KITTY_MAX_COUNT.get();
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        int count = Math.max(1, lo + r.nextInt(hi - lo + 1));
        int finalWarning = Config.KITTY_WARNING_TICKS.get();
        int lead = Math.max(Config.KITTY_EARLY_WARNING_TICKS.get(), finalWarning);
        int duration = Config.KITTY_DURATION_TICKS.get();
        double spread = Config.KITTY_SPREAD_RADIUS.get();
        double angle = Config.KITTY_MAX_ENTRY_ANGLE.get();
        return new KittyShowerParams(count, lead, duration, spread, angle, finalWarning);
    }
}

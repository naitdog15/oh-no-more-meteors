package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Who can tint the sky, and how (2026-11-21). One entry per meteor look plus the Komit Kitty shower omen. The shared
 * baseline of every glow (reach, fade-in and fade-out speed, peak strength) lives in client.SkyGlow; an entry only
 * changes the colour, how strong it is relative to that baseline, and whether it lifts or darkens the sky.
 *
 * Colours can be changed in the config ([skyGlow] colours, "kind=#RRGGBB"); the values here are the defaults.
 */
public enum SkyGlowKind {
    /** A plain Oh My, Meteors! meteor: warm amber. */
    PLAIN(0xE8A04A, 1.0F, 0.10F),
    /** Cindrite (fire): ember red-orange. */
    CINDRITE(0xD9502E, 1.0F, 0.12F),
    /** Rimerite (ice): pale frost blue. */
    RIMERITE(0x8CC4EE, 1.0F, 0.10F),
    /** Veyrite (auroral): teal-green. */
    VEYRITE(0x3CC8A0, 1.0F, 0.10F),
    /** Komit Kitty shower omen: dark purple with a touch of green, darkens the sky. */
    KOMIT_KITTY(0x4A3F66, 1.0F, -0.30F);

    private static final SkyGlowKind[] VALUES = values();

    private final int defaultRgb;
    /** Strength relative to the shared baseline. */
    public final float weight;
    /** Brightness change at full strength: positive glows, negative darkens. */
    public final float lift;

    private int rgb;
    private List<? extends String> parsedFrom;

    SkyGlowKind(int rgb, float weight, float lift) {
        this.defaultRgb = rgb;
        this.rgb = rgb;
        this.weight = weight;
        this.lift = lift;
    }

    /** The colour as 0xRRGGBB (config override, else the default). Cheap: the config list is parsed only when it changes. */
    public int rgb() {
        refresh();
        return rgb;
    }

    /** Sky kind for a meteor look; null look = plain meteor. */
    public static SkyGlowKind of(@Nullable MeteorVariant look) {
        if (look == null) {
            return PLAIN;
        }
        return switch (look) {
            case CINDRITE -> CINDRITE;
            case RIMERITE -> RIMERITE;
            case VEYRITE -> VEYRITE;
        };
    }

    public static SkyGlowKind byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PLAIN;
    }

    private static void refresh() {
        List<? extends String> list;
        try {
            list = Config.SKY_GLOW_COLOURS.get();
        } catch (RuntimeException ex) {
            return; // config not loaded yet: defaults
        }
        if (VALUES[0].parsedFrom == list) {
            return;
        }
        for (SkyGlowKind k : VALUES) {
            k.rgb = k.defaultRgb;
            k.parsedFrom = list;
        }
        for (String entry : list) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = entry.substring(0, eq).trim();
            String hex = entry.substring(eq + 1).trim();
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            for (SkyGlowKind k : VALUES) {
                if (k.name().equalsIgnoreCase(name)) {
                    try {
                        k.rgb = Integer.parseInt(hex, 16) & 0xFFFFFF;
                    } catch (NumberFormatException ignored) {
                        // keep the default
                    }
                }
            }
        }
    }
}

package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Crater extras (2026-11-21): other mods' blocks that trickle into a variant crater's stone filler. Each variant has
 * a block tag, ohnomoremeteors:crater_extras/cindrite, /rimerite and /veyrite, shipped EMPTY. When a mod fills one
 * (Building Bridges adds AE2 sky stone), a small share of that variant's STONE fillers becomes a random member of the
 * tag ([craterExtras] chances). Ice, snow and crying obsidian fillers are never replaced. LINED craters place no filler, so
 * they get no extras.
 *
 * <p><b>Randomness.</b> With an empty tag, a chance of 0 or a non-stone filler, no extra random number is drawn, so a
 * seeded crater is exactly what it was before. A populated tag draws one more number per stone filler (and one for
 * the member on a hit), so later random details of that crater differ from a crater without extras.
 *
 * <p><b>Cache.</b> The tag members are read once per tag reload into an immutable array per variant, sorted by block
 * id so the order does not depend on how the tag was put together. Empty results are cached too. TagsUpdatedEvent
 * clears the cache; the next filler call reads the tags again.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CraterExtras {

    private static final BlockState[] NONE = new BlockState[0];
    private static final MeteorVariant[] VARIANTS = MeteorVariant.values();
    private static final List<TagKey<Block>> TAGS = List.of(
            tag(MeteorVariant.CINDRITE), tag(MeteorVariant.RIMERITE), tag(MeteorVariant.VEYRITE));

    /** Per-variant members (index = variant ordinal), or null when the tags must be read again. */
    private static volatile BlockState[][] cache;

    private CraterExtras() {
    }

    /** The tag of one variant: ohnomoremeteors:crater_extras/&lt;variant&gt;. */
    public static TagKey<Block> tag(MeteorVariant variant) {
        return TagKey.create(Registries.BLOCK, OhNoMoreMeteors.id("crater_extras/" + variant.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        cache = null;
    }

    /**
     * Called by CraterBuilder.filler with the filler it already chose (its own random number already drawn). Returns
     * that filler unchanged unless it is a stone filler, the variant's tag has members and the chance is above 0;
     * only then is the chance drawn, and the member only on a hit.
     */
    static BlockState apply(MeteorVariant variant, BlockState chosen, RandomSource random) {
        if (!isStoneFiller(chosen)) {
            return chosen;
        }
        BlockState[] extras = cached(variant);
        if (extras.length == 0) {
            return chosen;
        }
        double chance = chance(variant);
        if (!(chance > 0.0D)) {
            return chosen;
        }
        if (random.nextDouble() >= chance) {
            return chosen;
        }
        return extras[random.nextInt(extras.length)];
    }

    /** The stone fillers: blackstone, basalt, smooth basalt, rime-worn stone, tektite, deepslate, cobbled deepslate and tuff (crying obsidian is not one). */
    public static boolean isStoneFiller(BlockState state) {
        return state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT)
                || state.is(ModBlocks.RIME_WORN_STONE.get()) || state.is(ModBlocks.TEKTITE.get()) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF);
    }

    /** A copy of the cached members of a variant's tag (default states, sorted by block id; empty when the tag is empty). */
    public static BlockState[] members(MeteorVariant variant) {
        return cached(variant).clone();
    }

    private static BlockState[] cached(MeteorVariant variant) {
        BlockState[][] c = cache;
        if (c == null) {
            c = read();
            cache = c;
        }
        return c[variant.ordinal()];
    }

    /** [craterExtras] chance for a variant; 0 when the config cannot be read. */
    private static double chance(MeteorVariant variant) {
        try {
            return switch (variant) {
                case CINDRITE -> Config.CRATER_EXTRAS_CINDRITE_CHANCE.get();
                case RIMERITE -> Config.CRATER_EXTRAS_RIMERITE_CHANCE.get();
                case VEYRITE -> Config.CRATER_EXTRAS_VEYRITE_CHANCE.get();
            };
        } catch (RuntimeException ex) {
            return 0.0D;
        }
    }

    private static BlockState[][] read() {
        BlockState[][] out = new BlockState[VARIANTS.length][];
        for (MeteorVariant v : VARIANTS) {
            BlockState[] states = NONE;
            try {
                var set = BuiltInRegistries.BLOCK.getTag(TAGS.get(v.ordinal()));
                if (set.isPresent()) {
                    List<Block> blocks = new ArrayList<>();
                    for (Holder<Block> holder : set.get()) {
                        Block block = holder.value();
                        if (block != Blocks.AIR && !blocks.contains(block)) {
                            blocks.add(block);
                        }
                    }
                    blocks.sort(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()));
                    states = new BlockState[blocks.size()];
                    for (int i = 0; i < states.length; i++) {
                        states[i] = blocks.get(i).defaultBlockState();
                    }
                }
            } catch (RuntimeException ex) {
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Could not read crater extras tag for {}; none are used.", v, ex);
                states = NONE;
            }
            out[v.ordinal()] = states;
        }
        return out;
    }
}

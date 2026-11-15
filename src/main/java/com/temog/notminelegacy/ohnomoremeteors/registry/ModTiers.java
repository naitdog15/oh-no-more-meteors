package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

/**
 * Tool tiers of the three meteor metals, sorted into the vanilla ladder:
 * stone &lt; rimerite &lt; iron &lt; meteoric &lt; cindrite &lt; diamond.
 * Each tier is repaired with its forge:ingots/&lt;metal&gt; tag (meteoric = Oh My Meteors' meteoric alloy).
 */
public final class ModTiers {

    // Brittle: between stone and iron, few uses, but the ice abilities (see gear/RimeriteAbilities).
    public static final Tier RIMERITE = register("rimerite", "rimerite", 1, 190, 5.0F, 1.5F, 16,
            List.of(Tiers.STONE), List.of(Tiers.IRON));
    // A little better than iron; its strength is durability (and the alloy is easy to get).
    public static final Tier METEORIC = register("meteoric", "meteoric_alloy", 2, 650, 6.5F, 2.0F, 14,
            List.of(Tiers.IRON), List.of(Tiers.DIAMOND));
    // Just under diamond (cannot mine obsidian); its strength is toughness and the heat abilities.
    public static final Tier CINDRITE = register("cindrite", "cindrite", 2, 1100, 7.5F, 2.5F, 12,
            List.of(METEORIC), List.of(Tiers.DIAMOND));

    private static Tier register(String name, String ingot, int level, int uses, float speed, float damage,
                                 int enchantment, List<Object> after, List<Object> before) {
        TagKey<Block> needs = BlockTags.create(new ResourceLocation(OhNoMoreMeteors.MODID, "needs_" + name + "_tool"));
        TagKey<Item> repair = ItemTags.create(new ResourceLocation("forge", "ingots/" + ingot));
        Tier tier = new ForgeTier(level, uses, speed, damage, enchantment, needs, () -> Ingredient.of(repair));
        TierSortingRegistry.registerTier(tier, new ResourceLocation(OhNoMoreMeteors.MODID, name), after, before);
        return tier;
    }

    private ModTiers() {
    }
}

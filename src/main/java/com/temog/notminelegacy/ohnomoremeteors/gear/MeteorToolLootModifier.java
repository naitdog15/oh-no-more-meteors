package com.temog.notminelegacy.ohnomoremeteors.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModTiers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Block-drop abilities of Rimerite ("cool") and Cindrite ("heat") tools. One modifier for all of them; every check
 * (tool, block, chance) is done here in Java. Only block loot is touched: no BLOCK_STATE or no TOOL means no change,
 * so chests and mob drops are never affected.
 *
 * Rimerite:
 *  - pickaxe: a magma block drops obsidian instead; ice, packed ice and blue ice drop themselves (plain ice still
 *    leaves water behind, as vanilla does without Silk Touch).
 *  - shovel: sand and red sand sometimes drop 1-2 snowballs as well; snow blocks and snow layers drop one extra.
 *  - hoe: leaves and grass sometimes drop a snowball.
 * Cindrite (never with Silk Touch):
 *  - pickaxe: ores sometimes come out smelted (raw iron becomes an iron ingot, and so on); coal ores, basalt and tuff
 *    sometimes also drop a fire charge.
 *  - shovel, axe, hoe: any drop with a furnace recipe sometimes comes out smelted (sand to glass, logs to charcoal,
 *    potatoes to baked potatoes).
 */
public class MeteorToolLootModifier extends LootModifier {

    public static final Codec<MeteorToolLootModifier> CODEC = RecordCodecBuilder.create(inst ->
            codecStart(inst).apply(inst, MeteorToolLootModifier::new));

    public MeteorToolLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (state == null || tool == null || !(tool.getItem() instanceof TieredItem tiered)) {
            return loot;
        }
        if (tiered.getTier() == ModTiers.RIMERITE && Config.RIMERITE_ABILITIES.get()) {
            return rimerite(loot, state, tool, context.getRandom());
        }
        if (tiered.getTier() == ModTiers.CINDRITE && Config.CINDRITE_ABILITIES.get()
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) == 0) {
            return cindrite(loot, state, tool, context.getLevel(), context.getRandom());
        }
        return loot;
    }

    private static ObjectArrayList<ItemStack> rimerite(ObjectArrayList<ItemStack> loot, BlockState state, ItemStack tool, RandomSource random) {
        if (tool.getItem() instanceof PickaxeItem) {
            if (state.is(Blocks.MAGMA_BLOCK)) {
                loot.clear();
                loot.add(new ItemStack(Items.OBSIDIAN));
            } else if (state.is(BlockTags.ICE) && loot.isEmpty()) {
                loot.add(new ItemStack(state.getBlock()));
            }
        } else if (tool.getItem() instanceof ShovelItem) {
            if (state.is(BlockTags.SAND) && random.nextDouble() < Config.RIMERITE_SNOWBALL_CHANCE.get()) {
                loot.add(new ItemStack(Items.SNOWBALL, 1 + random.nextInt(2)));
            } else if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)) {
                loot.add(new ItemStack(Items.SNOWBALL));
            }
        } else if (tool.getItem() instanceof HoeItem) {
            if ((state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES))
                    && random.nextDouble() < Config.RIMERITE_SNOWBALL_CHANCE.get() * 0.5D) {
                loot.add(new ItemStack(Items.SNOWBALL));
            }
        }
        return loot;
    }

    private static ObjectArrayList<ItemStack> cindrite(ObjectArrayList<ItemStack> loot, BlockState state, ItemStack tool,
                                                       ServerLevel level, RandomSource random) {
        boolean pickaxe = tool.getItem() instanceof PickaxeItem;
        boolean other = tool.getItem() instanceof ShovelItem || tool.getItem() instanceof AxeItem || tool.getItem() instanceof HoeItem;
        double chance;
        if (pickaxe && state.is(Tags.Blocks.ORES)) {
            chance = Config.CINDRITE_ORE_SMELT_CHANCE.get();
        } else if (other) {
            chance = Config.CINDRITE_SMELT_CHANCE.get();
        } else {
            chance = 0.0D;
        }
        if (chance > 0.0D) {
            for (int i = 0; i < loot.size(); i++) {
                if (random.nextDouble() < chance) {
                    loot.set(i, smelted(loot.get(i), level));
                }
            }
        }
        if (pickaxe && (state.is(BlockTags.COAL_ORES) || state.is(Blocks.BASALT) || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.POLISHED_BASALT) || state.is(Blocks.TUFF))
                && random.nextDouble() < Config.CINDRITE_FIRE_CHARGE_CHANCE.get()) {
            loot.add(new ItemStack(Items.FIRE_CHARGE));
        }
        return loot;
    }

    /** The furnace result for a dropped stack (same total count), or the stack unchanged when it has no recipe. */
    private static ItemStack smelted(ItemStack stack, ServerLevel level) {
        Optional<SmeltingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack), level);
        if (recipe.isEmpty()) {
            return stack;
        }
        ItemStack result = recipe.get().getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return stack;
        }
        ItemStack out = result.copy();
        out.setCount(Math.min(out.getMaxStackSize(), result.getCount() * stack.getCount()));
        return out;
    }
}

package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frostery block entity: a furnace block entity running the ohnomoremeteors:freezing recipe type. Fuel times come from
 * FrosteryRepair (cold items only). One addition to the vanilla furnace logic: when a recipe uses up a bucket of
 * something (water or lava bucket), the empty bucket is left in the input slot instead of being lost. Recipes with an
 * ingredient_count above 1 (9 ice -> 1 packed ice) take the remaining inputs once the vanilla step finishes a cook. The
 * fuel slot only accepts cold fuel.
 */
public class FrosteryBlockEntity extends AbstractFurnaceBlockEntity {

    public FrosteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModFrostery.FROSTERY_BLOCK_ENTITY.get(), pos, state, ModFrostery.FREEZING.get());
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container." + OhNoMoreMeteors.MODID + ".frostery");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new FrosteryMenu(id, inventory, this, this.dataAccess);
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        if (index == 1) {
            return FrosteryRepair.coldBurnTime(stack) > 0;
        }
        return super.canPlaceItem(index, stack);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FrosteryBlockEntity frostery) {
        ItemStack input = frostery.getItem(0);
        ItemStack remainder = input.hasCraftingRemainingItem() ? input.getCraftingRemainingItem() : ItemStack.EMPTY;
        ItemStack inputBefore = input.copy();
        int countBefore = input.getCount();
        AbstractFurnaceBlockEntity.serverTick(level, pos, state, frostery);
        ItemStack now = frostery.getItem(0);
        if (countBefore >= 2 && ItemStack.isSameItemSameTags(now, inputBefore) && now.getCount() == countBefore - 1) {
            // a cook finished (vanilla took one input); take the rest of the recipe's input count
            FreezingRecipe recipe = level.getRecipeManager()
                    .getRecipeFor(ModFrostery.FREEZING.get(), new SimpleContainer(inputBefore), level).orElse(null);
            if (recipe != null && recipe.getInputCount() > 1) {
                now.shrink(recipe.getInputCount() - 1);
                if (now.isEmpty()) {
                    frostery.setItem(0, ItemStack.EMPTY);
                }
                frostery.setChanged();
            }
        }
        // A finished recipe shrinks the input by one; give the container (bucket) back when the slot is now free.
        if (!remainder.isEmpty() && countBefore == 1 && frostery.getItem(0).isEmpty()) {
            frostery.setItem(0, remainder.copy());
        }
    }
}

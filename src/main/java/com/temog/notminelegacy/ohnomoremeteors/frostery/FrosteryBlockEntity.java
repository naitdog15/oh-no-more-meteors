package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frostery block entity: a furnace block entity running the ohnomoremeteors:freezing recipe type. Fuel times come from
 * FrosteryRepair (cold items only). One addition to the vanilla furnace logic: when a recipe uses up a bucket of
 * something (water or lava bucket), the empty bucket is left in the input slot instead of being lost.
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, FrosteryBlockEntity frostery) {
        ItemStack input = frostery.getItem(0);
        ItemStack remainder = input.hasCraftingRemainingItem() ? input.getCraftingRemainingItem() : ItemStack.EMPTY;
        int countBefore = input.getCount();
        AbstractFurnaceBlockEntity.serverTick(level, pos, state, frostery);
        // A finished recipe shrinks the input by one; give the container (bucket) back when the slot is now free.
        if (!remainder.isEmpty() && countBefore == 1 && frostery.getItem(0).isEmpty()) {
            frostery.setItem(0, remainder.copy());
        }
    }
}

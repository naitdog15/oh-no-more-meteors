package com.temog.notminelegacy.ohnomoremeteors.mixin;

import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryBlockEntity;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryRepair;
import com.temog.notminelegacy.ohnomoremeteors.gear.CindriteTempering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Furnace repair: Cindrite in a furnace (gear.CindriteTempering), Rimerite in a Frostery (frostery.FrosteryRepair). Remembers the fuel slot at the start of a furnace tick; if the
 * furnace used up a fuel item during that tick (litTime was just set to the new fuel's full duration), the damaged
 * item in the input slot is repaired by that fuel's heat (furnace) or cold (Frostery) value.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceRepairMixin {

    @Shadow
    protected NonNullList<ItemStack> items;

    @Shadow
    private int litTime;

    @Shadow
    private int litDuration;

    /** The fuel slot at the start of the running tick (server thread only; serverTick never nests). */
    @Unique
    private static ItemStack onmm$fuelBefore = ItemStack.EMPTY;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onmm$rememberFuel(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                          CallbackInfo ci) {
        AbstractFurnaceRepairMixin self = (AbstractFurnaceRepairMixin) (Object) furnace;
        onmm$fuelBefore = self.items.get(1).isEmpty() ? ItemStack.EMPTY : self.items.get(1).copy();
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void onmm$repairFromFuel(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                            CallbackInfo ci) {
        AbstractFurnaceRepairMixin self = (AbstractFurnaceRepairMixin) (Object) furnace;
        ItemStack fuel = onmm$fuelBefore;
        onmm$fuelBefore = ItemStack.EMPTY;
        // A fuel item was used up this tick exactly when litTime holds the new fuel's full, not yet decremented duration.
        if (fuel.isEmpty() || self.litTime <= 0 || self.litTime != self.litDuration) {
            return;
        }
        boolean repaired = furnace instanceof FrosteryBlockEntity
                ? FrosteryRepair.repairFromFuel(self.items, fuel)
                : CindriteTempering.repairFromFuel(self.items, fuel);
        if (repaired) {
            furnace.setChanged();
        }
    }
}

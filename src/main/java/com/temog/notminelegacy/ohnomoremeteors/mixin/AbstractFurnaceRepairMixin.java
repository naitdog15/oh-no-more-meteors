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
 * Furnace repair: Cindrite in a furnace (gear.CindriteTempering), Rimerite in a Frostery (frostery.FrosteryRepair). While
 * repairable gear is in the input slot, the fuel slot is remembered at the start of a furnace tick; if the furnace used
 * up a fuel item during that tick (litTime was just set to the new fuel's full duration), the gear is repaired by that
 * fuel's heat (furnace) or cold (Frostery) value and the fuel's remaining lit time is spent. The hidden tempering recipe
 * must never complete, so the cook progress is pinned to 0 at the start and end of every tick while repairable gear
 * is in the input.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceRepairMixin {

    @Shadow
    protected NonNullList<ItemStack> items;

    @Shadow
    private int litTime;

    @Shadow
    private int litDuration;

    @Shadow
    private int cookingProgress;

    /** The fuel slot at the start of the running tick (server thread only; serverTick never nests). */
    @Unique
    private static ItemStack onmm$fuelBefore = ItemStack.EMPTY;

    /** Same predicate for the HEAD snapshot, the repair and the progress pin. */
    @Unique
    private static boolean onmm$repairable(AbstractFurnaceBlockEntity furnace, ItemStack input) {
        return furnace instanceof FrosteryBlockEntity
                ? FrosteryRepair.isRepairable(input)
                : CindriteTempering.isRepairable(input);
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void onmm$rememberFuel(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                          CallbackInfo ci) {
        AbstractFurnaceRepairMixin self = (AbstractFurnaceRepairMixin) (Object) furnace;
        boolean repairable = !self.items.get(0).isEmpty() && onmm$repairable(furnace, self.items.get(0));
        // Covers cook progress loaded from old saves, before vanilla's increment-and-compare runs this tick.
        if (repairable) {
            self.cookingProgress = 0;
        }
        // Only snapshot the fuel when repairable gear is in the input (the same test the RETURN inject uses).
        onmm$fuelBefore = !self.items.get(1).isEmpty() && repairable
                ? self.items.get(1).copy() : ItemStack.EMPTY;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void onmm$repairFromFuel(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace,
                                            CallbackInfo ci) {
        AbstractFurnaceRepairMixin self = (AbstractFurnaceRepairMixin) (Object) furnace;
        ItemStack fuel = onmm$fuelBefore;
        onmm$fuelBefore = ItemStack.EMPTY;
        // A fuel item was used up this tick exactly when litTime holds the new fuel's full, not yet decremented duration.
        if (!fuel.isEmpty() && self.litTime > 0 && self.litTime == self.litDuration) {
            boolean repaired = furnace instanceof FrosteryBlockEntity
                    ? FrosteryRepair.repairFromFuel(self.items, fuel)
                    : CindriteTempering.repairFromFuel(self.items, fuel);
            if (repaired) {
                // A repair spends the fuel's remaining lit time. 1, not 0: vanilla flips the LIT blockstate inside
                // serverTick by comparing the lit flag at start and end, so zeroing here would leave the block
                // visually lit forever if no more fuel arrives. With 1 the next tick decrements it, then either
                // relights from the next fuel (repairing again) or goes out and updates the blockstate.
                self.litTime = 1;
                furnace.setChanged();
            }
        }
        // The hidden tempering recipe must never complete: vanilla increments 0 -> 1 and compares with the total
        // inside the tick, so any total >= 2 (including old saves) never finishes, and the arrow syncs as empty.
        if (onmm$repairable(furnace, self.items.get(0))) {
            self.cookingProgress = 0;
        }
    }
}

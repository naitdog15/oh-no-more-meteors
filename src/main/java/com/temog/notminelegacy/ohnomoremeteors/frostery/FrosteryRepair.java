package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.gear.RepairValues;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rimerite repair in the Frostery, the cold twin of Cindrite's furnace repair. Put the damaged Rimerite item in the
 * Frostery's top slot and cold fuel below: every cold fuel item used up gives back durability by
 * {@link RepairValues#COLD} (snowball little, blue ice a lot). The item stays in place; the Frostery stops taking fuel
 * once it is fully repaired.
 *
 * Fuel: the Frostery burns ONLY cold items (see {@link #coldBurnTime}); ordinary furnace fuel does nothing in it.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FrosteryRepair {

    public static final TagKey<Item> RIMERITE_GEAR = ItemTags.create(new ResourceLocation(OhNoMoreMeteors.MODID, "rimerite_gear"));

    private FrosteryRepair() {
    }

    public static boolean isRepairable(ItemStack stack) {
        return Config.RIMERITE_FROSTERY_REPAIR.get() && stack.is(RIMERITE_GEAR) && stack.isDamaged();
    }

    /** Repairs the Rimerite item in the input slot by the value of the cold fuel just used up. */
    public static boolean repairFromFuel(NonNullList<ItemStack> items, ItemStack burnedFuel) {
        ItemStack gear = items.get(0);
        if (!isRepairable(gear)) {
            return false;
        }
        int points = RepairValues.points(RepairValues.COLD, burnedFuel, gear);
        if (points <= 0) {
            return false;
        }
        gear.setDamageValue(Math.max(0, gear.getDamageValue() - points));
        return true;
    }

    /** Ticks of "cold" a fuel item gives in the Frostery (0 = not a Frostery fuel). */
    public static int coldBurnTime(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(ModItems.RAW_RIMERITE.get())) {
            return 1600;
        }
        if (stack.is(ModItems.RIMERITE.get())) {
            return 1200;
        }
        if (stack.is(ModItems.RAW_RIMERITE_BLOCK.get())) {
            return 14400;
        }
        Integer ticks = RepairValues.COLD_BURN_TICKS.get(item);
        return ticks == null ? 0 : ticks;
    }

    /** In the Frostery only cold items burn; everywhere else nothing changes. Runs last so it has the final word. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFuel(FurnaceFuelBurnTimeEvent event) {
        if (event.getRecipeType() == ModFrostery.FREEZING.get()) {
            event.setBurnTime(coldBurnTime(event.getItemStack()));
        }
    }
}

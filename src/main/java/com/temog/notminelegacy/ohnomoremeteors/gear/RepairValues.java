package com.temog.notminelegacy.ohnomoremeteors.gear;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModItems;

import java.util.Map;

/**
 * How much durability a "heat" or "cold" material gives back, as a fraction of the item's maximum durability, so
 * tools and armour scale alike. Common materials restore little, rare ones more.
 *
 * HEAT: Cindrite gear in a furnace, repaired by the fuel the furnace burns (see CindriteTempering).
 * COLD: Rimerite gear in the Frostery, repaired by the cold fuel it burns (see frostery.FrosteryRepair).
 */
public final class RepairValues {

    public static final Map<Item, Double> HEAT = Map.ofEntries(
            Map.entry(Items.COAL, 0.02D),
            Map.entry(Items.CHARCOAL, 0.02D),
            Map.entry(Items.MAGMA_BLOCK, 0.03D),
            Map.entry(Items.MAGMA_CREAM, 0.04D),
            Map.entry(Items.FIRE_CHARGE, 0.05D),
            Map.entry(Items.BLAZE_POWDER, 0.06D),
            Map.entry(Items.BLAZE_ROD, 0.12D),
            Map.entry(Items.COAL_BLOCK, 0.15D),
            Map.entry(Items.LAVA_BUCKET, 0.25D));

    public static final Map<Item, Double> COLD = Map.ofEntries(
            Map.entry(Items.SNOWBALL, 0.01D),
            Map.entry(Items.SNOW, 0.02D),
            Map.entry(Items.SNOW_BLOCK, 0.04D),
            Map.entry(Items.ICE, 0.05D),
            Map.entry(Items.PACKED_ICE, 0.12D),
            Map.entry(Items.BLUE_ICE, 0.25D));

    /** Raw Rimerite also chills: it is looked up by item at call time (registry objects are not ready at class load). */
    public static double rawRimeriteCold() {
        return 0.10D;
    }

    /** How long each cold fuel keeps a Frostery running, in ticks (a furnace burns coal for 1600). */
    public static final Map<Item, Integer> COLD_BURN_TICKS = Map.ofEntries(
            Map.entry(Items.SNOWBALL, 100),
            Map.entry(Items.SNOW, 100),
            Map.entry(Items.SNOW_BLOCK, 400),
            Map.entry(Items.ICE, 600),
            Map.entry(Items.PACKED_ICE, 2000),
            Map.entry(Items.BLUE_ICE, 5600));
    // Rule: a fuel never burns as long as the Frostery recipe that makes it takes (ice 800, packed 2400, blue 6400),
    // so no fuel can multiply itself.

    private RepairValues() {
    }

    /** Durability points {@code material} restores on {@code gear} from {@code table} (0 if it is not listed). */
    public static int points(Map<Item, Double> table, ItemStack material, ItemStack gear) {
        Double fraction = table.get(material.getItem());
        if (fraction == null && table == COLD) {
            if (material.is(ModItems.RAW_RIMERITE.get())) {
                fraction = rawRimeriteCold();
            } else if (material.is(ModItems.RIMERITE.get())) {
                fraction = 0.08D;
            } else if (material.is(ModItems.RAW_RIMERITE_BLOCK.get())) {
                fraction = 0.60D;
            }
        }
        if (fraction == null || !gear.isDamageableItem()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(gear.getMaxDamage() * fraction));
    }
}

package com.temog.notminelegacy.ohnomoremeteors.compat.jei;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FreezingRecipe;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryRepair;
import com.temog.notminelegacy.ohnomoremeteors.gear.CindriteTempering;
import com.temog.notminelegacy.ohnomoremeteors.gear.RepairValues;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optional Just Enough Items support. JEI finds this class by its annotation and is the only thing that loads it, so
 * the mod runs the same without JEI (jei is an optional dependency in mods.toml, compile-only in the build).
 *
 * Adds:
 *  - a Frostery tab with every freezing recipe (the hidden Rimerite repair recipe is left out), the Frostery as its
 *    workstation and the cold fuels in the fuel slot;
 *  - info pages for the two repairs that are mechanics rather than recipes: Cindrite gear in a furnace, Rimerite gear
 *    in the Frostery, and the Frostery's cold fuel.
 */
@JeiPlugin
public class OnmmJeiPlugin implements IModPlugin {

    private static final ResourceLocation ID = new ResourceLocation(OhNoMoreMeteors.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FreezingCategory(registration.getJeiHelpers().getGuiHelper(), coldFuels()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModFrostery.FROSTERY_ITEM.get()), FreezingCategory.TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<FreezingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(ModFrostery.FREEZING.get()).stream()
                    .filter(recipe -> !recipe.isSpecial())
                    .toList();
            registration.addRecipes(FreezingCategory.TYPE, recipes);
        }

        info(registration, tagItems(CindriteTempering.CINDRITE_GEAR), "jei.ohnomoremeteors.info.cindrite_repair");
        info(registration, tagItems(FrosteryRepair.RIMERITE_GEAR), "jei.ohnomoremeteors.info.rimerite_repair");
        info(registration, List.of(new ItemStack(ModFrostery.FROSTERY_ITEM.get())), "jei.ohnomoremeteors.info.frostery");
    }

    private static void info(IRecipeRegistration registration, List<ItemStack> stacks, String key) {
        if (!stacks.isEmpty()) {
            registration.addIngredientInfo(stacks, VanillaTypes.ITEM_STACK, Component.translatable(key));
        }
    }

    private static List<ItemStack> tagItems(TagKey<Item> tag) {
        var tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) {
            return List.of();
        }
        return tags.getTag(tag).stream().map(ItemStack::new).toList();
    }

    /** Every item the Frostery burns, shortest burn first. */
    static List<ItemStack> coldFuels() {
        List<Item> items = new ArrayList<>(RepairValues.COLD_BURN_TICKS.keySet());
        items.add(ModItems.RAW_RIMERITE.get());
        items.add(ModItems.RIMERITE.get());
        items.add(ModItems.RAW_RIMERITE_BLOCK.get());
        return items.stream()
                .map(ItemStack::new)
                .filter(stack -> FrosteryRepair.coldBurnTime(stack) > 0)
                .sorted(Comparator.comparingInt(FrosteryRepair::coldBurnTime))
                .toList();
    }
}

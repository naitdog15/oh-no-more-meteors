package com.temog.notminelegacy.ohnomoremeteors.gear;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Cindrite gear is repaired in a furnace. Put the damaged item in the furnace's top (input) slot and a heat source in
 * the fuel slot: every fuel item the furnace burns gives back durability by {@link RepairValues#HEAT} (magma block
 * little, lava bucket a lot; ordinary fuels like wood burn but repair nothing). The item stays in the input slot and
 * the furnace stops taking fuel once it is fully repaired.
 *
 * How: {@link Recipe} is a furnace recipe that matches only damaged Cindrite gear (item tag
 * ohnomoremeteors:cindrite_gear), so the furnace lights for it. It never finishes cooking (the mixin pins its cook progress
 * to 0 every tick; the cook time is only the short-safe 32767); the
 * repair itself is done in place by mixin.AbstractFurnaceRepairMixin each time a fuel item is used up.
 * With cindriteHeatFuels on, magma blocks, magma cream, blaze powder and fire charges also count as furnace fuel
 * (everywhere, not only for this repair).
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CindriteTempering {

    public static final TagKey<Item> CINDRITE_GEAR = ItemTags.create(new ResourceLocation(OhNoMoreMeteors.MODID, "cindrite_gear"));

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, OhNoMoreMeteors.MODID);
    public static final RegistryObject<RecipeSerializer<Recipe>> TEMPERING =
            SERIALIZERS.register("cindrite_tempering", Serializer::new);

    private CindriteTempering() {
    }

    /** True for damaged Cindrite gear while furnace repair is switched on. */
    public static boolean isRepairable(ItemStack stack) {
        return Config.CINDRITE_FURNACE_REPAIR.get() && stack.is(CINDRITE_GEAR) && stack.isDamaged();
    }

    /** Repairs the item in the input slot by the value of the fuel that was just used up. Called from the mixin. */
    public static boolean repairFromFuel(NonNullList<ItemStack> furnaceItems, ItemStack burnedFuel) {
        ItemStack gear = furnaceItems.get(0);
        if (!isRepairable(gear)) {
            return false;
        }
        int points = RepairValues.points(RepairValues.HEAT, burnedFuel, gear);
        if (points <= 0) {
            return false;
        }
        gear.setDamageValue(Math.max(0, gear.getDamageValue() - points));
        return true;
    }

    @SubscribeEvent
    public static void onFuel(FurnaceFuelBurnTimeEvent event) {
        if (!Config.CINDRITE_HEAT_FUELS.get() || event.getBurnTime() > 0
                || event.getRecipeType() == com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery.FREEZING.get()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.is(Items.MAGMA_BLOCK)) {
            event.setBurnTime(400);
        } else if (stack.is(Items.MAGMA_CREAM)) {
            event.setBurnTime(200);
        } else if (stack.is(Items.BLAZE_POWDER)) {
            event.setBurnTime(800);
        } else if (stack.is(Items.FIRE_CHARGE)) {
            event.setBurnTime(500);
        }
    }

    /**
     * Reads the recipe JSON: "ingredient", "result" (an item id; never actually produced), optional "cookingtime"
     * (default 32767, clamped to 2..32767; the recipe never finishes because the mixin pins the cook progress to 0
     * while repairable gear is in the input) and "experience".
     */
    public static class Serializer implements RecipeSerializer<Recipe> {

        @Override
        public Recipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "ingredient"));
            ItemStack result = new ItemStack(ShapedRecipe.itemFromJson(GsonHelper.getAsJsonObject(json, "result")));
            float experience = GsonHelper.getAsFloat(json, "experience", 0.0F);
            int cookingTime = Mth.clamp(GsonHelper.getAsInt(json, "cookingtime", 32_767), 2, 32_767);
            return new Recipe(id, "", CookingBookCategory.MISC, ingredient, result, experience, cookingTime);
        }

        @Override
        public Recipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient ingredient = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            float experience = buf.readFloat();
            int cookingTime = buf.readVarInt();
            return new Recipe(id, "", CookingBookCategory.MISC, ingredient, result, experience, cookingTime);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, Recipe recipe) {
            recipe.getIngredients().get(0).toNetwork(buf);
            buf.writeItem(recipe.getResultItem(null));
            buf.writeFloat(recipe.getExperience());
            buf.writeVarInt(recipe.getCookingTime());
        }
    }

    /** The furnace recipe that keeps the furnace lit for damaged Cindrite gear. Hidden from the recipe book. */
    public static class Recipe extends SmeltingRecipe {

        public Recipe(ResourceLocation id, String group, CookingBookCategory category, Ingredient ingredient,
                      ItemStack result, float experience, int cookingTime) {
            super(id, group, category, ingredient, result, experience, cookingTime);
        }

        @Override
        public boolean matches(Container container, Level level) {
            return isRepairable(container.getItem(0)) && super.matches(container, level);
        }

        @Override
        public boolean isSpecial() {
            return true;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return TEMPERING.get();
        }
    }
}

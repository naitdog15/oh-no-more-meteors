package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.google.gson.JsonObject;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * A Frostery recipe (type ohnomoremeteors:freezing): one input item is chilled into the result over "cookingtime"
 * ticks while the Frostery burns cold fuel. JSON like a furnace recipe:
 * {"type": "ohnomoremeteors:freezing", "ingredient": {...}, "result": {"item": ...}, "experience": 0.1, "cookingtime": 200}.
 *
 * {@link Repair} (type ohnomoremeteors:rimerite_tempering) is the hidden recipe that keeps a Frostery running for a
 * damaged Rimerite item; it never finishes, the repair is done per cold fuel item (see FrosteryRepair).
 */
public class FreezingRecipe extends AbstractCookingRecipe {

    public FreezingRecipe(ResourceLocation id, String group, CookingBookCategory category, Ingredient ingredient,
                          ItemStack result, float experience, int cookingTime) {
        super(ModFrostery.FREEZING.get(), id, group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModFrostery.FREEZING_SERIALIZER.get();
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModFrostery.FROSTERY_ITEM.get());
    }

    /** The hidden repair recipe: matches only damaged Rimerite gear while Frostery repair is on. */
    public static class Repair extends FreezingRecipe {

        public Repair(ResourceLocation id, String group, CookingBookCategory category, Ingredient ingredient,
                      ItemStack result, float experience, int cookingTime) {
            super(id, group, category, ingredient, result, experience, cookingTime);
        }

        @Override
        public boolean matches(Container container, Level level) {
            return FrosteryRepair.isRepairable(container.getItem(0)) && super.matches(container, level);
        }

        @Override
        public boolean isSpecial() {
            return true;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return ModFrostery.REPAIR_SERIALIZER.get();
        }
    }

    /** Builds a recipe from its parts (the vanilla cooking serializer's factory is not public in 1.20.1). */
    @FunctionalInterface
    public interface Factory<T extends AbstractCookingRecipe> {
        T create(ResourceLocation id, String group, CookingBookCategory category, Ingredient ingredient,
                 ItemStack result, float experience, int cookingTime);
    }

    /** JSON and network serializer for cooking-style recipes. */
    public static class Serializer<T extends AbstractCookingRecipe> implements RecipeSerializer<T> {

        private final Factory<T> factory;
        private final int defaultCookingTime;

        public Serializer(Factory<T> factory, int defaultCookingTime) {
            this.factory = factory;
            this.defaultCookingTime = defaultCookingTime;
        }

        @Override
        public T fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            Ingredient ingredient = Ingredient.fromJson(GsonHelper.isArrayNode(json, "ingredient")
                    ? GsonHelper.getAsJsonArray(json, "ingredient") : GsonHelper.getAsJsonObject(json, "ingredient"));
            ItemStack result = json.get("result").isJsonObject()
                    ? ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"))
                    : new ItemStack(GsonHelper.getAsItem(json, "result"));
            float experience = GsonHelper.getAsFloat(json, "experience", 0.0F);
            int cookingTime = GsonHelper.getAsInt(json, "cookingtime", defaultCookingTime);
            return factory.create(id, group, CookingBookCategory.MISC, ingredient, result, experience, cookingTime);
        }

        @Override
        public T fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            String group = buf.readUtf();
            Ingredient ingredient = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            float experience = buf.readFloat();
            int cookingTime = buf.readVarInt();
            return factory.create(id, group, CookingBookCategory.MISC, ingredient, result, experience, cookingTime);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, T recipe) {
            buf.writeUtf(recipe.getGroup());
            recipe.getIngredients().get(0).toNetwork(buf);
            buf.writeItem(recipe.getResultItem(null));
            buf.writeFloat(recipe.getExperience());
            buf.writeVarInt(recipe.getCookingTime());
        }
    }
}

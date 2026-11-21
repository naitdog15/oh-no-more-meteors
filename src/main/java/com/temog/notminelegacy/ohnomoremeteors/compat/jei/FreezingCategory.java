package com.temog.notminelegacy.ohnomoremeteors.compat.jei;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FreezingRecipe;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * JEI tab for the Frostery (recipe type ohnomoremeteors:freezing), drawn from the Frostery's own GUI texture in the
 * vanilla furnace layout: input top left, cold fuel below it, result on the right. The fuel slot cycles through every
 * cold fuel, so looking up the uses of a snowball or ice also finds the Frostery.
 */
public class FreezingCategory implements IRecipeCategory<FreezingRecipe> {

    public static final RecipeType<FreezingRecipe> TYPE =
            RecipeType.create(OhNoMoreMeteors.MODID, "freezing", FreezingRecipe.class);

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(OhNoMoreMeteors.MODID, "textures/gui/container/frostery.png");

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableAnimated flame;
    private final IGuiHelper guiHelper;
    private final List<ItemStack> coldFuels;
    private final java.util.Map<Integer, IDrawableAnimated> arrows = new java.util.HashMap<>();

    public FreezingCategory(IGuiHelper guiHelper, List<ItemStack> coldFuels) {
        this.guiHelper = guiHelper;
        this.coldFuels = coldFuels;
        // Same crop JEI uses for the vanilla furnace: input (1,1), fuel (1,37), result (61,19).
        this.background = guiHelper.createDrawable(TEXTURE, 55, 16, 82, 54);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModFrostery.FROSTERY_ITEM.get()));
        this.flame = guiHelper.createAnimatedDrawable(guiHelper.createDrawable(TEXTURE, 176, 0, 14, 14), 300,
                IDrawableAnimated.StartDirection.TOP, true);
    }

    @Override
    public RecipeType<FreezingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ohnomoremeteors.freezing");
    }

    @Override
    @SuppressWarnings("removal")
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public int getWidth() {
        return 82;
    }

    @Override
    public int getHeight() {
        return 54;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FreezingRecipe recipe, IFocusGroup focuses) {
        int count = recipe.getInputCount();
        List<ItemStack> inputs = Arrays.stream(recipe.getIngredients().get(0).getItems())
                .map(stack -> stack.copyWithCount(count))
                .toList();
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStacks(inputs);
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 37).addItemStacks(this.coldFuels);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 19).addItemStack(recipe.getResultItem(null));
    }

    @Override
    public void draw(FreezingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        this.flame.draw(graphics, 1, 20);
        int cookTime = Math.max(1, recipe.getCookingTime());
        // One animated arrow per cook time, kept so its timer runs on between frames.
        this.arrows.computeIfAbsent(cookTime, this.guiHelper::createAnimatedRecipeArrow).draw(graphics, 24, 18);

        Font font = Minecraft.getInstance().font;
        float xp = recipe.getExperience();
        if (xp > 0) {
            Component text = Component.translatable("gui.jei.category.smelting.experience", xp);
            graphics.drawString(font, text, 82 - font.width(text), 0, 0xFF808080, false);
        }
        Component time = Component.translatable("gui.jei.category.smelting.time.seconds", cookTime / 20);
        graphics.drawString(font, time, 82 - font.width(time), 45, 0xFF808080, false);
    }
}

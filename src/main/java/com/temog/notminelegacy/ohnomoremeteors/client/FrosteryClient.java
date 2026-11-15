package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client wiring for the Frostery: its screen, and a recipe book category so freezing recipes do not log warnings. */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrosteryClient {

    private FrosteryClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModFrostery.FROSTERY_MENU.get(), FrosteryScreen::new));
    }

    @SubscribeEvent
    public static void onRecipeBookCategories(RegisterRecipeBookCategoriesEvent event) {
        // The Frostery has no recipe book; file its recipes under UNKNOWN on purpose.
        event.registerRecipeCategoryFinder(ModFrostery.FREEZING.get(), recipe -> RecipeBookCategories.UNKNOWN);
    }
}

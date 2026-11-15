package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own creative tab, "Oh No, More Meteors!" (lang key itemGroup.ohnomoremeteors.main).
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OhNoMoreMeteors.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + OhNoMoreMeteors.MODID + ".main"))
                    .icon(() -> new ItemStack(ModItems.RAW_CINDRITE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.RAW_CINDRITE.get());
                        output.accept(ModItems.RAW_RIMERITE.get());
                        output.accept(ModItems.RAW_CINDRITE_BLOCK.get());
                        output.accept(ModItems.RAW_RIMERITE_BLOCK.get());
                        output.accept(ModItems.CINDRITE.get());
                        output.accept(ModItems.RIMERITE.get());
                        output.accept(ModItems.RAW_VEYRITE.get());
                        output.accept(ModItems.RAW_VEYRITE_BLOCK.get());
                        output.accept(ModItems.VEYRITE.get());
                        output.accept(ModFrostery.FROSTERY_ITEM.get());
                        // Gear: everything registered after the blocks (ingots, nuggets, tools, armour), in order.
                        boolean gear = false;
                        for (RegistryObject<Item> entry : ModItems.ITEMS.getEntries()) {
                            if (entry == ModItems.CINDRITE_INGOT) {
                                gear = true;
                            }
                            if (gear) {
                                output.accept(entry.get());
                            }
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}

package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;

/**
 * Frostery menu: the vanilla furnace menu (3 slots, progress data) on the freezing recipe type. The fuel slot accepts
 * only cold items (the furnace menu asks the fuel event with this recipe type). The Frostery screen has no recipe book,
 * so the book type is only a required placeholder.
 */
public class FrosteryMenu extends AbstractFurnaceMenu {

    /** Client side. */
    public FrosteryMenu(int id, Inventory inventory) {
        super(ModFrostery.FROSTERY_MENU.get(), ModFrostery.FREEZING.get(), RecipeBookType.FURNACE, id, inventory);
    }

    /** Server side. */
    public FrosteryMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModFrostery.FROSTERY_MENU.get(), ModFrostery.FREEZING.get(), RecipeBookType.FURNACE, id, inventory, container, data);
    }
}

package com.temog.notminelegacy.ohnomoremeteors.frostery;

import com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Frostery menu: the vanilla furnace menu (3 slots, progress data) on the freezing recipe type. The fuel slot accepts
 * only cold items (the vanilla fuel slot is replaced, since it always lets an empty bucket in). The Frostery screen has no recipe book,
 * so the book type is only a required placeholder.
 */
public class FrosteryMenu extends AbstractFurnaceMenu {

    /** Client side. */
    public FrosteryMenu(int id, Inventory inventory) {
        super(ModFrostery.FROSTERY_MENU.get(), ModFrostery.FREEZING.get(), RecipeBookType.FURNACE, id, inventory);
        coldOnlyFuelSlot();
    }

    /** Server side. */
    public FrosteryMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModFrostery.FROSTERY_MENU.get(), ModFrostery.FREEZING.get(), RecipeBookType.FURNACE, id, inventory, container, data);
        coldOnlyFuelSlot();
    }

    /** Vanilla's fuel slot always accepts an empty bucket, so swap it for one that accepts cold fuel only. */
    private void coldOnlyFuelSlot() {
        Slot old = this.slots.get(1);
        Slot fuel = new Slot(old.container, old.getContainerSlot(), old.x, old.y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FrosteryRepair.coldBurnTime(stack) > 0;
            }
        };
        fuel.index = old.index;
        this.slots.set(1, fuel);
    }
}

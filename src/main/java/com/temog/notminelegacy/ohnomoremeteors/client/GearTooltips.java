package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModArmorMaterials;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

/**
 * One tooltip line per meteoric, Rimerite and Cindrite gear item describing its ability (lang keys
 * tooltip.ohnomoremeteors.&lt;metal&gt;.&lt;kind&gt;). Rimerite and Cindrite gear also gets a repair line
 * (tooltip.ohnomoremeteors.&lt;metal&gt;.repair). Each Rimerite/Cindrite line is hidden when its config is off.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GearTooltips {

    private GearTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        String metal = null;
        String kind = null;
        if (item instanceof ArmorItem armor && armor.getMaterial() instanceof ModArmorMaterials material) {
            String name = material.getName().substring(material.getName().indexOf(':') + 1);
            metal = "meteoric".equals(name) || "rimerite".equals(name) || "cindrite".equals(name) ? name : null;
            kind = "armor";
        } else if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            metal = tier == ModTiers.METEORIC ? "meteoric" : tier == ModTiers.RIMERITE ? "rimerite"
                    : tier == ModTiers.CINDRITE ? "cindrite" : null;
            kind = item instanceof SwordItem ? "sword" : item instanceof PickaxeItem ? "pickaxe" : item instanceof AxeItem ? "axe"
                    : item instanceof ShovelItem ? "shovel" : item instanceof HoeItem ? "hoe" : null;
        }
        if (metal == null || kind == null) {
            return;
        }
        boolean rimerite = "rimerite".equals(metal);
        boolean cindrite = "cindrite".equals(metal);
        if (rimerite && !enabled(Config.RIMERITE_ABILITIES)) {
            // ability line hidden when the ability config is off
        } else if (cindrite && !enabled(Config.CINDRITE_ABILITIES)) {
            // ability line hidden when the ability config is off
        } else {
            String key = "tooltip." + OhNoMoreMeteors.MODID + "." + metal + "." + kind;
            event.getToolTip().add(1, Component.translatable(key).withStyle(
                    ChatFormatting.GRAY));
        }
        if ((rimerite && enabled(Config.RIMERITE_FROSTERY_REPAIR)) || (cindrite && enabled(Config.CINDRITE_FURNACE_REPAIR))) {
            String repairKey = "tooltip." + OhNoMoreMeteors.MODID + "." + metal + ".repair";
            int index = Math.min(event.getToolTip().size(), isAbilityHidden(rimerite, cindrite) ? 1 : 2);
            event.getToolTip().add(index, Component.translatable(repairKey).withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean isAbilityHidden(boolean rimerite, boolean cindrite) {
        return (rimerite && !enabled(Config.RIMERITE_ABILITIES)) || (cindrite && !enabled(Config.CINDRITE_ABILITIES));
    }

    private static boolean enabled(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}

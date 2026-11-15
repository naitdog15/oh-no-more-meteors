package com.temog.notminelegacy.ohnomoremeteors.client;

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
import net.minecraftforge.fml.common.Mod;

/**
 * One tooltip line per meteor gear item describing its ability (lang keys tooltip.ohnomoremeteors.&lt;metal&gt;.&lt;kind&gt;).
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
            metal = material.getName().substring(material.getName().indexOf(':') + 1);
            kind = "armor";
        } else if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            metal = tier == ModTiers.RIMERITE ? "rimerite" : tier == ModTiers.CINDRITE ? "cindrite" : tier == ModTiers.METEORIC ? "meteoric" : null;
            kind = item instanceof SwordItem ? "sword" : item instanceof PickaxeItem ? "pickaxe" : item instanceof AxeItem ? "axe"
                    : item instanceof ShovelItem ? "shovel" : item instanceof HoeItem ? "hoe" : null;
        }
        if (metal == null || kind == null) {
            return;
        }
        String key = "tooltip." + OhNoMoreMeteors.MODID + "." + metal + "." + kind;
        event.getToolTip().add(1, Component.translatable(key).withStyle(
                "cindrite".equals(metal) ? ChatFormatting.GOLD : "rimerite".equals(metal) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}

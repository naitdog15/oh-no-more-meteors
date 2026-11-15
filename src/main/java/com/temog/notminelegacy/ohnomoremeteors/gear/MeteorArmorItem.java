package com.temog.notminelegacy.ohnomoremeteors.gear;

import com.temog.notminelegacy.ohnomoremeteors.registry.ModArmorMaterials;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Armour piece of a meteor metal; worn with the material's shaped model (client/MeteorArmorModels). */
public class MeteorArmorItem extends ArmorItem {

    private final ModArmorMaterials meteorMaterial;

    public MeteorArmorItem(ModArmorMaterials material, Type type, Properties properties) {
        super(material, type, properties);
        this.meteorMaterial = material;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.temog.notminelegacy.ohnomoremeteors.client.MeteorArmorModels.extensions(meteorMaterial));
    }
}

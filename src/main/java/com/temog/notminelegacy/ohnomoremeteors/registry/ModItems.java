package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Item registry: the two raw materials, a block item for each of the four blocks, and the gear of the three meteor
 * metals (ingots, nuggets, tools, armour). Meteoric gear uses Oh My Meteors' meteoric_alloy as its ingot, so only a
 * nugget is added for it. Cindrite items survive fire and lava like netherite.
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OhNoMoreMeteors.MODID);

    public static final RegistryObject<Item> RAW_CINDRITE = ITEMS.register("raw_cindrite",
            () -> new Item(new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> RAW_RIMERITE = ITEMS.register("raw_rimerite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> RAW_VEYRITE = ITEMS.register("raw_veyrite",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> CINDRITE = ITEMS.register("cindrite",
            () -> new BlockItem(ModBlocks.CINDRITE.get(), new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> RIMERITE = ITEMS.register("rimerite",
            () -> new BlockItem(ModBlocks.RIMERITE.get(), new Item.Properties()));

    public static final RegistryObject<Item> RIME_WORN_STONE = ITEMS.register("rime_worn_stone",
            () -> new BlockItem(ModBlocks.RIME_WORN_STONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> RAW_CINDRITE_BLOCK = ITEMS.register("raw_cindrite_block",
            () -> new BlockItem(ModBlocks.RAW_CINDRITE_BLOCK.get(), new Item.Properties().fireResistant()));
    public static final RegistryObject<Item> RAW_RIMERITE_BLOCK = ITEMS.register("raw_rimerite_block",
            () -> new BlockItem(ModBlocks.RAW_RIMERITE_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> VEYRITE = ITEMS.register("veyrite",
            () -> new BlockItem(ModBlocks.VEYRITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> RAW_VEYRITE_BLOCK = ITEMS.register("raw_veyrite_block",
            () -> new BlockItem(ModBlocks.RAW_VEYRITE_BLOCK.get(), new Item.Properties()));

    // ---- Meteor metals: ingots and nuggets ----
    public static final RegistryObject<Item> CINDRITE_INGOT = simple("cindrite_ingot", true);
    public static final RegistryObject<Item> RIMERITE_INGOT = simple("rimerite_ingot", false);
    public static final RegistryObject<Item> METEORIC_ALLOY_NUGGET = simple("meteoric_alloy_nugget", false);
    public static final RegistryObject<Item> CINDRITE_NUGGET = simple("cindrite_nugget", true);
    public static final RegistryObject<Item> RIMERITE_NUGGET = simple("rimerite_nugget", false);

    // ---- Tools (attack damage / speed offsets follow the vanilla neighbour tier) ----
    public static final RegistryObject<Item> METEORIC_SWORD = sword("meteoric", ModTiers.METEORIC, false);
    public static final RegistryObject<Item> METEORIC_PICKAXE = pickaxe("meteoric", ModTiers.METEORIC, false);
    public static final RegistryObject<Item> METEORIC_AXE = axe("meteoric", ModTiers.METEORIC, 6.0F, -3.1F, false);
    public static final RegistryObject<Item> METEORIC_SHOVEL = shovel("meteoric", ModTiers.METEORIC, false);
    public static final RegistryObject<Item> METEORIC_HOE = hoe("meteoric", ModTiers.METEORIC, -2, -1.0F, false);

    public static final RegistryObject<Item> RIMERITE_SWORD = sword("rimerite", ModTiers.RIMERITE, false);
    public static final RegistryObject<Item> RIMERITE_PICKAXE = pickaxe("rimerite", ModTiers.RIMERITE, false);
    public static final RegistryObject<Item> RIMERITE_AXE = axe("rimerite", ModTiers.RIMERITE, 6.5F, -3.2F, false);
    public static final RegistryObject<Item> RIMERITE_SHOVEL = shovel("rimerite", ModTiers.RIMERITE, false);
    public static final RegistryObject<Item> RIMERITE_HOE = hoe("rimerite", ModTiers.RIMERITE, -1, -2.0F, false);

    public static final RegistryObject<Item> CINDRITE_SWORD = sword("cindrite", ModTiers.CINDRITE, true);
    public static final RegistryObject<Item> CINDRITE_PICKAXE = pickaxe("cindrite", ModTiers.CINDRITE, true);
    public static final RegistryObject<Item> CINDRITE_AXE = axe("cindrite", ModTiers.CINDRITE, 6.0F, -3.0F, true);
    public static final RegistryObject<Item> CINDRITE_SHOVEL = shovel("cindrite", ModTiers.CINDRITE, true);
    public static final RegistryObject<Item> CINDRITE_HOE = hoe("cindrite", ModTiers.CINDRITE, -2, -0.5F, true);

    // ---- Armour ----
    public static final List<RegistryObject<Item>> METEORIC_ARMOR = armorSet(ModArmorMaterials.METEORIC);
    public static final List<RegistryObject<Item>> RIMERITE_ARMOR = armorSet(ModArmorMaterials.RIMERITE);
    public static final List<RegistryObject<Item>> CINDRITE_ARMOR = armorSet(ModArmorMaterials.CINDRITE);

    private static Item.Properties props(boolean fireResistant) {
        Item.Properties properties = new Item.Properties();
        return fireResistant ? properties.fireResistant() : properties;
    }

    private static RegistryObject<Item> simple(String id, boolean fire) {
        return ITEMS.register(id, () -> new Item(props(fire)));
    }

    private static RegistryObject<Item> sword(String metal, Tier tier, boolean fire) {
        return ITEMS.register(metal + "_sword", () -> new SwordItem(tier, 3, -2.4F, props(fire)));
    }

    private static RegistryObject<Item> pickaxe(String metal, Tier tier, boolean fire) {
        return ITEMS.register(metal + "_pickaxe", () -> new PickaxeItem(tier, 1, -2.8F, props(fire)));
    }

    private static RegistryObject<Item> axe(String metal, Tier tier, float damage, float speed, boolean fire) {
        return ITEMS.register(metal + "_axe", () -> new AxeItem(tier, damage, speed, props(fire)));
    }

    private static RegistryObject<Item> shovel(String metal, Tier tier, boolean fire) {
        return ITEMS.register(metal + "_shovel", () -> new ShovelItem(tier, 1.5F, -3.0F, props(fire)));
    }

    private static RegistryObject<Item> hoe(String metal, Tier tier, int damage, float speed, boolean fire) {
        return ITEMS.register(metal + "_hoe", () -> new HoeItem(tier, damage, speed, props(fire)));
    }

    /** Helmet, chestplate, leggings, boots; ids are &lt;metal&gt;_helmet and so on. */
    private static List<RegistryObject<Item>> armorSet(ModArmorMaterials material) {
        String metal = material.getName().substring(material.getName().indexOf(':') + 1);
        boolean fire = material == ModArmorMaterials.CINDRITE;
        return List.of(
                ITEMS.register(metal + "_helmet", () -> new com.temog.notminelegacy.ohnomoremeteors.gear.MeteorArmorItem(material, ArmorItem.Type.HELMET, props(fire))),
                ITEMS.register(metal + "_chestplate", () -> new com.temog.notminelegacy.ohnomoremeteors.gear.MeteorArmorItem(material, ArmorItem.Type.CHESTPLATE, props(fire))),
                ITEMS.register(metal + "_leggings", () -> new com.temog.notminelegacy.ohnomoremeteors.gear.MeteorArmorItem(material, ArmorItem.Type.LEGGINGS, props(fire))),
                ITEMS.register(metal + "_boots", () -> new com.temog.notminelegacy.ohnomoremeteors.gear.MeteorArmorItem(material, ArmorItem.Type.BOOTS, props(fire))));
    }

    private ModItems() {
    }
}

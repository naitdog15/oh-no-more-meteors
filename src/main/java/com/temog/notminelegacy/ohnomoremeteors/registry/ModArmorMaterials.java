package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Armour materials of the three meteor metals. Worn textures are
 * assets/ohnomoremeteors/textures/models/armor/&lt;name&gt;_layer_1.png and _layer_2.png.
 * Stats: rimerite brittle (chainmail-like, low durability, ice set bonus), meteoric a little above iron
 * (much more durability), cindrite just under diamond (high toughness, fire set bonus).
 */
public enum ModArmorMaterials implements ArmorMaterial {
    // name, durability multiplier, helmet, chest, legs, boots, enchantability, sound, toughness, knockback, ingot tag
    METEORIC("meteoric", 25, 2, 6, 5, 2, 12, SoundEvents.ARMOR_EQUIP_IRON, 0.5F, 0.0F, "meteoric_alloy"),
    RIMERITE("rimerite", 12, 2, 5, 4, 1, 18, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F, "rimerite"),
    CINDRITE("cindrite", 28, 3, 7, 6, 2, 10, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5F, 0.05F, "cindrite");

    private final String name;
    private final int durabilityMultiplier;
    private final int helmet;
    private final int chest;
    private final int legs;
    private final int boots;
    private final int enchantment;
    private final SoundEvent sound;
    private final float toughness;
    private final float knockback;
    private final String ingot;

    ModArmorMaterials(String name, int durabilityMultiplier, int helmet, int chest, int legs, int boots, int enchantment,
                      SoundEvent sound, float toughness, float knockback, String ingot) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.helmet = helmet;
        this.chest = chest;
        this.legs = legs;
        this.boots = boots;
        this.enchantment = enchantment;
        this.sound = sound;
        this.toughness = toughness;
        this.knockback = knockback;
        this.ingot = ingot;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        // Vanilla per-slot base durability times the material multiplier.
        int base = switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
        return base * durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> helmet;
            case CHESTPLATE -> chest;
            case LEGGINGS -> legs;
            case BOOTS -> boots;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return enchantment;
    }

    @Override
    public SoundEvent getEquipSound() {
        return sound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ItemTags.create(new ResourceLocation("forge", "ingots/" + ingot)));
    }

    @Override
    public String getName() {
        return OhNoMoreMeteors.MODID + ":" + name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockback;
    }
}

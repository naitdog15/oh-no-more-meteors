package com.temog.notminelegacy.ohnomoremeteors.gear;

import com.mojang.serialization.Codec;
import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModArmorMaterials;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModTiers;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Combat and armour abilities of the meteor gear (block-drop abilities are in {@link MeteorToolLootModifier}).
 *
 * Rimerite ("cool", config rimeriteAbilities):
 *  - sword and axe hits chill the target (Slowness I, 2 s), put out a burning target, and deal +3 damage to
 *    fire-immune mobs (blazes, magma cubes, striders...).
 *  - full armour set: melee attackers are chilled (Slowness I, 2 s). Each piece also protects from freezing in
 *    powder snow (item tag minecraft:freeze_immune_wearables).
 * Cindrite ("heat", config cindriteAbilities):
 *  - sword and axe hits set the target on fire (3 s / 2 s).
 *  - full armour set: immune to fire, burning, lava and magma-floor damage; fire goes out once the wearer leaves
 *    fire or lava. Each 10 seconds of burning or lava, added up, costs each armour piece 1 durability.
 * Meteoric has no ability: its strength is durability.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GearAbilities {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, OhNoMoreMeteors.MODID);
    public static final RegistryObject<Codec<MeteorToolLootModifier>> METEOR_TOOLS =
            LOOT_MODIFIERS.register("meteor_tools", () -> MeteorToolLootModifier.CODEC);

    /** Ticks between the Cindrite armour's heat wear (10 seconds). */
    private static final int HEAT_WEAR_TICKS = 200;

    /** Persistent-data key for the accumulated burning ticks. */
    private static final String BURN_TICKS_KEY = "ohnomoremeteors:cindrite_burn_ticks";

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private GearAbilities() {
    }

    /** True when all four armour slots hold pieces of {@code material}. */
    public static boolean fullSet(LivingEntity entity, ModArmorMaterials material) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof ArmorItem armor) || armor.getMaterial() != material) {
                return false;
            }
        }
        return true;
    }

    private static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof TieredItem tiered ? tiered.getTier() : null;
    }

    /** The damage types the Cindrite set ignores (not explosions or fireballs). */
    private static boolean isHeatDamage(DamageSource source) {
        return source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (Config.CINDRITE_ABILITIES.get() && isHeatDamage(event.getSource()) && fullSet(victim, ModArmorMaterials.CINDRITE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (victim.level().isClientSide()
                || !(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker
                || source.is(DamageTypeTags.IS_PROJECTILE)) {
            return; // melee hits only
        }
        ItemStack weapon = attacker.getMainHandItem();
        boolean blade = weapon.getItem() instanceof SwordItem || weapon.getItem() instanceof AxeItem;
        Tier tier = tierOf(weapon);

        if (blade && tier == ModTiers.RIMERITE && Config.RIMERITE_ABILITIES.get()) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), attacker);
            victim.clearFire();
            if (victim.fireImmune()) {
                event.setAmount(event.getAmount() + 3.0F);
            }
        } else if (blade && tier == ModTiers.CINDRITE && Config.CINDRITE_ABILITIES.get() && !victim.fireImmune()) {
            victim.setSecondsOnFire(weapon.getItem() instanceof SwordItem ? 3 : 2);
        }

        if (Config.RIMERITE_ABILITIES.get() && fullSet(victim, ModArmorMaterials.RIMERITE)) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), victim);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        boolean burning = entity.isOnFire() || entity.isInLava();
        if (!burning || !Config.CINDRITE_ABILITIES.get() || !fullSet(entity, ModArmorMaterials.CINDRITE)) {
            return;
        }
        int ticks = entity.getPersistentData().getInt(BURN_TICKS_KEY) + 1;
        if (ticks >= HEAT_WEAR_TICKS) {
            ticks -= HEAT_WEAR_TICKS;
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                entity.getItemBySlot(slot).hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(slot));
            }
        }
        entity.getPersistentData().putInt(BURN_TICKS_KEY, ticks);
        if (!entity.isInLava()
                && entity.level().getBlockStatesIfLoaded(entity.getBoundingBox().deflate(1.0E-6D))
                .noneMatch(s -> s.is(BlockTags.FIRE) || s.is(Blocks.LAVA))) {
            entity.clearFire();
        }
    }
}

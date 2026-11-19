package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Scales the blast damage a fire, ice or space meteor deals to living things by the variant's entityDamageMult.
 * OMM has no direct-hit damage (hitting an entity just explodes the meteor), so the blast is the only meteor
 * damage. The discarded meteor is still the damage source's direct entity and keeps its claim, so the variant
 * can be read from it. Fails open: on any error the damage is left untouched.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorDamage {

    private static boolean loggedFailure;

    private MeteorDamage() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        try {
            DamageSource source = event.getSource();
            if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
                return;
            }
            Entity direct = source.getDirectEntity();
            if (direct == null || !MeteorEvents.isOmmMeteor(direct)) {
                return;
            }
            MeteorVariant variant = MeteorVariant.fromEntity(direct);
            if (variant == null || !variant.isEnabled()) {
                return;
            }
            double mult = VariantTuning.damageMult(variant);
            if (mult != 1.0D) {
                event.setAmount((float) (event.getAmount() * mult));
            }
        } catch (RuntimeException | LinkageError ex) {
            if (!loggedFailure) {
                loggedFailure = true;
                OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Meteor blast damage scaling failed; damage left unchanged (this is logged once).", ex);
            }
        }
    }
}

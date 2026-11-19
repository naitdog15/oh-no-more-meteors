package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client only: hooks the six meteor particle types to their sprite sets. */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModParticleProviders {

    private ModParticleProviders() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CINDRITE_EMBER.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.CINDRITE_EMBER));
        event.registerSpriteSet(ModParticles.CINDRITE_BURST.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.CINDRITE_BURST));
        event.registerSpriteSet(ModParticles.CINDRITE_HEAT_SPARK.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.CINDRITE_HEAT_SPARK));
        event.registerSpriteSet(ModParticles.RIMERITE_SHARD.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.RIMERITE_SHARD));
        event.registerSpriteSet(ModParticles.RIMERITE_BURST.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.RIMERITE_BURST));
        event.registerSpriteSet(ModParticles.RIMERITE_FROST_MIST.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.RIMERITE_FROST_MIST));
        event.registerSpriteSet(ModParticles.CINDRITE_FLAME.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.CINDRITE_FLAME));
        event.registerSpriteSet(ModParticles.CINDRITE_SMOKE.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.CINDRITE_SMOKE));
        event.registerSpriteSet(ModParticles.RIMERITE_FLAME.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.RIMERITE_FLAME));
        event.registerSpriteSet(ModParticles.RIMERITE_SMOKE.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.RIMERITE_SMOKE));
        event.registerSpriteSet(ModParticles.VEYRITE_FLAME.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.VEYRITE_FLAME));
        event.registerSpriteSet(ModParticles.VEYRITE_SMOKE.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.VEYRITE_SMOKE));
        event.registerSpriteSet(ModParticles.VEYRITE_SPARK.get(), s -> new MeteorParticle.Provider(s, MeteorParticle.Kind.VEYRITE_SPARK));
        event.registerSpriteSet(ModParticles.CINDRITE_FLASH.get(), VariantFlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RIMERITE_FLASH.get(), VariantFlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.VEYRITE_FLASH.get(), VariantFlashParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RIMERITE_BIG_SMOKE.get(), s -> new VariantBigSmokeParticle.Provider(s, VariantBigSmokeParticle.Palette.RIMERITE, 3.0F));
        event.registerSpriteSet(ModParticles.RIMERITE_BIG_PLUME.get(), s -> new VariantBigSmokeParticle.Provider(s, VariantBigSmokeParticle.Palette.RIMERITE, 5.0F));
        event.registerSpriteSet(ModParticles.VEYRITE_BIG_SMOKE.get(), s -> new VariantBigSmokeParticle.Provider(s, VariantBigSmokeParticle.Palette.VEYRITE, 3.0F));
        event.registerSpriteSet(ModParticles.VEYRITE_BIG_PLUME.get(), s -> new VariantBigSmokeParticle.Provider(s, VariantBigSmokeParticle.Palette.VEYRITE, 5.0F));
    }
}

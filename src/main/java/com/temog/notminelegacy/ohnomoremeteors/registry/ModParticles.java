package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The add-on's own particle types. The sprite lists are in assets/ohnomoremeteors/particles/&lt;id&gt;.json
 * and the client providers are in the client package.
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, OhNoMoreMeteors.MODID);

    public static final RegistryObject<SimpleParticleType> CINDRITE_EMBER = simple("cindrite_ember");
    public static final RegistryObject<SimpleParticleType> CINDRITE_BURST = simple("cindrite_burst");
    public static final RegistryObject<SimpleParticleType> CINDRITE_HEAT_SPARK = simple("cindrite_heat_spark");
    public static final RegistryObject<SimpleParticleType> RIMERITE_SHARD = simple("rimerite_shard");
    public static final RegistryObject<SimpleParticleType> RIMERITE_BURST = simple("rimerite_burst");
    public static final RegistryObject<SimpleParticleType> RIMERITE_FROST_MIST = simple("rimerite_frost_mist");

    // Falling meteor particles per variant (2026-11-19): replace Oh My, Meteors! flame / smoke / flash for claimed meteors.
    public static final RegistryObject<SimpleParticleType> CINDRITE_FLAME = simple("cindrite_flame");
    public static final RegistryObject<SimpleParticleType> CINDRITE_SMOKE = simple("cindrite_smoke");
    public static final RegistryObject<SimpleParticleType> CINDRITE_FLASH = simple("cindrite_flash");
    public static final RegistryObject<SimpleParticleType> RIMERITE_FLAME = simple("rimerite_flame");
    public static final RegistryObject<SimpleParticleType> RIMERITE_SMOKE = simple("rimerite_smoke");
    public static final RegistryObject<SimpleParticleType> RIMERITE_FLASH = simple("rimerite_flash");
    public static final RegistryObject<SimpleParticleType> VEYRITE_FLAME = simple("veyrite_flame");
    public static final RegistryObject<SimpleParticleType> VEYRITE_SMOKE = simple("veyrite_smoke");
    public static final RegistryObject<SimpleParticleType> VEYRITE_SPARK = simple("veyrite_spark");
    public static final RegistryObject<SimpleParticleType> VEYRITE_FLASH = simple("veyrite_flash");
    // OMM big meteor smoke recoloured per variant (puff = OMM scale 3, plume = the one big size-based puff).
    public static final RegistryObject<SimpleParticleType> RIMERITE_BIG_SMOKE = simple("rimerite_big_smoke");
    public static final RegistryObject<SimpleParticleType> RIMERITE_BIG_PLUME = simple("rimerite_big_plume");
    public static final RegistryObject<SimpleParticleType> VEYRITE_BIG_SMOKE = simple("veyrite_big_smoke");
    public static final RegistryObject<SimpleParticleType> VEYRITE_BIG_PLUME = simple("veyrite_big_plume");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> simple(String id) {
        // false = no "override limiter"; the trail sends its own long-distance flag per player.
        return PARTICLES.register(id, () -> new SimpleParticleType(false));
    }
}

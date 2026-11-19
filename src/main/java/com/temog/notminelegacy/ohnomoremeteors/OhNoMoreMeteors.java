package com.temog.notminelegacy.ohnomoremeteors;

import com.mojang.logging.LogUtils;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModBlocks;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModCreativeTabs;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModItems;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Oh No, More Meteors! - add-on for Oh My, Meteors! (modid ohmymeteors, REQUIRED).
 *
 * This class only wires the registries and the config. Game events (the meteor variant roll, the
 * fall trail) live in {@code meteor.*} classes that subscribe themselves with
 * {@code @Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID)}, so they need no wiring here.
 */
@Mod(OhNoMoreMeteors.MODID)
public class OhNoMoreMeteors {

    public static final String MODID = "ohnomoremeteors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OhNoMoreMeteors() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Sound events first, because the block sound types read them.
        ModSounds.SOUND_EVENTS.register(modBus);
        com.temog.notminelegacy.ohnomoremeteors.registry.ModParticles.PARTICLES.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        com.temog.notminelegacy.ohnomoremeteors.registry.ModFrostery.register(modBus);
        com.temog.notminelegacy.ohnomoremeteors.gear.GearAbilities.LOOT_MODIFIERS.register(modBus);
        com.temog.notminelegacy.ohnomoremeteors.gear.CindriteTempering.SERIALIZERS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        com.temog.notminelegacy.ohnomoremeteors.network.ModNetwork.register();
    }

    /** Builds a ResourceLocation in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}

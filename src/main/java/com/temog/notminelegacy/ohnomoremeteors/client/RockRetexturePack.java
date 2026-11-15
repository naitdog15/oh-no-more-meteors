package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the built-in client pack that gives Oh My Meteors' meteoric rock this add-on's own textures.
 *
 * The pack lives in the jar at {@code resourcepacks/ohnomoremeteors_rock/} and overrides
 * {@code assets/ohmymeteors/textures/block/meteoric_rock*} (PNGs plus their .mcmeta/.moremcmeta, because
 * animation metadata is read from the same pack as the PNG). It is a separate pack at the TOP of the
 * stack instead of plain files in this mod's assets, so it always wins over OMM regardless of mod order.
 * It is required (always on), so it does not show as a toggle in the resource pack screen.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RockRetexturePack {

    private static final String FOLDER = "resourcepacks/ohnomoremeteors_rock";

    private RockRetexturePack() {}

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = ModList.get().getModFileById(OhNoMoreMeteors.MODID).getFile().findResource(FOLDER);
        Pack pack = Pack.readMetaAndCreate(
                "builtin/" + OhNoMoreMeteors.MODID + "_rock",
                Component.literal("Oh No, More Meteors! meteoric rock"),
                true,
                id -> new PathPackResources(id, root, true),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack == null) {
            OhNoMoreMeteors.LOGGER.warn("Could not read the built-in meteoric rock pack at {}", FOLDER);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}

package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.util.ForgeSoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events and block sound types for the two meteor materials.
 *
 * The events are registered here; what they actually play is defined by
 * assets/ohnomoremeteors/sounds.json and the .ogg takes under assets/ohnomoremeteors/sounds/block/.
 * Registry ids: ohnomoremeteors:block.&lt;material&gt;.&lt;event&gt; (see CONTRACT.md).
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, OhNoMoreMeteors.MODID);

    // ---- Cindrite (also used by the raw Cindrite block) ----
    public static final RegistryObject<SoundEvent> CINDRITE_BREAK = register("block.cindrite.break");
    public static final RegistryObject<SoundEvent> CINDRITE_STEP = register("block.cindrite.step");
    public static final RegistryObject<SoundEvent> CINDRITE_PLACE = register("block.cindrite.place");
    public static final RegistryObject<SoundEvent> CINDRITE_HIT = register("block.cindrite.hit");
    public static final RegistryObject<SoundEvent> CINDRITE_FALL = register("block.cindrite.fall");

    // ---- Rimerite (also used by the raw Rimerite block) ----
    public static final RegistryObject<SoundEvent> RIMERITE_BREAK = register("block.rimerite.break");
    public static final RegistryObject<SoundEvent> RIMERITE_STEP = register("block.rimerite.step");
    public static final RegistryObject<SoundEvent> RIMERITE_PLACE = register("block.rimerite.place");
    public static final RegistryObject<SoundEvent> RIMERITE_HIT = register("block.rimerite.hit");
    public static final RegistryObject<SoundEvent> RIMERITE_FALL = register("block.rimerite.fall");

    // ---- Oh My Meteors' meteoric rock (applied by mixin omm.MeteoricRockSoundMixin) ----
    public static final RegistryObject<SoundEvent> METEORIC_ROCK_BREAK = register("block.meteoric_rock.break");
    public static final RegistryObject<SoundEvent> METEORIC_ROCK_STEP = register("block.meteoric_rock.step");
    public static final RegistryObject<SoundEvent> METEORIC_ROCK_PLACE = register("block.meteoric_rock.place");
    public static final RegistryObject<SoundEvent> METEORIC_ROCK_HIT = register("block.meteoric_rock.hit");
    public static final RegistryObject<SoundEvent> METEORIC_ROCK_FALL = register("block.meteoric_rock.fall");

    // ---- Veyrite (also used by the raw Veyrite block) ----
    public static final RegistryObject<SoundEvent> VEYRITE_BREAK = register("block.veyrite.break");
    public static final RegistryObject<SoundEvent> VEYRITE_STEP = register("block.veyrite.step");
    public static final RegistryObject<SoundEvent> VEYRITE_PLACE = register("block.veyrite.place");
    public static final RegistryObject<SoundEvent> VEYRITE_HIT = register("block.veyrite.hit");
    public static final RegistryObject<SoundEvent> VEYRITE_FALL = register("block.veyrite.fall");

    // ---- Frostery (block set = a very slight variant of Rimerite's; crackle = the soft cold hum while lit) ----
    public static final RegistryObject<SoundEvent> FROSTERY_CRACKLE = register("block.frostery.crackle");
    public static final RegistryObject<SoundEvent> FROSTERY_BREAK = register("block.frostery.break");
    public static final RegistryObject<SoundEvent> FROSTERY_STEP = register("block.frostery.step");
    public static final RegistryObject<SoundEvent> FROSTERY_PLACE = register("block.frostery.place");
    public static final RegistryObject<SoundEvent> FROSTERY_HIT = register("block.frostery.hit");
    public static final RegistryObject<SoundEvent> FROSTERY_FALL = register("block.frostery.fall");

    // ---- Distant meteor impact broadcast (meteor/DistantImpactBroadcast) ----
    public static final RegistryObject<SoundEvent> DISTANT_METEOR_FAR = register("distant.meteor.far");
    public static final RegistryObject<SoundEvent> DISTANT_METEOR_VERY_FAR = register("distant.meteor.very_far");
    public static final RegistryObject<SoundEvent> DISTANT_CINDRITE_FAR = register("distant.cindrite.far");
    public static final RegistryObject<SoundEvent> DISTANT_CINDRITE_VERY_FAR = register("distant.cindrite.very_far");
    public static final RegistryObject<SoundEvent> DISTANT_RIMERITE_FAR = register("distant.rimerite.far");
    public static final RegistryObject<SoundEvent> DISTANT_RIMERITE_VERY_FAR = register("distant.rimerite.very_far");
    public static final RegistryObject<SoundEvent> DISTANT_VEYRITE_FAR = register("distant.veyrite.far");
    public static final RegistryObject<SoundEvent> DISTANT_VEYRITE_VERY_FAR = register("distant.veyrite.very_far");

    // ---- Sound types handed to BlockBehaviour.Properties#sound ----
    public static final SoundType CINDRITE_TYPE = new ForgeSoundType(1.0F, 1.0F,
            CINDRITE_BREAK, CINDRITE_STEP, CINDRITE_PLACE, CINDRITE_HIT, CINDRITE_FALL);

    public static final SoundType RIMERITE_TYPE = new ForgeSoundType(1.0F, 1.0F,
            RIMERITE_BREAK, RIMERITE_STEP, RIMERITE_PLACE, RIMERITE_HIT, RIMERITE_FALL);

    public static final SoundType VEYRITE_TYPE = new ForgeSoundType(1.0F, 1.0F,
            VEYRITE_BREAK, VEYRITE_STEP, VEYRITE_PLACE, VEYRITE_HIT, VEYRITE_FALL);

    public static final SoundType FROSTERY_TYPE = new ForgeSoundType(1.0F, 1.0F,
            FROSTERY_BREAK, FROSTERY_STEP, FROSTERY_PLACE, FROSTERY_HIT, FROSTERY_FALL);

    public static final SoundType METEORIC_ROCK_TYPE = new ForgeSoundType(1.0F, 1.0F,
            METEORIC_ROCK_BREAK, METEORIC_ROCK_STEP, METEORIC_ROCK_PLACE, METEORIC_ROCK_HIT, METEORIC_ROCK_FALL);

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(OhNoMoreMeteors.MODID, name)));
    }

    private ModSounds() {
    }
}

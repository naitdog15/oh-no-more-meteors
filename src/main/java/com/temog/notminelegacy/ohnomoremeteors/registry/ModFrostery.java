package com.temog.notminelegacy.ohnomoremeteors.registry;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FreezingRecipe;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryBlock;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryBlockEntity;
import com.temog.notminelegacy.ohnomoremeteors.frostery.FrosteryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Everything the Frostery needs: block, block item, block entity type, menu type, the freezing recipe type and its two
 * serializers (ohnomoremeteors:freezing, ohnomoremeteors:rimerite_tempering).
 */
public final class ModFrostery {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, OhNoMoreMeteors.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OhNoMoreMeteors.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OhNoMoreMeteors.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, OhNoMoreMeteors.MODID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, OhNoMoreMeteors.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, OhNoMoreMeteors.MODID);

    /** Stone-hard like a furnace; glows softly (light 7) while working. */
    public static final RegistryObject<Block> FROSTERY = BLOCKS.register("frostery", () ->
            new FrosteryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F)
                    .lightLevel(state -> state.getValue(AbstractFurnaceBlock.LIT) ? 7 : 0)
                    .sound(ModSounds.FROSTERY_TYPE)));

    public static final RegistryObject<Item> FROSTERY_ITEM = ITEMS.register("frostery",
            () -> new BlockItem(FROSTERY.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue") // the data fixer type is null for mod block entities
    public static final RegistryObject<BlockEntityType<FrosteryBlockEntity>> FROSTERY_BLOCK_ENTITY = BLOCK_ENTITIES.register("frostery",
            () -> BlockEntityType.Builder.of(FrosteryBlockEntity::new, FROSTERY.get()).build(null));

    public static final RegistryObject<MenuType<FrosteryMenu>> FROSTERY_MENU = MENUS.register("frostery",
            () -> new MenuType<>(FrosteryMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryObject<RecipeType<FreezingRecipe>> FREEZING = RECIPE_TYPES.register("freezing",
            () -> RecipeType.simple(OhNoMoreMeteors.id("freezing")));

    public static final RegistryObject<RecipeSerializer<FreezingRecipe>> FREEZING_SERIALIZER = SERIALIZERS.register("freezing",
            () -> new FreezingRecipe.Serializer<>(FreezingRecipe::new, 200, false));

    public static final RegistryObject<RecipeSerializer<FreezingRecipe.Repair>> REPAIR_SERIALIZER = SERIALIZERS.register("rimerite_tempering",
            () -> new FreezingRecipe.Serializer<>(FreezingRecipe.Repair::new, 32_767, true));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }

    private ModFrostery() {
    }
}

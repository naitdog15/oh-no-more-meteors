package com.temog.notminelegacy.ohnomoremeteors.client;

import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import com.temog.notminelegacy.ohnomoremeteors.registry.ModArmorMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;

/**
 * Worn armour models with the shapes that stick out on the icons (meteoric flared lames, rimerite ice peaks,
 * cindrite forged shield points). Each extra is a thin plate (or a small faceted block) posed over the armour:
 * plates are 1 texel thick in UV and shrunk to 0.5 px with a negative grow, tilted and turned with the part pose so
 * they hug and flare off the armour. Unpainted texels are transparent, so the painted alpha cuts the plate's outline
 * (points, teeth); faces that are never seen stay unpainted. Every painted face sits at least 0.1 px off every armour
 * face and every other plate face (no coplanar overlap, no z-fighting). Left-side plates are exact mirrors of the right.
 * Textures: the vanilla hat part is emptied, freeing layer_1 x32-63 y0-15; layer_1 x56-63 y16-31 and layer_2
 * x0-63 y0-15, x40-63 y16-31 are unused by the vanilla layout.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MeteorArmorModels {

    private static final Map<ModArmorMaterials, ModelLayerLocation> OUTER = new EnumMap<>(ModArmorMaterials.class);
    private static final Map<ModArmorMaterials, ModelLayerLocation> INNER = new EnumMap<>(ModArmorMaterials.class);
    private static final Map<ModelLayerLocation, HumanoidModel<LivingEntity>> BAKED = new java.util.HashMap<>();

    static {
        for (ModArmorMaterials m : ModArmorMaterials.values()) {
            String id = m.name().toLowerCase(java.util.Locale.ROOT);
            OUTER.put(m, new ModelLayerLocation(new ResourceLocation(OhNoMoreMeteors.MODID, id + "_armor"), "outer"));
            INNER.put(m, new ModelLayerLocation(new ResourceLocation(OhNoMoreMeteors.MODID, id + "_armor"), "inner"));
        }
    }

    private MeteorArmorModels() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (ModArmorMaterials m : ModArmorMaterials.values()) {
            event.registerLayerDefinition(OUTER.get(m), () -> outer(m));
            event.registerLayerDefinition(INNER.get(m), () -> inner(m));
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        BAKED.clear(); // entity models were (re)baked; drop cached armour models
    }

    /** Client extension for an armour item of this material. */
    public static IClientItemExtensions extensions(ModArmorMaterials material) {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                                          HumanoidModel<?> original) {
                ModelLayerLocation loc = slot == EquipmentSlot.LEGS ? INNER.get(material) : OUTER.get(material);
                return BAKED.computeIfAbsent(loc,
                        l -> new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(l)));
            }
        };
    }

    // plate rows: texOffs u, v, UV mirror, box {x, y, z, w, h, d}, grow {x, y, z},
    // pose {x, y, z, xRot, yRot, zRot (degrees)}

    private static LayerDefinition outer(ModArmorMaterials m) {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        // Arm armour 0.15 px bigger than vanilla's 1.0 so its front/back faces are not coplanar with the chestplate's
        // where arm and body overlap (vanilla z-fights there on the inner arm).
        CubeDeformation arm = new CubeDeformation(1.15F);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, arm),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, arm),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        switch (m) {
            case METEORIC -> {
                pair(root, "head", "head", "cheek", 56, 0, false, f(0, 0, -0.5F, 3, 5, 1), f(0, 0, -0.25F), f(-5.4F, -4, -2, -10, 90, 0)); // cheek guard, side plate (flares out)
                pair(root, "right_arm", "left_arm", "cap", 32, 0, false, f(0, 0, -0.5F, 4, 7, 1), f(0, 0, -0.25F), f(-5.1F, -3.4F, 3.5F, -90, 0, 0)); // pauldron cap
                pair(root, "right_arm", "left_arm", "paul1", 42, 0, false, f(0, 0, -0.5F, 6, 3, 1), f(0, 0, -0.25F), f(-4.4F, -3, 3, -10, 90, 0)); // pauldron lame 1, outer side
                pair(root, "right_arm", "left_arm", "paul1f", 40, 6, false, f(0, 0, -0.5F, 4, 3, 1), f(0, 0, -0.25F), f(-4.9F, -3, -3.4F, 0, 0, 0)); // pauldron lame 1, front
                pair(root, "right_arm", "left_arm", "paul1b", 40, 6, true, f(0, 0, -0.5F, 4, 3, 1), f(0, 0, -0.25F), f(-0.9F, -3, 3.4F, 0, 180, 0)); // pauldron lame 1, back
                pair(root, "right_leg", "left_leg", "cufff", 32, 10, false, f(0, -2, -0.5F, 5, 2, 1), f(0, 0, -0.25F), f(-3.15F, 8, -3.4F, 16, 0, 0)); // boot cuff, front petal
                pair(root, "right_leg", "left_leg", "cuffs", 51, 4, false, f(-2, -7, -0.5F, 2, 7, 1), f(0, 0, -0.25F), f(-3.4F, 8, 3.3F, 90, 0, 74)); // boot cuff, outer petal
                pair(root, "right_leg", "left_leg", "cuffb", 32, 10, false, f(0, -2, -0.5F, 5, 2, 1), f(0, 0, -0.25F), f(1.85F, 8, 3.4F, 16, 180, 0)); // boot cuff, back petal
            }
            case CINDRITE -> {
                pair(root, "head", "head", "cheekside", 32, 4, false, f(0, 0, -0.5F, 3, 5, 1), f(0, 0, -0.25F), f(-5.4F, -4, -2, 0, 90, 0)); // cheek plate, side (point at the corner)
                pair(root, "head", "head", "cheekfront", 36, 9, false, f(0, -3, -0.5F, 5, 3, 1), f(0, 0, -0.25F), f(-5.7F, -4, -5.4F, 0, 0, 90)); // cheek plate, front (point at the corner)
                pair(root, "right_arm", "left_arm", "paul1", 48, 0, false, f(0, 0, -0.5F, 6, 3, 1), f(0, 0, -0.25F), f(-4.4F, -3, 3, -10, 90, 0)); // pauldron lame 1, outer side (points)
                pair(root, "right_arm", "left_arm", "paul1f", 48, 11, false, f(0, 0, -0.5F, 4, 3, 1), f(0, 0, -0.25F), f(-4.9F, -3, -3.4F, 0, 0, 0)); // pauldron lame 1, front (point)
                pair(root, "right_arm", "left_arm", "paul1b", 48, 11, false, f(0, 0, -0.5F, 4, 3, 1), f(0, 0, -0.25F), f(-0.9F, -3, 3.4F, 0, 180, 0)); // pauldron lame 1, back (point; same texels, symmetric outline)
                pair(root, "right_leg", "left_leg", "cufff", 47, 7, false, f(0, -3, -0.5F, 5, 3, 1), f(0, 0, -0.25F), f(-3.15F, 9, -3.4F, 12, 0, 0)); // boot cuff, front (shield point)
                pair(root, "right_leg", "left_leg", "cuffs", 32, 0, false, f(0, -3, -0.5F, 7, 3, 1), f(0, 0, -0.25F), f(-3.4F, 9, 3.3F, 12, 90, 0)); // boot cuff, outer (points)
                pair(root, "right_leg", "left_leg", "cuffb", 47, 7, false, f(0, -3, -0.5F, 5, 3, 1), f(0, 0, -0.25F), f(1.85F, 9, 3.4F, 12, 180, 0)); // boot cuff, back (shield point)
            }
            case RIMERITE -> {
                plate(root, "head", "crest", 32, 0, false, f(0, -3, -0.5F, 6, 3, 1), f(0, 0, 0), f(-0.15F, -9.1F, 3, 0, 90, 0)); // ice crest along the helmet top (shard silhouette)
                pair(root, "head", "head", "brow", 56, 3, false, f(0, -5, -0.5F, 3, 5, 1), f(0, 0, -0.25F), f(-5, -7, -5.4F, 0, 0, 90)); // brow icicles (half)
                plate(root, "body", "chest", 32, 4, false, f(0, 0, -0.5F, 3, 4, 1), f(0, 0, -0.25F), f(-1.5F, 9, -3.4F, 0, 0, 0)); // chest ice point hugging the breastplate (pointing down)
                pair(root, "right_arm", "left_arm", "paulice", 46, 0, false, f(0, 0, -0.5F, 6, 3, 1), f(0, 0, -0.25F), f(-4.4F, -3, 3, 0, 90, 0)); // shoulder ice plate with icicles
                pair(root, "right_arm", "left_arm", "icef", 41, 12, false, f(0, 0, -0.5F, 3, 3, 1), f(0, 0, -0.25F), f(-4, -3, -3.4F, 0, 0, 0)); // shoulder ice plate, front icicles
                pair(root, "right_arm", "left_arm", "iceb", 41, 12, true, f(0, 0, -0.5F, 3, 3, 1), f(0, 0, -0.25F), f(-1, -3, 3.4F, 0, 180, 0)); // shoulder ice plate, back icicles
                pair(root, "right_leg", "left_leg", "cufff", 38, 7, false, f(0, -2, -0.5F, 5, 2, 1), f(0, 0, -0.25F), f(-3.15F, 8, -3.4F, 4, 0, 0)); // boot cuff, front (ice peaks)
                pair(root, "right_leg", "left_leg", "cuffs", 48, 8, false, f(-2, -5, -0.5F, 2, 5, 1), f(0, 0, -0.25F), f(-3.4F, 8, 1.6F, 90, 0, 86)); // boot cuff, outer (ice peaks)
                pair(root, "right_leg", "left_leg", "cuffb", 38, 7, false, f(0, -2, -0.5F, 5, 2, 1), f(0, 0, -0.25F), f(1.85F, 8, 3.4F, 4, 180, 0)); // boot cuff, back (ice peaks)
            }
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static LayerDefinition inner(ModArmorMaterials m) {
        MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        switch (m) {
            case METEORIC -> {
                pair(root, "right_leg", "left_leg", "knee", 0, 0, false, f(0, 0, -0.5F, 4, 3, 1), f(0, 0, -0.25F), f(-2.6F, 2.6F, -3.55F, -10, 0, 0)); // knee cop
            }
            case CINDRITE -> {
                pair(root, "right_leg", "left_leg", "knee", 0, 0, false, f(0, 0, -0.5F, 4, 4, 1), f(0, 0, -0.25F), f(-2.4F, 1.6F, -2.9F, -8, 0, 0)); // knee shield
                pair(root, "right_leg", "left_leg", "kneeside", 10, 0, false, f(0, 0, -0.5F, 3, 4, 1), f(0, 0, -0.25F), f(-2.9F, 2.4F, 0.4F, -8, 90, 0)); // knee side plate
            }
            case RIMERITE -> {
                pair(root, "right_leg", "left_leg", "knee", 0, 0, false, f(0, 0, -0.5F, 3, 3, 1), f(0, 0, -0.25F), f(-1.9F, 2.4F, -2.9F, 0, 0, 0)); // knee ice plate pointing down
                pair(root, "right_leg", "left_leg", "hem", 8, 0, false, f(0, 0, -0.5F, 4, 2, 1), f(0, 0, -0.25F), f(-2.9F, 3.6F, 1.6F, 0, 90, 0)); // hem icicles (outer side)
            }
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static float[] f(float... v) {
        return v;
    }

    private static void plate(PartDefinition root, String part, String name, int u, int v, boolean mirror, float[] box,
                              float[] grow, float[] pose) {
        CubeListBuilder cube = CubeListBuilder.create().texOffs(u, v).mirror(mirror).addBox(box[0], box[1], box[2],
                box[3], box[4], box[5], new CubeDeformation(grow[0], grow[1], grow[2]));
        root.getChild(part).addOrReplaceChild("omm_" + name, cube, PartPose.offsetAndRotation(pose[0], pose[1], pose[2],
                (float) Math.toRadians(pose[3]), (float) Math.toRadians(pose[4]), (float) Math.toRadians(pose[5])));
    }

    /** A plate on the right part plus its mirror image on the left part (mirrored about x = 0, UV mirrored). */
    private static void pair(PartDefinition root, String right, String left, String name, int u, int v, boolean mirror,
                             float[] box, float[] grow, float[] pose) {
        boolean same = right.equals(left);
        plate(root, right, same ? name + "_r" : name, u, v, mirror, box, grow, pose);
        float[] mirroredBox = {-(box[0] + box[3]), box[1], box[2], box[3], box[4], box[5]};
        float[] mirroredPose = {-pose[0], pose[1], pose[2], pose[3], -pose[4], -pose[5]};
        plate(root, left, same ? name + "_l" : name, u, v, !mirror, mirroredBox, grow, mirroredPose);
    }
}

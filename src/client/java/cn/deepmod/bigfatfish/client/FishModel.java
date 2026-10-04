package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.BigFatFishMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Original block model inspired by the reference: blue hair, maid ruffles and a whale tail. */
public final class FishModel extends HumanoidModel<FishRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BigFatFishMod.id("big_fat_fish"), "main");
    private final ModelPart tail;
    public FishModel(ModelPart root) { super(root); tail = body.getChild("tail"); }
    private static CubeListBuilder cube(int u, int v, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
    }
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition(); PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", cube(0, 64, -5, -10, -5, 10, 10, 10), PartPose.ZERO);
        head.addOrReplaceChild("hair_back", cube(64, 0, -5.5F, -10.5F, 3, 11, 19, 3), PartPose.ZERO);
        head.addOrReplaceChild("hair_left", cube(64, 0, 4, -8, -2, 3, 17, 5), PartPose.rotation(0, 0, -0.07F));
        head.addOrReplaceChild("hair_right", cube(64, 0, -7, -8, -2, 3, 17, 5), PartPose.rotation(0, 0, 0.07F));
        head.addOrReplaceChild("bangs", cube(64, 0, -5.5F, -10.5F, -5.7F, 11, 4, 1), PartPose.ZERO);
        head.addOrReplaceChild("bang_middle", cube(64, 0, -1.5F, -7, -5.8F, 3, 3, 1), PartPose.rotation(0, 0, -0.15F));
        head.addOrReplaceChild("ear_right", cube(64, 0, -4, -1, -1, 4, 2, 3), PartPose.offsetAndRotation(-5, -4, 0, 0, 0, -0.3F));
        head.addOrReplaceChild("ear_left", cube(64, 0, 0, -1, -1, 4, 2, 3), PartPose.offsetAndRotation(5, -4, 0, 0, 0, 0.3F));
        head.addOrReplaceChild("headband", cube(128, 0, -5.7F, -11.2F, -2, 11.4F, 1, 5), PartPose.ZERO);
        for (int i = 0; i < 7; i++) head.addOrReplaceChild("ruffle_" + i, cube(128, 0, -0.9F, -1, -1.5F, 1.8F, 2, 3),
            PartPose.offsetAndRotation(-5.4F + i * 1.8F, -11.3F + Math.abs(3 - i) * 0.35F, -1, 0, 0, (i - 3) * 0.12F));
        head.addOrReplaceChild("ribbon", cube(128, 64, -1.5F, -1, -1, 3, 2, 2), PartPose.offset(6.5F, -7, -1.5F));
        head.addOrReplaceChild("ahoge", cube(64, 0, -0.5F, -4, 0, 1, 4, 1), PartPose.offsetAndRotation(0, -11, 0, 0, 0, 0.5F));
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", cube(192, 0, -4, 0, -2.5F, 8, 10, 5), PartPose.ZERO);
        body.addOrReplaceChild("bib", cube(0, 96, -3, 0.5F, -3, 6, 7, 1), PartPose.ZERO);
        body.addOrReplaceChild("bow", cube(192, 0, -2.5F, 0, -4, 5, 2, 1), PartPose.ZERO);
        body.addOrReplaceChild("brooch", cube(64, 64, -0.5F, 0.5F, -4.6F, 1, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("skirt", cube(192, 0, -6, 8, -4, 12, 6, 8), PartPose.ZERO);
        body.addOrReplaceChild("hem", cube(128, 0, -6.5F, 13, -4.5F, 13, 1, 9), PartPose.ZERO);
        body.addOrReplaceChild("apron", cube(192, 64, -4, 7.5F, -4.6F, 8, 6, 1), PartPose.ZERO);
        body.addOrReplaceChild("back_bow", cube(128, 0, -4, 7, 3, 8, 3, 2), PartPose.ZERO);
        PartDefinition tail = body.addOrReplaceChild("tail", cube(64, 0, -1.5F, -1.5F, 0, 3, 3, 7), PartPose.offsetAndRotation(0, 10, 3, -0.4F, 0, 0));
        PartDefinition tip = tail.addOrReplaceChild("curve", cube(64, 0, -1.3F, -1.3F, 0, 2.6F, 2.6F, 5), PartPose.offsetAndRotation(0, 0, 6, 0.8F, 0, 0));
        tip.addOrReplaceChild("fluke_right", cube(64, 0, -5, -0.7F, 0, 5, 1.4F, 4), PartPose.offsetAndRotation(0, 0, 4, 0, 0.4F, -0.15F));
        tip.addOrReplaceChild("fluke_left", cube(64, 0, 0, -0.7F, 0, 5, 1.4F, 4), PartPose.offsetAndRotation(0, 0, 4, 0, -0.4F, 0.15F));
        for (boolean left : new boolean[]{false, true}) {
            float sign = left ? 1 : -1;
            PartDefinition arm = root.addOrReplaceChild(left ? "left_arm" : "right_arm", cube(192, 0, -1.5F, -2, -1.5F, 3, 7, 3), PartPose.offset(sign * 5.5F, 2, 0));
            arm.addOrReplaceChild("cuff", cube(128, 0, -1.8F, 4.5F, -1.8F, 3.6F, 1.5F, 3.6F), PartPose.ZERO);
            arm.addOrReplaceChild("hand", cube(0, 0, -1.4F, 6, -1.4F, 2.8F, 3, 2.8F), PartPose.ZERO);
            PartDefinition leg = root.addOrReplaceChild(left ? "left_leg" : "right_leg", cube(128, 0, -1.6F, 0, -1.6F, 3.2F, 10, 3.2F), PartPose.offset(sign * 2, 12, 0));
            leg.addOrReplaceChild("shoe", cube(192, 0, -1.8F, 9.5F, -2.5F, 3.6F, 2.5F, 5), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 256, 128);
    }
    @Override public void setupAnim(FishRenderer.State state) {
        super.setupAnim(state);
        tail.yRot = (float)Math.sin(state.ageInTicks * 0.08F) * 0.2F;
        if (state.sitting) {
            body.y += 4; head.y += 4;
            rightArm.y += 4; leftArm.y += 4;
            rightLeg.xRot = leftLeg.xRot = -1.35F;
            rightLeg.y += 4; leftLeg.y += 4;
            rightLeg.yRot = 0.15F; leftLeg.yRot = -0.15F;
        }
    }
}

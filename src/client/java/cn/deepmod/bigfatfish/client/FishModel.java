package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.BigFatFishMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Original block model inspired by the reference: blue hair, maid ruffles and a whale tail. */
public final class FishModel extends HumanoidModel<FishRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BigFatFishMod.id("big_fat_fish"), "main");
    private final ModelPart tail, skirt, apron, hem, bib, bow, headband;
    public FishModel(ModelPart root) {
        super(root); tail = body.getChild("tail"); skirt=body.getChild("skirt"); apron=body.getChild("apron");
        hem=body.getChild("hem"); bib=body.getChild("bib"); bow=body.getChild("bow"); headband=head.getChild("headband");
    }
    private static CubeListBuilder cube(int u, int v, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d, CubeDeformation.NONE, 0.5F, 0.5F);
    }
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition(); PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", cube(0, 64, -4.5F, -9, -4.5F, 9, 8.5F, 9), PartPose.ZERO);
        head.addOrReplaceChild("chin",cube(0,0,-3.7F,-0.8F,-3.8F,7.4F,0.8F,7.6F),PartPose.ZERO);
        head.addOrReplaceChild("crown",cube(64,0,-4.7F,-9.6F,-4.5F,9.4F,2,9),PartPose.ZERO);
        for(int i=0;i<11;i++) {
            double a=Math.PI*i/10;
            PartDefinition lock=head.addOrReplaceChild("lock"+i,cube(64,0,-0.9F,0,-0.9F,1.8F,5.5F,1.8F),
                PartPose.offsetAndRotation((float)Math.cos(a)*5,-7,(float)Math.sin(a)*4,0,(float)a,(float)Math.cos(a)*-0.13F));
            for(int j=0;j<4;j++) lock=lock.addOrReplaceChild("curl"+j,cube(64,0,-0.9F+j*0.12F,0,-0.9F,1.8F-j*0.24F,5-j*0.6F,1.8F-j*0.2F),
                PartPose.offsetAndRotation(0,4.8F-j*0.5F,0.1F,j%2==0?0.2F:-0.25F,0,j%2==0?0.16F:-0.2F));
        }
        for(int i=0;i<9;i++) head.addOrReplaceChild("bang"+i,cube(64,0,-0.65F,0,-0.4F,1.3F,3+(i%3)*0.55F,0.8F),
            PartPose.offsetAndRotation((i-4)*1.05F,-9.4F,-4.8F,-0.06F,0,(i-4)*-0.035F));
        head.addOrReplaceChild("ear_right", cube(64, 0, -4, -1, -1, 4, 2, 3), PartPose.offsetAndRotation(-5, -4, 0, 0, 0, -0.3F));
        head.addOrReplaceChild("ear_left", cube(64, 0, 0, -1, -1, 4, 2, 3), PartPose.offsetAndRotation(5, -4, 0, 0, 0, 0.3F));
        head.addOrReplaceChild("headband", cube(128, 0, -5.7F, -11.2F, -2, 11.4F, 1, 5), PartPose.ZERO);
        head.addOrReplaceChild("summer_band", cube(128,64,-4.8F,-9.7F,-1.8F,9.6F,0.6F,3.6F),PartPose.ZERO);
        for (int i = 0; i < 7; i++) head.addOrReplaceChild("ruffle_" + i, cube(128, 0, -0.9F, -1, -1.5F, 1.8F, 2, 3),
            PartPose.offsetAndRotation(-5.4F + i * 1.8F, -11.3F + Math.abs(3 - i) * 0.35F, -1, 0, 0, (i - 3) * 0.12F));
        head.addOrReplaceChild("ribbon", cube(128, 64, -1.5F, -1, -1, 3, 2, 2), PartPose.offset(6.5F, -7, -1.5F));
        for(int i=0;i<12;i++) {
            float a=(float)(i*Math.PI*1.7/11);
            head.addOrReplaceChild("ahoge"+i,cube(64,0,-0.25F,-0.45F,-0.25F,0.5F,0.9F,0.5F),PartPose.offsetAndRotation((float)Math.cos(a)*2.5F-1,-11.5F+(float)Math.sin(a)*1.7F,0,0,0,a));
        }
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", cube(0, 0, -4, 0, -2.5F, 8, 10, 5), PartPose.ZERO);
        body.addOrReplaceChild("bodice",cube(192,0,-4.05F,0,-2.55F,8.1F,8,5.1F),PartPose.ZERO);
        body.addOrReplaceChild("bib", cube(0, 96, -3, 0.5F, -3, 6, 7, 1), PartPose.ZERO);
        body.addOrReplaceChild("bow", cube(192, 0, -2.5F, 0, -4, 5, 2, 1), PartPose.ZERO);
        body.addOrReplaceChild("brooch", cube(64, 64, -0.5F, 0.5F, -4.6F, 1, 1, 1), PartPose.ZERO);
        PartDefinition skirt=body.addOrReplaceChild("skirt",CubeListBuilder.create(),PartPose.ZERO);
        PartDefinition hem=body.addOrReplaceChild("hem",CubeListBuilder.create(),PartPose.ZERO);
        PartDefinition summer=body.addOrReplaceChild("summer_skirt",CubeListBuilder.create(),PartPose.ZERO);
        for(int i=0;i<20;i++) {
            float a=(float)(i*Math.PI*2/20),x=(float)Math.sin(a)*4,z=(float)Math.cos(a)*2.7F;
            skirt.addOrReplaceChild("pleat"+i,cube(192,0,-0.85F,0,-0.4F,1.7F,6,0.8F),PartPose.offsetAndRotation(x,8,z,0.3F,a,0));
            hem.addOrReplaceChild("lace"+i,cube(128,0,-0.95F,0,-0.4F,1.9F,0.9F,0.8F),PartPose.offsetAndRotation(x*1.4F,13.5F,z*1.6F,0,a,(i%2==0?0.1F:-0.1F)));
            summer.addOrReplaceChild("pleat"+i,cube(128,i%2==0?0:64,-0.85F,0,-0.3F,1.7F,4,0.6F),PartPose.offsetAndRotation(x,8.5F,z,0.4F,a,0));
        }
        body.addOrReplaceChild("apron", cube(192, 64, -3.5F, 0, -0.2F, 7, 5.8F, 0.4F), PartPose.offsetAndRotation(0,8,-3.1F,-0.32F,0,0));
        body.addOrReplaceChild("summer_top",cube(0,96,-4.1F,0,-2.6F,8.2F,6.5F,5.2F),PartPose.ZERO);
        for(int s:new int[]{-1,1}) body.addOrReplaceChild("summer_collar"+s,cube(128,64,-1,0,-0.2F,2,1.6F,0.4F),PartPose.offsetAndRotation(s*1.1F,0,-2.9F,0,0,s*0.25F));
        for(int i=0;i<3;i++) body.addOrReplaceChild("button"+i,cube(64,64,-0.2F,0,-0.2F,0.4F,0.4F,0.4F),PartPose.offset(0,2.5F+i*1.4F,-3.6F));
        body.addOrReplaceChild("back_bow", cube(128, 0, -4, 7, 3, 8, 3, 2), PartPose.ZERO);
        PartDefinition tail = body.addOrReplaceChild("tail", cube(64, 0, -1.5F, -1.5F, 0, 3, 3, 3.5F), PartPose.offsetAndRotation(0, 10, 3, -0.4F, 0, 0));
        PartDefinition tip=tail;
        for(int i=0;i<5;i++) tip=tip.addOrReplaceChild("curve"+i,cube(64,0,-1.5F+i*0.16F,-1.4F+i*0.13F,0,3-i*0.32F,2.8F-i*0.26F,3.5F),PartPose.offsetAndRotation(0,0,3.1F,0.22F,0.08F,0));
        tip.addOrReplaceChild("fluke_right", cube(64, 0, -5, -0.7F, 0, 5, 1.4F, 4), PartPose.offsetAndRotation(0, 0, 4, 0, 0.4F, -0.15F));
        tip.addOrReplaceChild("fluke_left", cube(64, 0, 0, -0.7F, 0, 5, 1.4F, 4), PartPose.offsetAndRotation(0, 0, 4, 0, -0.4F, 0.15F));
        for(int s:new int[]{-1,1}) tip.addOrReplaceChild("fluke_tip"+s,cube(64,0,-1.2F,-0.5F,0,2.4F,1,3),PartPose.offsetAndRotation(s*5,0,5,0,s*-0.55F,0));
        for (boolean left : new boolean[]{false, true}) {
            float sign = left ? 1 : -1;
            PartDefinition arm = root.addOrReplaceChild(left ? "left_arm" : "right_arm", cube(0, 0, -1.3F, -2, -1.3F, 2.6F, 7, 2.6F), PartPose.offset(sign * 5.5F, 2, 0));
            arm.addOrReplaceChild("sleeve",cube(192,0,-1.5F,-2,-1.5F,3,7,3),PartPose.ZERO);
            arm.addOrReplaceChild("cuff", cube(128, 0, -1.8F, 4.5F, -1.8F, 3.6F, 1.5F, 3.6F), PartPose.ZERO);
            arm.addOrReplaceChild("hand", cube(0, 0, -1.4F, 6, -1.4F, 2.8F, 3, 2.8F), PartPose.ZERO);
            PartDefinition leg = root.addOrReplaceChild(left ? "left_leg" : "right_leg", cube(0, 0, -1.5F, 0, -1.5F, 3, 10, 3), PartPose.offset(sign * 2, 12, 0));
            leg.addOrReplaceChild("stocking",cube(128,0,-1.6F,0,-1.6F,3.2F,10,3.2F),PartPose.ZERO);
            leg.addOrReplaceChild("sock",cube(128,0,-1.6F,7.5F,-1.6F,3.2F,2.5F,3.2F),PartPose.ZERO);
            leg.addOrReplaceChild("shoe", cube(192, 0, -1.8F, 9.5F, -2.5F, 3.6F, 2.5F, 5), PartPose.ZERO);
            leg.addOrReplaceChild("sneaker",cube(128,0,-1.8F,9.5F,-2.6F,3.6F,2.5F,5.2F),PartPose.ZERO);
            leg.addOrReplaceChild("sole",cube(128,64,-1.85F,11.5F,-2.65F,3.7F,0.5F,5.3F),PartPose.ZERO);
            for(int j=0;j<3;j++) leg.addOrReplaceChild("lace"+j,cube(128,64,-1.2F,9.4F,-1.8F+j*0.6F,2.4F,0.15F,0.2F),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 512, 256);
    }
    @Override public void setupAnim(FishRenderer.State state) {
        super.setupAnim(state);
        boolean m=state.skin==0;
        skirt.visible=apron.visible=hem.visible=bib.visible=bow.visible=headband.visible=m;
        body.getChild("summer_skirt").visible=body.getChild("summer_top").visible=!m;
        head.getChild("summer_band").visible=!m;
        for(int s:new int[]{-1,1}) body.getChild("summer_collar"+s).visible=!m;
        for(int i=0;i<7;i++) head.getChild("ruffle_"+i).visible=m;
        body.getChild("bodice").visible=body.getChild("back_bow").visible=body.getChild("brooch").visible=m;
        for(int i=0;i<3;i++) body.getChild("button"+i).visible=m;
        for(ModelPart arm:new ModelPart[]{rightArm,leftArm}) arm.getChild("sleeve").visible=arm.getChild("cuff").visible=m;
        for(ModelPart leg:new ModelPart[]{rightLeg,leftLeg}) {
            leg.getChild("stocking").visible=leg.getChild("shoe").visible=m;
            leg.getChild("sock").visible=leg.getChild("sneaker").visible=leg.getChild("sole").visible=!m;
            for(int i=0;i<3;i++) leg.getChild("lace"+i).visible=!m;
        }
        rightArm.visible=leftArm.visible=true;
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

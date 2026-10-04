package cn.deepmod.bigfatfish.client;

import java.util.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;

/** Smooth surfaces with independent juvenile proportions, outfits, curls and expressive poses. */
public final class FishModel extends HumanoidModel<FishRenderer.State> {
    final ModelPart face;
    final CharacterMesh.Loaded mesh;
    private final ModelPart tail;
    private final List<ModelPart> maid = new ArrayList<>(), summer = new ArrayList<>(), hair = new ArrayList<>();
    public FishModel(boolean juvenile) { this(CharacterMesh.load(juvenile)); }
    private FishModel(CharacterMesh.Loaded mesh) {
        super(mesh.root()); this.mesh=mesh; face = mesh.face(); tail = body.getChild("tail");
        for (String name : List.of("maid_head","summer_head")) (name.startsWith("maid") ? maid : summer).add(head.getChild(name));
        maid.add(body.getChild("maid_body")); summer.add(body.getChild("summer_body"));
        for (ModelPart arm : List.of(leftArm,rightArm)) maid.add(arm.getChild("maid_arm"));
        for (ModelPart leg : List.of(leftLeg,rightLeg)) { maid.add(leg.getChild("maid_leg")); summer.add(leg.getChild("summer_leg")); }
        for (int i = 0; i < 29; i++) hair.add(head.getChild("hair_"+i));
    }
    @Override public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack pose) {
        super.translateToHand(state,arm,pose);
        // Native item placement assumes longer vanilla arms; align the grip to these hands.
        pose.translate((arm==HumanoidArm.RIGHT?0.9F:-0.9F)/16,-1.7F/16,2F/16);
    }
    @Override public void setupAnim(FishRenderer.State state) {
        super.setupAnim(state);
        maid.forEach(p -> p.visible=state.skin==0); summer.forEach(p -> p.visible=state.skin==1);
        float time=state.ageInTicks;
        tail.yRot=0.4F+(float)Math.sin(time*0.07)*0.12F;
        tail.xRot=(float)Math.sin(time*0.045)*0.035F;
        for(int i=0;i<hair.size();i++) {
            hair.get(i).xRot=(float)Math.sin(time*0.055+i*0.32)*0.018F;
            hair.get(i).zRot=(float)Math.sin(time*0.04+i*0.28)*0.018F;
        }
        head.getChild("ahoge").zRot=(float)Math.sin(time*0.09)*0.09F;
        if(state.sitting) {
            float shift=state.isBaby?1.7F:3.8F;
            body.y+=shift;head.y+=shift;rightArm.y+=shift;leftArm.y+=shift;
            rightLeg.xRot=leftLeg.xRot=-1.3F; rightLeg.y+=shift;leftLeg.y+=shift;
            rightLeg.yRot=0.16F;leftLeg.yRot=-0.16F;
        }
        float phase=state.emoteTime/60F, envelope=(float)Math.sin(Math.clamp(phase,0,1)*Math.PI);
        switch(state.emote) {
            case 1 -> { // Wave and cheerful head tilt.
                rightArm.zRot=2.1F*envelope;rightArm.xRot=-0.3F*envelope+(float)Math.sin(time*0.45)*0.2F*envelope;
                head.zRot=0.16F*envelope;tail.yRot+=(float)Math.sin(time*0.3)*0.2F*envelope;
            }
            case 2 -> { // Shyly clasp both hands and rock side to side.
                leftArm.zRot=0.38F*envelope;rightArm.zRot=-0.38F*envelope;
                leftArm.xRot=rightArm.xRot=-0.7F*envelope;head.zRot=(float)Math.sin(time*0.12)*0.18F*envelope;
            }
            case 3 -> { // Stretch, then relax with closed eyes.
                leftArm.zRot=-2.6F*envelope;rightArm.zRot=2.6F*envelope;
                leftArm.xRot=rightArm.xRot=-0.25F*envelope;head.xRot-=0.15F*envelope;
            }
            case 4 -> { // Rice eating: hands close to the mouth, small happy nods.
                rightArm.xRot=-1.5F*envelope;leftArm.xRot=-1.2F*envelope;
                head.xRot+=(float)Math.sin(time*0.3)*0.08F*envelope;tail.yRot+=(float)Math.sin(time*0.25)*0.2F;
            }
            case 5 -> { // Beg with both hands, bobbing shoulders and a tiny hop.
                rightArm.xRot=leftArm.xRot=-1.2F*envelope;rightArm.zRot=-0.25F*envelope;leftArm.zRot=0.25F*envelope;
                float hop=Math.max(0,(float)Math.sin(time*0.22))*0.7F*envelope;
                body.y-=hop;head.y-=hop;leftArm.y-=hop;rightArm.y-=hop;leftLeg.y-=hop;rightLeg.y-=hop;
            }
        }
    }
}

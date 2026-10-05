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
    private final ModelPart leftForearm, rightForearm, leftShin, rightShin;
    private final List<ModelPart> maid = new ArrayList<>(), summer = new ArrayList<>(), hair = new ArrayList<>();
    public FishModel(boolean juvenile) { this(CharacterMesh.load(juvenile)); }
    private FishModel(CharacterMesh.Loaded mesh) {
        super(mesh.root()); this.mesh=mesh; face = mesh.face(); tail = body.getChild("tail");
        leftForearm=leftArm.getChild("forearm"); rightForearm=rightArm.getChild("forearm");
        leftShin=leftLeg.getChild("shin"); rightShin=rightLeg.getChild("shin");
        for (String name : List.of("maid_head","summer_head")) (name.startsWith("maid") ? maid : summer).add(head.getChild(name));
        maid.add(body.getChild("maid_body")); summer.add(body.getChild("summer_body"));
        for (ModelPart arm : List.of(leftArm,rightArm)) { maid.add(arm.getChild("maid_arm")); maid.add(arm.getChild("forearm").getChild("maid_forearm")); summer.add(arm.getChild("summer_arm")); }
        for (ModelPart leg : List.of(leftLeg,rightLeg)) { maid.add(leg.getChild("maid_leg")); maid.add(leg.getChild("shin").getChild("maid_shin")); summer.add(leg.getChild("shin").getChild("summer_shin")); }
        for (int i = 0; i < 29; i++) hair.add(head.getChild("hair_"+i));
    }
    @Override public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack pose) {
        super.translateToHand(state,arm,pose);
        ModelPart forearm=arm==HumanoidArm.RIGHT?rightForearm:leftForearm;
        forearm.translateAndRotate(pose);
        CharacterMesh.translateBentEnd(pose,forearm.xRot,state.isBaby);
        // Cancel the vanilla twelve-unit arm offset and anchor to the imported palm.
        ModelPart hand=forearm.getChild("hand");
        pose.translate((hand.x+(arm==HumanoidArm.RIGHT?1F:-1F))/16F,
            (hand.y+.4F-10F)/16F,(hand.z+2F)/16F);
    }
    @Override public void setupAnim(FishRenderer.State state) {
        super.setupAnim(state);
        leftArm.zRot=-.23F;rightArm.zRot=.23F;
        leftForearm.xRot=rightForearm.xRot=-.08F;
        leftShin.xRot=Math.max(0,-leftLeg.xRot)*.65F;
        rightShin.xRot=Math.max(0,-rightLeg.xRot)*.65F;
        maid.forEach(p -> p.visible=state.skin==0); summer.forEach(p -> p.visible=state.skin==1);
        float time=state.ageInTicks;
        tail.yRot=0.4F+(float)Math.sin(time*0.07)*0.12F+state.tailYaw;
        tail.xRot=(float)Math.sin(time*0.045)*0.035F;
        for(int i=0;i<hair.size();i++) {
            float response=.75F+(i%5)*.05F;
            hair.get(i).xRot=(float)Math.sin(time*0.055+i*0.32)*0.018F+state.hairPitch*response;
            hair.get(i).yRot=state.hairYaw*response;
            hair.get(i).zRot=(float)Math.sin(time*0.04+i*0.28)*0.018F;
        }
        head.getChild("ahoge").zRot=(float)Math.sin(time*0.09)*0.09F;
        if(state.sitting) {
            float bend=1.3F;
            float span=CharacterMesh.jointSpan(state.isBaby)*16F;
            // setupAnim restores the imported shin offset; compensate the thigh
            // rotation and knee arc so either age keeps its soles on the ground.
            float shift=leftShin.y*(1-(float)Math.cos(bend))+span*(1-(float)Math.sin(bend)/bend);
            body.y+=shift;head.y+=shift;rightArm.y+=shift;leftArm.y+=shift;
            rightLeg.xRot=leftLeg.xRot=-bend; rightLeg.y+=shift;leftLeg.y+=shift;
            rightShin.xRot=leftShin.xRot=bend;
            rightLeg.yRot=0.16F;leftLeg.yRot=-0.16F;
        }
        float phase=state.emoteTime/60F, envelope=(float)Math.sin(Math.clamp(phase,0,1)*Math.PI);
        switch(state.emote) {
            case 1 -> { // Wave and cheerful head tilt.
                rightArm.zRot=2.1F*envelope;rightArm.xRot=-0.3F*envelope+(float)Math.sin(time*0.45)*0.2F*envelope;
                head.zRot=0.16F*envelope;tail.yRot+=(float)Math.sin(time*0.3)*0.2F*envelope;
                rightForearm.xRot=-.45F*envelope;
            }
            case 2 -> { // Shyly clasp both hands and rock side to side.
                leftArm.zRot=0.38F*envelope;rightArm.zRot=-0.38F*envelope;
                leftArm.xRot=rightArm.xRot=-0.7F*envelope;head.zRot=(float)Math.sin(time*0.12)*0.18F*envelope;
                leftForearm.xRot=rightForearm.xRot=-.8F*envelope;
            }
            case 3 -> { // Stretch, then relax with closed eyes.
                leftArm.zRot=-2.6F*envelope;rightArm.zRot=2.6F*envelope;
                leftArm.xRot=rightArm.xRot=-0.25F*envelope;head.xRot-=0.15F*envelope;
            }
            case 4 -> { // Rice eating: hands close to the mouth, small happy nods.
                rightArm.xRot=-1.5F*envelope;leftArm.xRot=-1.2F*envelope;
                rightForearm.xRot=-.65F*envelope;leftForearm.xRot=-.75F*envelope;
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

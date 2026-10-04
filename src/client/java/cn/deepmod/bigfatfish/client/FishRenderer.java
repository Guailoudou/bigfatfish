package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public final class FishRenderer extends MobRenderer<BigFatFishEntity, FishRenderer.State, FishModel> {
    private final net.minecraft.client.renderer.item.ItemModelResolver items;
    private final FishModel adult, juvenile;
    public FishRenderer(EntityRendererProvider.Context context) {
        super(context, new FishModel(false), 0.4F);
        adult=model; juvenile=new FishModel(true);
        items = context.getItemModelResolver();
        addLayer(new ItemInHandLayer<>(this));
        addLayer(new RenderLayer<State,FishModel>(this) {
            @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, State state, float yaw, float pitch) {
                var m=getParentModel();
                var type=FishRenderer.this.getRenderType(state, !state.isInvisible, state.isInvisible && !state.isInvisibleToPlayer, state.appearsGlowing());
                if (type==null) return;
                int overlay=LivingEntityRenderer.getOverlayCoords(state,0);
                int color=state.isInvisible ? 0x26FFFFFF : 0xFFFFFFFF;
                m.mesh.bodySurface().submit(pose,collector,type,light,overlay,color);
                pose.pushPose();m.head.translateAndRotate(pose);
                boolean closed = ((int)state.ageInTicks % 93) < 3 || state.emote == 3 || state.emote == 4;
                var texture=BigFatFishMod.id("textures/entity/" + (closed ? "face_closed" : "face") + ".png");
                var faceType=state.isInvisible ? (state.isInvisibleToPlayer ? RenderTypes.outline(texture) : RenderTypes.entityTranslucent(texture)) : RenderTypes.entityCutout(texture);
                m.mesh.faceSurface().submit(pose,collector,faceType,light,overlay,color);
                pose.popPose();
            }
        });
    }
    public static final class State extends HumanoidRenderState { public boolean sitting; public int skin, emote; public float emoteTime; }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        model=state.isBaby?juvenile:adult;
        super.submit(state,pose,collector,camera);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return BigFatFishMod.id("textures/entity/materials.png"); }
    @Override public void extractRenderState(BigFatFishEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, items);
        state.sitting = entity.isInSittingPose();
        state.skin = entity.skin();
        state.emote = entity.emote();state.emoteTime=entity.level().getGameTime()-entity.emoteStart()+partialTick;
        if (entity.isBaby()) { state.rightHandItemState.clear(); state.leftHandItemState.clear(); }
    }
}

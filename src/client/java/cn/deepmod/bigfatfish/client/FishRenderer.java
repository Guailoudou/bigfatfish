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
import net.minecraft.client.renderer.texture.OverlayTexture;
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
                if (state.isInvisible) return;
                var m=getParentModel();
                pose.pushPose();m.head.translateAndRotate(pose);
                boolean closed = ((int)state.ageInTicks % 93) < 3 || state.emote == 3 || state.emote == 4;
                collector.submitModelPart(m.face,pose,RenderTypes.entityCutout(BigFatFishMod.id("textures/entity/" + (closed ? "face_closed" : "face") + ".png")),light,
                    state.hasRedOverlay ? OverlayTexture.pack(0,OverlayTexture.RED_OVERLAY_V) : OverlayTexture.NO_OVERLAY,null);
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

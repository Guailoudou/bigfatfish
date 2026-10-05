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
    // Extraction owns this cache; submitted geometry only receives copied scalar values.
    private final java.util.Map<BigFatFishEntity,FishMotion> motion = new java.util.WeakHashMap<>();
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
                MmdMesh.forSkin(state.skin).submit(pose,collector,m.mmdPose,state,light,overlay);
            }
        });
    }
    public static final class State extends HumanoidRenderState {
        public boolean sitting;
        public int skin, emote;
        public float emoteTime, hairPitch, hairYaw, tailYaw, tailIdle, clothPitch, clothSway;
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        model=state.isBaby?juvenile:adult;
        super.submit(state,pose,collector,camera);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return BigFatFishMod.id("textures/entity/character_atlas.png"); }
    @Override public void extractRenderState(BigFatFishEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, items);
        state.sitting = entity.isInSittingPose();
        FishMotion secondary=motion.computeIfAbsent(entity, ignored -> new FishMotion(entity.getUUID().getLeastSignificantBits()));
        secondary.update(entity.tickCount,entity.getX(),entity.getY(),entity.getZ(),entity.yBodyRot,
            entity.isBaby(),state.sitting);
        state.hairPitch=secondary.hairPitch.sample(partialTick);
        state.hairYaw=secondary.hairYaw.sample(partialTick);
        state.tailYaw=secondary.tailYaw.sample(partialTick);
        state.tailIdle=secondary.tailIdle.sample(partialTick);
        state.clothPitch=secondary.clothPitch.sample(partialTick);
        state.clothSway=secondary.clothSway.sample(partialTick);
        state.skin = entity.skin();
        state.emote = entity.emote();state.emoteTime=entity.level().getGameTime()-entity.emoteStart()+partialTick;
        if (entity.isBaby()) { state.rightHandItemState.clear(); state.leftHandItemState.clear(); }
    }
}

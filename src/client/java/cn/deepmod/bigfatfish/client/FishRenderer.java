package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public final class FishRenderer extends MobRenderer<BigFatFishEntity, FishRenderer.State, FishModel> {
    private final net.minecraft.client.renderer.item.ItemModelResolver items;
    public FishRenderer(EntityRendererProvider.Context context) {
        super(context, new FishModel(context.bakeLayer(FishModel.LAYER)), 0.4F);
        items = context.getItemModelResolver();
        addLayer(new ItemInHandLayer<>(this));
    }
    public static final class State extends HumanoidRenderState { public boolean sitting; }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return BigFatFishMod.id("textures/entity/big_fat_fish.png"); }
    @Override public void extractRenderState(BigFatFishEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTick, items);
        state.sitting = entity.isInSittingPose();
    }
}

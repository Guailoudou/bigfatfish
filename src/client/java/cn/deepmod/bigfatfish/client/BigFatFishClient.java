package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.BigFatFishMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public final class BigFatFishClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(BigFatFishMod.BIG_FAT_FISH, FishRenderer::new);
        MenuScreens.register(BigFatFishMod.FISH_MENU, FishScreen::new);
    }
}

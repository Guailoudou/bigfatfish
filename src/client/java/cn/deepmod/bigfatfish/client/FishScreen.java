package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.FishMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class FishScreen extends AbstractContainerScreen<FishMenu> {
    private final Button[] skins = new Button[2];
    public FishScreen(FishMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 304, 233);
        inventoryLabelY = 139;
    }
    @Override protected void init() {
        super.init();
        for (int i = 0; i < 2; i++) {
            final int skin = i;
            skins[i] = addRenderableWidget(Button.builder(Component.translatable("skin.bigfatfish." + (i == 0 ? "maid" : "summer")),
                button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, skin))
                .bounds(leftPos + 184, topPos + 168 + i * 24, 112, 20).build());
        }
        containerTick();
    }
    @Override public void containerTick() {
        super.containerTick();
        for (int i = 0; i < 2; i++) if (skins[i] != null) skins[i].active = menu.fish() != null && menu.fish().skin() != i;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        super.extractBackground(g, mouseX, mouseY, tick);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF243552);
        g.outline(x, y, imageWidth, imageHeight, 0xFF91B6E5);
        g.fill(x + 178, y + 8, x + 298, y + 158, 0xFF152136);
        if (menu.fish() != null) InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 180, y + 22, x + 296, y + 154, 64, 0.0625F, x + 220, y + 88, menu.fish());
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF152136);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF7191B9);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, 0xFFFFFFFF, false);
        g.text(font, Component.translatable("screen.bigfatfish.skin"), 184, 10, 0xFFB8D5F8, false);
        if (menu.fish() != null) g.text(font, Component.translatable(menu.fish().isBaby() ? "screen.bigfatfish.juvenile" : "screen.bigfatfish.adult"), 184, 151, 0xFFB8D5F8, false);
        g.text(font, Component.translatable("screen.bigfatfish.backpack"), 8, 18, 0xFFB8D5F8, false);
        g.text(font, Component.translatable("screen.bigfatfish.mainhand"), 8, 89, 0xFFFFFFFF, false);
        g.text(font, Component.translatable("screen.bigfatfish.offhand"), 104, 89, 0xFFFFFFFF, false);
        var fish = menu.fish();
        if (fish != null) {
            g.text(font, Component.translatable("screen.bigfatfish.hunger", fish.hunger()), 8, 126, 0xFFFFFFFF, false);
            g.text(font, Component.translatable("activity.bigfatfish." + fish.activity()), 106, 126, 0xFFB8D5F8, false);
        }
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFFFFFFF, false);
    }
}

package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.FishMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FishScreen extends AbstractContainerScreen<FishMenu> {
    public FishScreen(FishMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 233);
        inventoryLabelY = 139;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        super.extractBackground(g, mouseX, mouseY, tick);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF243552);
        g.outline(x, y, imageWidth, imageHeight, 0xFF91B6E5);
        for (var slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF152136);
            g.outline(x + slot.x - 1, y + slot.y - 1, 18, 18, 0xFF7191B9);
        }
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, 0xFFFFFFFF, false);
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

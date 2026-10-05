package cn.deepmod.bigfatfish;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class FishMenu extends AbstractContainerMenu {
    private final BigFatFishEntity fish;
    private final Container hands;
    public FishMenu(int id, Inventory inv, Integer entityId) {
        this(id, inv, inv.player.level().getEntity(entityId) instanceof BigFatFishEntity f ? f : null);
    }
    public FishMenu(int id, Inventory inv, BigFatFishEntity fish) {
        super(BigFatFishMod.FISH_MENU, id);
        this.fish = fish;
        Container bag = fish == null ? new SimpleContainer(27) : fish.backpack;
        hands = fish == null ? new SimpleContainer(2) : new HandContainer(fish);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(bag, row * 9 + col, 8 + col * 18, 30 + row * 18));
        addSlot(new Slot(hands, 0, 26, 103));
        addSlot(new Slot(hands, 1, 116, 103));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 151 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 209));
        if (fish != null && !inv.player.level().isClientSide()) fish.setBackpackOpen(true);
    }
    public BigFatFishEntity fish() { return fish; }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide() || !stillValid(player)) return false;
        if(id==2) { fish.specialInteract(player);return true; }
        if(id<0 || id>1) return false;
        fish.setSkin(id);
        return true;
    }
    @Override public boolean stillValid(Player player) { return fish != null && fish.isAlive() && fish.isOwnedBy(player) && player.distanceToSqr(fish) <= 64; }
    @Override public void removed(Player player) {
        super.removed(player);
        if (fish != null && !player.level().isClientSide()) fish.setBackpackOpen(false);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !stillValid(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 29) {
            if (!moveItemStackTo(stack, 29, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
    /** Native equipment is the source of truth: combat, rendering and saves see exactly these slots. */
    private record HandContainer(BigFatFishEntity fish) implements Container {
        private EquipmentSlot slot(int index) { return index == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND; }
        @Override public int getContainerSize() { return 2; }
        @Override public boolean isEmpty() { return getItem(0).isEmpty() && getItem(1).isEmpty(); }
        @Override public ItemStack getItem(int i) { return fish.getItemBySlot(slot(i)); }
        @Override public ItemStack removeItem(int i, int count) { ItemStack result = getItem(i).split(count); setChanged(); return result; }
        @Override public ItemStack removeItemNoUpdate(int i) { ItemStack result = getItem(i); setItem(i, ItemStack.EMPTY); return result; }
        @Override public void setItem(int i, ItemStack stack) { fish.setItemSlot(slot(i), stack); setChanged(); }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return fish.isAlive() && fish.isOwnedBy(player) && player.distanceToSqr(fish) <= 64; }
        @Override public void clearContent() { setItem(0, ItemStack.EMPTY); setItem(1, ItemStack.EMPTY); }
    }
}

package cn.deepmod.bigfatfish;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;

public final class FishGameTests {
    @GameTest(maxTicks = 100) public void hungerOverridesWorkAndFeedingResumes(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos p = new BlockPos(4, 2, 3);
        h.setBlock(p.below(), Blocks.FARMLAND);
        h.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        var owner = h.makeMockServerPlayerInLevel(); owner.setGameMode(GameType.SURVIVAL);
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 3.5F, 2, 3.5F);
        fish.tame(owner); owner.setPos(fish.position());
        fish.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        fish.saveWithoutId(output); output.putInt("Hunger", 0);
        fish.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.runAfterDelay(25, () -> {
            h.assertBlockPresent(Blocks.WHEAT, p);
            h.assertTrue(fish.activity() == BigFatFishEntity.BEG && fish.isOrderedToSit(), "Hunger suspends work but keeps the order");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BigFatFishMod.COOKED_RICE));
            fish.mobInteract(owner, InteractionHand.MAIN_HAND);
            h.assertTrue(fish.hunger() == 8, "Feeding fills hunger: actual=" + fish.hunger());
            h.assertTrue(owner.getMainHandItem().is(Items.BOWL), "Feeding returns bowl: actual=" + owner.getMainHandItem() + ", creative=" + owner.isCreative());
        });
        h.succeedWhen(() -> {
            h.assertBlockNotPresent(Blocks.WHEAT, p);
            h.assertTrue(fish.backpack.getItems().stream().anyMatch(s -> s.is(Items.WHEAT)), "Work resumes after feeding");
        });
    }
    @GameTest(maxTicks = 100) public void swordAttacksHostiles(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        var owner = h.makeMockServerPlayerInLevel();
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 3.5F, 2, 3.5F);
        fish.tame(owner); owner.setPos(fish.position());
        fish.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        var zombie = h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE, 4.5F, 2, 3.5F);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        h.succeedWhen(() -> h.assertTrue(zombie.getHealth() < 20, "An equipped sword attacks nearby hostile mobs"));
    }
    @GameTest(structure = "bigfatfish:work_area", maxTicks = 120) public void workRadiusAndReturnToSeat(GameTestHelper h) {
        for (int x = 34; x < 70; x++) for (int z = 34; z <= 36; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos inside = new BlockPos(37, 2, 35), outside = new BlockPos(68, 2, 35);
        for (BlockPos p : new BlockPos[]{inside, outside}) {
            h.setBlock(p.below(), Blocks.FARMLAND);
            h.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        }
        var owner = h.makeMockServerPlayerInLevel();
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 35.5F, 2, 35.5F);
        fish.tame(owner); owner.setPos(fish.position());
        fish.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        h.succeedWhen(() -> {
            h.assertBlockNotPresent(Blocks.WHEAT, inside);
            h.assertBlockPresent(Blocks.WHEAT, outside);
            h.assertTrue(fish.isInSittingPose(), "Returns to the ordered seat after finishing");
        });
    }
    @GameTest public void riceHeightAndSingleHarvest(GameTestHelper h) {
        BlockPos p = new BlockPos(3, 2, 3), absolute = h.absolutePos(p);
        h.setBlock(p.below(), Blocks.DIRT); h.setBlock(p, Blocks.WATER);
        h.assertTrue(RiceBlock.canPlant(h.getLevel(), absolute), "Rice accepts shallow water");
        h.setBlock(p, BigFatFishMod.RICE_CROP);
        BigFatFishMod.RICE_CROP.grow(h.getLevel(), absolute, BigFatFishMod.RICE_CROP.defaultBlockState(), 7);
        h.assertBlockProperty(p.above(2), RiceBlock.PART, 2);
        h.assertTrue(h.getLevel().getFluidState(absolute).isSource(), "Root retains source water");
        h.assertTrue(h.getLevel().getFluidState(absolute.above()).isEmpty(), "Shoots stay above water");
        h.destroyBlock(p.above(2));
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(BigFatFishMod.RICE_CROP, p);
            h.assertBlockNotPresent(BigFatFishMod.RICE_CROP, p.above());
            h.assertItemEntityCountIs(BigFatFishMod.PADDY, p, 4, 3);
            h.succeed();
        });
    }
    @GameTest public void riceRejectsDeepWater(GameTestHelper h) {
        BlockPos p = new BlockPos(3, 2, 3);
        h.setBlock(p.below(), Blocks.DIRT); h.setBlock(p, Blocks.WATER); h.setBlock(p.above(), Blocks.WATER);
        h.assertTrue(!RiceBlock.canPlant(h.getLevel(), h.absolutePos(p)), "Rice cannot be planted in deep water");
        h.succeed();
    }
    @GameTest public void ownershipEquipmentAndSave(GameTestHelper h) {
        var owner = h.makeMockServerPlayerInLevel();
        var other = h.makeMockServerPlayerInLevel();
        other.setUUID(java.util.UUID.randomUUID());
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 3, 2, 3);
        fish.tame(owner); fish.setNoAi(true);
        owner.setPos(fish.position()); other.setPos(fish.position());
        fish.backpack.setItem(26, new ItemStack(Items.DIAMOND, 7));
        fish.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        fish.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH, 12));
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        h.assertTrue(fish.isOrderedToSit() && fish.anchor() != null, "Owner establishes a work anchor");
        fish.mobInteract(other, InteractionHand.MAIN_HAND);
        h.assertTrue(fish.isOrderedToSit(), "Other players cannot change orders");
        var menu = new FishMenu(1, owner.getInventory(), fish);
        h.assertTrue(menu.slots.size() == 65, "27 storage + 2 hands + 36 player slots");
        h.assertTrue(menu.stillValid(owner) && !menu.stillValid(other), "Inventory enforces ownership");
        menu.slots.get(28).setByPlayer(new ItemStack(Items.SHIELD));
        h.assertTrue(fish.getOffhandItem().is(Items.SHIELD), "Hand slot updates native equipment");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        fish.saveWithoutId(output);
        var loaded = BigFatFishMod.BIG_FAT_FISH.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(loaded.backpack.getItem(26).getCount() == 7 && loaded.backpack.getItem(26).is(Items.DIAMOND), "Sparse backpack slots survive saves");
        h.assertTrue(loaded.getMainHandItem().is(Items.IRON_HOE) && loaded.getOffhandItem().is(Items.SHIELD), "Equipment survives saves");
        h.assertTrue(loaded.isOwnedBy(owner) && loaded.isOrderedToSit() && loaded.anchor().equals(fish.anchor()), "Owner and work order survive saves");
        menu.removed(owner); h.succeed();
    }
    @GameTest(maxTicks = 240) public void fullBagPreservesCropAndResumes(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos p = new BlockPos(4, 2, 3);
        h.setBlock(p.below(), Blocks.FARMLAND);
        h.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        var owner = h.makeMockServerPlayerInLevel();
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 3.5F, 2, 3.5F);
        fish.tame(owner); owner.setPos(fish.position());
        fish.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        for (int i = 0; i < 27; i++) fish.backpack.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        h.runAfterDelay(35, () -> {
            h.assertBlockPresent(Blocks.WHEAT, p);
            fish.backpack.setItem(0, ItemStack.EMPTY); fish.backpack.setItem(1, ItemStack.EMPTY);
        });
        h.succeedWhen(() -> {
            h.assertBlockNotPresent(Blocks.WHEAT, p);
            h.assertTrue(fish.backpack.getItems().stream().anyMatch(s -> s.is(Items.WHEAT)), "Harvest goes into the backpack");
            h.assertTrue(fish.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(fish.anchor())) <= 1024, "Worker stays in range");
        });
    }
    @GameTest public void millAndFood(GameTestHelper h) {
        BlockPos p = new BlockPos(3, 2, 3);
        h.setBlock(p, BigFatFishMod.MILL);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BigFatFishMod.PADDY, 4));
        h.useBlock(p, player);
        h.assertTrue(player.getMainHandItem().getCount() == 3, "One paddy consumed per click");
        h.assertTrue(player.getInventory().countItem(BigFatFishMod.RICE) == 1, "Mill gives one rice");
        var food = new ItemStack(BigFatFishMod.COOKED_RICE).get(net.minecraft.core.component.DataComponents.FOOD);
        var steak = new ItemStack(Items.COOKED_BEEF).get(net.minecraft.core.component.DataComponents.FOOD);
        h.assertTrue(food.equals(steak), "Rice matches steak hunger and saturation");
        h.succeed();
    }
}

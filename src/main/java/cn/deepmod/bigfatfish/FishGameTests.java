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
    @GameTest public void naturalSpawnProvidesBothAges(GameTestHelper h) {
        int babies=0,adults=0;
        for(int seed=0;seed<32;seed++) {
            var fish=BigFatFishMod.BIG_FAT_FISH.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            fish.setPos(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(3,2,3))));
            // Spread seed bits: consecutive small LCG seeds share the initial nextInt(4) high bits.
            fish.getRandom().setSeed(seed * 0x9E3779B97F4A7C15L);
            fish.finalizeSpawn(h.getLevel(),h.getLevel().getCurrentDifficultyAt(fish.blockPosition()),net.minecraft.world.entity.EntitySpawnReason.NATURAL,null);
            if(fish.isBaby()) babies++;else adults++;
            h.assertTrue(fish.skin()==0 || fish.skin()==1,"Natural entity uses a supported outfit");
        }
        h.assertTrue(babies>0 && adults>0,"Seeded natural spawns provide both juvenile and adult forms: young="+babies+", adults="+adults);h.succeed();
    }
    @GameTest public void juvenileSizeGrowthAndSave(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();owner.setGameMode(GameType.SURVIVAL);
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.tame(owner);fish.setBaby(true);fish.setSkin(1);
        h.assertTrue(fish.getBbHeight()==1 && fish.isBaby(),"Juvenile collision height is exactly one block");
        int before=fish.getAge();
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE));
        fish.mobInteract(owner,InteractionHand.MAIN_HAND);
        h.assertTrue(fish.getAge()==before+net.minecraft.world.entity.AgeableMob.getSpeedUpSecondsWhenFeeding(-before)*20,"Rice accelerates growth by vanilla feeding amount even when full");
        h.assertTrue(owner.getMainHandItem().is(Items.BOWL),"Growth feeding consumes rice and returns bowl");
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());fish.saveWithoutId(output);
        var loaded=BigFatFishMod.BIG_FAT_FISH.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.LOAD);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),output.buildResult()));
        h.assertTrue(loaded.isBaby() && loaded.getAge()==fish.getAge() && loaded.skin()==1 && loaded.getBbHeight()==1,"Age, dimensions and skin survive reload");
        fish.setAge(0);
        h.assertTrue(!fish.isBaby() && Math.abs(fish.getBbHeight()-1.8)<0.001,"Maturity restores adult dimensions");
        fish.setAge(-5);
        h.runAfterDelay(10,()->{h.assertTrue(!fish.isBaby() && fish.getBbHeight()>1.7,"Natural aging crosses the adult boundary");h.succeed();});
    }
    @GameTest public void tamingIsIndependentCatProbability(GameTestHelper h) {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);
        // Compare actual interactions with the first roll from the same seeded vanilla RandomSource.
        for(long seed=0;seed<24;seed++) {
            fish.setTame(false,false);fish.setOwner(null);fish.getRandom().setSeed(seed);
            boolean expected=net.minecraft.util.RandomSource.create(seed).nextInt(3)==0;
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE));
            fish.mobInteract(player,InteractionHand.MAIN_HAND);
            h.assertTrue(fish.isTame()==expected,"Each bowl matches the cat's independent one-in-three roll, seed="+seed);
            h.assertTrue(player.getMainHandItem().is(Items.BOWL),"Failed and successful attempts both consume rice");
        }
        h.succeed();
    }
    @GameTest(maxTicks=160) public void juvenileCannotWorkOrFight(GameTestHelper h) {
        for(int x=0;x<8;x++) for(int z=0;z<8;z++) h.setBlock(x,1,z,Blocks.DIRT);
        BlockPos crop=new BlockPos(4,2,3);h.setBlock(crop.below(),Blocks.FARMLAND);
        h.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7));
        var owner=h.makeMockServerPlayerInLevel();var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3.5F,2,3.5F);
        fish.tame(owner);fish.setBaby(true);owner.setPos(fish.position());
        fish.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_HOE));fish.mobInteract(owner,InteractionHand.MAIN_HAND);
        var zombie=h.spawnWithNoFreeWill(net.minecraft.world.entity.EntityTypes.ZOMBIE,4.5F,2,3.5F);
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        h.runAfterDelay(30,()->{
            h.assertBlockPresent(Blocks.WHEAT,crop);h.assertTrue(fish.backpack.isEmpty(),"Juvenile ignores harvesting tools");
            fish.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        });
        h.runAfterDelay(65,()->{
            h.assertTrue(zombie.getHealth()==20 && fish.getTarget()==null,"Juvenile ignores sword combat");
            h.assertTrue(fish.activity()!=BigFatFishEntity.HARVEST && fish.activity()!=BigFatFishEntity.FIGHT,"Juvenile only follows, rests or begs");
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE));
        });
        h.runAfterDelay(135,()->{
            h.assertTrue(fish.activity()==BigFatFishEntity.BEG && fish.hunger()==20 && fish.emote()==5,"A full juvenile still begs for growth rice held by its owner");h.succeed();
        });
    }
    @GameTest(structure="bigfatfish:work_area",maxTicks=180) public void catFollowDistanceHysteresis(GameTestHelper h) {
        for(int x=30;x<50;x++) for(int z=32;z<39;z++) h.setBlock(x,1,z,Blocks.DIRT);
        var owner=h.makeMockServerPlayerInLevel();var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,35.5F,2,35.5F);
        fish.tame(owner);var initial=fish.position();owner.setPos(initial.add(7,0,0));
        h.runAfterDelay(25,()->{
            h.assertTrue(fish.position().distanceToSqr(initial)<0.1,"Does not pursue an owner seven blocks away");
            owner.setPos(initial.add(11,0,0));
        });
        h.runAfterDelay(65,()->{
            h.assertTrue(fish.getX()>initial.x+0.5,"Starts following beyond ten blocks");
            owner.setPos(fish.position().add(3,0,0));
        });
        h.runAfterDelay(70,()->h.assertTrue(fish.getNavigation().isDone(),"Stops inside five blocks"));
        h.runAfterDelay(75,()->{
            var p=fish.position();owner.setPos(p.add(7,0,0));
            h.runAfterDelay(20,()->{h.assertTrue(fish.position().distanceToSqr(p)<0.1,"Stays stopped until owner again crosses ten blocks");h.succeed();});
        });
    }
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
        h.assertTrue(menu.clickMenuButton(owner, 1) && fish.skin() == 1, "Owner can select summer skin");
        h.assertTrue(!menu.clickMenuButton(other, 0) && fish.skin() == 1, "Other players cannot change skin");
        h.assertTrue(!menu.clickMenuButton(owner, 2) && !menu.clickMenuButton(owner, -1), "Invalid skin buttons rejected");
        menu.slots.get(28).setByPlayer(new ItemStack(Items.SHIELD));
        h.assertTrue(fish.getOffhandItem().is(Items.SHIELD), "Hand slot updates native equipment");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        fish.saveWithoutId(output);
        var loaded = BigFatFishMod.BIG_FAT_FISH.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(loaded.skin() == 1, "Skin survives saves");
        var legacy = output.buildResult(); legacy.remove("Skin");
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), legacy));
        h.assertTrue(loaded.skin() == 0, "Legacy saves default to maid skin");
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

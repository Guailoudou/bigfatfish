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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.*;

public final class FishGameTests {
    private static void restore(GameTestHelper h,BigFatFishEntity fish,String field,int value) {
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());
        fish.saveWithoutId(output);output.putInt(field,value);
        fish.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),output.buildResult()));
    }
    private static void assertReplantedWheat(GameTestHelper h, BlockPos p) {
        h.assertBlockPresent(Blocks.WHEAT, p);
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(p)).getValue(net.minecraft.world.level.block.CropBlock.AGE)<7, "The mature crop is replaced by growing wheat");
    }
    @GameTest public void affectionFeedingSatietyAndSave(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();owner.setGameMode(GameType.SURVIVAL);
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);fish.tame(owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE,3));
        fish.mobInteract(owner,InteractionHand.MAIN_HAND);
        h.assertTrue(fish.affection()>=1 && fish.affection()<=4,"Each accepted rice bowl increases affection randomly by 1-4");
        int affection=fish.affection(),count=owner.getMainHandItem().getCount();
        fish.mobInteract(owner,InteractionHand.MAIN_HAND);
        h.assertTrue(fish.affection()==affection && owner.getMainHandItem().getCount()==count,"Full fish rejects food without consuming rice or raising affection");
        h.assertTrue(fish.satietyTicks()==BigFatFishEntity.SATIETY_TICKS,"Feeding starts one-minute satiety period");
        restore(h,fish,"Satiety",1);fish.aiStep();
        h.assertTrue(fish.satietyTicks()==0,"Satiety expires on server tick");
        fish.setAffection(99);fish.mobInteract(owner,InteractionHand.MAIN_HAND);
        h.assertTrue(fish.affection()==100,"Affection clamps at 100");
        restore(h,fish,"UnfedTicks",1234);
        h.assertTrue(fish.affection()==100 && fish.satietyTicks()>0,"Affection and full state survive entity reload");h.succeed();
    }
    @GameTest public void neglectLowersAffection(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);fish.tame(owner);fish.setAffection(100);
        restore(h,fish,"UnfedTicks",BigFatFishEntity.NEGLECT_TICKS-1);fish.aiStep();
        h.assertTrue(fish.affection()==99,"Twenty minutes without feeding starts affection decay");
        for(int tick=0;tick<6000;tick++) fish.aiStep();
        h.assertTrue(fish.affection()==98,"Neglect continues to reduce affection every five minutes");
        fish.setAffection(0);restore(h,fish,"UnfedTicks",BigFatFishEntity.NEGLECT_TICKS-1);fish.aiStep();
        h.assertTrue(fish.affection()==-1,"Neglect can reduce affection below zero");h.succeed();
    }
    @GameTest public void specialInteractionRequiresOwnerAdultAndCooldown(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();var other=h.makeMockServerPlayerInLevel();other.setUUID(java.util.UUID.randomUUID());
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);fish.tame(owner);owner.setPos(fish.position());other.setPos(fish.position());
        h.assertTrue(!fish.specialInteract(owner),"Affection zero cannot breed");fish.setAffection(100);fish.setBaby(true);
        h.assertTrue(!fish.specialInteract(owner),"Juvenile cannot breed even with full affection");fish.setAge(0);fish.setAffection(100);
        h.assertTrue(!fish.specialInteract(other),"Other player cannot trigger owner-only interaction");
        var menu=new FishMenu(1,owner.getInventory(),fish);
        h.assertTrue(menu.clickMenuButton(owner,2),"Owner can use actual inventory action at full affection");
        var babies=h.getLevel().getEntitiesOfClass(BigFatFishEntity.class,fish.getBoundingBox().inflate(2),f->f!=fish && f.isBaby());
        h.assertTrue(babies.size()==1 && babies.getFirst().isOwnedBy(owner) && babies.getFirst().affection()==0,"Interaction creates exactly one owned juvenile with zero affection");
        h.assertTrue(fish.breedingCooldown()==6000 && fish.getAge()==6000,"Parent uses vanilla five-minute reproduction cooldown");
        h.assertTrue(menu.clickMenuButton(owner,2),"Cooldown interaction is still handled so the owner receives refusal dialogue");
        h.assertTrue(h.getLevel().getEntitiesOfClass(BigFatFishEntity.class,fish.getBoundingBox().inflate(2),f->f!=fish && f.isBaby()).size()==1,"Repeated interaction does not produce a second child during cooldown");
        restore(h,fish,"UnfedTicks",0);h.assertTrue(fish.breedingCooldown()==6000,"Breeding cooldown persists through reload");
        restore(h,fish,"BreedingCooldown",1);fish.aiStep();h.assertTrue(fish.canSpecialInteract(),"Special interaction unlocks when cooldown expires");
        menu.removed(owner);h.succeed();
    }
    @GameTest public void nameTagChangesDialogueSpeaker(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();owner.setGameMode(GameType.SURVIVAL);
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.tame(owner);
        var tag=new ItemStack(Items.NAME_TAG);tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("蓝蓝"));
        owner.setItemInHand(InteractionHand.MAIN_HAND,tag);
        tag.getItem().interactLivingEntity(tag,owner,fish,InteractionHand.MAIN_HAND);
        h.assertTrue(fish.getName().getString().equals("蓝蓝"),"Vanilla name tag renames companion");
        var contents=(net.minecraft.network.chat.contents.TranslatableContents)fish.dialogue("fed",0).getContents();
        h.assertTrue(((net.minecraft.network.chat.Component)contents.getArgs()[0]).getString().equals("蓝蓝"),"Dialogue speaker uses custom name instead of hardcoded species name");
        restore(h,fish,"UnfedTicks",0);h.assertTrue(fish.getName().getString().equals("蓝蓝"),"Custom name survives reload");h.succeed();
    }
    @GameTest public void affectionDialogueStages(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.tame(owner);
        int[] values={-100,-75,-74,-50,-49,-25,-24,-1,0,24,25,49,50,74,75,99,100};int[] stages={-4,-4,-3,-3,-2,-2,-1,-1,0,0,1,1,2,2,3,3,4};
        for(int i=0;i<values.length;i++) {
            fish.setAffection(values[i]);h.assertTrue(fish.affectionStage()==stages[i],"Affection tier at "+values[i]);
            var formatted=(net.minecraft.network.chat.contents.TranslatableContents)fish.dialogue("fed",0).getContents();
            var spoken=(net.minecraft.network.chat.contents.TranslatableContents)((net.minecraft.network.chat.Component)formatted.getArgs()[1]).getContents();
            h.assertTrue(spoken.getKey().equals("dialogue.bigfatfish.stage."+stages[i]+".fed.0"),"Current tier selects its own prewritten dialogue");
        }
        h.succeed();
    }
    @GameTest public void negativeAffectionRunawayDropsInventory(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);fish.tame(owner);
        fish.backpack.setItem(26,new ItemStack(Items.DIAMOND,7));fish.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
        fish.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TORCH,4));fish.setAffection(-99);
        restore(h,fish,"UnfedTicks",BigFatFishEntity.NEGLECT_TICKS-1);
        h.assertTrue(fish.affection()==-99,"Negative affection persists through reload");
        var area=fish.getBoundingBox().inflate(2);fish.aiStep();
        h.assertTrue(fish.affection()==-100 && fish.isRemoved(),"At minus 100 the companion immediately disappears");
        var items=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area);
        h.assertTrue(items.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==7,"Runaway leaves all stored diamonds behind");
        h.assertTrue(items.stream().anyMatch(e->e.getItem().is(Items.IRON_SWORD)) && items.stream().filter(e->e.getItem().is(Items.TORCH)).mapToInt(e->e.getItem().getCount()).sum()==4,"Runaway drops both hands without losing equipment");h.succeed();
    }
    @GameTest public void inappropriateBreedingRequestsReduceAffection(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();var other=h.makeMockServerPlayerInLevel();other.setUUID(java.util.UUID.randomUUID());
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);fish.tame(owner);owner.setPos(fish.position());other.setPos(fish.position());
        fish.setAffection(49);h.assertTrue(!fish.specialInteract(owner) && fish.affection()==44,"Adult below the third tier feels offended and loses five affection");
        fish.setAffection(50);h.assertTrue(!fish.specialInteract(owner) && fish.affection()==50,"Third tier still refuses before 100 but does not penalize");
        fish.setBaby(true);fish.setAffection(100);
        h.assertTrue(!fish.specialInteract(other) && fish.affection()==100,"Non-owner requests do not affect relationship");
        h.assertTrue(!fish.specialInteract(owner) && fish.affection()==90,"Juvenile refuses even at full affection and loses ten points");
        fish.setAffection(-95);fish.specialInteract(owner);h.assertTrue(fish.affection()==-100,"Refusal penalty clamps at minus 100");
        h.assertTrue(fish.isRemoved(),"Repeated inappropriate requests can cause runaway at minus 100");h.succeed();
    }
    @GameTest public void terminalAffectionCannotBeFedOrDropTwice(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();
        for(boolean baby:new boolean[]{false,true}) {
            var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,baby ? 6 : 3,2,3);fish.setNoAi(true);fish.tame(owner);fish.setBaby(baby);
            owner.setPos(fish.position());fish.setAffection(baby ? -90 : -95);
            fish.backpack.setItem(26,new ItemStack(Items.DIAMOND,7));
            fish.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            fish.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TORCH,4));
            var area=fish.getBoundingBox().inflate(1);
            h.assertTrue(!fish.specialInteract(owner) && fish.isRemoved(),"Offensive refusal reaching minus 100 removes the companion before the next tick");
            owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE,3));
            fish.mobInteract(owner,InteractionHand.MAIN_HAND);
            h.assertTrue(fish.affection()==-100 && fish.isRemoved() && fish.satietyTicks()==0,"Same-tick feeding cannot revive a companion that already left");
            h.assertTrue(owner.getMainHandItem().is(BigFatFishMod.COOKED_RICE) && owner.getMainHandItem().getCount()==3,"Rejected terminal interaction does not consume rice");
            fish.specialInteract(owner);fish.aiStep();fish.mobInteract(owner,InteractionHand.MAIN_HAND);
            var items=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area);
            h.assertTrue(items.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==7,"Runaway preserves stored items exactly once across repeated same-tick calls");
            h.assertTrue(items.stream().filter(e->e.getItem().is(Items.IRON_SWORD)).mapToInt(e->e.getItem().getCount()).sum()==1
                && items.stream().filter(e->e.getItem().is(Items.TORCH)).mapToInt(e->e.getItem().getCount()).sum()==4,"Both equipped hands drop exactly once");
        }
        h.succeed();
    }
    @GameTest public void naturalSpawnOnlyJuveniles(GameTestHelper h) {
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
        h.assertTrue(babies==32 && adults==0,"All wild natural spawns must be juvenile: young="+babies+", adults="+adults);h.succeed();
    }
    @GameTest public void juvenileSizeGrowthAndSave(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();owner.setGameMode(GameType.SURVIVAL);
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.tame(owner);fish.setBaby(true);fish.setSkin(1);
        h.assertTrue(fish.isBaby(),"Fish starts juvenile");assertFishDimensions(h,fish,true);
        int before=fish.getAge();
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE));
        fish.mobInteract(owner,InteractionHand.MAIN_HAND);
        h.assertTrue(fish.getAge()==before+net.minecraft.world.entity.AgeableMob.getSpeedUpSecondsWhenFeeding(-before)*20,"Rice accelerates growth by vanilla feeding amount even when full");
        h.assertTrue(owner.getMainHandItem().is(Items.BOWL),"Growth feeding consumes rice and returns bowl");
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,h.getLevel().registryAccess());fish.saveWithoutId(output);
        var loaded=BigFatFishMod.BIG_FAT_FISH.create(h.getLevel(),net.minecraft.world.entity.EntitySpawnReason.LOAD);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),output.buildResult()));
        h.assertTrue(loaded.isBaby() && loaded.getAge()==fish.getAge() && loaded.skin()==1,"Age and skin survive reload");
        assertFishDimensions(h,loaded,true);
        fish.setAge(0);
        h.assertTrue(!fish.isBaby(),"Maturity restores adult state");assertFishDimensions(h,fish,false);
        fish.setAge(-5);
        assertFishDimensions(h,fish,true);
        h.runAfterDelay(10,()->{h.assertTrue(!fish.isBaby(),"Natural aging crosses the adult boundary");assertFishDimensions(h,fish,false);h.succeed();});
    }
    private static void assertFishDimensions(GameTestHelper h,BigFatFishEntity fish,boolean juvenile) {
        float width=juvenile?BigFatFishEntity.JUVENILE_WIDTH:BigFatFishEntity.ADULT_WIDTH;
        float height=juvenile?BigFatFishEntity.JUVENILE_HEIGHT:BigFatFishEntity.ADULT_HEIGHT;
        float eyes=juvenile?BigFatFishEntity.JUVENILE_EYE_HEIGHT:BigFatFishEntity.ADULT_EYE_HEIGHT;
        h.assertTrue(Math.abs(fish.getBbWidth()-width)<.0001 && Math.abs(fish.getBbHeight()-height)<.0001,
            "Age-specific body dimensions match the pixel model");
        h.assertTrue(Math.abs(fish.getEyeHeight()-eyes)<.0001,"Eye height follows the visible pupil line");
        h.assertTrue(Math.abs(fish.getBoundingBox().getYsize()-height)<.0001
            && Math.abs(fish.getBoundingBox().getXsize()-width)<.0001,"Live collision box refreshes after age/load changes");
    }
    @GameTest public void tamingIsIndependentCatProbability(GameTestHelper h) {
        var player=h.makeMockServerPlayerInLevel();player.setGameMode(GameType.SURVIVAL);
        // Compare actual interactions with the first roll from the same seeded vanilla RandomSource.
        for(long seed=0;seed<24;seed++) {
            var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);
            fish.setTame(false,false);fish.setOwner(null);fish.getRandom().setSeed(seed);
            boolean expected=net.minecraft.util.RandomSource.create(seed).nextInt(3)==0;
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BigFatFishMod.COOKED_RICE));
            fish.mobInteract(player,InteractionHand.MAIN_HAND);
            h.assertTrue(fish.isTame()==expected,"Each bowl matches the cat's independent one-in-three roll, seed="+seed);
            h.assertTrue(player.getMainHandItem().is(Items.BOWL),"Failed and successful attempts both consume rice");
            h.assertTrue(fish.affection()==0,"Newly tamed companion starts at zero affection");fish.discard();
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
        fish.backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS));
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
            assertReplantedWheat(h, p);
            h.assertTrue(fish.backpack.getItems().stream().anyMatch(s -> s.is(Items.WHEAT)), "Work resumes after feeding");
        });
    }
    @GameTest(maxTicks = 50) public void satietyPreventsAdultBegging(GameTestHelper h) {
        var owner=h.makeMockServerPlayerInLevel();
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3.5F,2,3.5F);
        fish.tame(owner);owner.setPos(fish.position());
        restore(h,fish,"Hunger",0);restore(h,fish,"Satiety",100);
        h.runAfterDelay(10,()->{
            h.assertTrue(fish.activity()!=BigFatFishEntity.BEG,"A satiated adult must not ask for another meal even with low hunger");
            restore(h,fish,"Satiety",1);
        });
        h.runAfterDelay(25,()->{
            h.assertTrue(fish.activity()==BigFatFishEntity.BEG,"Begging resumes after satiety expires");
            h.succeed();
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
        fish.backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS));
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        h.succeedWhen(() -> {
            assertReplantedWheat(h, inside);
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
    private static BigFatFishEntity farmWorker(GameTestHelper h, float x, float z) {
        var owner = h.makeMockServerPlayerInLevel();
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, x, 2, z);
        fish.tame(owner); owner.setPos(fish.position());
        fish.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        fish.mobInteract(owner, InteractionHand.MAIN_HAND);
        return fish;
    }
    @GameTest public void preferredSeedSelectionConsumesStoredSeed(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 2, 3); h.setBlock(p.below(), Blocks.FARMLAND);
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 3.5F, 2, 3.5F); fish.setNoAi(true);
        fish.backpack.setItem(0, new ItemStack(Items.BEETROOT_SEEDS, 3));
        fish.backpack.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 2));
        h.assertTrue(fish.plantFromBackpack(h.getLevel(), h.absolutePos(p), Blocks.WHEAT), "Stored original seed can be planted");
        h.assertBlockPresent(Blocks.WHEAT, p);
        h.assertTrue(fish.backpack.countItem(Items.WHEAT_SEEDS) == 1 && fish.backpack.countItem(Items.BEETROOT_SEEDS) == 3, "Exactly one preferred seed is consumed even when another crop comes first in the backpack");
        h.succeed();
    }
    @GameTest public void missingOriginalSeedFallsBackToLegalCrop(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 2, 3); h.setBlock(p.below(), Blocks.FARMLAND);
        var fish = h.spawn(BigFatFishMod.BIG_FAT_FISH, 3.5F, 2, 3.5F); fish.setNoAi(true);
        fish.backpack.setItem(0, new ItemStack(BigFatFishMod.PADDY, 2));
        fish.backpack.setItem(1, new ItemStack(Items.CARROT, 2));
        h.assertTrue(fish.plantFromBackpack(h.getLevel(), h.absolutePos(p), Blocks.WHEAT), "Missing wheat seed falls back to another seed that survives on farmland");
        h.assertBlockPresent(Blocks.CARROTS, p);
        h.assertTrue(fish.backpack.countItem(Items.CARROT) == 1 && fish.backpack.countItem(BigFatFishMod.PADDY) == 2, "Fallback consumes one carrot and skips paddy that needs shallow water");
        h.succeed();
    }
    @GameTest(maxTicks = 120) public void harvestingReplantsOriginalCrop(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos p = new BlockPos(4, 2, 3);
        h.setBlock(p.below(), Blocks.FARMLAND); h.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        var fish = farmWorker(h, 3.5F, 3.5F);
        fish.backpack.setItem(0, new ItemStack(Items.BEETROOT_SEEDS, 3));
        fish.backpack.setItem(1, new ItemStack(Items.WHEAT_SEEDS));
        h.succeedWhen(() -> {
            assertReplantedWheat(h, p);
            h.assertTrue(fish.backpack.countItem(Items.BEETROOT_SEEDS) == 3, "Original wheat seeds take priority over the first inventory slot");
            h.assertTrue(fish.backpack.countItem(Items.WHEAT) > 0, "Harvested wheat stays in the backpack");
        });
    }
    @GameTest(maxTicks = 120) public void emptyFarmlandConsumesOneSeed(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos p = new BlockPos(4, 2, 3); h.setBlock(p.below(), Blocks.FARMLAND);
        // Keep the empty test plot from randomly drying before the worker scans.
        h.setBlock(p.below().east(), Blocks.WATER);
        var fish = farmWorker(h, 3.5F, 3.5F);
        fish.backpack.setItem(0, new ItemStack(Items.CARROT, 2));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.CARROTS, p);
            h.assertTrue(fish.backpack.countItem(Items.CARROT) == 1, "Planting empty farmland consumes exactly one stored carrot");
        });
    }
    @GameTest(maxTicks = 120) public void emptyFarmlandWithoutSeedsStaysEmpty(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos p = new BlockPos(4, 2, 3); h.setBlock(p.below(), Blocks.FARMLAND);
        var fish = farmWorker(h, 3.5F, 3.5F);
        fish.backpack.setItem(0, new ItemStack(Items.DIAMOND, 4));
        h.runAfterDelay(105, () -> {
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(p)).isAir(), "A hoe and unrelated items cannot create seeds");
            h.assertTrue(fish.backpack.countItem(Items.DIAMOND) == 4, "Unplantable inventory contents stay intact");
            h.succeed();
        });
    }
    @GameTest(structure = "bigfatfish:work_area", maxTicks = 150) public void plantingRespectsWorkRadius(GameTestHelper h) {
        for (int x = 34; x < 70; x++) for (int z = 34; z <= 36; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos inside = new BlockPos(37, 2, 35), outside = new BlockPos(68, 2, 35);
        h.setBlock(inside.below(), Blocks.FARMLAND); h.setBlock(outside.below(), Blocks.FARMLAND);
        var fish = farmWorker(h, 35.5F, 35.5F);
        fish.backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        h.runAfterDelay(130, () -> {
            h.assertBlockPresent(Blocks.WHEAT, inside);
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(outside)).isAir(), "Empty farmland beyond 32 blocks remains unplanted");
            h.assertTrue(fish.backpack.countItem(Items.WHEAT_SEEDS) == 1, "Only the seed within the work radius is spent");
            h.succeed();
        });
    }
    @GameTest(maxTicks = 120) public void harvestedRiceReplantsInSourceWater(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(x, 1, z, Blocks.DIRT);
        BlockPos p = new BlockPos(4, 2, 3);
        h.setBlock(p, BigFatFishMod.RICE_CROP);
        BigFatFishMod.RICE_CROP.grow(h.getLevel(), h.absolutePos(p), BigFatFishMod.RICE_CROP.defaultBlockState(), 7);
        var fish = farmWorker(h, 3.5F, 3.5F);
        fish.backpack.setItem(0, new ItemStack(BigFatFishMod.PADDY));
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(p)).is(BigFatFishMod.RICE_CROP)
                && h.getLevel().getBlockState(h.absolutePos(p)).getValue(RiceBlock.AGE) < 4, "Harvested rice becomes a young, single-block plant");
            h.assertBlockProperty(p, RiceBlock.PART, 0);
            h.assertTrue(h.getLevel().getFluidState(h.absolutePos(p)).isSource(), "Replanted rice preserves its shallow source water");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(p.above())).isAir() && h.getLevel().getBlockState(h.absolutePos(p.above(2))).isAir(), "Old mature shoots are removed without breaking the new root");
            h.assertTrue(fish.backpack.countItem(BigFatFishMod.PADDY) == 3, "Three harvested paddy plus the stored seed minus one replanted seed remain");
        });
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
        h.assertTrue(!menu.clickMenuButton(owner, 3) && !menu.clickMenuButton(owner, -1), "Invalid skin buttons rejected");
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
            fish.backpack.setItem(0, new ItemStack(Items.WHEAT_SEEDS)); fish.backpack.setItem(1, ItemStack.EMPTY);
        });
        h.succeedWhen(() -> {
            assertReplantedWheat(h, p);
            h.assertTrue(fish.backpack.getItems().stream().anyMatch(s -> s.is(Items.WHEAT)), "Harvest goes into the backpack");
            h.assertTrue(fish.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(fish.anchor())) <= 1024, "Worker stays in range");
        });
    }
    @GameTest public void riverRiceFeatureConditions(GameTestHelper h) {
        var level=h.getLevel();
        var feature=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.FEATURE)
            .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.FEATURE,BigFatFishMod.id("river_rice"))).value();
        var generator=level.getChunkSource().getGenerator();
        var random=net.minecraft.util.RandomSource.create(123);
        BlockPos root=h.absolutePos(new BlockPos(3,2,3));
        for(int attempt=0;attempt<64;attempt++) {
            level.setBlock(root.below(),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(root,Blocks.WATER.defaultBlockState(),2);
            level.setBlock(root.above(),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(root.above(2),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(root.east(),Blocks.DIRT.defaultBlockState(),2);
            h.assertTrue(feature.place(level,generator,random,root),"Registered feature accepts shallow water beside a bank");
            int age=level.getBlockState(root).getValue(RiceBlock.AGE),height=age==7?3:age>=4?2:1;
            for(int part=0;part<3;part++) {
                var state=level.getBlockState(root.above(part));
                h.assertTrue(part<height ? state.is(BigFatFishMod.RICE_CROP) && state.getValue(RiceBlock.PART)==part
                    && state.getValue(RiceBlock.AGE)==age : state.isAir(),"Random age produces matching rice parts");
            }
            h.assertTrue(level.getFluidState(root).isSource(),"Generated root retains source water");
        }
        for(int mode=0;mode<6;mode++) {
            level.setBlock(root,Blocks.WATER.defaultBlockState(),2);
            level.setBlock(root.below(),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(root.above(),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(root.above(2),Blocks.AIR.defaultBlockState(),2);
            for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL) level.setBlock(root.relative(d),Blocks.AIR.defaultBlockState(),2);
            if(mode!=0) level.setBlock(root.east(),Blocks.DIRT.defaultBlockState(),2);
            if(mode==1) level.setBlock(root.below(),Blocks.WATER.defaultBlockState(),2);
            if(mode==2) level.setBlock(root.above(),Blocks.WATER.defaultBlockState(),2);
            if(mode==3) level.setBlock(root.above(2),Blocks.STONE.defaultBlockState(),2);
            if(mode==4) level.setBlock(root,Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,1),2);
            if(mode==5) level.setBlock(root.below(),Blocks.STONE.defaultBlockState(),2);
            var before=level.getBlockState(root);
            h.assertTrue(!feature.place(level,generator,random,root),"Rejects missing bank, deep water, obstruction, flowing water and bad soil: "+mode);
            h.assertTrue(level.getBlockState(root).equals(before),"Rejected placement preserves original blocks");
        }
        h.succeed();
    }
    @GameTest(structure="bigfatfish:work_area") public void riverRicePlacementAndSeed(GameTestHelper h) {
        var level=h.getLevel();
        var placed=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE).getOrThrow(RiverRice.PLACED).value();
        var biomes=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        // The GameTest flat generator strips vegetation; use normal Overworld
        // generation settings when exercising vanilla's biome filter.
        var generator=new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
            new net.minecraft.world.level.biome.FixedBiomeSource(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.RIVER)),
            level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD));
        int step=net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        for(var biome:biomes.listElements().toList()) {
            var settings=biome.value().getGenerationSettings();
            h.assertTrue(settings.hasFeature(placed)==biome.is(net.minecraft.world.level.biome.Biomes.RIVER),"Rice feature is added only to river biomes");
            if(biome.is(net.minecraft.world.level.biome.Biomes.RIVER))
                h.assertTrue(settings.features().get(step).stream().anyMatch(f->f.value()==placed),"Rice runs during vanilla vegetation decoration");
        }
        BlockPos origin=h.absolutePos(new BlockPos(16,4,16));
        java.util.List<net.minecraft.world.level.block.state.BlockState> first=null;
        for(int replay=0;replay<4;replay++) {
            if(replay==0 || replay==3) {
                var biome=biomes.getOrThrow(replay==3 ? net.minecraft.world.level.biome.Biomes.PLAINS : net.minecraft.world.level.biome.Biomes.RIVER);
                var result=net.minecraft.server.commands.FillBiomeCommand.fill(level,origin.offset(-4,-4,-4),origin.offset(20,4,20),biome);
                h.assertTrue(result.right().isEmpty(),"Set fixture biome: "+result);
            }
            for(int x=-1;x<=16;x++) for(int z=-1;z<=16;z++) {
                BlockPos p=origin.offset(x,0,z);
                level.setBlock(p.below(),Blocks.DIRT.defaultBlockState(),2);
                level.setBlock(p,(z%2==0?Blocks.DIRT:Blocks.WATER).defaultBlockState(),2);
                // GameTest adds a barrier roof; remove it over the artificial
                // shoreline so the real surface heightmap can see the water.
                for(int y=p.getY()+1;y<=Math.ceil(h.getBoundsWithPadding().maxY);y++)
                    level.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);
            }
            // Vanilla decoration seeds each feature. Unrelated runtime RNG calls must not matter.
            for(int i=0;i<replay*117;i++) level.getRandom().nextLong();
            var random=new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.XoroshiroRandomSource(0));
            long decoration=random.setDecorationSeed(replay==2?67890:12345,origin.getX(),origin.getZ());
            random.setFeatureSeed(decoration,0,step);
            int surface=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,origin.getX(),origin.getZ()+1);
            h.assertTrue(surface==origin.getY(),"Heightmap finds fixture water: "+surface+" expected "+origin.getY());
            var placer=new net.minecraft.world.level.levelgen.placement.FeaturePlacer(level,generator);
            for(int attempt=0;attempt<32;attempt++) placer.placeWithBiomeCheck(placed,random,origin);
            var snapshot=new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
            int count=0;
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int part=0;part<3;part++) {
                var state=level.getBlockState(origin.offset(x,part,z));snapshot.add(state);
                if(part==0 && state.is(BigFatFishMod.RICE_CROP)) count++;
            }
            if(replay==3) {
                h.assertTrue(count==0,"Native biome filter rejects identical shallow-water terrain in plains");
                continue;
            }
            h.assertTrue(count>0,"Loaded placed feature successfully finds water with the ocean floor heightmap");
            if(replay==0) first=snapshot;
            else h.assertTrue(snapshot.equals(first)==(replay==1),"Same decoration seed reproduces positions and ages; different seed changes them");
        }
        h.succeed();
    }
    @GameTest public void plantingDoesNotLoadChunks(GameTestHelper h) {
        var level=h.getLevel();var position=new net.minecraft.world.level.ChunkPos(100000,100000);
        var fish=h.spawn(BigFatFishMod.BIG_FAT_FISH,3,2,3);fish.setNoAi(true);
        fish.backpack.setItem(0,new ItemStack(BigFatFishMod.PADDY,3));
        BlockPos far=new BlockPos(position.getMinBlockX()+1,64,position.getMinBlockZ()+3);
        h.assertTrue(level.getChunkSource().getChunkNow(position.x(),position.z())==null,"Target starts unloaded");
        h.assertTrue(!fish.plantFromBackpack(level,far,BigFatFishMod.RICE_CROP),"Worker refuses planting in an unloaded chunk");
        h.assertTrue(fish.backpack.getItem(0).getCount()==3,"Failed unloaded planting preserves seeds");
        h.assertTrue(level.getChunkSource().getChunkNow(position.x(),position.z())==null,"Worker did not load the chunk");
        h.succeed();
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

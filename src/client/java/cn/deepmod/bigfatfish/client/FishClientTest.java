package cn.deepmod.bigfatfish.client;

import cn.deepmod.bigfatfish.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

/** Smoke test with real rendering and server-to-client menu synchronization. */
public final class FishClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var connection = world.getConnection();
            int entityId = world.getServer().computeOnServer(server -> {
                var level = connection.getServerLevel();
                var player = connection.getServerPlayer();
                player.teleportTo(0.5, -60, 5.5);
                player.setYRot(180); player.setXRot(4);
                var fish = BigFatFishMod.BIG_FAT_FISH.create(level, EntitySpawnReason.COMMAND);
                fish.setPos(0.5, -60, 1.5); fish.setYRot(0); fish.yBodyRot = 0; fish.yHeadRot = 0;
                fish.tame(player); fish.setNoAi(true);
                fish.setSkin(0);
                fish.backpack.setItem(0, new ItemStack(BigFatFishMod.COOKED_RICE, 12));
                fish.backpack.setItem(1, new ItemStack(BigFatFishMod.PADDY, 24));
                fish.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
                level.addFreshEntity(fish);
                BlockPos rice = new BlockPos(2, -61, 1);
                level.setBlockAndUpdate(rice.below(), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(rice, BigFatFishMod.RICE_CROP.defaultBlockState());
                BigFatFishMod.RICE_CROP.grow(level, rice, BigFatFishMod.RICE_CROP.defaultBlockState(), 7);
                level.setBlockAndUpdate(new BlockPos(-2, -60, 1), BigFatFishMod.MILL.defaultBlockState());
                return fish.getId();
            });
            context.waitTicks(160); // Let the tame advancement toast finish before visual captures.
            connection.waitForClientboundEntityUpdates(BigFatFishMod.BIG_FAT_FISH);
            context.runOnClient(mc -> { mc.player.setYRot(180); mc.player.setXRot(4); });
            connection.waitForChunksRender();
            context.takeScreenshot("bigfatfish-model");
            world.getServer().runOnServer(server -> {
                var player = connection.getServerPlayer();
                var fish = (BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                player.teleportTo(fish.getX(), fish.getY(), fish.getZ() + 2);
                player.openMenu(fish);
            });
            context.waitForScreen(FishScreen.class);
            connection.waitForClientboundPackets();
            context.runOnClient(mc -> {
                var menu = ((FishScreen) mc.gui.screen()).getMenu();
                if (menu.slots.size() != 65 || !menu.slots.get(0).getItem().is(BigFatFishMod.COOKED_RICE)
                    || !menu.slots.get(27).getItem().is(Items.IRON_HOE)) throw new AssertionError("Client slots must match server storage and hands");
            });
            context.takeScreenshot("bigfatfish-backpack");
            context.clickScreenButton("skin.bigfatfish.summer");
            connection.waitForServerboundPackets();
            context.waitTicks(5);
            connection.waitForClientboundEntityUpdates(BigFatFishMod.BIG_FAT_FISH);
            world.getServer().runOnServer(server -> {
                if (((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).skin() != 1)
                    throw new AssertionError("Skin button must update server entity");
            });
            context.runOnClient(mc -> {
                if (((FishScreen)mc.gui.screen()).getMenu().fish().skin() != 1) throw new AssertionError("Skin must synchronize to client");
            });
            context.takeScreenshot("bigfatfish-summer-backpack");
            context.setScreen(() -> null);
            world.getServer().runOnServer(server -> {
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                fish.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);
                connection.getServerPlayer().teleportTo(fish.getX(),fish.getY(),fish.getZ()+2.8);
            });
            context.waitTicks(5);
            context.runOnClient(mc -> {mc.player.setYRot(180);mc.player.setXRot(12);});
            context.takeScreenshot("adult-summer-front");
            world.getServer().runOnServer(server -> {
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);fish.setSkin(0);
            });
            context.waitTicks(5);context.takeScreenshot("adult-maid-front");
            for(int angle : new int[]{90,180}) {
                world.getServer().runOnServer(server -> {
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setYRot(angle);fish.yBodyRot=angle;fish.yHeadRot=angle;
                });
                context.waitTicks(5);context.takeScreenshot("adult-maid-"+angle);
            }
            world.getServer().runOnServer(server -> {
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                fish.setBaby(true);fish.setYRot(0);fish.yBodyRot=0;fish.yHeadRot=0;
                connection.getServerPlayer().openMenu(fish);
            });
            context.waitForScreen(FishScreen.class);context.waitTicks(5);
            connection.waitForClientboundEntityUpdates(BigFatFishMod.BIG_FAT_FISH);
            context.runOnClient(mc -> {
                var fish=((FishScreen)mc.gui.screen()).getMenu().fish();
                if(!fish.isBaby() || fish.getBbHeight()!=1) throw new AssertionError("Juvenile age and one-block dimensions synchronize");
            });
            context.takeScreenshot("juvenile-maid-backpack");
            context.clickScreenButton("skin.bigfatfish.summer");connection.waitForServerboundPackets();context.waitTicks(5);
            context.takeScreenshot("juvenile-summer-backpack");
            world.getServer().runOnServer(server -> ((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).emote(5));
            context.waitTicks(20);
            context.runOnClient(mc -> {
                if(((FishScreen)mc.gui.screen()).getMenu().fish().emote()!=5) throw new AssertionError("Beg animation synchronizes");
            });
            context.takeScreenshot("juvenile-beg");
            for(int emote : new int[]{1,2,3,4}) {
                world.getServer().runOnServer(server -> ((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).emote(emote));
                context.waitTicks(24);
                context.runOnClient(mc -> {
                    var fish=((FishScreen)mc.gui.screen()).getMenu().fish();
                    var renderer=(FishRenderer)mc.getEntityRenderDispatcher().getRenderer(fish);
                    var state=renderer.createRenderState(fish,0);
                    if(state.emote!=emote || state.emoteTime<10 || state.emoteTime>50)
                        throw new AssertionError("Emote must be at a visible animation phase: id="+state.emote+", elapsed="+state.emoteTime);
                    renderer.getModel().setupAnim(state);
                    if((emote==1 || emote==3) && renderer.getModel().rightArm.zRot<0.6F)
                        throw new AssertionError("Wave and stretch must raise the arm");
                    if((emote==2 || emote==4) && renderer.getModel().rightArm.xRot>-0.3F)
                        throw new AssertionError("Shy and eating gestures must move hands forward");
                });
                context.takeScreenshot("juvenile-emote-"+emote);
            }
            // Same world and renderer must return to adult geometry after crossing the age boundary.
            world.getServer().runOnServer(server -> {
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);fish.setAge(0);fish.emote(0);
            });
            context.waitTicks(5);connection.waitForClientboundEntityUpdates(BigFatFishMod.BIG_FAT_FISH);
            context.runOnClient(mc -> {
                var fish=((FishScreen)mc.gui.screen()).getMenu().fish();
                if(fish.isBaby() || Math.abs(fish.getBbHeight()-1.8)>0.001) throw new AssertionError("Maturity synchronizes adult geometry and dimensions");
            });
            context.takeScreenshot("adult-after-growth");context.setScreen(() -> null);
        }
    }
}

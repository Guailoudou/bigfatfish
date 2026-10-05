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
        assertJointSkinning();
        var replies=new java.util.ArrayList<net.minecraft.network.chat.Component>();
        context.runOnClient(mc->net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message,overlay)->replies.add(message)));
        var world=context.worldBuilder().create();
        var save=world.getWorldSave().getSaveDirectory().toAbsolutePath().normalize();
        var saves=context.computeOnClient(mc->mc.getLevelSource().getBaseDir().toAbsolutePath().normalize());
        recordTestWorld(save,saves);
        var testServer=world.getServer().computeOnServer(server->server);
        try {
            var connection = world.getConnection();
            int entityId = world.getServer().computeOnServer(server -> {
                var level = connection.getServerLevel();
                // Compare textures under daylight rather than the world's initial dawn light.
                System.out.println("BIGFATFISH_CAPTURE_TIME before="+level.getOverworldClockTime());
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                System.out.println("BIGFATFISH_CAPTURE_TIME after="+level.getOverworldClockTime());
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
            assertVisible(context, "adult-summer-front", false);
            world.getServer().runOnServer(server -> {
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);fish.setSkin(0);
            });
            context.waitTicks(5);assertVisible(context, "adult-maid-front", false);
            for(int angle : new int[]{90,180}) {
                world.getServer().runOnServer(server -> {
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setYRot(angle);fish.yBodyRot=angle;fish.yHeadRot=angle;
                    fish.yRotO=angle;fish.yBodyRotO=angle;fish.yHeadRotO=angle;
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
            assertVisible(context, "juvenile-maid-backpack", true);
            context.clickScreenButton("skin.bigfatfish.summer");connection.waitForServerboundPackets();context.waitTicks(5);
            assertVisible(context, "juvenile-summer-backpack", true);
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
                    if(emote==4 && renderer.getModel().rightArm.getChild("forearm").xRot>-.3F)
                        throw new AssertionError("Eating must bend the elbow rather than move a rigid whole arm");
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
            world.getServer().runOnServer(server->{
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                fish.setAffection(40);fish.setCustomName(net.minecraft.network.chat.Component.literal("蓝蓝"));
                connection.getServerPlayer().openMenu(fish);
            });
            context.waitForScreen(FishScreen.class);context.waitTicks(5);
            context.clickScreenButton("screen.bigfatfish.special");connection.waitForServerboundPackets();context.waitTicks(5);
            assertReply(context,replies,"breed_reject_affection");
            context.runOnClient(mc->{if(((FishScreen)mc.gui.screen()).getMenu().fish().affection()!=35) throw new AssertionError("Offended low-affection adult loses five points");});
            world.getServer().runOnServer(server->{
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);fish.setAffection(100);fish.setBaby(true);
            });
            context.waitTicks(5);context.clickScreenButton("screen.bigfatfish.special");connection.waitForServerboundPackets();context.waitTicks(5);
            assertReply(context,replies,"breed_reject_young");
            context.runOnClient(mc->{if(((FishScreen)mc.gui.screen()).getMenu().fish().affection()!=90) throw new AssertionError("Juvenile refusal loses ten points");});
            world.getServer().runOnServer(server->{var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);fish.setAge(0);fish.setAffection(100);});context.waitTicks(5);
            context.runOnClient(mc->{
                var fish=((FishScreen)mc.gui.screen()).getMenu().fish();
                if(fish.affection()!=100 || !fish.getName().getString().equals("蓝蓝") || !fish.canSpecialInteract())
                    throw new AssertionError("Affection, name and special interaction eligibility must synchronize");
                for(int tier=0;tier<5;tier++) if(!net.minecraft.locale.Language.getInstance().has("dialogue.bigfatfish.stage."+tier+".fed.0"))
                    throw new AssertionError("Missing localized affection dialogue tier "+tier);
            });
            context.takeScreenshot("affection-ready");context.clickScreenButton("screen.bigfatfish.special");
            connection.waitForServerboundPackets();context.waitTicks(5);
            world.getServer().runOnServer(server->{
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                var babies=connection.getServerLevel().getEntitiesOfClass(BigFatFishEntity.class,fish.getBoundingBox().inflate(2),f->f!=fish && f.isBaby());
                if(babies.size()!=1 || !babies.getFirst().isOwnedBy(connection.getServerPlayer()) || fish.breedingCooldown()<=0)
                    throw new AssertionError("Inventory action must create one owned child and start reproduction cooldown");
                babies.getFirst().discard();
            });
            context.takeScreenshot("affection-cooldown");context.setScreen(()->null);
            world.getServer().runOnServer(server->connection.getServerPlayer().openMenu((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)));
            context.waitForScreen(FishScreen.class);context.waitTicks(3);context.clickScreenButton("screen.bigfatfish.special");
            connection.waitForServerboundPackets();context.waitTicks(5);assertReply(context,replies,"breed_reject_cooldown");context.setScreen(()->null);
            world.getServer().runOnServer(server->((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).setAffection(0));
            context.waitTicks(30); // Let emitted hearts fade before inspecting model details.
            // Large, unobstructed turnarounds for judging silhouette, facial shape and clothing.
            world.getServer().runOnServer(server->connection.getServerPlayer().setGameMode(net.minecraft.world.level.GameType.SPECTATOR));
            context.runOnClient(mc->{mc.getWindow().setWindowed(1024,1024);mc.options.fov().set(38);if(!mc.gui.hud.isHidden()) mc.gui.hud.toggle();});
            for(boolean baby : new boolean[]{false,true}) for(int skin : new int[]{0,1}) for(int angle : new int[]{0,90,180}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(skin);fish.emote(0);
                    fish.setYRot(angle);fish.yBodyRot=angle;fish.yHeadRot=angle;
                    fish.yRotO=angle;fish.yBodyRotO=angle;fish.yHeadRotO=angle;
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?.85:0),fish.getZ()+(baby?2.4:3.3));
                });
                context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(baby?8:12);});
                if(context.computeOnClient(mc->mc.level.getEntity(entityId).tickCount%93<6)) context.waitTicks(6);
                setPortraitRotation(context,entityId,angle);
                if(!baby && skin==1 && angle==90) context.runOnClient(mc->{
                    var camera=mc.gameRenderer.mainCamera();
                    var fish=mc.level.getEntity(entityId);
                    System.out.println("BIGFATFISH_SIDE_CAMERA position="+camera.position()+" fov="+camera.getFov()+" pitch="+camera.xRot()+" fish="+fish.position()+" scale="+((BigFatFishEntity)fish).getScale());
                    System.out.println("BIGFATFISH_SIDE_PROJECTION "+camera.getViewRotationProjectionMatrix(new org.joml.Matrix4f()));
                });
                context.takeScreenshot("study-"+(baby?"juvenile":"adult")+"-"+skin+"-"+angle);
            }
            for(boolean baby : new boolean[]{false,true}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(0);fish.setInSittingPose(true);
                    fish.setYRot(90);fish.yBodyRot=fish.yHeadRot=90;
                    fish.yRotO=fish.yBodyRotO=fish.yHeadRotO=90;
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?.85:0),fish.getZ()+(baby?2.4:3.3));
                });
                context.waitTicks(6);context.runOnClient(mc->{
                    mc.player.setYRot(180);mc.player.setXRot(baby?8:12);
                    var fish=(BigFatFishEntity)mc.level.getEntity(entityId);
                    var renderer=(FishRenderer)mc.getEntityRenderDispatcher().getRenderer(fish);
                    var state=renderer.createRenderState(fish,0);renderer.getModel().setupAnim(state);
                    if(!state.sitting || renderer.getModel().leftLeg.getChild("shin").xRot<1)
                        throw new AssertionError("Sitting must synchronize and bend the knees");
                });
                setPortraitRotation(context,entityId,90);
                context.takeScreenshot("study-sitting-"+(baby?"juvenile":"adult"));
            }
            for(boolean baby : new boolean[]{false,true}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setInSittingPose(false);fish.emote(4);
                    fish.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL));
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?.85:0),fish.getZ()+(baby?2.4:3.3));
                });
                context.waitTicks(24);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(baby?8:12);});
                setPortraitRotation(context,entityId,0);
                context.takeScreenshot("study-eating-"+(baby?"juvenile":"adult"));
            }
            world.getServer().runOnServer(server->{
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                fish.setBaby(false);fish.setSkin(0);fish.emote(0);
                connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()+.10,fish.getZ()+1.55);
            });
            context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
            setPortraitRotation(context,entityId,45);
            context.takeScreenshot("study-adult-face-three-quarter");
            for(boolean baby : new boolean[]{false,true}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(0);fish.emote(0);
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?.82:.13),fish.getZ()+1.10);
                });
                context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
                for(int angle : new int[]{0,45,90}) {
                    setPortraitRotation(context,entityId,angle);
                    context.runOnClient(mc->mc.level.getEntity(entityId).tickCount=20);
                    context.takeScreenshot("head-detail-"+(baby?"juvenile":"adult")+"-"+angle);
                }
                world.getServer().runOnServer(server->((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).emote(3));
                context.waitTicks(3);setPortraitRotation(context,entityId,0);
                context.takeScreenshot("head-detail-"+(baby?"juvenile":"adult")+"-closed");
            }
            for(boolean baby : new boolean[]{false,true}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(1);fish.emote(0);
                    fish.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);
                    connection.getServerPlayer().teleportTo(fish.getX()+(baby?.20:.30),fish.getY()-(baby?1.30:.85),fish.getZ()+(baby?.75:1.10));
                });
                context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
                setPortraitRotation(context,entityId,0);
                context.takeScreenshot("study-hands-"+(baby?"juvenile":"adult"));
            }
            for(int skin : new int[]{0,1}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(false);fish.setSkin(skin);fish.emote(0);
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-.60,fish.getZ()+1.40);
                });
                context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(8);});
                setPortraitRotation(context,entityId,0);
                context.takeScreenshot("study-outfit-adult-"+skin);
            }
            for(boolean baby : new boolean[]{false,true}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(1);fish.emote(0);
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?.82:.13),fish.getZ()+1.10);
                });
                context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
                for(int angle : new int[]{0,45,90}) {
                    setPortraitRotation(context,entityId,angle);
                    context.runOnClient(mc->mc.level.getEntity(entityId).tickCount=20);
                    context.takeScreenshot("head-detail-summer-"+(baby?"juvenile":"adult")+"-"+angle);
                }
            }
            // The juvenile tail crosses the near camera at 90 degrees;
            // capture the opposite profile so ears and jaw remain inspectable.
            setPortraitRotation(context,entityId,270);
            context.runOnClient(mc->mc.level.getEntity(entityId).tickCount=20);
            context.takeScreenshot("head-detail-summer-juvenile-270");
            for(boolean baby : new boolean[]{false,true}) {
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(1);fish.emote(0);
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?.82:.13),fish.getZ()+1.10);
                });
                context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
                setPortraitRotation(context,entityId,180);
                context.runOnClient(mc->mc.level.getEntity(entityId).tickCount=20);
                context.takeScreenshot("head-detail-back-"+(baby?"juvenile":"adult"));
            }
            world.getServer().runOnServer(server->{
                var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                fish.setBaby(false);fish.setSkin(0);fish.emote(0);
                connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-.13,fish.getZ()+1.10);
            });
            context.waitTicks(6);context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
            setPortraitRotation(context,entityId,270);
            context.runOnClient(mc->mc.level.getEntity(entityId).tickCount=20);
            context.takeScreenshot("head-detail-adult-270");
            world.getServer().runOnServer(server->((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).setSkin(1));
            context.waitTicks(6);
            setPortraitRotation(context,entityId,180);
            for (int time : new int[]{20,100}) {
                context.runOnClient(mc->mc.level.getEntity(entityId).tickCount=time);
                context.takeScreenshot("hair-root-motion-"+time);
            }
            for(boolean baby : new boolean[]{false,true}) {
                for(int skin : new int[]{0,1}) {
                    world.getServer().runOnServer(server->{
                        var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                        fish.setBaby(baby);fish.setSkin(skin);fish.setInSittingPose(true);fish.emote(0);
                        connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?1.25:.95),fish.getZ()+(baby?1.3:1.7));
                    });
                    context.waitTicks(6);
                    context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
                    setPortraitRotation(context,entityId,270);
                    context.takeScreenshot("joint-knee-"+(baby?"juvenile":"adult")+"-"+skin);
                }
                world.getServer().runOnServer(server->{
                    var fish=(BigFatFishEntity)connection.getServerLevel().getEntity(entityId);
                    fish.setBaby(baby);fish.setSkin(1);fish.setInSittingPose(false);fish.emote(2);
                    connection.getServerPlayer().teleportTo(fish.getX(),fish.getY()-(baby?1.0:.55),fish.getZ()+(baby?1.3:1.7));
                });
                context.waitTicks(30);
                context.runOnClient(mc->{mc.player.setYRot(180);mc.player.setXRot(0);});
                for(int angle : new int[]{270,315}) {
                    setPortraitRotation(context,entityId,angle);
                    context.takeScreenshot("joint-elbow-"+(baby?"juvenile":"adult")+"-"+angle);
                }
            }
            context.runOnClient(mc->{mc.options.fov().set(70);if(mc.gui.hud.isHidden()) mc.gui.hud.toggle();mc.getWindow().setWindowed(854,480);});
            world.getServer().runOnServer(server->((BigFatFishEntity)connection.getServerLevel().getEntity(entityId)).setAffection(-100));
            context.waitTicks(5);assertReply(context,replies,"runaway");
            context.runOnClient(mc->{if(mc.level.getEntity(entityId)!=null) throw new AssertionError("Runaway must remove the client entity");});
        } finally {
            closeTestWorld(context,world,testServer);
        }
    }
    private static void setPortraitRotation(ClientGameTestContext context,int entityId,int angle) {
        context.runOnClient(mc->{
            var fish=(BigFatFishEntity)mc.level.getEntity(entityId);
            fish.setYRot(angle);fish.yRotO=angle;
            fish.yBodyRot=fish.yBodyRotO=fish.yHeadRot=fish.yHeadRotO=angle;
        });
    }
    private static void assertReply(ClientGameTestContext context,java.util.List<net.minecraft.network.chat.Component> replies,String event) {
        context.runOnClient(mc->{
            boolean found=replies.stream().anyMatch(message->{
                if(!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents outer)
                    || !outer.getKey().equals("dialogue.bigfatfish.format")) return false;
                var name=outer.getArgs()[0];String speaker=name instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(name);
                return speaker.equals("蓝蓝") && outer.getArgs()[1] instanceof net.minecraft.network.chat.Component line
                    && line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents spoken && spoken.getKey().contains("."+event+".");
            });
            if(!found) throw new AssertionError("Owner must receive named dialogue for "+event);
        });
    }
    private static void closeTestWorld(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world,
            net.minecraft.server.MinecraftServer server) {
        // 26.3 halt() uses executeBlocking, which can deadlock on Fabric's tick
        // barrier when called from the render thread. Stop on the server thread.
        context.runOnClient(mc->{
            try {
                var field=net.minecraft.client.Minecraft.class.getDeclaredField("singleplayerServer");
                field.setAccessible(true);
                if(field.get(mc)!=server) throw new AssertionError("Unexpected client test server");
                field.set(mc,null);
            } catch(ReflectiveOperationException e) { throw new AssertionError("Cannot detach the 26.3 client test server",e); }
        });
        world.getServer().runOnServer(s->s.halt(false));
        context.waitFor(mc->server.isStopped() && !net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl.isServerRunning,
            net.minecraft.SharedConstants.TICKS_PER_MINUTE);
        if(context.computeOnClient(mc->mc.level!=null)) world.close();
    }
    private static void recordTestWorld(java.nio.file.Path save,java.nio.file.Path saves) {
        if(!save.getParent().equals(saves)) throw new AssertionError("Refusing to record a world outside the test saves directory: "+save);
        try {
            // Gradle removes only these recorded worlds after the client process
            // exits, so no remaining server or storage handle can hold the save.
            java.nio.file.Files.writeString(saves.getParent().resolve("worlds-to-delete.txt"),save.getFileName()+"\n",
                java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
        } catch(java.io.IOException e) { throw new AssertionError("Cannot record client test world for cleanup",e); }
    }
    /** The root must stay welded, and translated clothing must follow the same bend as skin. */
    private static void assertJointSkinning() {
        for(float angle : new float[]{-1.6F,-.8F,-1e-7F,0F,1e-7F,1.3F}) {
            float c=(float)Math.cos(angle), s=(float)Math.sin(angle);
            float span=CharacterMesh.jointSpan(false);
            var bend=new CharacterMesh.JointBend(angle,span,0,0);
            for(float z : new float[]{-.04F,.04F}) {
                float[] root={.025F,0,z,0,0,1};
                bend.deform(root);
                assertJointNear(root[1],0,"joint root Y");
                assertJointNear(root[2],z,"joint root Z");
                float[] tip={.025F,.2F,z,0,0,1};
                bend.deform(tip);
                // The held-item frame must agree with the deformed hand,
                // including the arc's distal translation.
                var pose=new com.mojang.blaze3d.vertex.PoseStack();
                pose.mulPose(new org.joml.Matrix4f().rotationX(angle));
                CharacterMesh.translateBentEnd(pose,angle,false);
                var held=new org.joml.Vector3f(.025F,.2F,z).mulPosition(pose.last().pose());
                assertJointNear(tip[1],held.y,"hand/foot rigid Y with arc offset");
                assertJointNear(tip[2],held.z,"hand/foot rigid Z with arc offset");
                float[] shoe={.025F,.02F,z,0,0,1};
                bend.asRigid().deform(shoe);
                var shoeFrame=new org.joml.Vector3f(.025F,.02F,z).mulPosition(pose.last().pose());
                assertJointNear(shoe[1],shoeFrame.y,"shoe above ankle remains rigid Y");
                assertJointNear(shoe[2],shoeFrame.z,"shoe above ankle remains rigid Z");
                float[] further={.025F,.3F,z,0,0,1};bend.deform(further);
                assertJointNear(further[1]-tip[1],.1F*c,"distal rigid tangent Y");
                assertJointNear(further[2]-tip[2],.1F*s,"distal rigid tangent Z");
            }
            var offset=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
            offset.y=-3F;offset.z=.2F;
            var clothing=bend.offset(offset);
            for(float y : new float[]{-.02F,0F,.02F,.04F,1F/16F,.2F}) {
                float[] skin={.025F,y,.035F,0,.6F,.8F};
                float[] sleeve={skin[0],skin[1]-offset.y/16F,skin[2]-offset.z/16F,0,.6F,.8F};
                bend.deform(skin);clothing.deform(sleeve);
                assertJointNear(sleeve[1]+offset.y/16F,skin[1],"translated garment Y");
                assertJointNear(sleeve[2]+offset.z/16F,skin[2],"translated garment Z");
                assertJointNear(sleeve[4],skin[4],"translated garment normal Y");
                assertJointNear(sleeve[5],skin[5],"translated garment normal Z");
                assertJointNear(skin[3]*skin[3]+skin[4]*skin[4]+skin[5]*skin[5],1F,"unit skinned normal");
            }
        }
        for(boolean juvenile : new boolean[]{false,true}) {
            var mesh=CharacterMesh.load(juvenile);
            var elbows=java.util.Set.of(mesh.root().getChild("left_arm").getChild("forearm"),
                mesh.root().getChild("right_arm").getChild("forearm"));
            assertMeshJointDeterminants(mesh.bodySurface(),elbows);
        }
    }
    private static void assertMeshJointDeterminants(CharacterMesh.Surface surface,
            java.util.Set<net.minecraft.client.model.geom.ModelPart> elbows) {
        if(surface.jointSpan()>0) {
            assertJointDescendants(surface,new CharacterMesh.JointBend(elbows.contains(surface.bone()) ? -.8F : 1.3F,
                surface.jointSpan(),0,0));
            return;
        }
        for(var child : surface.children()) assertMeshJointDeterminants(child,elbows);
    }
    private static void assertJointDescendants(CharacterMesh.Surface surface,CharacterMesh.JointBend bend) {
        if(surface.rigid()) bend=bend.asRigid();
        float[] vertices=surface.vertices();
        for(int i=0;i<vertices.length;i+=8) {
            float determinant=bend.determinant(vertices[i+1],vertices[i+2]);
            if(!Float.isFinite(determinant) || determinant<=.05F)
                throw new AssertionError("Joint surface folds inside bend: determinant="+determinant);
        }
        for(var child : surface.children()) assertJointDescendants(child,bend.offset(child.bone()));
    }
    private static void assertJointNear(float actual,float expected,String label) {
        if(!Float.isFinite(actual) || Math.abs(actual-expected)>1e-5F)
            throw new AssertionError(label+": "+actual+" != "+expected);
    }
    /** Check actual rendered hair pixels, so an empty mesh cannot pass on entity state alone. */
    private static void assertVisible(ClientGameTestContext context, String name, boolean menu) {
        var screenshot=context.takeScreenshot(name);
        try {
            var image=javax.imageio.ImageIO.read(screenshot.toFile());
            int x0=(int)(image.getWidth()*(menu ? .63 : .42)), x1=(int)(image.getWidth()*(menu ? .79 : .58));
            int y0=(int)(image.getHeight()*(menu ? .28 : .38)), y1=(int)(image.getHeight()*(menu ? .53 : .74));
            int hairPixels=0;
            for(int y=y0;y<y1;y++) for(int x=x0;x<x1;x++) {
                int pixel=image.getRGB(x,y), r=(pixel>>16)&255, g=(pixel>>8)&255, b=pixel&255;
                // Exclude the pale blue sky: a missing model must not pass on background pixels.
                if(b>80 && g<160 && b>r*1.25 && b>g*1.08) hairPixels++;
            }
            if(hairPixels<(x1-x0)*(y1-y0)/20) throw new AssertionError("Character is invisible in " + name);
        } catch(java.io.IOException e) { throw new AssertionError("Cannot inspect rendered character", e); }
    }
}

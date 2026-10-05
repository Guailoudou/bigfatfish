package cn.deepmod.bigfatfish;

import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class BigFatFishEntity extends TamableAnimal implements ExtendedMenuProvider<Integer> {
    private static final EntityDataAccessor<Integer> HUNGER = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> EMOTE_START = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> AFFECTION = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SATIETY = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BREED_COOLDOWN = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    public static final int SATIETY_TICKS=1200, NEGLECT_TICKS=24000, BREEDING_TICKS=PARENT_AGE_AFTER_BREEDING;
    public static final int IDLE = 0, FOLLOW = 1, REST = 2, HARVEST = 3, FIGHT = 4, LOAF = 5, BEG = 6, BED = 7;
    public final SimpleContainer backpack = new SimpleContainer(27);
    private BlockPos workAnchor;
    private int chatCooldown, hungryClock, effortTicks;
    private boolean backpackOpen;
    private int unfedTicks;

    public BigFatFishEntity(EntityType<? extends BigFatFishEntity> type, Level level) {
        super(type, level);
        if (!level.isClientSide()) setSkin(random.nextInt(2));
        setGuaranteedDrop(EquipmentSlot.MAINHAND);
        setGuaranteedDrop(EquipmentSlot.OFFHAND);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(HUNGER, 20); b.define(ACTIVITY, IDLE); b.define(SKIN, 0); b.define(EMOTE, 0); b.define(EMOTE_START, 0L);
        b.define(AFFECTION,0); b.define(SATIETY,0); b.define(BREED_COOLDOWN,0);
    }
    public int emote() { return entityData.get(EMOTE); }
    public long emoteStart() { return entityData.get(EMOTE_START); }
    public void emote(int value) { entityData.set(EMOTE, value); entityData.set(EMOTE_START, level().getGameTime()); }
    @Override public float getAgeScale() { return 1; } // The juvenile has its own proportions and one-block mesh.
    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        return isBaby() ? EntityDimensions.scalable(0.45F, 1).withEyeHeight(0.78F) : super.getDefaultDimensions(pose);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData group) {
        var result = super.finalizeSpawn(level, difficulty, reason, group);
        if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION || reason == EntitySpawnReason.SPAWN_ITEM_USE)
            setBaby(true);
        return result;
    }
    public int skin() { return entityData.get(SKIN); }
    public void setSkin(int value) { entityData.set(SKIN, Math.clamp(value, 0, 1)); }
    public int hunger() { return entityData.get(HUNGER); }
    public int affection() { return entityData.get(AFFECTION); }
    public int affectionStage() {
        int value=affection();
        if(value<0) return value<=-75 ? -4 : value<=-50 ? -3 : value<=-25 ? -2 : -1;
        return value==100 ? 4 : value>=75 ? 3 : value>=50 ? 2 : value>=25 ? 1 : 0;
    }
    public void setAffection(int value) { entityData.set(AFFECTION,Math.clamp(value,-100,100)); }
    public int satietyTicks() { return entityData.get(SATIETY); }
    public int breedingCooldown() { return entityData.get(BREED_COOLDOWN); }
    public boolean canSpecialInteract() { return isTame() && !isBaby() && affection()==100 && breedingCooldown()==0; }
    public boolean specialInteract(Player player) {
        if (!(level() instanceof ServerLevel server) || !isAlive() || !isTame() || !isOwnedBy(player) || player.distanceToSqr(this)>64) return false;
        if(isBaby()) { setAffection(affection()-10);say("breed_reject_young",true);leaveIfUnhappy();return false; }
        if(affection()<100) {
            if(affection()<50) setAffection(affection()-5);
            else emote(2);
            say("breed_reject_affection",true);leaveIfUnhappy();return false;
        }
        if(breedingCooldown()>0) { say("breed_reject_cooldown",true);return false; }
        var baby=BigFatFishMod.BIG_FAT_FISH.create(server,EntitySpawnReason.BREEDING);
        if(baby==null) return false;
        baby.setBaby(true);baby.setSkin(skin());baby.tame(player);
        baby.setPos(getX(),getY(),getZ());baby.setOrderedToSit(true);baby.workAnchor=blockPosition();
        if(!server.addFreshEntity(baby)) return false;
        entityData.set(BREED_COOLDOWN,BREEDING_TICKS);
        setAge(BREEDING_TICKS);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,getX(),getY()+1,getZ(),7,.4,.4,.4,0);
        emote(2);say("family",true);
        return true;
    }
    public int activity() { return entityData.get(ACTIVITY); }
    public BlockPos anchor() { return workAnchor; }
    public boolean hungry() { return hunger() <= 6; }
    private void activity(int value) { entityData.set(ACTIVITY, value); }
    public void setBackpackOpen(boolean value) { backpackOpen = value; }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CompanionGoal());
        goalSelector.addGoal(2, new TemptGoal(this, 2.5, s -> s.is(BigFatFishMod.COOKED_RICE), false) {
            @Override public boolean canUse() { return !isTame() && super.canUse(); }
        });
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 2) {
            @Override public boolean canUse() { return !isTame() && super.canUse(); }
        });
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }
    @Override public boolean isFood(ItemStack stack) { return stack.is(BigFatFishMod.COOKED_RICE); }
    @Override public AgeableMob getBreedOffspring(ServerLevel l, AgeableMob other) { return null; }
    @Override public boolean canFallInLove() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        if (reason == EntitySpawnReason.SPAWN_ITEM_USE || reason == EntitySpawnReason.COMMAND) return true;
        if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) && random.nextInt(12) != 0) return false;
        BlockPos p = blockPosition();
        if (!level.getBlockState(p.below()).isSolid() || !level.getFluidState(p).isEmpty() || level.getRawBrightness(p, 0) < 9) return false;
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-5, -2, -5), p.offset(5, 0, 5)))
            if (level.getFluidState(q).is(net.minecraft.tags.FluidTags.WATER)) return true;
        return false;
    }
    @Override public void aiStep() {
        if(isRemoved()) return;
        super.aiStep();
        if (!level().isClientSide() && emote() != 0 && level().getGameTime() - emoteStart() >= 60) emote(0);
        if (!level().isClientSide()) {
            if(satietyTicks()>0) entityData.set(SATIETY,satietyTicks()-1);
            if(breedingCooldown()>0) entityData.set(BREED_COOLDOWN,breedingCooldown()-1);
        }
        if (!(level() instanceof ServerLevel) || !isTame()) return;
        if(unfedTicks<Integer.MAX_VALUE) unfedTicks++;
        if(unfedTicks>=NEGLECT_TICKS && (unfedTicks-NEGLECT_TICKS)%6000==0) setAffection(affection()-1);
        if(leaveIfUnhappy()) return;
        if(affection()==100 && tickCount%100==0) ((ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,getX(),getY()+getBbHeight(),getZ(),2,.25,.2,.25,0);
        if (!isBaby() && emote() == 0 && (activity() == FOLLOW || activity() == REST || activity() == BED) && tickCount % 240 == 0 && random.nextInt(4) == 0) emote(1 + random.nextInt(3));
        if (chatCooldown > 0) chatCooldown--;
        if (activity() == HARVEST || activity() == FIGHT) effortTicks++;
        if (++hungryClock >= 400) {
            hungryClock = 0;
            double chance = isBaby() ? 0.45 : effortTicks > 0 ? 0.55 : 0.12;
            effortTicks = 0;
            if (random.nextDouble() < chance) entityData.set(HUNGER, Math.max(0, hunger() - 1));
        }
    }
    private boolean leaveIfUnhappy() {
        if(!(level() instanceof ServerLevel server) || !isTame() || affection()!=-100) return false;
        if(isRemoved()) return true;
        say("runaway",true);
        for(var stack:backpack.removeAllItems()) spawnAtLocation(server,stack);
        for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND)) {
            if(!getItemBySlot(slot).isEmpty()) spawnAtLocation(server,getItemBySlot(slot).copy());
            setItemSlot(slot,ItemStack.EMPTY);
        }
        discard();return true;
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if(isRemoved() || leaveIfUnhappy()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (isTame() && !isOwnedBy(player)) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.translatable("message.bigfatfish.not_owner"));
            return InteractionResult.SUCCESS;
        }
        if (held.is(Items.NAME_TAG)) return super.mobInteract(player,hand);
        if (held.is(BigFatFishMod.COOKED_RICE)) {
            if (!level().isClientSide()) {
                boolean consume = satietyTicks()==0;
                if (consume) {
                    boolean infiniteMaterials = player.hasInfiniteMaterials();
                    held.consume(1, player);
                    if (!infiniteMaterials) {
                        ItemStack bowl = new ItemStack(Items.BOWL);
                        if (held.isEmpty()) player.setItemInHand(hand, bowl);
                        else if (!player.getInventory().add(bowl)) player.drop(bowl, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                    entityData.set(HUNGER, Math.min(20, hunger() + 8)); heal(4);
                    entityData.set(SATIETY,SATIETY_TICKS);unfedTicks=0;
                    emote(4);
                    if (isBaby()) ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-getAge()), true);
                    if (!isTame()) {
                        // Same independent 1/3 roll as Minecraft 26.3 Cat.tryToTame; failed bowls do not accumulate a guarantee.
                        if (random.nextInt(3) == 0) {
                            tame(player); setAffection(0); setOrderedToSit(true); workAnchor = blockPosition();
                            level().broadcastEntityEvent(this, (byte) 7); emote(2); say(isBaby() ? "baby_tame" : "tame", true);
                        } else {
                            level().broadcastEntityEvent(this, (byte) 6);
                            player.sendSystemMessage(dialogue("taming",random.nextInt(6)));
                            emote(2);
                        }
                    } else { setAffection(affection()+1+random.nextInt(4));say(isBaby() ? "baby_fed" : "fed", true); }
                } else if(isTame()) say("full", true);
                else player.sendSystemMessage(dialogue("full",random.nextInt(3)));
            }
            return InteractionResult.SUCCESS;
        }
        if (isTame()) {
            if (!level().isClientSide()) {
                if (player.isShiftKeyDown()) player.openMenu(this);
                else if (held.isEmpty()) {
                    setOrderedToSit(!isOrderedToSit());
                    workAnchor = isOrderedToSit() ? blockPosition() : null;
                    getNavigation().stop(); setTarget(null);
                    setInSittingPose(isOrderedToSit()); activity(isOrderedToSit() ? REST : FOLLOW);
                    say(isOrderedToSit() ? "sit" : "follow", true);
                } else player.sendOverlayMessage(Component.translatable("message.bigfatfish.controls"));
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    public void say(String event, boolean force) {
        if (!(getOwner() instanceof ServerPlayer owner) || (!force && chatCooldown > 0)) return;
        owner.sendSystemMessage(dialogue(event,random.nextInt(3)));
        chatCooldown = event.endsWith("hungry") ? 600 : 200;
    }
    public Component dialogue(String event,int line) {
        String key="dialogue.bigfatfish."+(isTame() ? "stage."+affectionStage()+"." : "")+event+"."+line;
        return Component.translatable("dialogue.bigfatfish.format",getName(),Component.translatable(key));
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Skin", skin());
        ContainerHelper.saveAllItems(out.child("Backpack"), backpack.getItems());
        out.putInt("Hunger", hunger()); out.putInt("HungerClock", hungryClock); out.putInt("Effort", effortTicks);
        out.putInt("Affection",affection());out.putInt("Satiety",satietyTicks());out.putInt("BreedingCooldown",breedingCooldown());out.putInt("UnfedTicks",unfedTicks);
        if (workAnchor != null) out.store("WorkAnchor", BlockPos.CODEC, workAnchor);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        setSkin(in.getIntOr("Skin", 0));
        setAffection(in.getIntOr("Affection",0));entityData.set(SATIETY,Math.clamp(in.getIntOr("Satiety",0),0,SATIETY_TICKS));
        entityData.set(BREED_COOLDOWN,Math.clamp(in.getIntOr("BreedingCooldown",0),0,BREEDING_TICKS));unfedTicks=Math.max(0,in.getIntOr("UnfedTicks",0));
        ContainerHelper.loadAllItems(in.childOrEmpty("Backpack"), backpack.getItems());
        entityData.set(HUNGER, Math.clamp(in.getIntOr("Hunger", 20), 0, 20));
        hungryClock = Math.clamp(in.getIntOr("HungerClock", 0), 0, 399);
        effortTicks = Math.clamp(in.getIntOr("Effort", 0), 0, 400);
        workAnchor = in.read("WorkAnchor", BlockPos.CODEC).orElse(null);
        if (isOrderedToSit() && workAnchor == null) workAnchor = blockPosition();
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : backpack.removeAllItems()) spawnAtLocation(level, stack);
        say("death", true);
    }
    @Override public Component getDisplayName() { return getName(); }
    @Override public Integer getScreenOpeningData(ServerPlayer player) { return getId(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return isOwnedBy(player) ? new FishMenu(id, inv, this) : null; }

    private Block seedBlock(ItemStack stack) {
        if(stack.isEmpty()) return null;
        if(stack.is(BigFatFishMod.PADDY)) return BigFatFishMod.RICE_CROP;
        if(stack.getItem() instanceof BlockItem item) {
            Block block=item.getBlock();
            if(block instanceof CropBlock || block instanceof StemBlock || block.defaultBlockState().is(BlockTags.CROPS)) return block;
        }
        return null;
    }
    private boolean canPlant(ServerLevel server,BlockPos p,ItemStack seed) {
        Block block=seedBlock(seed);
        if(block==null) return false;
        if(block==BigFatFishMod.RICE_CROP) return RiceBlock.canPlant(server,p);
        if(!server.getBlockState(p).isAir() || !server.getBlockState(p.below()).is(Blocks.FARMLAND)) return false;
        var context=new DirectionalPlaceContext(server,p,Direction.DOWN,seed,Direction.UP);
        BlockState state=block.getStateForPlacement(context);
        return state!=null && state.canSurvive(server,p);
    }
    private int seedSlot(ServerLevel server,BlockPos p,Block preferred) {
        if(!server.getBlockState(p).isAir() && !server.getBlockState(p).is(Blocks.WATER)) return -1;
        if(server.getBlockState(p).isAir() && !server.getBlockState(p.below()).is(Blocks.FARMLAND)) return -1;
        // Same-crop seeds come first; the second pass accepts any legal crop.
        for(int pass=0;pass<(preferred==null ? 1 : 2);pass++) {
            for(int i=0;i<backpack.getContainerSize();i++) {
                ItemStack seed=backpack.getItem(i);
                if(preferred!=null && pass==0 && seedBlock(seed)!=preferred) continue;
                if(canPlant(server,p,seed)) return i;
            }
        }
        return -1;
    }
    boolean plantFromBackpack(ServerLevel server,BlockPos p,Block preferred) {
        int slot=seedSlot(server,p,preferred);
        if(slot<0) return false;
        ItemStack seed=backpack.getItem(slot);
        if(seed.is(BigFatFishMod.PADDY)) {
            if(!server.setBlockAndUpdate(p,BigFatFishMod.RICE_CROP.defaultBlockState())) return false;
            seed.shrink(1);
        } else {
            BlockItem item=(BlockItem)seed.getItem();
            if(!item.place(new DirectionalPlaceContext(server,p,Direction.DOWN,seed,Direction.UP)).consumesAction()) return false;
        }
        backpack.setChanged();return true;
    }

    /** All owned movement shares one policy, so hunger and combat cannot bypass the work boundary. */
    private final class CompanionGoal extends Goal {
        private BlockPos crop, bed;
        private int scanCooldown, actionCooldown, loafTicks, bedTicks, walkCooldown, attackCooldown;
        private boolean fullAnnounced;
        private boolean following;
        private List<ItemStack> blockedHarvest;
        CompanionGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
        @Override public boolean canUse() { return isTame(); }
        @Override public boolean canContinueToUse() { return isTame(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { getNavigation().stop(); setInSittingPose(false); }
        private boolean allowed(Vec3 p) { return !isOrderedToSit() || workAnchor == null || p.distanceToSqr(Vec3.atBottomCenterOf(workAnchor)) <= 32 * 32; }
        private boolean move(Vec3 p, double speed) {
            if (!allowed(p)) return false;
            if (walkCooldown > 0) return true;
            walkCooldown = 20;
            var path = getNavigation().createPath(p.x, p.y, p.z, 1);
            if (path == null || !path.canReach()) return false;
            if (isOrderedToSit()) for (int i = 0; i < path.getNodeCount(); i++)
                if (!allowed(Vec3.atBottomCenterOf(path.getNodePos(i)))) return false;
            setInSittingPose(false);
            return getNavigation().moveTo(path, speed);
        }
        private void rest() {
            activity(REST); setTarget(null);
            if (workAnchor == null) workAnchor = blockPosition();
            if (position().distanceToSqr(Vec3.atBottomCenterOf(workAnchor)) > 1.5) move(Vec3.atBottomCenterOf(workAnchor), 2.5);
            else { getNavigation().stop(); setInSittingPose(true); }
        }
        @Override public void tick() {
            if (!(level() instanceof ServerLevel server)) return;
            if (walkCooldown > 0) walkCooldown--;
            if (scanCooldown > 0) scanCooldown--;
            if (actionCooldown > 0) actionCooldown--;
            if (attackCooldown > 0) attackCooldown--;
            if (backpackOpen) { getNavigation().stop(); setTarget(null); return; }
            if (isOrderedToSit() && workAnchor == null) workAnchor = blockPosition();
            if (isOrderedToSit() && !allowed(position())) { crop = null; bed = null; setTarget(null); walkCooldown = 0; rest(); return; }
            LivingEntity owner = getOwner();
            if (isBaby()) {
                crop = null; bed = null; loafTicks = 0; setTarget(null);
                boolean wantsRice = satietyTicks()==0 && (hungry() || tickCount % 600 < 100 || owner != null
                    && (owner.getMainHandItem().is(BigFatFishMod.COOKED_RICE) || owner.getOffhandItem().is(BigFatFishMod.COOKED_RICE)));
                if (owner != null && owner.isAlive() && wantsRice && allowed(owner.position()) && distanceToSqr(owner) <= 1024) {
                    activity(BEG); setInSittingPose(false); getLookControl().setLookAt(owner, 30, 30);
                    if (distanceToSqr(owner) > 9) move(owner.position(), 2.5); else getNavigation().stop();
                    if (emote() == 0) emote(5);
                    say("baby_hungry", false);
                } else if (isOrderedToSit()) rest();
                else followOwner(owner);
                if (tickCount % 160 == 0 && random.nextInt(3) == 0 && emote() == 0) {
                    emote(1 + random.nextInt(3)); say("baby_cute", false);
                }
                return;
            }
            if (hungry() && satietyTicks()==0) {
                crop = null; bed = null; setTarget(null); bedTicks = 0;
                if (owner != null && owner.isAlive() && allowed(owner.position()) && distanceToSqr(owner) <= 32 * 32) {
                    activity(BEG); setInSittingPose(false); getLookControl().setLookAt(owner, 30, 30);
                    if (distanceToSqr(owner) > 4) move(owner.position(), 3); else getNavigation().stop();
                    say("hungry", false);
                    return;
                }
                if (isOrderedToSit()) { rest(); return; }
            }
            boolean sword = getMainHandItem().is(ItemTags.SWORDS);
            boolean hoe = getMainHandItem().is(ItemTags.HOES);
            if (isOrderedToSit() && (hoe || sword) && !hungry() && loafTicks == 0
                && (activity() == HARVEST || activity() == FIGHT) && tickCount % 300 == 0 && random.nextInt(8) == 0) {
                loafTicks = 300 + random.nextInt(400); crop = null; setTarget(null); say("loaf", false);
            }
            if (sword && !hungry() && loafTicks == 0) {
                LivingEntity target = getTarget();
                if (target == null || !target.isAlive() || !allowed(target.position()) || distanceToSqr(target) > 32 * 32) {
                    setTarget(null);
                    if (tickCount % 20 == 0) {
                        target = server.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(16),
                            e -> e.isAlive() && allowed(e.position()) && getSensing().hasLineOfSight(e))
                            .stream().min(Comparator.comparingDouble(BigFatFishEntity.this::distanceToSqr)).orElse(null);
                        setTarget(target);
                        if (target != null) say("fight", false);
                    } else target = null;
                }
                if (target != null) {
                    activity(FIGHT); setInSittingPose(false); bed = null;
                    getLookControl().setLookAt(target, 30, 30);
                    if (isWithinMeleeAttackRange(target)) {
                        getNavigation().stop();
                        if (attackCooldown == 0) {
                            swingForAttack(InteractionHand.MAIN_HAND);
                            doHurtTarget(server, target);
                            getMainHandItem().hurtAndBreak(1, BigFatFishEntity.this, EquipmentSlot.MAINHAND);
                            attackCooldown = 10;
                        }
                    } else if (!move(target.position(), 3)) setTarget(null);
                    return;
                }
            } else setTarget(null);
            if (isOrderedToSit()) {
                bed = null; bedTicks = 0;
                if (loafTicks > 0 && (hoe || sword) && !hungry()) {
                    loafTicks--; activity(LOAF); setInSittingPose(false);
                    if (tickCount % 60 == 0) {
                        BlockPos wander = workAnchor.offset(random.nextInt(33) - 16, 0, random.nextInt(33) - 16);
                        move(Vec3.atBottomCenterOf(wander), 1.8);
                    }
                    return;
                }
                if (hoe && !hungry()) {
                    if (blockedHarvest != null && !canStore(blockedHarvest)) { crop = null; rest(); return; }
                    blockedHarvest = null;
                    if (crop == null && scanCooldown == 0) { crop = findCrop(server); scanCooldown = 100; }
                    if (crop != null && (mature(server.getBlockState(crop)) || seedSlot(server,crop,null)>=0)) {
                        activity(HARVEST); setInSittingPose(false);
                        if (position().distanceToSqr(Vec3.atCenterOf(crop)) < 6.25) {
                            getNavigation().stop();
                            if (actionCooldown == 0) {
                                if(mature(server.getBlockState(crop))) harvest(server,crop);
                                else if(plantFromBackpack(server,crop,null)) swingForAttack(InteractionHand.MAIN_HAND);
                                crop = null; actionCooldown = 20;
                            }
                        } else if (!move(Vec3.atCenterOf(crop), 2.5)) { crop = null; scanCooldown = 100; }
                        return;
                    }
                    crop = null;
                } else crop = null;
                rest(); return;
            }
            crop = null; loafTicks = 0;
            if (bed != null && bedTicks-- > 0 && server.getBlockState(bed).is(BlockTags.BEDS) && owner != null && distanceToSqr(owner) < 100) {
                activity(BED);
                Vec3 bedTop = Vec3.atBottomCenterOf(bed).add(0, 1, 0);
                double horizontalDistance = Math.pow(getX() - bedTop.x, 2) + Math.pow(getZ() - bedTop.z, 2);
                if (horizontalDistance < 0.36 && getY() >= bed.getY() + 0.5) {
                    getNavigation().stop(); setInSittingPose(true);
                } else if (!move(bedTop, 2)) bed = null;
                return;
            }
            bed = null;
            if (!hungry() && tickCount % 200 == 0 && random.nextInt(5) == 0 && owner != null && distanceToSqr(owner) < 64) {
                for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-6, -2, -6), blockPosition().offset(6, 2, 6))) {
                    BlockState state = server.getBlockState(p);
                    if (state.is(BlockTags.BEDS) && !state.getValue(BedBlock.OCCUPIED) && server.getBlockState(p.above()).isAir()) {
                        bed = p.immutable(); bedTicks = 300 + random.nextInt(400); say("bed", false); break;
                    }
                }
            }
            followOwner(owner);
        }
        private void followOwner(LivingEntity owner) {
            activity(FOLLOW); setInSittingPose(false);
            if (owner != null && owner.isAlive()) {
                getLookControl().setLookAt(owner, 30, 30);
                double distance = distanceToSqr(owner);
                // Vanilla cat hysteresis: start at ten blocks, stop at five, teleport at twelve.
                if (distance >= 100) following = true;
                if (distance <= 25) following = false;
                if (following && distance >= 144 && !isLeashed() && !isPassenger()) tryToTeleportToOwner();
                else if (following) move(owner.position(), 2.5);
                else getNavigation().stop();
            } else { following = false; activity(IDLE); getNavigation().stop(); }
        }
        private boolean mature(BlockState s) {
            return s.getBlock() instanceof CropBlock c && c.isMaxAge(s)
                || s.is(BigFatFishMod.RICE_CROP) && s.getValue(RiceBlock.PART) == 0 && s.getValue(RiceBlock.AGE) == 7;
        }
        private BlockPos findCrop(ServerLevel server) {
            BlockPos best = null, empty = null; double distance = Double.MAX_VALUE, emptyDistance = Double.MAX_VALUE;
            // ponytail: bounded block scan every 5 seconds; use a crop index if many companions become common.
            for (BlockPos p : BlockPos.betweenClosed(workAnchor.offset(-32, -4, -32), workAnchor.offset(32, 4, 32))) {
                if (!allowed(Vec3.atCenterOf(p)) || !server.hasChunkAt(p)) continue;
                double d = position().distanceToSqr(Vec3.atCenterOf(p));
                if (mature(server.getBlockState(p))) {
                    if (d < distance) { best = p.immutable(); distance = d; }
                } else if(d<emptyDistance && seedSlot(server,p,null)>=0) { empty=p.immutable();emptyDistance=d; }
            }
            return best!=null ? best : empty;
        }
        private void harvest(ServerLevel server, BlockPos p) {
            BlockState s = server.getBlockState(p);
            List<ItemStack> drops = Block.getDrops(s, server, p, null, BigFatFishEntity.this, getMainHandItem());
            if (!canStore(drops)) {
                if (!fullAnnounced) say("backpack_full", false);
                blockedHarvest = drops.stream().map(ItemStack::copy).toList();
                fullAnnounced = true; scanCooldown = 20; rest(); return;
            }
            fullAnnounced = false;
            // Store the harvest before choosing a seed so its fresh seeds can be reused.
            server.setBlock(p, s.is(BigFatFishMod.RICE_CROP) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            for (ItemStack drop : drops) backpack.addItem(drop);
            plantFromBackpack(server,p,s.getBlock());
            swingForAttack(InteractionHand.MAIN_HAND);
            getMainHandItem().hurtAndBreak(1, BigFatFishEntity.this, EquipmentSlot.MAINHAND);
            say("harvest", false);
        }
        private boolean canStore(List<ItemStack> drops) {
            SimpleContainer trial = new SimpleContainer(27);
            for (int i = 0; i < 27; i++) trial.setItem(i, backpack.getItem(i).copy());
            for (ItemStack drop : drops) if (!trial.addItem(drop.copy()).isEmpty()) return false;
            return true;
        }
    }
}

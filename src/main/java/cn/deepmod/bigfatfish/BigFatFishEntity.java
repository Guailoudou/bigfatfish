package cn.deepmod.bigfatfish;

import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class BigFatFishEntity extends TamableAnimal implements ExtendedMenuProvider<Integer> {
    private static final EntityDataAccessor<Integer> HUNGER = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(BigFatFishEntity.class, EntityDataSerializers.INT);
    public static final int IDLE = 0, FOLLOW = 1, REST = 2, HARVEST = 3, FIGHT = 4, LOAF = 5, BEG = 6, BED = 7;
    public final SimpleContainer backpack = new SimpleContainer(27);
    private BlockPos workAnchor;
    private int chatCooldown, hungryClock, effortTicks;
    private boolean backpackOpen;

    public BigFatFishEntity(EntityType<? extends BigFatFishEntity> type, Level level) {
        super(type, level);
        setGuaranteedDrop(EquipmentSlot.MAINHAND);
        setGuaranteedDrop(EquipmentSlot.OFFHAND);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b); b.define(HUNGER, 20); b.define(ACTIVITY, IDLE);
    }
    public int hunger() { return entityData.get(HUNGER); }
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
        super.aiStep();
        if (!(level() instanceof ServerLevel) || !isTame()) return;
        if (chatCooldown > 0) chatCooldown--;
        if (activity() == HARVEST || activity() == FIGHT) effortTicks++;
        if (++hungryClock >= 400) {
            hungryClock = 0;
            double chance = effortTicks > 0 ? 0.55 : 0.12;
            effortTicks = 0;
            if (random.nextDouble() < chance) entityData.set(HUNGER, Math.max(0, hunger() - 1));
        }
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isTame() && !isOwnedBy(player)) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.translatable("message.bigfatfish.not_owner"));
            return InteractionResult.SUCCESS;
        }
        if (held.is(BigFatFishMod.COOKED_RICE)) {
            if (!level().isClientSide()) {
                boolean consume = !isTame() || hunger() < 20 || getHealth() < getMaxHealth();
                if (consume) {
                    boolean infiniteMaterials = player.hasInfiniteMaterials();
                    held.consume(1, player);
                    if (!infiniteMaterials) {
                        ItemStack bowl = new ItemStack(Items.BOWL);
                        if (held.isEmpty()) player.setItemInHand(hand, bowl);
                        else if (!player.getInventory().add(bowl)) player.drop(bowl, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                    entityData.set(HUNGER, Math.min(20, hunger() + 8)); heal(4);
                    if (!isTame()) {
                        if (random.nextInt(3) == 0) { tame(player); level().broadcastEntityEvent(this, (byte) 7); say("tame", true); }
                        else level().broadcastEntityEvent(this, (byte) 6);
                    } else say("fed", true);
                } else say("full", true);
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
        owner.sendSystemMessage(Component.translatable("dialogue.bigfatfish." + event + "." + random.nextInt(3)));
        chatCooldown = event.equals("hungry") ? 600 : 200;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        ContainerHelper.saveAllItems(out.child("Backpack"), backpack.getItems());
        out.putInt("Hunger", hunger()); out.putInt("HungerClock", hungryClock); out.putInt("Effort", effortTicks);
        if (workAnchor != null) out.store("WorkAnchor", BlockPos.CODEC, workAnchor);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
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

    /** All owned movement shares one policy, so hunger and combat cannot bypass the work boundary. */
    private final class CompanionGoal extends Goal {
        private BlockPos crop, bed;
        private int scanCooldown, actionCooldown, loafTicks, bedTicks, walkCooldown, attackCooldown;
        private boolean fullAnnounced;
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
            if (hungry()) {
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
                    if (crop != null && mature(server.getBlockState(crop))) {
                        activity(HARVEST); setInSittingPose(false);
                        if (position().distanceToSqr(Vec3.atCenterOf(crop)) < 6.25) {
                            getNavigation().stop();
                            if (actionCooldown == 0) { harvest(server, crop); crop = null; actionCooldown = 20; }
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
            activity(FOLLOW); setInSittingPose(false);
            if (owner != null && owner.isAlive()) {
                getLookControl().setLookAt(owner, 30, 30);
                if (distanceToSqr(owner) > 144 && !isLeashed() && !isPassenger()) tryToTeleportToOwner();
                else if (distanceToSqr(owner) > 6.25) move(owner.position(), 3);
                else getNavigation().stop();
            } else { activity(IDLE); getNavigation().stop(); }
        }
        private boolean mature(BlockState s) {
            return s.getBlock() instanceof CropBlock c && c.isMaxAge(s)
                || s.is(BigFatFishMod.RICE_CROP) && s.getValue(RiceBlock.PART) == 0 && s.getValue(RiceBlock.AGE) == 7;
        }
        private BlockPos findCrop(ServerLevel server) {
            BlockPos best = null; double distance = Double.MAX_VALUE;
            // ponytail: bounded block scan every 5 seconds; use a crop index if many companions become common.
            for (BlockPos p : BlockPos.betweenClosed(workAnchor.offset(-32, -4, -32), workAnchor.offset(32, 4, 32))) {
                if (!allowed(Vec3.atCenterOf(p)) || !server.hasChunkAt(p) || !mature(server.getBlockState(p))) continue;
                double d = position().distanceToSqr(Vec3.atCenterOf(p));
                if (d < distance) { best = p.immutable(); distance = d; }
            }
            return best;
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
            // Keep the complete harvest. Replanting is deliberately left to the player.
            server.setBlock(p, s.is(BigFatFishMod.RICE_CROP) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            for (ItemStack drop : drops) backpack.addItem(drop);
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

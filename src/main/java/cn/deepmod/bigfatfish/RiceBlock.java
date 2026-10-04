package cn.deepmod.bigfatfish;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** One waterlogged root and, at maturity, two dry shoots. Only the root owns drops. */
public final class RiceBlock extends Block implements BonemealableBlock {
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 7);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);
    public RiceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(PART, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AGE, PART); }
    public static boolean soil(BlockState s) { return s.is(BlockTags.DIRT) || s.is(Blocks.FARMLAND) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL); }
    public static boolean canPlant(LevelReader level, BlockPos p) {
        return level.getBlockState(p).is(Blocks.WATER) && level.getFluidState(p).isSource()
            && soil(level.getBlockState(p.below())) && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir();
    }
    @Override protected FluidState getFluidState(BlockState s) { return s.getValue(PART) == 0 ? Fluids.WATER.getSource(false) : super.getFluidState(s); }
    @Override protected boolean canSurvive(BlockState s, LevelReader level, BlockPos p) {
        int part = s.getValue(PART);
        if (part == 0) return soil(level.getBlockState(p.below()));
        BlockState below = level.getBlockState(p.below());
        return below.is(this) && below.getValue(PART) == part - 1 && below.getValue(AGE) >= (part == 1 ? 4 : 7);
    }
    @Override protected BlockState updateShape(BlockState s, LevelReader level, ScheduledTickAccess ticks, BlockPos p,
            Direction dir, BlockPos neighbor, BlockState n, RandomSource random) {
        if (s.getValue(PART) == 0) ticks.scheduleTick(p, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        // Cleanup runs after removal so breaking any shoot also harvests its root exactly once.
        if (!canSurvive(s, level, p)) ticks.scheduleTick(p, this, 1);
        return s;
    }
    @Override protected void tick(BlockState s, ServerLevel level, BlockPos p, RandomSource random) {
        if (!canSurvive(s, level, p)) level.destroyBlock(p, true);
    }
    @Override protected boolean isRandomlyTicking(BlockState s) { return s.getValue(PART) == 0 && s.getValue(AGE) < 7; }
    @Override protected void randomTick(BlockState s, ServerLevel level, BlockPos p, RandomSource r) {
        if (level.getRawBrightness(p.above(), 0) >= 9 && r.nextInt(7) == 0) grow(level, p, s, 1);
    }
    public void grow(Level level, BlockPos p, BlockState s, int amount) {
        int age = Math.min(7, s.getValue(AGE) + amount);
        int height = age == 7 ? 3 : age >= 4 ? 2 : 1;
        for (int i = 1; i < height; i++) {
            BlockState above = level.getBlockState(p.above(i));
            if (!above.isAir() && !above.is(this)) return;
        }
        level.setBlock(p, s.setValue(AGE, age), 3);
        for (int i = 1; i < height; i++) level.setBlock(p.above(i), defaultBlockState().setValue(AGE, age).setValue(PART, i), 3);
    }
    public void reset(ServerLevel level, BlockPos root) {
        level.setBlock(root, defaultBlockState(), 3);
        for (int i = 1; i <= 2; i++) if (level.getBlockState(root.above(i)).is(this)) level.removeBlock(root.above(i), false);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState old, ServerLevel level, BlockPos p, boolean moving) {
        super.affectNeighborsAfterRemoval(old, level, p, moving);
        if (level.getBlockState(p).is(this)) return;
        int part = old.getValue(PART);
        if (part > 0) {
            BlockPos root = p.below(part);
            if (level.getBlockState(root).is(this) && level.getBlockState(root).getValue(AGE) >= 4) level.destroyBlock(root, true);
        } else {
            for (int i = 1; i <= 2; i++) if (level.getBlockState(p.above(i)).is(this)) level.removeBlock(p.above(i), false);
        }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos p, BlockState s, Player player) {
        if (!level.isClientSide() && s.getValue(PART) > 0) {
            BlockPos root = p.below(s.getValue(PART));
            if (level.getBlockState(root).is(this)) level.destroyBlock(root, !player.isCreative());
        }
        return super.playerWillDestroy(level, p, s, player);
    }
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos p, BlockState s, BonemealSource source) { return s.getValue(PART) == 0 && s.getValue(AGE) < 7; }
    @Override public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos p, BlockState s, BonemealSource source) { return true; }
    @Override public void performBonemeal(ServerLevel level, RandomSource random, BlockPos p, BlockState s, BonemealSource source) { grow(level, p, s, 2 + random.nextInt(4)); }
}

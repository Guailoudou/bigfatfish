package cn.deepmod.bigfatfish;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;

public final class StoneMillBlock extends Block {
    public StoneMillBlock(Properties p) { super(p); }
    @Override protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos p, CollisionContext c) { return box(1, 0, 1, 15, 13, 15); }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(BigFatFishMod.PADDY)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            int count = player.isShiftKeyDown() ? stack.getCount() : 1;
            stack.consume(count, player);
            ItemStack rice = new ItemStack(BigFatFishMod.RICE, count);
            if (!player.getInventory().add(rice)) player.drop(rice, false, net.minecraft.util.Prediction.SERVER_ONLY);
            level.playSound(null, p, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 0.9F);
        }
        return InteractionResult.SUCCESS;
    }
}

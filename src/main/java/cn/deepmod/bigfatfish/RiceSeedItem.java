package cn.deepmod.bigfatfish;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class RiceSeedItem extends Item {
    public RiceSeedItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        return hit.getType() == HitResult.Type.BLOCK ? plant(level, player, hand, hit.getBlockPos()) : InteractionResult.PASS;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().getFluidState(pos).isSource()) pos = pos.relative(context.getClickedFace());
        return plant(context.getLevel(), context.getPlayer(), context.getHand(), pos);
    }
    private InteractionResult plant(Level level, Player player, InteractionHand hand, BlockPos pos) {
        if (player == null || !level.mayInteract(player, pos)
            || !player.mayUseItemAt(pos, net.minecraft.core.Direction.UP, player.getItemInHand(hand))
            || !RiceBlock.canPlant(level, pos)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, BigFatFishMod.RICE_CROP.defaultBlockState());
            player.getItemInHand(hand).consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}

package cn.deepmod.bigfatfish;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class RiverRice {
    private RiverRice() {}
    public static void generate(ServerLevel level, LevelChunk chunk) {
        // Only new chunks: harvesting a river cannot make rice reappear on reload.
        if (level.getRandom().nextInt(3) != 0) return;
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos p = new BlockPos(x0 + x, y, z0 + z);
            // FULL's completion callback has not returned yet. Re-entering
            // level.getBlockState/setBlock here can wait on this very chunk's
            // unfinished FULL future. All reads and writes stay on the supplied
            // chunk; the one-block border also contains every bank lookup.
            if (!chunk.getNoiseBiome(QuartPos.fromBlock(p.getX()),QuartPos.fromBlock(y),QuartPos.fromBlock(p.getZ())).is(Biomes.RIVER)
                    || level.getRandom().nextInt(8) != 0 || !RiceBlock.canPlant(chunk, p)) continue;
            boolean bank = false;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (RiceBlock.soil(chunk.getBlockState(p.relative(d)))) bank = true;
            }
            if (bank) {
                plant(chunk,p,level.getRandom().nextInt(8));
            }
        }
    }
    static void plant(LevelChunk chunk, BlockPos root, int age) {
        int height=age==7 ? 3 : age>=4 ? 2 : 1;
        for(int part=0;part<height;part++)
            chunk.setBlockState(root.above(part),BigFatFishMod.RICE_CROP.defaultBlockState()
                .setValue(RiceBlock.AGE,age).setValue(RiceBlock.PART,part),Block.UPDATE_CLIENTS|Block.UPDATE_SKIP_ON_PLACE);
    }
}

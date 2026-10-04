package cn.deepmod.bigfatfish;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
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
            if (!level.getBiome(p).is(Biomes.RIVER) || level.getRandom().nextInt(8) != 0 || !RiceBlock.canPlant(level, p)) continue;
            boolean bank = false;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (RiceBlock.soil(level.getBlockState(p.relative(d)))) bank = true;
            }
            if (bank) {
                level.setBlock(p, BigFatFishMod.RICE_CROP.defaultBlockState(), 3);
                BigFatFishMod.RICE_CROP.grow(level, p, BigFatFishMod.RICE_CROP.defaultBlockState(), level.getRandom().nextInt(8));
            }
        }
    }
}

package cn.deepmod.bigfatfish;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** One shallow-water rice plant; vanilla placement supplies positions and seeded randomness. */
public final class RiverRice implements Feature {
    public static final MapCodec<RiverRice> CODEC = MapCodec.unit(RiverRice::new);
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, BigFatFishMod.id("river_rice"));
    public static void register() {
        Registry.register(BuiltInRegistries.FEATURE_TYPE, BigFatFishMod.id("river_rice"), CODEC);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.RIVER), GenerationStep.Decoration.VEGETAL_DECORATION, PLACED);
    }
    @Override public MapCodec<RiverRice> codec() { return CODEC; }
    @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos root) {
        // Respect the generation region's write boundary before reading or placing.
        if (!level.ensureCanWrite(root) || !level.ensureCanWrite(root.above(2)) || !RiceBlock.canPlant(level, root)) return false;
        boolean bank = false;
        for (Direction direction : Direction.Plane.HORIZONTAL)
            if (RiceBlock.soil(level.getBlockState(root.relative(direction)))) bank = true;
        if (!bank) return false;
        int age = random.nextInt(8);
        int height = age == 7 ? 3 : age >= 4 ? 2 : 1;
        for (int part = 0; part < height; part++)
            level.setBlock(root.above(part), BigFatFishMod.RICE_CROP.defaultBlockState()
                .setValue(RiceBlock.AGE, age).setValue(RiceBlock.PART, part), Block.UPDATE_CLIENTS);
        return true;
    }
}

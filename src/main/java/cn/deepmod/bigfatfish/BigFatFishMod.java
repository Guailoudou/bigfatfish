package cn.deepmod.bigfatfish;

import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.Foods;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class BigFatFishMod implements ModInitializer {
    public static final String ID = "bigfatfish";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }

    public static final RiceBlock RICE_CROP = Registry.register(BuiltInRegistries.BLOCK, id("rice_crop"),
        new RiceBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("rice_crop")))
            .noCollision().instabreak().randomTicks().sound(SoundType.CROP)));
    public static final Block MILL = Registry.register(BuiltInRegistries.BLOCK, id("stone_mill"),
        new StoneMillBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("stone_mill")))
            .strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final Item PADDY = item("paddy", RiceSeedItem::new);
    public static final Item RICE = item("rice", Item::new);
    public static final Item COOKED_RICE = item("cooked_rice", p -> new Item(p.food(Foods.COOKED_BEEF).usingConvertsTo(Items.BOWL)));
    public static final Item STONE_MILL = item("stone_mill", p -> new BlockItem(MILL, p));
    public static final EntityType<BigFatFishEntity> BIG_FAT_FISH = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("big_fat_fish"),
        EntityType.Builder.of(BigFatFishEntity::new, MobCategory.CREATURE)
            .sized(BigFatFishEntity.ADULT_WIDTH, BigFatFishEntity.ADULT_HEIGHT).eyeHeight(BigFatFishEntity.ADULT_EYE_HEIGHT)
            .clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id("big_fat_fish"))));
    public static final Item SPAWN_EGG = item("big_fat_fish_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(BIG_FAT_FISH)));
    public static final MenuType<FishMenu> FISH_MENU = Registry.register(BuiltInRegistries.MENU, id("fish_backpack"),
        new ExtendedMenuType<>(FishMenu::new, ByteBufCodecs.VAR_INT));

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        return Registry.register(BuiltInRegistries.ITEM, id(name), factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)))));
    }

    @Override public void onInitialize() {
        FabricDefaultAttributeRegistry.register(BIG_FAT_FISH, BigFatFishEntity.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.1)
            .add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.ATTACK_SPEED, 4)
            .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 0));
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.RIVER, Biomes.FROZEN_RIVER), MobCategory.CREATURE, BIG_FAT_FISH, 2, 1, 1);
        RiverRice.register();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(e -> { e.accept(PADDY); e.accept(STONE_MILL); });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(e -> { e.accept(RICE); e.accept(COOKED_RICE); });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> e.accept(SPAWN_EGG));
    }
}

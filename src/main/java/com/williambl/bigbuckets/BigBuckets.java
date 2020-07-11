package com.williambl.bigbuckets;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BigBuckets implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "bigbuckets";

    public static BigBucketItem BIG_BUCKET_ITEM;

    public static RecipeSerializer<Recipe<?>> BIG_BUCKET_RECIPE_SERIALIZER;
    public static RecipeSerializer<Recipe<?>> BIG_BUCKET_INCREASE_CAPACITY_RECIPE_SERIALIZER;

    @Override
    public void onInitialize() {
        BIG_BUCKET_ITEM = Registry.register(Registry.ITEM, new Identifier(MODID, "bigbucket"), new BigBucketItem(new Item.Settings().maxCount(1).group(ItemGroup.MISC)));
        BIG_BUCKET_RECIPE_SERIALIZER = Registry.register(Registry.RECIPE_SERIALIZER, new Identifier(MODID, "crafting_special_big_bucket"), new SpecialRecipeSerializer<>(BigBucketRecipe::new));
        BIG_BUCKET_INCREASE_CAPACITY_RECIPE_SERIALIZER = Registry.register(Registry.RECIPE_SERIALIZER, new Identifier(MODID, "crafting_special_big_bucket_increase_capacity"), new SpecialRecipeSerializer<>(BigBucketIncreaseCapacityRecipe::new));
    }
}

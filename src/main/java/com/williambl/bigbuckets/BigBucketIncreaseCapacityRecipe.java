package com.williambl.bigbuckets;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class BigBucketIncreaseCapacityRecipe extends SpecialCraftingRecipe {
    public BigBucketIncreaseCapacityRecipe(Identifier idIn) {
        super(idIn);
    }

    @Override
    public boolean matches(CraftingInventory inv, World worldIn) {
        int i = 0;
        ItemStack bigBucketStack = ItemStack.EMPTY;

        for (int j = 0; j < inv.getInvSize(); ++j) {
            ItemStack stackInSlot = inv.getInvStack(j);
            if (!stackInSlot.isEmpty()) {
                if (stackInSlot.getItem() == BigBuckets.BIG_BUCKET_ITEM) {
                    if (bigBucketStack.isEmpty())
                        bigBucketStack = stackInSlot;
                    else
                        return false;
                } else {
                    if (stackInSlot.getItem() == Items.BUCKET)
                        i++;
                    else {
                        return false;
                    }
                }
            }
        }

        return !bigBucketStack.isEmpty() && i > 0;
    }

    @Override
    public ItemStack craft(CraftingInventory inv) {
        int i = 0;
        ItemStack bigBucketStack = ItemStack.EMPTY;

        for (int j = 0; j < inv.getInvSize(); ++j) {
            ItemStack stackInSlot = inv.getInvStack(j);
            if (!stackInSlot.isEmpty()) {
                if (stackInSlot.getItem() == BigBuckets.BIG_BUCKET_ITEM) {
                    bigBucketStack = stackInSlot.copy();
                } else {
                    if (stackInSlot.getItem() == Items.BUCKET)
                        i++;
                }
            }
        }

        BigBuckets.BIG_BUCKET_ITEM.setCapacity(bigBucketStack, BigBuckets.BIG_BUCKET_ITEM.getCapacity(bigBucketStack) + i);
        return bigBucketStack;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BigBuckets.BIG_BUCKET_INCREASE_CAPACITY_RECIPE_SERIALIZER;
    }

    /**
     * Used to determine if this recipe can fit in a grid of the given width/height
     */
    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }
}
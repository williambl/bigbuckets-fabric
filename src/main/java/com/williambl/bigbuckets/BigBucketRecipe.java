package com.williambl.bigbuckets;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class BigBucketRecipe extends SpecialCraftingRecipe {
   public BigBucketRecipe(Identifier idIn) {
      super(idIn);
   }

   @Override
   public boolean matches(CraftingInventory inv, World worldIn) {
      int i = 0;

      for (int j = 0; j < inv.size(); ++j) {
         ItemStack stackInSlot = inv.getStack(j);
         if (!stackInSlot.isEmpty()) {
            if (stackInSlot.getItem() == Items.BUCKET)
               i++;
            else
               return false;
         }
      }

      return i == 2;
   }

   @Override
   public ItemStack craft(CraftingInventory inv) {
      ItemStack stack = new ItemStack(BigBuckets.BIG_BUCKET_ITEM);
      stack.getOrCreateSubTag("BigBuckets").putInt("Capacity", 2);
      return stack;
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return BigBuckets.BIG_BUCKET_RECIPE_SERIALIZER;
   }

   /**
    * Used to determine if this recipe can fit in a grid of the given width/height
    */
   @Override
   public boolean fits(int width, int height) {
      return width * height >= 2;
   }
}
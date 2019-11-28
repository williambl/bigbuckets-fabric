package com.williambl.bigbuckets;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.item.crafting.SpecialRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class BigBucketRecipe extends SpecialRecipe {
   public BigBucketRecipe(ResourceLocation idIn) {
      super(idIn);
   }

   @Override
   public boolean matches(CraftingInventory inv, World worldIn) {
      int i = 0;

      for (int j = 0; j < inv.getSizeInventory(); ++j) {
         ItemStack stackInSlot = inv.getStackInSlot(j);
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
   public ItemStack getCraftingResult(CraftingInventory inv) {
      ItemStack stack = new ItemStack(BigBuckets.BIG_BUCKET_ITEM);
      stack.getOrCreateChildTag("BigBuckets").putInt("Capacity", 2);
      return stack;
   }

   @Override
   public IRecipeSerializer<?> getSerializer() {
      return BigBuckets.BIG_BUCKET_RECIPE_SERIALIZER;
   }

   /**
    * Used to determine if this recipe can fit in a grid of the given width/height
    */
   @Override
   public boolean canFit(int width, int height) {
      return width * height >= 2;
   }
}
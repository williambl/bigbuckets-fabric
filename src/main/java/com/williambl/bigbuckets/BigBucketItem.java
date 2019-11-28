package com.williambl.bigbuckets;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.block.ILiquidContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class BigBucketItem extends Item {

    public BigBucketItem(Properties builder) {
        super(builder);
    }

    protected ItemStack emptyBucket(ItemStack stack, PlayerEntity player) {
        int fullness = getFullness(stack);

        if (fullness - 1 >= 0)
            setFullness(stack, fullness - 1);
        if (fullness - 1 == 0)
            setFluid(stack, Fluids.EMPTY);
        return stack;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn) {
        ItemStack stack = playerIn.getHeldItem(handIn);
        RayTraceResult raytraceresult = rayTrace(worldIn, playerIn, this.getFullness(stack) == this.getCapacity(stack) ? RayTraceContext.FluidMode.NONE : RayTraceContext.FluidMode.SOURCE_ONLY);
        ActionResult<ItemStack> ret = net.minecraftforge.event.ForgeEventFactory.onBucketUse(playerIn, worldIn, stack, raytraceresult);
        if (ret != null) return ret;
        if (raytraceresult.getType() == RayTraceResult.Type.MISS) {
            return new ActionResult<>(ActionResultType.PASS, stack);
        } else if (raytraceresult.getType() != RayTraceResult.Type.BLOCK) {
            return new ActionResult<>(ActionResultType.PASS, stack);
        } else {
            BlockRayTraceResult blockraytraceresult = (BlockRayTraceResult) raytraceresult;
            BlockPos blockpos = blockraytraceresult.getPos();
            if (worldIn.isBlockModifiable(playerIn, blockpos) && playerIn.canPlayerEdit(blockpos, blockraytraceresult.getFace(), stack)) {
                if (this.getFullness(stack) != this.getCapacity(stack)) {
                    BlockState blockstate1 = worldIn.getBlockState(blockpos);
                    if (blockstate1.getBlock() instanceof IBucketPickupHandler) {
                        Fluid fluid = ((IBucketPickupHandler) blockstate1.getBlock()).pickupFluid(worldIn, blockpos, blockstate1);
                        if (fluid != Fluids.EMPTY && canAcceptFluid(stack, fluid)) {
                            playerIn.addStat(Stats.ITEM_USED.get(this));

                            SoundEvent soundevent = this.getFluid(stack).getAttributes().getEmptySound();
                            if (soundevent == null)
                                soundevent = fluid.isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL;
                            playerIn.playSound(soundevent, 1.0F, 1.0F);
                            ItemStack itemstack1 = this.fillBucket(stack, playerIn, fluid);
                            if (!worldIn.isRemote) {
                                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayerEntity) playerIn, new ItemStack(fluid.getFilledBucket()));
                            }

                            return new ActionResult<>(ActionResultType.SUCCESS, itemstack1);
                        }
                    }

                }
                BlockState blockstate = worldIn.getBlockState(blockpos);
                BlockPos blockpos1 = blockstate.getBlock() instanceof ILiquidContainer && this.getFluid(stack) == Fluids.WATER ? blockpos : blockraytraceresult.getPos().offset(blockraytraceresult.getFace());
                if (this.tryPlaceContainedLiquid(playerIn, worldIn, blockpos1, blockraytraceresult, stack)) {
                    this.onLiquidPlaced(worldIn, stack, blockpos1);
                    if (playerIn instanceof ServerPlayerEntity) {
                        CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayerEntity) playerIn, blockpos1, stack);
                    }

                    playerIn.addStat(Stats.ITEM_USED.get(this));
                    return new ActionResult<>(ActionResultType.SUCCESS, this.emptyBucket(stack, playerIn));
                } else {
                    return new ActionResult<>(ActionResultType.FAIL, stack);
                }
            } else {
                return new ActionResult<>(ActionResultType.FAIL, stack);
            }
        }
    }

    public void onLiquidPlaced(World worldIn, ItemStack p_203792_2_, BlockPos pos) {
    }

    private ItemStack fillBucket(ItemStack stack, PlayerEntity player, Fluid fluid) {
        int capacity = getCapacity(stack);
        int fullness = getFullness(stack);
        if (fullness == 0) {
            setFluid(stack, fluid);
        }

        if (fullness + 1 <= capacity)
            setFullness(stack, fullness + 1);
        return stack;
    }

    public boolean tryPlaceContainedLiquid(@Nullable PlayerEntity player, World worldIn, BlockPos posIn, @Nullable BlockRayTraceResult raytrace, ItemStack stack) {
        if (!(this.getFluid(stack) instanceof FlowingFluid)) {
            return false;
        } else {
            BlockState blockstate = worldIn.getBlockState(posIn);
            Material material = blockstate.getMaterial();
            boolean flag = !material.isSolid();
            boolean flag1 = material.isReplaceable();
            if (worldIn.isAirBlock(posIn) || flag || flag1 || blockstate.getBlock() instanceof ILiquidContainer && ((ILiquidContainer) blockstate.getBlock()).canContainFluid(worldIn, posIn, blockstate, this.getFluid(stack))) {
                if (worldIn.dimension.doesWaterVaporize() && this.getFluid(stack).isIn(FluidTags.WATER)) {
                    int i = posIn.getX();
                    int j = posIn.getY();
                    int k = posIn.getZ();
                    worldIn.playSound(player, posIn, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.6F + (worldIn.rand.nextFloat() - worldIn.rand.nextFloat()) * 0.8F);

                    for (int l = 0; l < 8; ++l) {
                        worldIn.addParticle(ParticleTypes.LARGE_SMOKE, (double) i + Math.random(), (double) j + Math.random(), (double) k + Math.random(), 0.0D, 0.0D, 0.0D);
                    }
                } else if (blockstate.getBlock() instanceof ILiquidContainer && this.getFluid(stack) == Fluids.WATER) {
                    if (((ILiquidContainer) blockstate.getBlock()).receiveFluid(worldIn, posIn, blockstate, ((FlowingFluid) this.getFluid(stack)).getStillFluidState(false))) {
                        this.playEmptySound(player, worldIn, posIn, stack);
                    }
                } else {
                    if (!worldIn.isRemote && (flag || flag1) && !material.isLiquid()) {
                        worldIn.destroyBlock(posIn, true);
                    }

                    this.playEmptySound(player, worldIn, posIn, stack);
                    worldIn.setBlockState(posIn, this.getFluid(stack).getDefaultState().getBlockState(), 11);
                }

                return true;
            } else {
                return raytrace != null && this.tryPlaceContainedLiquid(player, worldIn, raytrace.getPos().offset(raytrace.getFace()), null, stack);
            }
        }
    }

    protected void playEmptySound(@Nullable PlayerEntity player, IWorld worldIn, BlockPos pos, ItemStack stack) {
        SoundEvent soundevent = this.getFluid(stack).getAttributes().getEmptySound();
        if (soundevent == null)
            soundevent = this.getFluid(stack).isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY;
        worldIn.playSound(player, pos, soundevent, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new StringTextComponent("Fluid: ").appendSibling(getFluid(stack).getDefaultState().getBlockState().getBlock().getNameTextComponent()));
        tooltip.add(new StringTextComponent("Capacity: " + getCapacity(stack)));
        tooltip.add(new StringTextComponent("Fullness: " + getFullness(stack)));
    }

    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        if (getFluid(stack) == Fluids.EMPTY)
            return super.getDisplayName(stack);
        return super.getDisplayName(stack).appendSibling(new StringTextComponent(" (").appendSibling(getFluid(stack).getDefaultState().getBlockState().getBlock().getNameTextComponent()).appendSibling(new StringTextComponent(")")));
    }

    public Fluid getFluid(ItemStack stack) {
        CompoundNBT tag = stack.getOrCreateChildTag("BigBuckets");

        if (tag.getString("Fluid").equals(""))
            return Fluids.EMPTY;

        ResourceLocation loc = new ResourceLocation(tag.getString("Fluid"));
        return ForgeRegistries.FLUIDS.getValue(loc);
    }

    public int getCapacity(ItemStack stack) {
        CompoundNBT tag = stack.getOrCreateChildTag("BigBuckets");
        return tag.getInt("Capacity");
    }

    public int getFullness(ItemStack stack) {
        CompoundNBT tag = stack.getOrCreateChildTag("BigBuckets");
        return tag.getInt("Fullness");
    }

    public void setFluid(ItemStack stack, Fluid fluid) {
        CompoundNBT tag = stack.getOrCreateChildTag("BigBuckets");

        String loc = fluid.getRegistryName().toString();
        tag.putString("Fluid", loc);
    }

    public void setCapacity(ItemStack stack, int capacity) {
        CompoundNBT tag = stack.getOrCreateChildTag("BigBuckets");
        tag.putInt("Capacity", capacity);
    }

    public void setFullness(ItemStack stack, int fullness) {
        CompoundNBT tag = stack.getOrCreateChildTag("BigBuckets");
        tag.putInt("Fullness", fullness);
    }

    public boolean canAcceptFluid(ItemStack stack, Fluid fluid) {
        return getFullness(stack) != getCapacity(stack) && (getFluid(stack) == fluid || getFluid(stack) == Fluids.EMPTY);
    }
}

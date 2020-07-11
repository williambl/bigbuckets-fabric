package com.williambl.bigbuckets;

import com.williambl.bigbuckets.hooks.CustomDurabilityItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.block.FluidFillable;
import net.minecraft.block.Material;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.tag.FluidTags;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.RayTraceContext;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import javax.annotation.Nullable;
import java.util.List;

public class BigBucketItem extends Item implements CustomDurabilityItem {

    public BigBucketItem(Settings builder) {
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
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        HitResult hitresult = rayTrace(world, user, this.getFullness(stack) == this.getCapacity(stack) ? RayTraceContext.FluidHandling.NONE : RayTraceContext.FluidHandling.SOURCE_ONLY);
        if (hitresult.getType() == HitResult.Type.MISS) {
            return new TypedActionResult<>(ActionResult.PASS, stack);
        } else if (hitresult.getType() != HitResult.Type.BLOCK) {
            return new TypedActionResult<>(ActionResult.PASS, stack);
        } else {
            BlockHitResult blockraytraceresult = (BlockHitResult) hitresult;
            BlockPos blockpos = blockraytraceresult.getBlockPos();
            if (world.canPlayerModifyAt(user, blockpos) && user.canPlaceOn(blockpos, blockraytraceresult.getSide(), stack)) {
                if (this.getFullness(stack) != this.getCapacity(stack)) {
                    BlockState blockstate1 = world.getBlockState(blockpos);
                    if (blockstate1.getBlock() instanceof FluidDrainable) {
                        Fluid fluid = ((FluidDrainable) blockstate1.getBlock()).tryDrainFluid(world, blockpos, blockstate1);
                        if (fluid != Fluids.EMPTY && canAcceptFluid(stack, fluid)) {
                            user.incrementStat(Stats.USED.getOrCreateStat(this));

                            user.playSound(fluid.isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL, 1.0F, 1.0F);
                            ItemStack itemstack1 = this.fillBucket(stack, user, fluid);
                            if (!world.isClient) {
                                Criteria.FILLED_BUCKET.trigger((ServerPlayerEntity) user, new ItemStack(fluid.getBucketItem()));
                            }

                            return new TypedActionResult<>(ActionResult.SUCCESS, itemstack1);
                        }
                    }

                }
                BlockState blockstate = world.getBlockState(blockpos);
                BlockPos blockpos1 = blockstate.getBlock() instanceof FluidDrainable && this.getFluid(stack) == Fluids.WATER ? blockpos : blockraytraceresult.getBlockPos().offset(blockraytraceresult.getSide());
                if (this.tryPlaceContainedLiquid(user, world, blockpos1, blockraytraceresult, stack)) {
                    this.onLiquidPlaced(world, stack, blockpos1);
                    if (user instanceof ServerPlayerEntity) {
                        Criteria.PLACED_BLOCK.trigger((ServerPlayerEntity) user, blockpos1, stack);
                    }

                    user.incrementStat(Stats.USED.getOrCreateStat(this));
                    return new TypedActionResult<>(ActionResult.SUCCESS, this.emptyBucket(stack, user));
                } else {
                    return new TypedActionResult<>(ActionResult.FAIL, stack);
                }
            } else {
                return new TypedActionResult<>(ActionResult.FAIL, stack);
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

    public boolean tryPlaceContainedLiquid(@Nullable PlayerEntity player, World worldIn, BlockPos posIn, @Nullable BlockHitResult raytrace, ItemStack stack) {
        if (!(this.getFluid(stack) instanceof FlowableFluid)) {
            return false;
        } else {
            BlockState blockstate = worldIn.getBlockState(posIn);
            Material material = blockstate.getMaterial();
            boolean flag = !material.isSolid();
            boolean flag1 = material.isReplaceable();
            if (worldIn.isAir(posIn) || flag || flag1 || blockstate.getBlock() instanceof FluidFillable && ((FluidFillable) blockstate.getBlock()).canFillWithFluid(worldIn, posIn, blockstate, this.getFluid(stack))) {
                if (worldIn.getDimension().isUltrawarm() && this.getFluid(stack).isIn(FluidTags.WATER)) {
                    int i = posIn.getX();
                    int j = posIn.getY();
                    int k = posIn.getZ();
                    worldIn.playSound(player, posIn, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.6F + (worldIn.random.nextFloat() - worldIn.random.nextFloat()) * 0.8F);

                    for (int l = 0; l < 8; ++l) {
                        worldIn.addParticle(ParticleTypes.LARGE_SMOKE, (double) i + Math.random(), (double) j + Math.random(), (double) k + Math.random(), 0.0D, 0.0D, 0.0D);
                    }
                } else if (blockstate.getBlock() instanceof FluidFillable && this.getFluid(stack) == Fluids.WATER) {
                    if (((FluidFillable) blockstate.getBlock()).tryFillWithFluid(worldIn, posIn, blockstate, ((FlowableFluid) this.getFluid(stack)).getStill(false))) {
                        this.playEmptySound(player, worldIn, posIn, stack);
                    }
                } else {
                    if (!worldIn.isClient && (flag || flag1) && !material.isLiquid()) {
                        worldIn.breakBlock(posIn, true);
                    }

                    this.playEmptySound(player, worldIn, posIn, stack);
                    worldIn.setBlockState(posIn, this.getFluid(stack).getDefaultState().getBlockState(), 11);
                }

                return true;
            } else {
                return raytrace != null && this.tryPlaceContainedLiquid(player, worldIn, raytrace.getBlockPos().offset(raytrace.getSide()), null, stack);
            }
        }
    }

    protected void playEmptySound(@Nullable PlayerEntity player, WorldAccess worldIn, BlockPos pos, ItemStack stack) {
        SoundEvent soundevent = this.getFluid(stack).isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_EMPTY_LAVA : SoundEvents.ITEM_BUCKET_EMPTY;
        worldIn.playSound(player, pos, soundevent, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendTooltip(ItemStack stack, @Nullable World worldIn, List<Text> tooltip, TooltipContext flagIn) {
        super.appendTooltip(stack, worldIn, tooltip, flagIn);
        tooltip.add(new LiteralText("Fluid: ").append(getFluid(stack).getDefaultState().getBlockState().getBlock().getName()));
        tooltip.add(new LiteralText("Capacity: " + getCapacity(stack)));
        tooltip.add(new LiteralText("Fullness: " + getFullness(stack)));
    }

    @Override
    @Environment(EnvType.CLIENT)
    public Text getName(ItemStack stack) {
        if (getFluid(stack) == Fluids.EMPTY)
            return super.getName(stack);
        return super.getName(stack).copy().append(new LiteralText(" (").copy().append(getFluid(stack).getDefaultState().getBlockState().getBlock().getName()).append(new LiteralText(")")));
    }

    @Override
    public void appendStacks(ItemGroup group, DefaultedList<ItemStack> stacks) {
        if (isIn(group)) {
            ItemStack stack = new ItemStack(this);
            setCapacity(stack, 2);
            stacks.add(stack);
        }
    }

    public Fluid getFluid(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");

        if (tag.getString("Fluid").equals(""))
            return Fluids.EMPTY;

        Identifier loc = new Identifier(tag.getString("Fluid"));
        return Registry.FLUID.get(loc);
    }

    public int getCapacity(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        return tag.getInt("Capacity");
    }

    public int getFullness(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        return tag.getInt("Fullness");
    }

    public void setFluid(ItemStack stack, Fluid fluid) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");

        String loc = Registry.FLUID.getId(fluid).toString();
        tag.putString("Fluid", loc);
    }

    public void setCapacity(ItemStack stack, int capacity) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        tag.putInt("Capacity", capacity);
    }

    public void setFullness(ItemStack stack, int fullness) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        tag.putInt("Fullness", fullness);
    }

    public boolean canAcceptFluid(ItemStack stack, Fluid fluid) {
        return getFullness(stack) != getCapacity(stack) && (getFluid(stack) == fluid || getFluid(stack) == Fluids.EMPTY);
    }

    @Override
    public boolean shouldShowDurability(ItemStack stack) {
        return getFluid(stack) != Fluids.EMPTY;
    }

    @Override
    public int getMaxDurability(ItemStack stack) {
        return getCapacity(stack);
    }

    @Override
    public int getDurability(ItemStack stack) {
        return getFullness(stack);
    }

    @Override
    public int getDurabilityColor(ItemStack stack) {
        float f = getDurability(stack);
        float g = getMaxDurability(stack);
        float h = Math.max(0.0F, f / g);
        return MathHelper.hsvToRgb(h / 3.0F, 1.0F, 1.0F);
    }
}

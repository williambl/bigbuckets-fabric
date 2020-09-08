package com.williambl.bigbuckets;

import alexiil.mc.lib.attributes.AttributeProviderItem;
import alexiil.mc.lib.attributes.ItemAttributeList;
import alexiil.mc.lib.attributes.Simulation;
import alexiil.mc.lib.attributes.fluid.FluidAttributes;
import alexiil.mc.lib.attributes.fluid.FluidExtractable;
import alexiil.mc.lib.attributes.fluid.FluidInsertable;
import alexiil.mc.lib.attributes.fluid.FluidVolumeUtil;
import alexiil.mc.lib.attributes.fluid.amount.FluidAmount;
import alexiil.mc.lib.attributes.fluid.filter.ConstantFluidFilter;
import alexiil.mc.lib.attributes.fluid.item.ItemBasedSingleFluidInv;
import alexiil.mc.lib.attributes.fluid.volume.FluidKey;
import alexiil.mc.lib.attributes.fluid.volume.FluidKeys;
import alexiil.mc.lib.attributes.fluid.volume.FluidVolume;
import alexiil.mc.lib.attributes.fluid.world.FluidWorldUtil;
import alexiil.mc.lib.attributes.misc.LimitedConsumer;
import alexiil.mc.lib.attributes.misc.PlayerInvUtil;
import alexiil.mc.lib.attributes.misc.Reference;
import com.williambl.bigbuckets.hooks.CustomDurabilityItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CauldronBlock;
import net.minecraft.block.FluidFillable;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FlowableFluid;
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
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

import static net.minecraft.world.RaycastContext.FluidHandling;

public class BigBucketItem extends Item implements CustomDurabilityItem, AttributeProviderItem {

    public BigBucketItem(Settings builder) {
        super(builder);
    }

    @Override
    @Nonnull
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        Reference<ItemStack> stack = PlayerInvUtil.referenceHand(player, hand);
        BlockHitResult raytraceresult = raycast(world, player, getFullness(stack.get()) == getCapacity(stack.get()) ? FluidHandling.NONE : FluidHandling.SOURCE_ONLY);

        if (raytraceresult.getType() != HitResult.Type.BLOCK)
            return new TypedActionResult<>(ActionResult.PASS, stack.get());

        BlockPos blockPos = raytraceresult.getBlockPos().toImmutable();

        if (
                world.canPlayerModifyAt(player, blockPos)
                        && player.canPlaceOn(blockPos, raytraceresult.getSide(), stack.get())
        ) {
            BlockState blockState = world.getBlockState(blockPos);

            if (tryFill(stack, blockState, world, blockPos, player, raytraceresult))
                return new TypedActionResult<>(ActionResult.SUCCESS, stack.get());

            if (tryEmpty(player, world, blockPos, raytraceresult, stack))
                return new TypedActionResult<>(ActionResult.SUCCESS, stack.get());
        }
        return new TypedActionResult<>(ActionResult.FAIL, stack.get());
    }

    private boolean tryFill(Reference<ItemStack> stack, BlockState blockstate, World world, BlockPos pos, PlayerEntity player, BlockHitResult raytrace) {
        if (getFullness(stack.get()).isLessThan(getCapacity(stack.get()))) {
            FluidVolume simVol = FluidWorldUtil.drain(world, pos, Simulation.SIMULATE);
            if (simVol.amount().isPositive() && canAcceptFluid(stack, simVol)) {
                SoundEvent soundevent = simVol.getRawFluid().isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL;
                player.playSound(soundevent, 1.0F, 1.0F);
                player.incrementStat(Stats.USED.getOrCreateStat(this));
                fill(stack, FluidWorldUtil.drain(world, pos, Simulation.ACTION));
                return true;
            }
            if (blockstate.getBlock() instanceof CauldronBlock && blockstate.get(CauldronBlock.LEVEL) > 0 && canAcceptFluid(stack, FluidKeys.WATER.withAmount(FluidAmount.BOTTLE))) {
                player.playSound(SoundEvents.ITEM_BUCKET_FILL, 1.0F, 1.0F);
                FluidVolume volume = FluidKeys.WATER.withAmount(FluidAmount.BOTTLE.mul(blockstate.get(CauldronBlock.LEVEL)));
                FluidVolume excess = fill(stack, volume);
                world.setBlockState(pos, blockstate.with(CauldronBlock.LEVEL, excess.amount().div(FluidAmount.BOTTLE).as1620()/1620));
                player.incrementStat(Stats.USE_CAULDRON);
                player.incrementStat(Stats.USED.getOrCreateStat(this));
                return true;
            }
            FluidExtractable tank = FluidAttributes.EXTRACTABLE.get(world, pos);
            if (tank.couldExtractAnything()) {
                FluidVolume volume = FluidVolumeUtil.move(tank, FluidAttributes.INSERTABLE.get(stack), getCapacity(stack.get()).min(getFullness(stack.get())));
                return volume.amount().isPositive();
            }
        }
        return false;
    }

    public boolean tryEmpty(PlayerEntity player, World world, BlockPos pos, @Nullable BlockHitResult raytrace, Reference<ItemStack> stack) {
        if (!(getFluid(stack.get()).getRawFluid() instanceof FlowableFluid))
            return false;

        FluidKey fluid = getFluid(stack.get());
        BlockState blockstate = world.getBlockState(pos);
        FluidInsertable tank = FluidAttributes.INSERTABLE.get(world, pos);
        boolean done = false;
        if (blockstate.getBlock() instanceof FluidFillable && fluid.getRawFluid().isIn(FluidTags.WATER)) {
            if (((FluidFillable) blockstate.getBlock()).tryFillWithFluid(world, pos, blockstate, ((FlowableFluid) fluid.getRawFluid()).getStill(false))) {
                playEmptySound(player, world, pos);
                drain(stack, FluidAmount.BUCKET);
            }
            done = true;
        } else if (blockstate.getBlock() instanceof CauldronBlock && fluid.getRawFluid().isIn(FluidTags.WATER)) {
            int level = blockstate.get(CauldronBlock.LEVEL);
            if (level < 3) {
                playEmptySound(player, world, pos);
                FluidVolume volume = drain(stack, FluidAmount.BOTTLE.mul(3-level));
                world.setBlockState(pos, blockstate.with(CauldronBlock.LEVEL, volume.amount().div(FluidAmount.BOTTLE).as1620()/1620));
                player.incrementStat(Stats.FILL_CAULDRON);
            }
            done = true;
        } else if (!tank.attemptInsertion(getFluidVolume(stack.get()), Simulation.SIMULATE).amount().equals(getFullness(stack.get()))) {
            playEmptySound(player, world, pos);
            FluidVolumeUtil.move(FluidAttributes.EXTRACTABLE.get(stack), tank);
            done = true;
        } else if (getFullness(stack.get()).isGreaterThanOrEqual(FluidAmount.BUCKET)) {
            if (!world.isClient && blockstate.canBucketPlace(fluid.getRawFluid()) && !blockstate.getMaterial().isLiquid()) {
                if (world.getDimension().isUltrawarm() && fluid.getRawFluid().isIn(FluidTags.WATER)) {
                    world.playSound(player, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);
                    drain(stack, FluidAmount.BUCKET);
                    for (int i = 0; i < 8; ++i)
                        world.addParticle(ParticleTypes.LARGE_SMOKE, (double) pos.getX() + world.random.nextDouble(), (double) pos.getY() + world.random.nextDouble(), (double) pos.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
                } else {
                    world.removeBlock(pos, true);
                    world.setBlockState(pos, fluid.getRawFluid().getDefaultState().getBlockState(), 1 | 2 | 8);
                }
                playEmptySound(player, world, pos);
                drain(stack, FluidAmount.BUCKET);
                done = true;
            }
        }

        if (done || raytrace == null) {
            if (player instanceof ServerPlayerEntity) {
                Criteria.PLACED_BLOCK.trigger((ServerPlayerEntity) player, pos, stack.get());
            }
            player.incrementStat(Stats.USED.getOrCreateStat(this));
            return true;
        }
        return tryEmpty(player, world, raytrace.getBlockPos().offset(raytrace.getSide()), null, stack);
    }

    protected void playEmptySound(@Nullable PlayerEntity player, WorldAccess worldIn, BlockPos pos) {
        SoundEvent soundevent = SoundEvents.ITEM_BUCKET_EMPTY;
        worldIn.playSound(player, pos, soundevent, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendTooltip(ItemStack stack, @Nullable World worldIn, List<Text> tooltip, TooltipContext flagIn) {
        super.appendTooltip(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslatableText("item.bigbuckets.bigbucket.desc.fluid", getFluidName(getFluid(stack))));
        tooltip.add(new TranslatableText("item.bigbuckets.bigbucket.desc.capacity", getCapacity(stack).toDisplayString()));
        tooltip.add(new TranslatableText("item.bigbuckets.bigbucket.desc.fullness", getFullness(stack).toDisplayString()));
    }

    @Override
    @Nonnull
    public Text getName(ItemStack stack) {
        if (getFluid(stack) == FluidKeys.EMPTY)
            return super.getName(stack);
        return super.getName(stack).shallowCopy().append(new LiteralText(" (").append(getFluid(stack).name).append(new LiteralText(")")));
    }

    @Override
    public void appendStacks(ItemGroup itemGroup, DefaultedList<ItemStack> itemStacks) {
        if (isIn(itemGroup)) {
            ItemStack stack = new ItemStack(this);
            setCapacity(stack, FluidAmount.BUCKET.mul(16));
            itemStacks.add(stack);
        }
    }

    public boolean canAcceptFluid(Reference<ItemStack> stack, FluidVolume volume) {
        final FluidInsertable inv = FluidAttributes.INSERTABLE.get(stack);
        return !inv.attemptInsertion(volume, Simulation.SIMULATE).amount().equals(volume.amount());
    }

    /*
     * PLATFORM DEPENDENT CODE
     */

    @PlatformDependent
    public FluidVolume getFluidVolume(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        return FluidVolume.fromTag(tag.getCompound("FluidVolume"));
    }

    @PlatformDependent
    public FluidKey getFluid(ItemStack stack) {
        return getFluidVolume(stack).getFluidKey();
    }

    @PlatformDependent
    public FluidAmount getCapacity(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        return FluidAmount.fromNbt(tag.getCompound("Capacity"));
    }

    @PlatformDependent
    public FluidAmount getFullness(ItemStack stack) {
        return getFluidVolume(stack).amount();
    }

    @PlatformDependent
    public void setCapacity(ItemStack stack, FluidAmount capacity) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        tag.put("Capacity", capacity.toNbt());
    }

    @PlatformDependent
    public FluidVolume fill(Reference<ItemStack> stack, FluidVolume volume) {
        final FluidInsertable inv = FluidAttributes.INSERTABLE.get(stack);
        return inv.attemptInsertion(volume, Simulation.ACTION);
    }

    @PlatformDependent
    public FluidVolume drain(Reference<ItemStack> stack, FluidAmount drainAmount) {
        final FluidExtractable inv = FluidAttributes.EXTRACTABLE.get(stack);
        return inv.attemptExtraction(ConstantFluidFilter.ANYTHING, drainAmount, Simulation.ACTION);
    }

    public void setFluidVolume(ItemStack stack, FluidVolume volume) {
        CompoundTag tag = stack.getOrCreateSubTag("BigBuckets");
        tag.put("FluidVolume", volume.toTag());
    }

    /*
     * FABRIC SPECIFIC START
     */

    @Override
    public boolean shouldShowDurability(ItemStack stack) {
        return getFluid(stack) != FluidKeys.EMPTY;
    }

    @Override
    public int getMaxDurability(ItemStack stack) {
        return getCapacity(stack).as1620();
    }

    @Override
    public int getDurability(ItemStack stack) {
        return getFullness(stack).as1620();
    }

    @Override
    public int getDurabilityColor(ItemStack stack) {
        float f = getDurability(stack);
        float g = getMaxDurability(stack);
        float h = Math.max(0.0F, f / g);
        return MathHelper.hsvToRgb(h / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void addAllAttributes(Reference<ItemStack> stack, LimitedConsumer<ItemStack> excess, ItemAttributeList<?> to) {
        System.out.println(stack.getClass().getName());
        to.offer(new BigBucketTank(stack, excess));
    }

    private Text getFluidName(FluidKey key) {
        if (key.isEmpty())
            return Blocks.AIR.getName();
        else return key.name;
    }

    private class BigBucketTank extends ItemBasedSingleFluidInv {
        protected BigBucketTank(Reference<ItemStack> stackRef, LimitedConsumer<ItemStack> excessStacks) {
            super(stackRef, excessStacks);
        }

        @Override
        protected boolean isInvalid(ItemStack stack) {
            return false;
        }

        @Override
        protected HeldFluidInfo getInfo(ItemStack stack) {
            return new HeldFluidInfo(BigBucketItem.this.getFluidVolume(stack), BigBucketItem.this.getCapacity(stack));
        }

        @Nullable
        @Override
        protected ItemStack writeToStack(ItemStack stack, FluidVolume fluid) {
            BigBucketItem.this.setFluidVolume(stack, fluid);
            return stack;
        }
    }
}

package com.example.landhurl;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Empty: hold right-click to charge, release to rip a cube of land out of the ground (2x2x2 up to 9x9x9).
 * Loaded: hold right-click to charge, release to hurl it.
 */
public class LandGauntletItem extends Item {

    private static final int MAX_USE = 72000;

    public LandGauntletItem(Properties properties) {
        super(properties);
    }

    public static boolean isLoaded(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("Land", Tag.TAG_COMPOUND);
    }

    private static int sizeFor(int chargeTicks) {
        return Mth.clamp(2 + chargeTicks / Config.TICKS_PER_SIZE.get(), 2, Config.MAX_SIZE.get());
    }

    private static float powerFor(int chargeTicks) {
        return Mth.clamp(chargeTicks / 30.0F, 0.2F, 1.0F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return MAX_USE;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isLoaded(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isLoaded(stack)) {
            int size = stack.getTag().getCompound("Land").getInt("Size");
            tooltip.add(Component.literal("Loaded: " + size + "x" + size + "x" + size + " chunk").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("Hold right-click to charge, release to throw").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Hold right-click on land to charge, release to pick up").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        int charge = MAX_USE - remaining;
        if (charge % 2 != 0) {
            return;
        }
        Component message;
        if (isLoaded(stack)) {
            int size = stack.getTag().getCompound("Land").getInt("Size");
            message = Component.literal("Throw power: " + (int) (powerFor(charge) * 100) + "%   (" + size + "x" + size + "x" + size + " chunk)")
                    .withStyle(ChatFormatting.GOLD);
        } else {
            int size = sizeFor(charge);
            message = Component.literal("Pick up size: " + size + "x" + size + "x" + size).withStyle(ChatFormatting.GREEN);
        }
        player.displayClientMessage(message, true);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int charge = MAX_USE - timeLeft;
        if (charge < 4) {
            return;
        }
        if (isLoaded(stack)) {
            throwChunk(serverLevel, player, stack, charge);
        } else {
            pickUp(serverLevel, player, stack, sizeFor(charge));
        }
        player.getCooldowns().addCooldown(this, 10);
    }

    private void pickUp(ServerLevel level, Player player, ItemStack stack, int size) {
        HitResult hit = player.pick(14.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal("Aim at the ground or a wall first.").withStyle(ChatFormatting.RED), true);
            return;
        }
        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos target = blockHit.getBlockPos();
        int half = (size - 1) / 2;
        int ox = target.getX() - half;
        int oz = target.getZ() - half;
        int oy = blockHit.getDirection() == Direction.UP ? target.getY() - size + 1 : target.getY() - half;

        List<BlockState> palette = new ArrayList<>();
        byte[] cells = new byte[size * size * size];
        List<BlockPos> toRemove = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int k = 0; k < size; k++) {
                    BlockPos pos = new BlockPos(ox + i, oy + j, oz + k);
                    if (level.isOutsideBuildHeight(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null
                            || state.getDestroySpeed(level, pos) < 0.0F || !level.mayInteract(player, pos)) {
                        continue;
                    }
                    int index = palette.indexOf(state);
                    if (index < 0) {
                        if (palette.size() >= 250) {
                            continue;
                        }
                        palette.add(state);
                        index = palette.size() - 1;
                    }
                    cells[(i * size + j) * size + k] = (byte) (index + 1);
                    toRemove.add(pos);
                }
            }
        }

        if (toRemove.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing to pick up there.").withStyle(ChatFormatting.RED), true);
            return;
        }

        int particles = 0;
        for (BlockPos pos : toRemove) {
            if (particles++ < 40) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        LandData data = new LandData(size, palette.toArray(new BlockState[0]), cells);
        stack.getOrCreateTag().put("Land", data.toTag());
        level.playSound(null, player.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
        player.displayClientMessage(Component.literal("Picked up a " + size + "x" + size + "x" + size
                + " chunk (" + toRemove.size() + " blocks)").withStyle(ChatFormatting.GREEN), true);
    }

    private void throwChunk(ServerLevel level, Player player, ItemStack stack, int charge) {
        LandData data = LandData.fromTag(stack.getTag().getCompound("Land"), level.holderLookup(Registries.BLOCK));
        stack.removeTagKey("Land");
        if (data == null) {
            return;
        }
        float power = powerFor(charge);
        ThrownLand projectile = new ThrownLand(level, player, data, power);
        Vec3 look = player.getLookAngle();
        projectile.setPos(player.getEyePosition().add(look.scale(data.size * 0.5D + 1.0D)));
        float speed = (0.6F + power * 1.6F) / (1.0F + data.size * 0.06F);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, speed, 0.3F);
        level.addFreshEntity(projectile);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.5F, 0.5F);
    }
}

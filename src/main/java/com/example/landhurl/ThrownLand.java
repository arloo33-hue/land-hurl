package com.example.landhurl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A flying cube of land. Hurts what it hits and explodes on impact; bigger cube = bigger boom. */
public class ThrownLand extends Projectile implements IEntityAdditionalSpawnData {

    public record Cell(int i, int j, int k, BlockState state) {
    }

    @Nullable
    private LandData data;
    private float power = 1.0F;
    @Nullable
    private List<Cell> shell;

    public ThrownLand(EntityType<? extends ThrownLand> type, Level level) {
        super(type, level);
    }

    public ThrownLand(Level level, LivingEntity owner, LandData data, float power) {
        this(LandHurlMod.THROWN_LAND.get(), level);
        this.setOwner(owner);
        this.data = data;
        this.power = power;
    }

    @Nullable
    public LandData getData() {
        return data;
    }

    /** Only the blocks on the outside of the cube are drawn (much cheaper for a 9x9x9). */
    public List<Cell> shell() {
        if (shell == null) {
            List<Cell> list = new ArrayList<>();
            if (data != null) {
                int s = data.size;
                for (int i = 0; i < s; i++) {
                    for (int j = 0; j < s; j++) {
                        for (int k = 0; k < s; k++) {
                            BlockState state = data.stateAt(i, j, k);
                            if (state == null) {
                                continue;
                            }
                            boolean exposed = data.stateAt(i + 1, j, k) == null || data.stateAt(i - 1, j, k) == null
                                    || data.stateAt(i, j + 1, k) == null || data.stateAt(i, j - 1, k) == null
                                    || data.stateAt(i, j, k + 1) == null || data.stateAt(i, j, k - 1) == null;
                            if (exposed) {
                                list.add(new Cell(i, j, k, state));
                            }
                        }
                    }
                }
            }
            shell = list;
        }
        return shell;
    }

    @Override
    protected void defineSynchedData() {
    }

    private AABB cubeAt(Vec3 center) {
        double half = data == null ? 0.5 : data.size * 0.4;
        return new AABB(center.x - half, center.y - half, center.z - half, center.x + half, center.y + half, center.z + half);
    }

    @Override
    public void tick() {
        super.tick();
        if (data == null) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }

        Vec3 motion = this.getDeltaMovement();

        if (!this.level().isClientSide) {
            for (int step = 1; step <= 3; step++) {
                Vec3 probe = this.position().add(motion.scale(step / 3.0));
                AABB box = cubeAt(probe);

                for (Entity e : this.level().getEntities(this, box, this::canHitEntity)) {
                    impact(e);
                    return;
                }
                if (this.tickCount > 4 && !this.level().noCollision(box)) {
                    impact(null);
                    return;
                }
            }
            if (this.getY() < this.level().getMinBuildHeight() - 20 || this.tickCount > 400) {
                impact(null);
                return;
            }
        }

        this.setPos(this.position().add(motion));
        this.setDeltaMovement(motion.scale(0.995).add(0.0, -0.045, 0.0));
    }

    private void impact(@Nullable Entity hit) {
        if (!(this.level() instanceof ServerLevel serverLevel) || data == null) {
            return;
        }
        int size = data.size;
        Vec3 pos = this.position();

        if (hit != null) {
            float damage = (float) (size * size * 0.3D * (0.5D + 0.5D * power));
            hit.hurt(serverLevel.damageSources().thrown(this, this.getOwner()), damage);
        }

        float radius = (float) ((1.0D + size * 0.7D) * (0.6D + 0.4D * power) * Config.EXPLOSION_SCALE.get());
        if (radius > 0.0F) {
            serverLevel.explode(this, pos.x, pos.y, pos.z, radius,
                    Config.BREAK_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        serverLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.6F);

        if (Config.DROP_BLOCKS.get()) {
            Map<Item, Integer> counts = new HashMap<>();
            for (byte cell : data.cells) {
                int v = cell & 0xFF;
                if (v == 0 || v > data.palette.length) {
                    continue;
                }
                Item item = data.palette[v - 1].getBlock().asItem();
                if (item != net.minecraft.world.item.Items.AIR) {
                    counts.merge(item, 1, Integer::sum);
                }
            }
            BlockPos at = BlockPos.containing(pos);
            int stacks = 0;
            for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
                int remaining = Math.max(1, entry.getValue() / 2);
                while (remaining > 0 && stacks < 40) {
                    int n = Math.min(remaining, entry.getKey().getMaxStackSize());
                    Block_pop(serverLevel, at, new ItemStack(entry.getKey(), n));
                    remaining -= n;
                    stacks++;
                }
            }
        }
        this.discard();
    }

    private static void Block_pop(ServerLevel level, BlockPos pos, ItemStack stack) {
        net.minecraft.world.level.block.Block.popResource(level, pos, stack);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (data != null) {
            tag.put("Land", data.toTag());
        }
        tag.putFloat("Power", power);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Land")) {
            data = LandData.fromTag(tag.getCompound("Land"), this.level().holderLookup(Registries.BLOCK));
        }
        power = tag.getFloat("Power");
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeBoolean(data != null);
        if (data != null) {
            data.write(buf);
        }
        buf.writeFloat(power);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            data = LandData.read(buf);
            shell = null;
        }
        power = buf.readFloat();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

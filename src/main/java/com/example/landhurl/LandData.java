package com.example.landhurl;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** A cube of blocks: size x size x size cells; cell value 0 = empty, n = palette[n-1]. */
public final class LandData {

    public final int size;
    public final BlockState[] palette;
    public final byte[] cells;

    public LandData(int size, BlockState[] palette, byte[] cells) {
        this.size = size;
        this.palette = palette;
        this.cells = cells;
    }

    public int index(int i, int j, int k) {
        return (i * size + j) * size + k;
    }

    @Nullable
    public BlockState stateAt(int i, int j, int k) {
        if (i < 0 || j < 0 || k < 0 || i >= size || j >= size || k >= size) {
            return null;
        }
        int v = cells[index(i, j, k)] & 0xFF;
        return v == 0 || v > palette.length ? null : palette[v - 1];
    }

    public int count() {
        int n = 0;
        for (byte c : cells) {
            if (c != 0) {
                n++;
            }
        }
        return n;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", size);
        ListTag list = new ListTag();
        for (BlockState state : palette) {
            list.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", list);
        tag.putByteArray("Cells", cells);
        return tag;
    }

    @Nullable
    public static LandData fromTag(CompoundTag tag, HolderGetter<Block> blocks) {
        int size = tag.getInt("Size");
        if (size < 1 || size > 9) {
            return null;
        }
        byte[] cells = tag.getByteArray("Cells");
        if (cells.length != size * size * size) {
            return null;
        }
        ListTag list = tag.getList("Palette", Tag.TAG_COMPOUND);
        BlockState[] palette = new BlockState[list.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = NbtUtils.readBlockState(blocks, list.getCompound(i));
        }
        return new LandData(size, palette, cells);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(size);
        buf.writeVarInt(palette.length);
        for (BlockState state : palette) {
            buf.writeVarInt(Block.getId(state));
        }
        buf.writeByteArray(cells);
    }

    public static LandData read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        BlockState[] palette = new BlockState[buf.readVarInt()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = Block.stateById(buf.readVarInt());
        }
        byte[] cells = buf.readByteArray();
        return new LandData(size, palette, cells);
    }
}

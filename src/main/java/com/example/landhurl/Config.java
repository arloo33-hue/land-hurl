package com.example.landhurl;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_SIZE;
    public static final ForgeConfigSpec.IntValue TICKS_PER_SIZE;
    public static final ForgeConfigSpec.DoubleValue EXPLOSION_SCALE;
    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue DROP_BLOCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MAX_SIZE = b.comment("Biggest chunk you can pick up (2-9, cube edge length).")
                .defineInRange("maxSize", 9, 2, 9);
        TICKS_PER_SIZE = b.comment("How many ticks of charging add one block to the cube (20 ticks = 1 second).")
                .defineInRange("ticksPerSize", 10, 1, 100);
        EXPLOSION_SCALE = b.comment("Multiplier for the explosion size when a chunk lands. 0 = no explosion.")
                .defineInRange("explosionScale", 1.0D, 0.0D, 10.0D);
        BREAK_BLOCKS = b.comment("true = the explosion destroys blocks. false = it only hurts creatures.")
                .define("breakBlocks", true);
        DROP_BLOCKS = b.comment("true = half of the thrown blocks drop as items where the chunk lands.")
                .define("dropBlocks", true);
        SPEC = b.build();
    }

    private Config() {
    }
}

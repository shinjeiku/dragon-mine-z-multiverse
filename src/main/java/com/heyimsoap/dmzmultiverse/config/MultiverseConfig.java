package com.heyimsoap.dmzmultiverse.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MultiverseConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue TELEPORT_COOLDOWN_SECONDS = BUILDER
            .comment("Cooldown after successful multiversal travel, in seconds.")
            .defineInRange("teleportCooldownSeconds", 10, 0, 3600);

    public static final ForgeConfigSpec.IntValue ARRIVAL_SEARCH_RADIUS = BUILDER
            .comment("Horizontal radius searched for a safe arrival point around the selected destination.")
            .defineInRange("arrivalSearchRadius", 4, 0, 16);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private MultiverseConfig() {
    }

    public static int teleportCooldownTicks() {
        return TELEPORT_COOLDOWN_SECONDS.get() * 20;
    }
}

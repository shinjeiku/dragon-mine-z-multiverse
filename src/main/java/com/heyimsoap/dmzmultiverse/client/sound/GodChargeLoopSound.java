package com.heyimsoap.dmzmultiverse.client.sound;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.heyimsoap.dmzmultiverse.forms.MultiverseForms;
import com.heyimsoap.dmzmultiverse.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public final class GodChargeLoopSound extends AbstractTickableSoundInstance {
    private static final float TARGET_VOLUME = 0.5F;
    private static final int FADE_TICKS = 5;
    private static final float FADE_STEP = TARGET_VOLUME / FADE_TICKS;

    private final Player player;

    public GodChargeLoopSound(Player player) {
        super(ModSounds.GOD_CHARGE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        updatePosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        updatePosition();

        boolean shouldPlay = !player.isRemoved() && StatsProvider.get(StatsCapability.INSTANCE, player)
                .map(stats -> {
                    var character = stats.getCharacter();
                    boolean hasAura = stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura();
                    return hasAura && MultiverseForms.isMultiverseForm(
                            character.getActiveFormGroup(), character.getActiveForm());
                })
                .orElse(false);

        if (shouldPlay) {
            volume = Math.min(TARGET_VOLUME, volume + FADE_STEP);
        } else {
            volume = Math.max(0.0F, volume - FADE_STEP);
            if (volume <= 0.0F) {
                stop();
            }
        }
    }

    private void updatePosition() {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }
}

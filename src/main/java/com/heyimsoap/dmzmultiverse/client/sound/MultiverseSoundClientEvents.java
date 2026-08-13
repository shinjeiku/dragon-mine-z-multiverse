package com.heyimsoap.dmzmultiverse.client.sound;

import com.dragonminez.common.init.sounds.AuraLoopSound;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.forms.MultiverseForms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = DMZMultiverse.MOD_ID, value = Dist.CLIENT)
public final class MultiverseSoundClientEvents {
    private static final ResourceLocation DMZ_CHARGE =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "ki_charge_loop");
    private static final Map<UUID, GodChargeLoopSound> ACTIVE_LOOPS = new HashMap<>();
    private static final Map<UUID, AuraLoopSound> MUTED_ORIGINALS = new HashMap<>();
    private static final Map<AuraLoopSound, UUID> PENDING_MUTES = new ConcurrentHashMap<>();

    private MultiverseSoundClientEvents() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance original = event.getOriginalSound();
        if (!(original instanceof AuraLoopSound auraLoop) || !DMZ_CHARGE.equals(original.getLocation())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : minecraft.level.players()) {
            double distance = player.distanceToSqr(original.getX(), original.getY(), original.getZ());
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        if (nearest == null || nearestDistance > 0.25D) {
            return;
        }

        if (shouldControl(nearest)) {
            UUID playerId = nearest.getUUID();
            AuraLoopSound previous = MUTED_ORIGINALS.put(playerId, auraLoop);
            PENDING_MUTES.put(auraLoop, playerId);

            if (previous != null && previous != auraLoop) {
                minecraft.getSoundManager().stop(previous);
            }
        }
    }

    /**
     * The source event runs on Minecraft's sound executor. Pausing here keeps
     * DMZ's original SoundInstance registered as active without allowing its
     * audio to mix with the Multiverse loop.
     */
    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (!(event.getSound() instanceof AuraLoopSound auraLoop)
                || !DMZ_CHARGE.equals(auraLoop.getLocation())) {
            return;
        }

        UUID playerId = PENDING_MUTES.remove(auraLoop);
        if (playerId == null) {
            return;
        }

        event.getChannel().setVolume(0.0F);
        event.getChannel().pause();

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> activateReplacement(minecraft, playerId, auraLoop));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE_LOOPS.values().forEach(minecraft.getSoundManager()::stop);
            MUTED_ORIGINALS.values().forEach(minecraft.getSoundManager()::stop);
            ACTIVE_LOOPS.clear();
            MUTED_ORIGINALS.clear();
            PENDING_MUTES.clear();
            return;
        }

        // SoundEngine.resume() unpauses every OpenAL source. Retire controlled
        // originals while the game is paused so DMZ creates a fresh, muted one
        // after gameplay resumes.
        if (minecraft.isPaused()) {
            MUTED_ORIGINALS.values().forEach(minecraft.getSoundManager()::stop);
            MUTED_ORIGINALS.clear();
        } else {
            MUTED_ORIGINALS.entrySet().removeIf(entry -> {
                Player player = minecraft.level.getPlayerByUUID(entry.getKey());
                if (player != null && shouldControl(player) && !entry.getValue().isStopped()) {
                    return false;
                }
                minecraft.getSoundManager().stop(entry.getValue());
                return true;
            });
        }

        ACTIVE_LOOPS.entrySet().removeIf(entry -> entry.getValue().isStopped()
                || !minecraft.getSoundManager().isActive(entry.getValue()));
    }

    private static void activateReplacement(Minecraft minecraft, UUID playerId, AuraLoopSound original) {
        if (minecraft.level == null || MUTED_ORIGINALS.get(playerId) != original) {
            minecraft.getSoundManager().stop(original);
            return;
        }

        Player player = minecraft.level.getPlayerByUUID(playerId);
        if (player == null || !shouldControl(player)) {
            MUTED_ORIGINALS.remove(playerId, original);
            minecraft.getSoundManager().stop(original);
            return;
        }

        GodChargeLoopSound existing = ACTIVE_LOOPS.get(playerId);
        if (existing == null || existing.isStopped()) {
            GodChargeLoopSound replacement = new GodChargeLoopSound(player);
            ACTIVE_LOOPS.put(playerId, replacement);
            minecraft.getSoundManager().play(replacement);
        }
    }

    private static boolean shouldControl(Player player) {
        return !player.isRemoved() && StatsProvider.get(StatsCapability.INSTANCE, player)
                .map(stats -> {
                    var character = stats.getCharacter();
                    boolean hasAura = stats.getStatus().isAuraActive() || stats.getStatus().isPermanentAura();
                    return hasAura && MultiverseForms.isMultiverseForm(
                            character.getActiveFormGroup(), character.getActiveForm());
                })
                .orElse(false);
    }
}

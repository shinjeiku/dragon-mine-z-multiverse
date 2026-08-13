package com.heyimsoap.dmzmultiverse.sound;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.forms.MultiverseForms;
import com.heyimsoap.dmzmultiverse.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = DMZMultiverse.MOD_ID)
public final class MultiverseSoundEvents {
    private static final ResourceLocation DMZ_TRANSFORM =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "transform_on");
    private static final ResourceLocation DMZ_INSTANT_TRANSFORM =
            ResourceLocation.fromNamespaceAndPath("dragonminez", "insta_form_on");
    private static final Map<UUID, MultiverseForms.FormKey> LAST_BASE_FORMS = new HashMap<>();

    private MultiverseSoundEvents() {
    }

    @SubscribeEvent
    public static void onLevelSound(PlayLevelSoundEvent.AtPosition event) {
        if (event.getLevel().isClientSide || event.getSource() != SoundSource.PLAYERS) {
            return;
        }

        ResourceLocation soundId = event.getSound().value().getLocation();
        if (!DMZ_TRANSFORM.equals(soundId) && !DMZ_INSTANT_TRANSFORM.equals(soundId)) {
            return;
        }

        Vec3 position = event.getPosition();
        ServerPlayer nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var player : event.getLevel().players()) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                continue;
            }
            double distance = serverPlayer.distanceToSqr(position);
            if (distance < nearestDistance) {
                nearest = serverPlayer;
                nearestDistance = distance;
            }
        }

        if (nearest == null || nearestDistance > 0.25D) {
            return;
        }

        ServerPlayer target = nearest;
        StatsProvider.get(StatsCapability.INSTANCE, target).ifPresent(stats -> {
            MultiverseForms.FormKey current = currentBaseForm(stats);
            MultiverseForms.FormKey previous = LAST_BASE_FORMS.get(target.getUUID());

            // DMZ also reuses transform_on for stack forms and Zenkai. Only a
            // changed addon base form is a Multiverse transformation.
            if (previous != null
                    && !current.equals(previous)
                    && MultiverseForms.isMultiverseForm(current.group(), current.form())) {
                ModSounds.GOD_TRANSFORM.getHolder().ifPresent(event::setSound);
                LAST_BASE_FORMS.put(target.getUUID(), current);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            StatsProvider.get(StatsCapability.INSTANCE, player)
                    .ifPresent(stats -> rememberBaseForm(player, stats));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatsProvider.get(StatsCapability.INSTANCE, player)
                    .ifPresent(stats -> rememberBaseForm(player, stats));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_BASE_FORMS.remove(event.getEntity().getUUID());
    }

    public static void rememberBaseForm(ServerPlayer player, StatsData stats) {
        LAST_BASE_FORMS.put(player.getUUID(), currentBaseForm(stats));
    }

    private static MultiverseForms.FormKey currentBaseForm(StatsData stats) {
        return new MultiverseForms.FormKey(
                normalize(stats.getCharacter().getActiveFormGroup()),
                normalize(stats.getCharacter().getActiveForm())
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}

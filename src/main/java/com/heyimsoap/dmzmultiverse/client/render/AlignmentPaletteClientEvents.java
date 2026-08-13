package com.heyimsoap.dmzmultiverse.client.render;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.forms.MultiverseForms;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies evil-alignment palettes only while the client renders a frame.
 *
 * <p>The server and the client between frames retain the canonical form IDs.
 * Dragon Mine Z's renderers resolve their colors from the active form, so a
 * short-lived client-side form descriptor gives every renderer (including
 * deferred auras and first-person hands) the alternate palette without
 * creating a second gameplay transformation.</p>
 */
@Mod.EventBusSubscriber(
        modid = DMZMultiverse.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class AlignmentPaletteClientEvents {
    private static final Map<UUID, PaletteSnapshot> ACTIVE_SNAPSHOTS = new LinkedHashMap<>();

    private AlignmentPaletteClientEvents() {
    }

    /** Recovers a frame that exited before its normal END event. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTickStartRecovery(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            restoreAll();
        }
    }

    /** Applies after other START listeners, immediately before Minecraft renders. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderTickStartApply(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        for (Player player : minecraft.level.players()) {
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
                Character character = stats.getCharacter();
                if (!MultiverseForms.SAIYAN_RACE.equalsIgnoreCase(character.getRaceName())
                        || stats.getResources().getAlignment() > MultiverseForms.EVIL_ALIGNMENT_MAX) {
                    return;
                }

                String variantForm = variantFor(character.getActiveFormGroup(), character.getActiveForm());
                if (variantForm == null
                        || ConfigManager.getForm(
                                MultiverseForms.SAIYAN_RACE,
                                MultiverseForms.ALIGNMENT_GROUP,
                                variantForm
                        ) == null) {
                    return;
                }

                applyPalette(player.getUUID(), character, variantForm);
            });
        }
    }

    /** Restores before any other END listener observes the client capability. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            restoreAll();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        restoreAll();
    }

    private static void applyPalette(UUID playerId, Character character, String variantForm) {
        // A failed restoration retains its snapshot for the next recovery pass.
        // Never overwrite that original state with another temporary snapshot.
        if (ACTIVE_SNAPSHOTS.containsKey(playerId)) {
            return;
        }

        FormMasteries masteries = character.getFormMasteries();
        String canonicalGroup = character.getActiveFormGroup();
        String canonicalForm = character.getActiveForm();
        int durationTicks = character.getActiveFormItemDurationTicks();
        double canonicalMastery = masteries.getMastery(canonicalGroup, canonicalForm);
        double previousVariantMastery = masteries.getMastery(MultiverseForms.ALIGNMENT_GROUP, variantForm);

        PaletteSnapshot snapshot = new PaletteSnapshot(
                character,
                canonicalGroup,
                canonicalForm,
                durationTicks,
                variantForm,
                previousVariantMastery
        );
        ACTIVE_SNAPSHOTS.put(playerId, snapshot);

        setMasteryExactly(masteries, MultiverseForms.ALIGNMENT_GROUP, variantForm, canonicalMastery);
        character.setActiveForm(MultiverseForms.ALIGNMENT_GROUP, variantForm);
        character.setActiveFormItemDurationTicks(durationTicks);
    }

    private static String variantFor(String group, String form) {
        if (!MultiverseForms.GOD_GROUP.equalsIgnoreCase(group)) {
            return null;
        }
        if (MultiverseForms.SUPER_SAIYAN_BLUE.equalsIgnoreCase(form)) {
            return MultiverseForms.SUPER_SAIYAN_ROSE;
        }
        if (MultiverseForms.SUPER_SAIYAN_EVOLVED.equalsIgnoreCase(form)) {
            return MultiverseForms.SUPER_SAIYAN_EVOLVED_CORRUPTED;
        }
        return null;
    }

    private static void restoreAll() {
        if (ACTIVE_SNAPSHOTS.isEmpty()) {
            return;
        }

        // Remove only successfully restored entries. A failure remains queued
        // for the next START/END/logout recovery pass and cannot be overwritten.
        var snapshots = new ArrayList<>(ACTIVE_SNAPSHOTS.entrySet());
        for (Map.Entry<UUID, PaletteSnapshot> entry : snapshots) {
            try {
                entry.getValue().restore();
                ACTIVE_SNAPSHOTS.remove(entry.getKey(), entry.getValue());
            } catch (RuntimeException exception) {
                DMZMultiverse.LOGGER.error("Could not restore a client alignment palette snapshot", exception);
            }
        }
    }

    private static void setMasteryExactly(FormMasteries masteries, String group, String form, double value) {
        FormConfig.FormData formData = ConfigManager.getForm(MultiverseForms.SAIYAN_RACE, group, form);
        double configuredMaximum = formData != null ? Math.max(0.0, formData.getMaxMastery()) : 100.0;
        masteries.setMastery(group, form, value, Math.max(configuredMaximum, value));
    }

    private record PaletteSnapshot(
            Character character,
            String activeGroup,
            String activeForm,
            int durationTicks,
            String variantForm,
            double variantMastery
    ) {
        private void restore() {
            character.setActiveForm(activeGroup, activeForm);
            character.setActiveFormItemDurationTicks(durationTicks);
            setMasteryExactly(
                    character.getFormMasteries(),
                    MultiverseForms.ALIGNMENT_GROUP,
                    variantForm,
                    variantMastery
            );
        }
    }
}

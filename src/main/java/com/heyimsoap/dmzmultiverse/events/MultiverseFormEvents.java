package com.heyimsoap.dmzmultiverse.events;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.extras.FormMasteries;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.forms.MultiverseForms;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Installs forms and keeps alignment variants authoritative on the server. */
@Mod.EventBusSubscriber(modid = DMZMultiverse.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MultiverseFormEvents {
    private MultiverseFormEvents() {
    }

    private static final Set<String> LEGACY_INTERNAL_SKILLS = Set.of(
            "dmz_multiverse_internal",
            "dmz_multiverse_god_internal"
    );

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MultiverseForms.install();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> updatePlayer(player, stats));
    }

    private static void updatePlayer(ServerPlayer player, StatsData stats) {
        if (!MultiverseForms.isInstalled()) {
            return;
        }

        boolean changed = removeLegacyInternalSkills(stats);
        if (!MultiverseForms.SAIYAN_RACE.equalsIgnoreCase(stats.getCharacter().getRaceName())) {
            if (changed) {
                sync(player);
            }
            return;
        }

        changed |= repairFormSkills(stats);
        changed |= synchronizeAlignmentMasteries(stats.getCharacter());
        changed |= reconcileAlignmentVariant(player, stats);
        if (changed) {
            sync(player);
        }
    }

    private static boolean repairFormSkills(StatsData stats) {
        boolean missingGodSkill = !stats.getSkills().hasSkill(MultiverseForms.GOD_SKILL);
        boolean missingUltraSkill = !stats.getSkills().hasSkill(MultiverseForms.ULTRA_SKILL);
        if (!missingGodSkill && !missingUltraSkill) {
            return false;
        }

        // Existing characters predate the new form-skill entries. Rebuilding
        // their limits registers those entries without changing progression.
        stats.updateTransformationSkillLimits(MultiverseForms.SAIYAN_RACE);
        return (missingGodSkill && stats.getSkills().hasSkill(MultiverseForms.GOD_SKILL))
                || (missingUltraSkill && stats.getSkills().hasSkill(MultiverseForms.ULTRA_SKILL));
    }

    private static boolean removeLegacyInternalSkills(StatsData stats) {
        List<String> staleSkills = stats.getSkills().getAllSkills().keySet().stream()
                .filter(skill -> LEGACY_INTERNAL_SKILLS.contains(normalize(skill)))
                .toList();
        staleSkills.forEach(stats.getSkills()::removeSkill);
        return !staleSkills.isEmpty();
    }

    private static boolean synchronizeAlignmentMasteries(Character character) {
        FormMasteries masteries = character.getFormMasteries();
        boolean changed = synchronizeMasteryPair(
                masteries,
                MultiverseForms.SUPER_SAIYAN_BLUE,
                MultiverseForms.SUPER_SAIYAN_ROSE
        );
        changed |= synchronizeMasteryPair(
                masteries,
                MultiverseForms.SUPER_SAIYAN_EVOLVED,
                MultiverseForms.SUPER_SAIYAN_EVOLVED_CORRUPTED
        );
        return changed;
    }

    private static boolean synchronizeMasteryPair(FormMasteries masteries, String canonicalForm, String variantForm) {
        double canonical = masteries.getMastery(MultiverseForms.GOD_GROUP, canonicalForm);
        double variant = masteries.getMastery(MultiverseForms.ALIGNMENT_GROUP, variantForm);
        double shared = Math.max(canonical, variant);
        boolean changed = false;
        if (Double.compare(canonical, shared) != 0) {
            setMastery(masteries, MultiverseForms.GOD_GROUP, canonicalForm, shared);
            changed = true;
        }
        if (Double.compare(variant, shared) != 0) {
            setMastery(masteries, MultiverseForms.ALIGNMENT_GROUP, variantForm, shared);
            changed = true;
        }
        return changed;
    }

    private static void setMastery(FormMasteries masteries, String group, String form, double value) {
        FormConfig.FormData config = ConfigManager.getForm(MultiverseForms.SAIYAN_RACE, group, form);
        double maximum = config == null ? 100.0 : Math.max(0.0, config.getMaxMastery());
        masteries.setMastery(group, form, value, Math.max(maximum, value));
    }

    private static boolean reconcileAlignmentVariant(ServerPlayer player, StatsData stats) {
        Character character = stats.getCharacter();
        boolean evil = stats.getResources().getAlignment() <= MultiverseForms.EVIL_ALIGNMENT_MAX;
        boolean changed = reconcileSelection(character, evil);

        MultiverseForms.FormKey target = alignmentTarget(
                character.getActiveFormGroup(),
                character.getActiveForm(),
                evil
        );
        if (target == null) {
            return changed;
        }

        float[] resources = stats.snapshotMultiplierResources();
        int durationTicks = character.getActiveFormItemDurationTicks();
        character.setActiveForm(target.group(), target.form());
        character.setActiveFormItemDurationTicks(durationTicks);
        stats.restoreMultiplierGains(player, resources);
        player.refreshDimensions();
        return true;
    }

    private static boolean reconcileSelection(Character character, boolean evil) {
        MultiverseForms.FormKey target = alignmentTarget(
                character.getSelectedFormGroup(),
                character.getSelectedForm(),
                evil
        );
        if (target == null) {
            return false;
        }
        character.setSelectedFormGroup(target.group());
        character.setSelectedForm(target.form());
        return true;
    }

    private static MultiverseForms.FormKey alignmentTarget(String group, String form, boolean evil) {
        if (evil && MultiverseForms.GOD_GROUP.equalsIgnoreCase(group)) {
            if (MultiverseForms.SUPER_SAIYAN_BLUE.equalsIgnoreCase(form)) {
                return new MultiverseForms.FormKey(
                        MultiverseForms.ALIGNMENT_GROUP,
                        MultiverseForms.SUPER_SAIYAN_ROSE
                );
            }
            if (MultiverseForms.SUPER_SAIYAN_EVOLVED.equalsIgnoreCase(form)) {
                return new MultiverseForms.FormKey(
                        MultiverseForms.ALIGNMENT_GROUP,
                        MultiverseForms.SUPER_SAIYAN_EVOLVED_CORRUPTED
                );
            }
        }
        if (!evil && MultiverseForms.ALIGNMENT_GROUP.equalsIgnoreCase(group)) {
            if (MultiverseForms.SUPER_SAIYAN_ROSE.equalsIgnoreCase(form)) {
                return new MultiverseForms.FormKey(
                        MultiverseForms.GOD_GROUP,
                        MultiverseForms.SUPER_SAIYAN_BLUE
                );
            }
            if (MultiverseForms.SUPER_SAIYAN_EVOLVED_CORRUPTED.equalsIgnoreCase(form)) {
                return new MultiverseForms.FormKey(
                        MultiverseForms.GOD_GROUP,
                        MultiverseForms.SUPER_SAIYAN_EVOLVED
                );
            }
        }
        return null;
    }

    private static void sync(ServerPlayer player) {
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

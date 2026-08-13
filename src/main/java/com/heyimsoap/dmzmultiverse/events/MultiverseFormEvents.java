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
import com.dragonminez.common.util.TransformationsHelper;
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
        changed |= migrateAlignmentUnlocks(stats.getCharacter());
        changed |= synchronizeAlignmentMasteries(stats.getCharacter());
        changed |= discoverAlignmentVariants(stats);
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

    private static boolean migrateAlignmentUnlocks(Character character) {
        FormMasteries masteries = character.getFormMasteries();
        if (masteries.getMastery(
                MultiverseForms.ALIGNMENT_UNLOCK_STATE_GROUP,
                MultiverseForms.ALIGNMENT_UNLOCK_MIGRATION
        ) >= 1.0) {
            return false;
        }

        // Grandfather only durable evidence that the variant itself was
        // selected or used. The previous release mirrored variant mastery for
        // every Saiyan, so mastery alone cannot prove low-alignment discovery.
        migrateVariantUnlock(character, MultiverseForms.SUPER_SAIYAN_ROSE);
        migrateVariantUnlock(character, MultiverseForms.SUPER_SAIYAN_EVOLVED_CORRUPTED);
        masteries.setMastery(
                MultiverseForms.ALIGNMENT_UNLOCK_STATE_GROUP,
                MultiverseForms.ALIGNMENT_UNLOCK_MIGRATION,
                1.0,
                1.0
        );
        return true;
    }

    private static void migrateVariantUnlock(Character character, String variantForm) {
        FormMasteries masteries = character.getFormMasteries();
        if (isFormReference(character.getActiveFormGroup(), character.getActiveForm(), variantForm)
                || isFormReference(character.getSelectedFormGroup(), character.getSelectedForm(), variantForm)
                || (character.isHasPreviousFormRecord()
                    && isFormReference(character.getPreviousFormGroup(), character.getPreviousForm(), variantForm))
                || character.getFormsUsedBefore().getFormGroup(MultiverseForms.ALIGNMENT_GROUP).stream()
                    .anyMatch(variantForm::equalsIgnoreCase)) {
            grantVariantUnlock(masteries, variantForm);
        }
    }

    private static boolean discoverAlignmentVariants(StatsData stats) {
        if (stats.getResources().getAlignment() > MultiverseForms.EVIL_ALIGNMENT_MAX
                || (stats.getPlayer() != null && stats.getPlayer().isCreative())) {
            return false;
        }

        List<FormConfig.FormData> unlockedCanonicalForms = TransformationsHelper.getUnlockedForms(
                stats,
                MultiverseForms.SAIYAN_RACE,
                MultiverseForms.GOD_GROUP
        );
        boolean blueUnlocked = hasForm(unlockedCanonicalForms, MultiverseForms.SUPER_SAIYAN_BLUE);
        boolean evolvedUnlocked = hasForm(unlockedCanonicalForms, MultiverseForms.SUPER_SAIYAN_EVOLVED);
        FormMasteries masteries = stats.getCharacter().getFormMasteries();
        boolean changed = blueUnlocked
                && grantVariantUnlock(masteries, MultiverseForms.SUPER_SAIYAN_ROSE);
        changed |= evolvedUnlocked
                && grantVariantUnlock(masteries, MultiverseForms.SUPER_SAIYAN_EVOLVED_CORRUPTED);
        return changed;
    }

    private static boolean hasForm(List<FormConfig.FormData> forms, String formName) {
        return forms.stream().anyMatch(form -> formName.equalsIgnoreCase(form.getName()));
    }

    private static boolean grantVariantUnlock(FormMasteries masteries, String variantForm) {
        double current = masteries.getMastery(
                MultiverseForms.ALIGNMENT_UNLOCK_STATE_GROUP,
                variantForm
        );
        if (current >= MultiverseForms.ALIGNMENT_UNLOCK_MARKER) {
            return false;
        }
        masteries.setMastery(
                MultiverseForms.ALIGNMENT_UNLOCK_STATE_GROUP,
                variantForm,
                MultiverseForms.ALIGNMENT_UNLOCK_MARKER,
                MultiverseForms.ALIGNMENT_UNLOCK_MARKER
        );
        return true;
    }

    private static boolean isFormReference(String group, String form, String variantForm) {
        return MultiverseForms.ALIGNMENT_GROUP.equalsIgnoreCase(group)
                && variantForm.equalsIgnoreCase(form);
    }

    private static void sync(ServerPlayer player) {
        NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

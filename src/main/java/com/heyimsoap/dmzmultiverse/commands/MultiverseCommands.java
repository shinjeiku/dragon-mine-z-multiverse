package com.heyimsoap.dmzmultiverse.commands;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.SkillsConfig;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ProgressionSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.skills.Skills;
import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Operator commands for administering Dragon Mine Z: Multiverse. */
@Mod.EventBusSubscriber(modid = DMZMultiverse.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MultiverseCommands {
    private static final Set<String> LEGACY_INTERNAL_SKILLS = Set.of(
            "dmz_multiverse_internal",
            "dmz_multiverse_god_internal"
    );

    private MultiverseCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("dmzmultiverse")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("maxskills")
                        .executes(context -> maxSkills(
                                context.getSource(),
                                List.of(context.getSource().getPlayerOrException())
                        ))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> maxSkills(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets")
                                )))));
    }

    private static int maxSkills(CommandSourceStack source, Collection<ServerPlayer> targets) {
        SkillsConfig config = ConfigManager.getSkillsConfig();
        if (config == null || config.getSkills() == null) {
            source.sendFailure(Component.translatable("command.dmz_multiverse.maxskills.config_unavailable"));
            return 0;
        }

        Set<String> excludedSkills = collectExcludedSkills(config);
        Set<String> techniqueSkills = collectTechniqueSkills(config);
        List<ServerPlayer> processedPlayers = new ArrayList<>();
        int maxedSkillEntries = 0;

        for (ServerPlayer target : targets) {
            StatsData stats = StatsProvider.get(StatsCapability.INSTANCE, target).resolve().orElse(null);
            if (stats == null || !stats.getStatus().isHasCreatedCharacter()) {
                continue;
            }

            int targetSkillEntries = maxRegularSkills(config, excludedSkills, techniqueSkills, stats);
            if (targetSkillEntries <= 0) {
                continue;
            }

            maxedSkillEntries += targetSkillEntries;
            NetworkHandler.sendToTrackingEntityAndSelf(new ProgressionSyncS2C(target), target);
            processedPlayers.add(target);
        }

        if (processedPlayers.isEmpty()) {
            source.sendFailure(Component.translatable("command.dmz_multiverse.maxskills.no_characters"));
            return 0;
        }

        int skippedPlayers = targets.size() - processedPlayers.size();
        if (processedPlayers.size() == 1) {
            ServerPlayer target = processedPlayers.get(0);
            int finalMaxedSkillEntries = maxedSkillEntries;
            source.sendSuccess(() -> Component.translatable(
                    "command.dmz_multiverse.maxskills.success.single",
                    finalMaxedSkillEntries,
                    target.getDisplayName()
            ), false);
        } else {
            int finalMaxedSkillEntries = maxedSkillEntries;
            source.sendSuccess(() -> Component.translatable(
                    "command.dmz_multiverse.maxskills.success.multiple",
                    finalMaxedSkillEntries,
                    processedPlayers.size()
            ), false);
        }

        if (skippedPlayers > 0) {
            source.sendSuccess(() -> Component.translatable(
                    "command.dmz_multiverse.maxskills.skipped",
                    skippedPlayers
            ), false);
        }

        return processedPlayers.size();
    }

    private static int maxRegularSkills(
            SkillsConfig config,
            Set<String> excludedSkills,
            Set<String> techniqueSkills,
            StatsData stats
    ) {
        Skills playerSkills = stats.getSkills();
        String raceName = stats.getCharacter().getRaceName();
        int maxedSkillEntries = 0;

        for (Map.Entry<String, SkillsConfig.SkillCosts> entry : config.getSkills().entrySet()) {
            String configuredId = entry.getKey();
            SkillsConfig.SkillCosts costs = entry.getValue();
            if (configuredId == null || configuredId.isBlank() || costs == null || costs.getCosts() == null) {
                continue;
            }

            String skillId = normalize(configuredId);
            if (excludedSkills.contains(skillId) || !config.isSkillAllowedForRace(skillId, raceName)) {
                continue;
            }

            int runtimeMax = runtimeMaxLevel(skillId, costs);
            if (runtimeMax <= 0) {
                continue;
            }

            // Refresh an existing skill's cap or register a missing configured
            // skill before setting it. Skill#setLevel then enforces that cap.
            playerSkills.registerDefaultSkill(skillId, runtimeMax);
            playerSkills.setSkillLevel(skillId, runtimeMax);
            if (techniqueSkills.contains(skillId)) {
                unlockTechniqueIfPresent(stats, skillId);
            }
            maxedSkillEntries++;
        }

        return maxedSkillEntries;
    }

    private static Set<String> collectExcludedSkills(SkillsConfig config) {
        Set<String> excluded = new HashSet<>(LEGACY_INTERNAL_SKILLS);
        addNormalized(excluded, config.getFormSkills());
        addNormalized(excluded, config.getStackSkills());
        return excluded;
    }

    private static Set<String> collectTechniqueSkills(SkillsConfig config) {
        Set<String> techniqueSkills = new HashSet<>();
        addNormalized(techniqueSkills, config.getKiSkills());
        addNormalized(techniqueSkills, config.getStrikeSkills());
        return techniqueSkills;
    }

    /** Mirrors Dragon Mine Z 2.1.3's predefined-technique unlock path. */
    private static void unlockTechniqueIfPresent(StatsData stats, String skillId) {
        if (stats.getTechniques().getUnlockedTechniques().containsKey(skillId)) {
            return;
        }

        KiAttackData predefinedKi = PredefinedTechniques.REGISTRY.get(skillId);
        if (predefinedKi != null) {
            KiAttackData unlockedKi = new KiAttackData();
            unlockedKi.load(predefinedKi.save());
            stats.getTechniques().unlockTechnique(unlockedKi);
            return;
        }

        StrikeAttackData predefinedStrike = PredefinedTechniques.STRIKE_REGISTRY.get(skillId);
        if (predefinedStrike != null) {
            StrikeAttackData unlockedStrike = new StrikeAttackData();
            unlockedStrike.load(predefinedStrike.save());
            stats.getTechniques().unlockTechnique(unlockedStrike);
        }
    }

    private static void addNormalized(Set<String> destination, Collection<String> skillIds) {
        if (skillIds == null) {
            return;
        }

        for (String skillId : skillIds) {
            if (skillId != null && !skillId.isBlank()) {
                destination.add(normalize(skillId));
            }
        }
    }

    private static int runtimeMaxLevel(String skillId, SkillsConfig.SkillCosts costs) {
        int configuredMax = costs.getCosts().size();
        int hardCap = "potentialunlock".equalsIgnoreCase(skillId) ? 30 : 50;
        return Math.min(configuredMax, hardCap);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

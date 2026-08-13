package com.heyimsoap.dmzmultiverse.events;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.forms.MultiverseForms;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned form installation and existing-character skill repair. */
@Mod.EventBusSubscriber(modid = DMZMultiverse.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MultiverseFormEvents {
    private MultiverseFormEvents() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MultiverseForms.install();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> repairFormSkills(player, stats));
    }

    private static void repairFormSkills(ServerPlayer player, StatsData stats) {
        if (!MultiverseForms.isInstalled()
                || !MultiverseForms.SAIYAN_RACE.equalsIgnoreCase(stats.getCharacter().getRaceName())) {
            return;
        }

        boolean missingGodSkill = !stats.getSkills().hasSkill(MultiverseForms.GOD_SKILL);
        boolean missingUltraSkill = !stats.getSkills().hasSkill(MultiverseForms.ULTRA_SKILL);
        if (!missingGodSkill && !missingUltraSkill) {
            return;
        }

        // Existing characters predate the new form-skill entries. Rebuilding
        // their limits registers those entries without changing progression.
        stats.updateTransformationSkillLimits(MultiverseForms.SAIYAN_RACE);
        boolean repaired = (missingGodSkill && stats.getSkills().hasSkill(MultiverseForms.GOD_SKILL))
                || (missingUltraSkill && stats.getSkills().hasSkill(MultiverseForms.ULTRA_SKILL));
        if (repaired) {
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
        }
    }
}

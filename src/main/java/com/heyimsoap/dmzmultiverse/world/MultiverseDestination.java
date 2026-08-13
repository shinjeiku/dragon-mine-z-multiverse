package com.heyimsoap.dmzmultiverse.world;

import com.dragonminez.common.spacepod.SpacePodDestinationDefinition;
import com.dragonminez.common.spacepod.SpacePodDestinationRegistry;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A destination resolved from Dragon Mine Z's reloadable Space Pod registry, or
 * one of the two vanilla dimensions that registry does not define.
 */
public final class MultiverseDestination {
    private static final String DMZ_SELECTION_PREFIX = "dragonminez:";
    private static final String NETHER_SELECTION_ID = "minecraft:the_nether";
    private static final String END_SELECTION_ID = "minecraft:the_end";

    private final String selectionId;
    private final Component displayName;
    private final ResourceKey<Level> dimension;
    private final @Nullable SpacePodDestinationDefinition dmzDefinition;
    private final ArrivalStrategy arrivalStrategy;

    private MultiverseDestination(
            String selectionId,
            Component displayName,
            ResourceKey<Level> dimension,
            @Nullable SpacePodDestinationDefinition dmzDefinition,
            ArrivalStrategy arrivalStrategy
    ) {
        this.selectionId = selectionId;
        this.displayName = displayName;
        this.dimension = dimension;
        this.dmzDefinition = dmzDefinition;
        this.arrivalStrategy = arrivalStrategy;
    }

    public String id() {
        return selectionId;
    }

    public Component displayName() {
        return displayName.copy();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public ArrivalStrategy arrivalStrategy() {
        return arrivalStrategy;
    }

    public boolean isUnlockedFor(ServerPlayer player) {
        return dmzDefinition == null || dmzDefinition.unlockRules().test(player);
    }

    public Vec3 resolveAnchor(ServerPlayer player, ServerLevel targetLevel) {
        if (dmzDefinition != null) {
            return dmzDefinition.resolvePosition(player.position());
        }

        if (dimension.equals(Level.END)) {
            return Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT);
        }

        double scale = DimensionType.getTeleportationScale(
                player.serverLevel().dimensionType(),
                targetLevel.dimensionType()
        );
        return new Vec3(player.getX() * scale, player.getY(), player.getZ() * scale);
    }

    public static boolean isDmzCharacterDead(ServerPlayer player) {
        return StatsProvider.get(StatsCapability.INSTANCE, player)
                .map(data -> !data.getStatus().isAlive())
                .orElse(false);
    }

    /**
     * Resolves even locked entries so travel can return an accurate failure.
     */
    public static Optional<MultiverseDestination> byId(ServerPlayer player, String selectionId) {
        if (NETHER_SELECTION_ID.equals(selectionId)) {
            return vanillaDestination(player, selectionId, Level.NETHER, "nether", ArrivalStrategy.NETHER_INTERIOR);
        }
        if (END_SELECTION_ID.equals(selectionId)) {
            return vanillaDestination(player, selectionId, Level.END, "end", ArrivalStrategy.END_PLATFORM);
        }
        if (!selectionId.startsWith(DMZ_SELECTION_PREFIX)) {
            return Optional.empty();
        }

        String registryId = selectionId.substring(DMZ_SELECTION_PREFIX.length());
        SpacePodDestinationDefinition definition = SpacePodDestinationRegistry.getServerDestination(registryId);
        return definition == null ? Optional.empty() : fromDefinition(player, definition);
    }

    public static List<MultiverseDestination> availableTo(ServerPlayer player) {
        Map<String, MultiverseDestination> destinations = new LinkedHashMap<>();

        for (SpacePodDestinationDefinition definition : SpacePodDestinationRegistry.getServerDestinations()) {
            fromDefinition(player, definition)
                    .filter(destination -> destination.isUnlockedFor(player))
                    .ifPresent(destination -> destinations.putIfAbsent(destination.id(), destination));
        }

        vanillaDestination(player, NETHER_SELECTION_ID, Level.NETHER, "nether", ArrivalStrategy.NETHER_INTERIOR)
                .ifPresent(destination -> destinations.put(destination.id(), destination));
        vanillaDestination(player, END_SELECTION_ID, Level.END, "end", ArrivalStrategy.END_PLATFORM)
                .ifPresent(destination -> destinations.put(destination.id(), destination));

        return List.copyOf(destinations.values());
    }

    public static Optional<MultiverseDestination> nextAvailable(ServerPlayer player, String currentId) {
        List<MultiverseDestination> available = new ArrayList<>(availableTo(player));
        if (available.isEmpty()) {
            return Optional.empty();
        }

        if (currentId.isBlank()) {
            return available.stream()
                    .filter(destination -> !destination.dimension().equals(player.level().dimension()))
                    .findFirst()
                    .or(() -> Optional.of(available.get(0)));
        }

        for (int index = 0; index < available.size(); index++) {
            if (available.get(index).id().equals(currentId)) {
                return Optional.of(available.get((index + 1) % available.size()));
            }
        }

        return Optional.of(available.get(0));
    }

    public static Component tooltipName(String selectionId) {
        if (NETHER_SELECTION_ID.equals(selectionId)) {
            return Component.translatable("destination." + DMZMultiverse.MOD_ID + ".nether");
        }
        if (END_SELECTION_ID.equals(selectionId)) {
            return Component.translatable("destination." + DMZMultiverse.MOD_ID + ".end");
        }
        if (!selectionId.startsWith(DMZ_SELECTION_PREFIX)) {
            return Component.literal(selectionId);
        }

        String registryId = selectionId.substring(DMZ_SELECTION_PREFIX.length());
        return switch (registryId) {
            case "overworld", "namek", "otherworld", "time_chamber" ->
                    Component.translatable("destination." + DMZMultiverse.MOD_ID + "." + registryId);
            case "sacredkaiplanet" ->
                    Component.translatable("destination." + DMZMultiverse.MOD_ID + ".sacred_kai_planet");
            default -> Component.literal(registryId);
        };
    }

    private static Optional<MultiverseDestination> fromDefinition(
            ServerPlayer player,
            SpacePodDestinationDefinition definition
    ) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(definition.dimension());
        if (dimensionId == null) {
            return Optional.empty();
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (player.getServer().getLevel(dimension) == null) {
            return Optional.empty();
        }

        Component name = definition.translate()
                ? Component.translatable(definition.name())
                : Component.literal(definition.name());

        ArrivalStrategy strategy;
        if (definition.y() != null) {
            strategy = ArrivalStrategy.DESCEND_FROM_ANCHOR;
        } else if (dimension.equals(Level.NETHER)) {
            strategy = ArrivalStrategy.NETHER_INTERIOR;
        } else if (dimension.equals(Level.END)) {
            strategy = ArrivalStrategy.END_PLATFORM;
        } else {
            strategy = ArrivalStrategy.SURFACE;
        }

        return Optional.of(new MultiverseDestination(
                DMZ_SELECTION_PREFIX + definition.id(),
                name,
                dimension,
                definition,
                strategy
        ));
    }

    private static Optional<MultiverseDestination> vanillaDestination(
            ServerPlayer player,
            String selectionId,
            ResourceKey<Level> dimension,
            String translationSuffix,
            ArrivalStrategy strategy
    ) {
        if (player.getServer().getLevel(dimension) == null) {
            return Optional.empty();
        }

        return Optional.of(new MultiverseDestination(
                selectionId,
                Component.translatable("destination." + DMZMultiverse.MOD_ID + "." + translationSuffix),
                dimension,
                null,
                strategy
        ));
    }

    public enum ArrivalStrategy {
        SURFACE,
        DESCEND_FROM_ANCHOR,
        NETHER_INTERIOR,
        END_PLATFORM
    }
}

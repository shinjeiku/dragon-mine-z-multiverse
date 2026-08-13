package com.heyimsoap.dmzmultiverse.world;

import com.heyimsoap.dmzmultiverse.config.MultiverseConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Optional;

public final class MultiverseTravelService {
    private static final int MAX_ARRIVAL_CANDIDATES = 8_192;

    private MultiverseTravelService() {
    }

    public static TravelResult travel(ServerPlayer player, MultiverseDestination requestedDestination) {
        if (MultiverseDestination.isDmzCharacterDead(player)) {
            return TravelResult.CHARACTER_DEAD;
        }

        Optional<MultiverseDestination> currentDestination =
                MultiverseDestination.byId(player, requestedDestination.id());
        if (currentDestination.isEmpty()) {
            return TravelResult.DESTINATION_UNAVAILABLE;
        }

        MultiverseDestination destination = currentDestination.get();
        if (!destination.isUnlockedFor(player)) {
            return TravelResult.DESTINATION_LOCKED;
        }

        ServerLevel targetLevel = player.getServer().getLevel(destination.dimension());
        if (targetLevel == null) {
            return TravelResult.DESTINATION_UNAVAILABLE;
        }
        if (player.level().dimension().equals(destination.dimension())) {
            return TravelResult.ALREADY_THERE;
        }

        if (destination.arrivalStrategy() == MultiverseDestination.ArrivalStrategy.END_PLATFORM) {
            ServerLevel.makeObsidianPlatform(targetLevel);
        }

        Vec3 anchor = destination.resolveAnchor(player, targetLevel);
        Optional<BlockPos> arrival = findSafeArrival(targetLevel, player, destination.arrivalStrategy(), anchor);
        if (arrival.isEmpty()) {
            return TravelResult.NO_SAFE_ARRIVAL;
        }

        BlockPos position = arrival.get();
        player.stopRiding();
        player.teleportTo(
                targetLevel,
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D,
                Collections.emptySet(),
                player.getYRot(),
                player.getXRot()
        );

        if (player.serverLevel() != targetLevel) {
            return TravelResult.TRAVEL_DENIED;
        }

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        return TravelResult.SUCCESS;
    }

    private static Optional<BlockPos> findSafeArrival(
            ServerLevel level,
            ServerPlayer player,
            MultiverseDestination.ArrivalStrategy strategy,
            Vec3 anchor
    ) {
        int baseX = Mth.floor(anchor.x);
        int baseZ = Mth.floor(anchor.z);
        int radius = MultiverseConfig.ARRIVAL_SEARCH_RADIUS.get();
        SearchBudget budget = new SearchBudget(MAX_ARRIVAL_CANDIDATES);

        return searchColumns(level, baseX, baseZ, radius, budget, (x, z) -> switch (strategy) {
            case SURFACE -> searchSurfaceColumn(level, player, x, z, budget);
            case DESCEND_FROM_ANCHOR -> searchDownwardColumn(level, player, x, z, Mth.floor(anchor.y), budget);
            case NETHER_INTERIOR -> searchNearbyVerticalColumn(level, player, x, z, Mth.floor(anchor.y), budget);
            case END_PLATFORM -> searchDownwardColumn(level, player, x, z, Mth.floor(anchor.y), budget);
        });
    }

    private static Optional<BlockPos> searchColumns(
            ServerLevel level,
            int baseX,
            int baseZ,
            int radius,
            SearchBudget budget,
            ColumnSearch columnSearch
    ) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int xOffset = -ring; xOffset <= ring; xOffset++) {
                for (int zOffset = -ring; zOffset <= ring; zOffset++) {
                    if (ring > 0 && Math.abs(xOffset) != ring && Math.abs(zOffset) != ring) {
                        continue;
                    }

                    if (budget.exhausted()) {
                        return Optional.empty();
                    }

                    int x = baseX + xOffset;
                    int z = baseZ + zOffset;
                    if (!level.getWorldBorder().isWithinBounds(new BlockPos(x, level.getMinBuildHeight(), z))) {
                        continue;
                    }

                    Optional<BlockPos> result = columnSearch.find(x, z);
                    if (result.isPresent()) {
                        return result;
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> searchSurfaceColumn(
            ServerLevel level,
            ServerPlayer player,
            int x,
            int z,
            SearchBudget budget
    ) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int startY = Mth.clamp(surfaceY, minimumStandingY(level), maximumStandingY(level));
        int lowestY = Math.max(minimumStandingY(level), startY - 16);

        for (int y = startY; y >= lowestY; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!budget.tryCandidate()) {
                return Optional.empty();
            }
            if (isSafe(level, player, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> searchDownwardColumn(
            ServerLevel level,
            ServerPlayer player,
            int x,
            int z,
            int requestedY,
            SearchBudget budget
    ) {
        int minimumY = minimumStandingY(level);
        int startY = Mth.clamp(requestedY, minimumY, maximumStandingY(level));

        for (int y = startY; y >= minimumY; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!budget.tryCandidate()) {
                return Optional.empty();
            }
            if (isSafe(level, player, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> searchNearbyVerticalColumn(
            ServerLevel level,
            ServerPlayer player,
            int x,
            int z,
            int requestedY,
            SearchBudget budget
    ) {
        int minimumY = minimumStandingY(level);
        int maximumY = maximumStandingY(level);
        int preferredY = Mth.clamp(requestedY, minimumY, maximumY);
        int furthestDistance = Math.max(preferredY - minimumY, maximumY - preferredY);

        for (int distance = 0; distance <= furthestDistance; distance++) {
            int lowerY = preferredY - distance;
            if (lowerY >= minimumY) {
                BlockPos lower = new BlockPos(x, lowerY, z);
                if (!budget.tryCandidate()) {
                    return Optional.empty();
                }
                if (isSafe(level, player, lower)) {
                    return Optional.of(lower);
                }
            }

            int upperY = preferredY + distance;
            if (distance > 0 && upperY <= maximumY) {
                BlockPos upper = new BlockPos(x, upperY, z);
                if (!budget.tryCandidate()) {
                    return Optional.empty();
                }
                if (isSafe(level, player, upper)) {
                    return Optional.of(upper);
                }
            }
        }
        return Optional.empty();
    }

    private static int minimumStandingY(ServerLevel level) {
        return level.getMinBuildHeight() + 1;
    }

    private static int maximumStandingY(ServerLevel level) {
        int logicalTopExclusive = level.getMinBuildHeight() + level.dimensionType().logicalHeight();
        return Math.min(level.getMaxBuildHeight() - 2, logicalTopExclusive - 2);
    }

    private static boolean isSafe(ServerLevel level, ServerPlayer player, BlockPos position) {
        if (position.getY() < minimumStandingY(level) || position.getY() > maximumStandingY(level)) {
            return false;
        }

        AABB standingBox = player.getDimensions(Pose.STANDING)
                .makeBoundingBox(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (!level.getWorldBorder().isWithinBounds(standingBox)) {
            return false;
        }

        BlockPos below = position.below();
        BlockState support = level.getBlockState(below);
        if (!support.isFaceSturdy(level, below, Direction.UP) || isHazardous(support)) {
            return false;
        }

        BlockState feet = level.getBlockState(position);
        BlockState head = level.getBlockState(position.above());
        if (isHazardous(feet) || isHazardous(head)) {
            return false;
        }
        if (!level.getFluidState(position).isEmpty() || !level.getFluidState(position.above()).isEmpty()) {
            return false;
        }
        if (!level.noCollision(player, standingBox)) {
            return false;
        }

        return level.getEntities(player, standingBox).isEmpty();
    }

    private static boolean isHazardous(BlockState state) {
        return state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.POINTED_DRIPSTONE);
    }

    @FunctionalInterface
    private interface ColumnSearch {
        Optional<BlockPos> find(int x, int z);
    }

    private static final class SearchBudget {
        private final int limit;
        private int checked;

        private SearchBudget(int limit) {
            this.limit = limit;
        }

        private boolean tryCandidate() {
            if (checked >= limit) {
                return false;
            }
            checked++;
            return true;
        }

        private boolean exhausted() {
            return checked >= limit;
        }
    }

    public enum TravelResult {
        SUCCESS,
        CHARACTER_DEAD,
        DESTINATION_LOCKED,
        DESTINATION_UNAVAILABLE,
        ALREADY_THERE,
        NO_SAFE_ARRIVAL,
        TRAVEL_DENIED
    }
}

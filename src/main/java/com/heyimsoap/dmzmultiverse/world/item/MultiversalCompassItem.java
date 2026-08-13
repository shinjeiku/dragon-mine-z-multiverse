package com.heyimsoap.dmzmultiverse.world.item;

import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.config.MultiverseConfig;
import com.heyimsoap.dmzmultiverse.world.MultiverseDestination;
import com.heyimsoap.dmzmultiverse.world.MultiverseTravelService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class MultiversalCompassItem extends Item {
    private static final String DESTINATION_TAG = DMZMultiverse.MOD_ID + ".destination";
    private static final String DESTINATION_NAME_TAG = DMZMultiverse.MOD_ID + ".destination_name";

    public MultiversalCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        if (MultiverseDestination.isDmzCharacterDead(serverPlayer)) {
            return failure(serverPlayer, stack, "message.dmz_multiverse.character_dead");
        }

        if (player.isShiftKeyDown()) {
            return cycleDestination(serverPlayer, stack);
        }

        if (serverPlayer.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        String selectedId = selectedId(stack);
        Optional<MultiverseDestination> selected = selectedId == null
                ? MultiverseDestination.nextAvailable(serverPlayer, "")
                : MultiverseDestination.byId(serverPlayer, selectedId);

        if (selected.isEmpty()) {
            return selectedId == null
                    ? failure(serverPlayer, stack, "message.dmz_multiverse.no_destinations")
                    : failure(
                            serverPlayer,
                            stack,
                            "message.dmz_multiverse.destination_unavailable",
                            MultiverseDestination.tooltipName(selectedId)
                    );
        }

        MultiverseDestination destination = selected.get();
        setDestination(stack, destination);
        MultiverseTravelService.TravelResult result = MultiverseTravelService.travel(serverPlayer, destination);
        int cooldown = MultiverseConfig.teleportCooldownTicks();
        if (cooldown > 0 && result != MultiverseTravelService.TravelResult.ALREADY_THERE) {
            serverPlayer.getCooldowns().addCooldown(this, cooldown);
        }

        return switch (result) {
            case SUCCESS -> {
                serverPlayer.displayClientMessage(
                        Component.translatable("message.dmz_multiverse.traveled", destination.displayName())
                                .withStyle(ChatFormatting.LIGHT_PURPLE),
                        true
                );
                yield InteractionResultHolder.success(stack);
            }
            case CHARACTER_DEAD -> failure(
                    serverPlayer,
                    stack,
                    "message.dmz_multiverse.character_dead"
            );
            case DESTINATION_LOCKED -> failure(
                    serverPlayer,
                    stack,
                    "message.dmz_multiverse.destination_locked",
                    destination.displayName()
            );
            case DESTINATION_UNAVAILABLE -> failure(
                    serverPlayer,
                    stack,
                    "message.dmz_multiverse.destination_unavailable",
                    destination.displayName()
            );
            case ALREADY_THERE -> failure(
                    serverPlayer,
                    stack,
                    "message.dmz_multiverse.already_there",
                    destination.displayName()
            );
            case NO_SAFE_ARRIVAL -> failure(
                    serverPlayer,
                    stack,
                    "message.dmz_multiverse.no_safe_arrival",
                    destination.displayName()
            );
            case TRAVEL_DENIED -> failure(
                    serverPlayer,
                    stack,
                    "message.dmz_multiverse.travel_denied",
                    destination.displayName()
            );
        };
    }

    private InteractionResultHolder<ItemStack> cycleDestination(ServerPlayer player, ItemStack stack) {
        String storedId = selectedId(stack);
        String currentId = storedId == null ? "" : storedId;
        Optional<MultiverseDestination> next = MultiverseDestination.nextAvailable(player, currentId);
        if (next.isEmpty()) {
            return failure(player, stack, "message.dmz_multiverse.no_destinations");
        }

        setDestination(stack, next.get());
        player.displayClientMessage(
                Component.translatable("message.dmz_multiverse.destination_selected", next.get().displayName())
                        .withStyle(ChatFormatting.AQUA),
                true
        );
        return InteractionResultHolder.success(stack);
    }

    private static InteractionResultHolder<ItemStack> failure(
            ServerPlayer player,
            ItemStack stack,
            String translationKey,
            Object... arguments
    ) {
        player.displayClientMessage(
                Component.translatable(translationKey, arguments).withStyle(ChatFormatting.RED),
                true
        );
        return InteractionResultHolder.fail(stack);
    }

    private static @Nullable String selectedId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DESTINATION_TAG)) {
            return null;
        }
        return tag.getString(DESTINATION_TAG);
    }

    private static void setDestination(ItemStack stack, MultiverseDestination destination) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(DESTINATION_TAG, destination.id());
        tag.putString(DESTINATION_NAME_TAG, Component.Serializer.toJson(destination.displayName()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String storedId = selectedId(stack);
        Component destinationName = storedId == null
                ? Component.translatable("destination.dmz_multiverse.unset")
                : storedDestinationName(stack).orElseGet(() -> MultiverseDestination.tooltipName(storedId));

        tooltip.add(Component.translatable("tooltip.dmz_multiverse.multiversal_compass.destination", destinationName)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dmz_multiverse.multiversal_compass.use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dmz_multiverse.multiversal_compass.cycle")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    private static Optional<Component> storedDestinationName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DESTINATION_NAME_TAG)) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(Component.Serializer.fromJson(tag.getString(DESTINATION_NAME_TAG)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}

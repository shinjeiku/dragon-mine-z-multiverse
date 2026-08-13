package com.heyimsoap.dmzmultiverse.registry;

import com.heyimsoap.dmzmultiverse.DMZMultiverse;
import com.heyimsoap.dmzmultiverse.world.item.MultiversalCompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DMZMultiverse.MOD_ID);

    public static final RegistryObject<Item> MULTIVERSAL_COMPASS = ITEMS.register(
            "multiversal_compass",
            () -> new MultiversalCompassItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant())
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

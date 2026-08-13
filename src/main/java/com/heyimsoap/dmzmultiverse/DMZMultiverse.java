package com.heyimsoap.dmzmultiverse;

import com.dragonminez.common.init.MainTabs;
import com.heyimsoap.dmzmultiverse.config.MultiverseConfig;
import com.heyimsoap.dmzmultiverse.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DMZMultiverse.MOD_ID)
public final class DMZMultiverse {
    public static final String MOD_ID = "dmz_multiverse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DMZMultiverse(FMLJavaModLoadingContext loadingContext) {
        IEventBus modEventBus = loadingContext.getModEventBus();

        ModItems.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabContents);
        loadingContext.registerConfig(ModConfig.Type.COMMON, MultiverseConfig.SPEC);

        LOGGER.info("Dragon Mine Z: Multiverse is charting the available worlds");
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTab() == MainTabs.ITEMS_TAB.get()) {
            event.accept(ModItems.MULTIVERSAL_COMPASS.get());
        }
    }
}

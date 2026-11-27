package com.cinematicchests;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(CinematicChests.MODID)
public final class CinematicChests {
    public static final String MODID = "cinematicchests";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CinematicChests(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ChestConfig.SPEC);
        // loaded at once, so the block list is known in common setup, on both sides alike
        container.registerConfig(ModConfig.Type.STARTUP, StartupConfig.SPEC);
        // Both sides must know the stations: the server starts the session, the client plays the scene.
        modBus.addListener(FMLCommonSetupEvent.class, event -> {
            StorageBlocks.registerStations();
            if (!FMLEnvironment.production) {
                // dev only: load the patched host classes now, so a mixin that no longer fits fails at startup, not in play
                loadForMixinCheck("dev.dyslexic.cinematic.station.MenuActions");
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    loadForMixinCheck("dev.dyslexic.cinematic.client.Scene");
                    loadForMixinCheck("dev.dyslexic.cinematic.api.client.AddonScenes");
                    loadForMixinCheck("dev.dyslexic.cinematic.client.SceneScreen");
                    // fully linked, not just loaded: proves the scene runs without any optional mod (-Pbare) installed
                    try {
                        Class.forName("com.cinematicchests.client.StorageScene", true, CinematicChests.class.getClassLoader());
                        LOGGER.info("[Cinematic Chests] scene class linked fine (Sophisticated Core present: {})",
                                net.neoforged.fml.ModList.get().isLoaded("sophisticatedcore"));
                    } catch (Throwable t) {
                        throw new IllegalStateException("[Cinematic Chests] the scene class needs a mod that is not installed", t);
                    }
                }
            }
        });
        // a filter slot set from the scene (it holds a pattern, not an item, so it is not an ordinary slot click)
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class, event -> event.registrar("1")
                .playToServer(SetFilterSlotPayload.TYPE, SetFilterSlotPayload.STREAM_CODEC, SetFilterSlotPayload::handle));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.cinematicchests.client.ChestsClient.init(modBus);
        }
    }

    private static void loadForMixinCheck(String name) {
        try {
            // loading (not initialising) is enough: mixins are applied when the class is defined
            Class.forName(name, false, CinematicChests.class.getClassLoader());
            LOGGER.info("[Cinematic Chests] mixin target loaded: {}", name);
        } catch (Throwable t) {
            throw new IllegalStateException("[Cinematic Chests] mixin target failed to load: " + name, t);
        }
    }
}

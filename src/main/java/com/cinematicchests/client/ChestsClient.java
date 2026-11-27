package com.cinematicchests.client;

import com.cinematicchests.StorageBlocks;
import dev.dyslexic.cinematic.api.AddonStations;
import dev.dyslexic.cinematic.api.client.AddonScenes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client setup: one scene factory per registered storage block. Runs after the common setup registered the stations. */
public final class ChestsClient {
    private ChestsClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, event -> registerScenes());
    }

    private static void registerScenes() {
        for (StorageBlocks.Entry entry : StorageBlocks.entries()) {
            if (AddonStations.byId(entry.sceneId()) == null) {
                continue;
            }
            AddonScenes.register(entry.sceneId(), (pos, state, player, side) -> create(pos, side, state, entry));
        }
    }

    private static <M extends net.minecraft.world.inventory.AbstractContainerMenu> StorageScene<M> create(
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side, net.minecraft.world.level.block.state.BlockState state,
            StorageBlocks.Entry entry) {
        @SuppressWarnings("unchecked")
        Class<M> menuClass = (Class<M>) entry.menuClass();
        return new StorageScene<>(pos, side, state, entry.shape(), menuClass);
    }
}

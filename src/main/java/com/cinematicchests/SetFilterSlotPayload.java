package com.cinematicchests;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sets (or with an empty stack clears) a filter slot of a storage block's open upgrade, from the scene. A filter slot holds only
 * a pattern, not an item, so the mod has its own way of setting one; the server does it through that.
 */
public record SetFilterSlotPayload(int containerId, int slot, ItemStack pattern) implements CustomPacketPayload {
    public static final Type<SetFilterSlotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CinematicChests.MODID, "set_filter_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetFilterSlotPayload::containerId,
            ByteBufCodecs.VAR_INT, SetFilterSlotPayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, SetFilterSlotPayload::pattern,
            SetFilterSlotPayload::new);

    @Override
    public Type<SetFilterSlotPayload> type() {
        return TYPE;
    }

    static void handle(SetFilterSlotPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.containerMenu.containerId != payload.containerId()
                || !player.containerMenu.stillValid(player) || player.isSpectator()) {
            return;
        }
        if (ModList.get().isLoaded("sophisticatedcore")) {
            try {
                com.cinematicchests.compat.SophisticatedFilters.set(player.containerMenu, payload.slot(), payload.pattern());
            } catch (LinkageError e) {
                // a Sophisticated version this was not built for: the filter is left as it is
                CinematicChests.LOGGER.warn("[Cinematic Chests] could not set a Sophisticated filter slot (version mismatch?)", e);
            }
        }
    }
}

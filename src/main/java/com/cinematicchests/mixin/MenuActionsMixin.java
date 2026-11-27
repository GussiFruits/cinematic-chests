package com.cinematicchests.mixin;

import com.cinematicchests.StorageBlocks;
import com.cinematicchests.StorageMenus;
import dev.dyslexic.cinematic.station.MenuActions;
import dev.dyslexic.cinematic.station.Sessions;
import dev.dyslexic.cinematic.station.StationKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How storage blocks are opened. Cinematic Interactions asks the block state for its menu, which is right for most stations
 * but not for storage:
 * <ul>
 * <li>Vanilla storage needs vanilla's extra rules (an ender chest has no state menu at all, a shulker box needs room for its
 * lid, and opening counts a statistic and angers piglins): {@link StorageMenus#provider}.</li>
 * <li>A modded block may open its menu with extra data the client needs, which a bare menu provider would leave out. It is
 * opened by running its own right-click instead: {@link StorageMenus#useBlock}.</li>
 * </ul>
 * {@code open} starts the scene; {@code provider} is also what the fallback window ({@code Sessions.openWindow}) uses.
 */
@Mixin(value = MenuActions.class, remap = false)
public abstract class MenuActionsMixin {
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private static void cinematicchests$open(ServerPlayer player, Sessions.Session session, CallbackInfoReturnable<Boolean> cir) {
        StorageBlocks.Entry entry = session.kind() == StationKind.ADDON ? StorageBlocks.get(player.level().getBlockState(session.pos())) : null;
        if (entry != null && !entry.vanilla()) {
            StorageMenus.useBlock(player, session.pos(), session.side());
            cir.setReturnValue(player.containerMenu != player.inventoryMenu && MenuActions.isOpen(session, player.containerMenu));
        }
    }

    @Inject(method = "provider", at = @At("HEAD"), cancellable = true)
    private static void cinematicchests$provider(ServerPlayer player, BlockPos pos, StationKind kind, CallbackInfoReturnable<MenuProvider> cir) {
        StorageBlocks.Entry entry = kind == StationKind.ADDON ? StorageBlocks.get(player.level().getBlockState(pos)) : null;
        if (entry == null) {
            return;
        }
        if (entry.vanilla()) {
            cir.setReturnValue(StorageMenus.provider(player, pos));
        } else {
            // only reached for the fallback window: the block opens it itself, nothing is left for the caller to open
            StorageMenus.useBlock(player, pos, StorageMenus.faceToward(player, pos));
            cir.setReturnValue(null);
        }
    }
}

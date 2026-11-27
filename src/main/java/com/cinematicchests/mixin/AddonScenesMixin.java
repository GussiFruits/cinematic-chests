package com.cinematicchests.mixin;

import com.cinematicchests.ChestConfig;
import com.cinematicchests.StorageBlocks;
import dev.dyslexic.cinematic.api.AddonStations;
import dev.dyslexic.cinematic.api.client.AddonScenes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A storage block turned off in the client config has no scene: Cinematic Interactions then opens its normal window, and does
 * not offer it as a station to move over to.
 */
@Mixin(value = AddonScenes.class, remap = false)
public abstract class AddonScenesMixin {
    @Inject(method = "available", at = @At("HEAD"), cancellable = true)
    private static void cinematicchests$available(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        StorageBlocks.Entry entry = StorageBlocks.get(state);
        if (entry == null || ChestConfig.enabled(entry)) {
            return;
        }
        AddonStations.Definition definition = AddonStations.forBlock(state);
        if (definition != null && StorageBlocks.owns(definition.id())) {
            cir.setReturnValue(false);
        }
    }
}

package com.cinematicchests.mixin;

import com.cinematicchests.client.StorageScene;
import dev.dyslexic.cinematic.client.Scene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The other half of the double chest being worked is the same chest, not a station to move over to. */
@Mixin(value = Scene.class, remap = false)
public abstract class SceneMixin {
    @Inject(method = "findNeighbor", at = @At("TAIL"))
    private static void cinematicchests$findNeighbor(CallbackInfo ci) {
        if (Scene.neighbor != null && Scene.station instanceof StorageScene<?> scene && Scene.neighbor.equals(scene.partner())) {
            Scene.neighbor = null;
            Scene.neighborKind = null;
        }
    }
}

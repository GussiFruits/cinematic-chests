package com.cinematicchests.mixin;

import com.cinematicchests.client.StorageScene;
import dev.dyslexic.cinematic.client.Scene;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A storage block's jukebox upgrade (Sophisticated's) sends music notes up out of the block, which in a scene is right in front of
 * the camera. While a storage scene is being worked, they are not added. Matched by class name, so nothing here needs that mod.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void cinematicchests$add(Particle particle, CallbackInfo ci) {
        if (Scene.active() && Scene.station instanceof StorageScene<?> && particle.getClass().getSimpleName().equals("JukeboxUpgradeNoteParticle")) {
            ci.cancel();
        }
    }
}

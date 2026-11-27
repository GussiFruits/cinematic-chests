package com.cinematicchests.mixin;

import com.cinematicchests.client.StorageNames;
import dev.dyslexic.cinematic.client.Scene;
import dev.dyslexic.cinematic.client.SceneScreen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The "move over" label names the station under the cursor: a storage block by the item it gives (see {@link StorageNames}). */
@Mixin(value = SceneScreen.class, remap = false)
public abstract class SceneScreenMixin {
    @Redirect(method = "drawMoveOver",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getName()Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent cinematicchests$name(Block block) {
        return StorageNames.of(block, Scene.neighbor);
    }
}

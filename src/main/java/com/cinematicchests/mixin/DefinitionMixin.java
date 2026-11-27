package com.cinematicchests.mixin;

import com.cinematicchests.StorageBlocks;
import dev.dyslexic.cinematic.api.AddonStations;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A modded storage block may open any menu class, so its station is registered for {@link AbstractContainerMenu}. That would
 * also accept the player's own inventory menu (which is what is "open" once the block's menu has closed), so the session would
 * outlive its container. Here such a definition accepts any menu except the inventory.
 */
@Mixin(value = AddonStations.Definition.class, remap = false)
public abstract class DefinitionMixin {
    @Inject(method = "accepts", at = @At("HEAD"), cancellable = true)
    private void cinematicchests$accepts(AbstractContainerMenu menu, CallbackInfoReturnable<Boolean> cir) {
        AddonStations.Definition self = (AddonStations.Definition) (Object) this;
        if (self.menuClass() == AbstractContainerMenu.class && StorageBlocks.owns(self.id())) {
            cir.setReturnValue(menu != null && !(menu instanceof InventoryMenu));
        }
    }
}
